/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.onebusaway.android.api.data.StopArrivals
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.app.di.WidgetEntryPoint
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.ui.arrivals.DefaultArrivalsRepository

/**
 * Background worker that fetches arrival times for a widget's stop and saves the result to
 * [WidgetPrefs] as a [WidgetArrivalSnapshot]. Once saved, it triggers
 * [StopTimesWidget.updateArrivalsFromCache] to redraw the widget from the new snapshot.
 *
 * A plain (blocking) [Worker], not `CoroutineWorker`: `WorkManager`'s default `WorkerFactory`
 * constructs this by reflection (not Hilt), and `doWork()` already runs on WorkManager's own
 * background executor — exactly where `runBlocking` bridging to the `suspend`
 * [StopArrivalsDataSource.arrivals] is the sanctioned pattern, without adding a
 * `work-runtime-ktx` dependency this project doesn't otherwise have.
 */
class WidgetArrivalWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val widgetId = inputData.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.w(TAG, "doWork: invalid widget ID")
            return Result.failure()
        }

        val config = WidgetPrefs.loadConfig(applicationContext, widgetId)
        if (config == null || config.routeShortNames.isEmpty()) {
            // Widget was deleted, not configured yet, or has no routes selected.
            Log.d(TAG, "doWork id=$widgetId no usable config, skipping")
            return Result.success()
        }

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

        val fetchResult = runBlocking { fetchArrivals(entryPoint.stopArrivalsDataSource(), config.stopId) }
        val snapshot = fetchResult.getOrNull()
        if (snapshot == null) {
            Log.w(TAG, "doWork id=$widgetId fetch failed, retrying")
            // allowStaleRefresh = false: this call is itself reacting to a failed fetch, so it must not
            // turn around and enqueue another one — that would bypass WorkManager's retry backoff.
            StopTimesWidget.updateArrivalsFromCache(applicationContext, widgetId, allowStaleRefresh = false)
            return Result.retry()
        }

        WidgetPrefs.saveSnapshot(
            applicationContext,
            widgetId,
            buildSnapshot(snapshot, config.routeShortNames, entryPoint.elapsedClock())
        )
        Log.d(TAG, "doWork id=$widgetId fetch succeeded, updating widget")
        StopTimesWidget.updateArrivalsFromCache(applicationContext, widgetId)

        return Result.success()
    }

    /**
     * Fetches arrivals for [stopId], widening the look-ahead window until arrivals are found or the
     * maximum window is reached — the same policy [DefaultArrivalsRepository.getArrivals] uses. A
     * failed fetch stops widening immediately (its `getOrNull()` is null, ending the loop) and is
     * returned as-is so the caller can distinguish "network failure" (retry) from "stop just has no
     * upcoming service" (an empty-but-successful result).
     */
    private suspend fun fetchArrivals(dataSource: StopArrivalsDataSource, stopId: String): kotlin.Result<StopArrivals> {
        var minutesAfter = DefaultArrivalsRepository.MINUTES_AFTER_DEFAULT
        var result = dataSource.arrivals(stopId, minutesAfter)
        while (result.getOrNull()?.hasArrivals == false && minutesAfter < DefaultArrivalsRepository.MINUTES_AFTER_MAX) {
            minutesAfter += DefaultArrivalsRepository.MINUTES_AFTER_INCREMENT
            result = dataSource.arrivals(stopId, minutesAfter)
        }
        return result
    }

    /**
     * Converts a fetch into a [WidgetArrivalSnapshot], including every configured route — a route
     * with no arrivals in the response is still included, with an empty arrivals list, so the widget
     * always shows all of its configured routes (as "N/A") rather than silently dropping one.
     */
    private fun buildSnapshot(
        snapshot: StopArrivals,
        routeShortNames: Map<String, String>,
        elapsedClock: ElapsedClock
    ): WidgetArrivalSnapshot {
        val arrivalsByRoute = snapshot.arrivals
            .filter { it.routeId in routeShortNames }
            .groupBy { it.routeId }
            .mapValues { (_, arrivals) -> arrivals.sortedBy { it.toSnapshotArrival().displayTimeMs } }

        val routesWithArrivals = mutableListOf<WidgetArrivalSnapshot.Route>()
        val routesWithoutArrivals = mutableListOf<WidgetArrivalSnapshot.Route>()

        for ((routeId, shortName) in routeShortNames) {
            val arrivals = arrivalsByRoute[routeId]
            if (arrivals != null) {
                routesWithArrivals += WidgetArrivalSnapshot.Route(
                    shortName = shortName,
                    arrivals = arrivals.take(MAX_ARRIVALS_PER_ROUTE).map { it.toSnapshotArrival() }
                )
            } else {
                routesWithoutArrivals += WidgetArrivalSnapshot.Route(shortName, emptyList())
            }
        }

        // Routes with arrivals sorted soonest-first; routes with no arrivals appended after.
        routesWithArrivals.sortBy { it.arrivals.first().displayTimeMs }

        return WidgetArrivalSnapshot(
            serverTimeAtFetchMs = snapshot.currentTime,
            receivedAtElapsedMs = elapsedClock.now().ms,
            routes = routesWithArrivals + routesWithoutArrivals
        )
    }

    /**
     * Resolves the arrival/departure choice and the real-time-vs-scheduled decision exactly as
     * [org.onebusaway.android.ui.arrivals.ArrivalInfo]'s init block does, so the widget can never
     * disagree with the app's own arrivals board about which instant is "the" time for a trip.
     */
    private fun ArrivalData.toSnapshotArrival(): WidgetArrivalSnapshot.Arrival {
        val scheduled = if (stopSequence != 0) scheduledArrivalTime else scheduledDepartureTime
        val predictedTime = if (stopSequence != 0) predictedArrivalTime else predictedDepartureTime
        val hasPrediction = predicted && predictedTime != null
        val displayTime = if (hasPrediction) predictedTime else scheduled
        return WidgetArrivalSnapshot.Arrival(
            scheduledTimeMs = scheduled.epochMs,
            displayTimeMs = displayTime.epochMs,
            isPredicted = hasPrediction
        )
    }

    companion object {

        private const val TAG = "WidgetArrivalWorker"
        private const val MAX_ARRIVALS_PER_ROUTE = 4

        fun enqueue(context: Context, widgetId: Int) {
            val data = Data.Builder()
                .putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .build()

            val request = OneTimeWorkRequest.Builder(WidgetArrivalWorker::class.java)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_refresh_$widgetId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
