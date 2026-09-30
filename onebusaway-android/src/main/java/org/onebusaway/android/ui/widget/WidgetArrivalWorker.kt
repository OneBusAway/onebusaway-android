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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.onebusaway.android.api.data.StopArrivals
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.app.di.WidgetEntryPoint
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
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
        val deployments = entryPoint.widgetDeployments()

        // Requests go to the app's current server, so only fetch when that's this widget's (see WidgetDeployment).
        when (val current = runBlocking { deployments.awaitCurrent(REGION_LOAD_TIMEOUT) }) {
            CurrentServer.Loading -> {
                Log.w(TAG, "doWork id=$widgetId region still loading, retrying")
                return Result.retry()
            }
            else -> if (!config.isServedBy(current)) {
                Log.d(TAG, "doWork id=$widgetId app is on another server, paused")
                StopTimesWidget.updateArrivalsFromCache(applicationContext, widgetId, allowStaleRefresh = false)
                return Result.success()
            }
        }

        val fetchResult = runBlocking { fetchArrivals(entryPoint.stopArrivalsDataSource(), config.stopId) }
        val snapshot = fetchResult.getOrNull()
        if (snapshot == null) {
            Log.w(TAG, "doWork id=$widgetId fetch failed, retrying")
            // allowStaleRefresh = false: this call is itself reacting to a failed fetch, so it must not
            // turn around and enqueue another one — that would bypass WorkManager's retry backoff.
            StopTimesWidget.updateArrivalsFromCache(applicationContext, widgetId, allowStaleRefresh = false)
            return Result.retry()
        }

        val built = buildWidgetSnapshot(
            stopId = config.stopId,
            routeShortNames = config.routeShortNames,
            arrivals = snapshot.arrivals,
            serverNow = ServerTime(snapshot.currentTime),
            receivedAt = entryPoint.elapsedClock().now()
        )
        // Reconfigured mid-fetch: don't overwrite what the replacement run saves.
        val current = WidgetPrefs.loadConfig(applicationContext, widgetId)
        if (current == null || !built.isFor(current)) {
            Log.d(TAG, "doWork id=$widgetId config changed during fetch, discarding result")
            return Result.success()
        }
        // Server switched mid-fetch: the answer may be from the wrong one.
        if (!config.isServedBy(deployments.current())) {
            Log.d(TAG, "doWork id=$widgetId server changed during fetch, discarding result")
            StopTimesWidget.updateArrivalsFromCache(applicationContext, widgetId, allowStaleRefresh = false)
            return Result.success()
        }
        WidgetPrefs.saveSnapshot(applicationContext, widgetId, built)
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

    companion object {

        private const val TAG = "WidgetArrivalWorker"

        // Upper bound on waiting for the cold-start region load (a local read); then retry with backoff.
        private val REGION_LOAD_TIMEOUT = 30.seconds

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

/**
 * Converts a fetch into a [WidgetArrivalSnapshot]. Every configured route is included, empty ones as
 * "N/A". Departed arrivals are dropped before caching and all upcoming ones kept, so trips that depart
 * before the next fetch don't leave the row empty.
 */
internal fun buildWidgetSnapshot(
    stopId: String,
    routeShortNames: Map<String, String>,
    arrivals: List<ArrivalData>,
    serverNow: ServerTime,
    receivedAt: ElapsedTime
): WidgetArrivalSnapshot {
    val arrivalsByRoute = arrivals
        .filter { it.routeId in routeShortNames }
        .groupBy({ it.routeId }, { it.toSnapshotArrival() })
        .mapValues { (_, routeArrivals) -> routeArrivals.filter { it.isShownAt(serverNow) }.sortedBy { it.displayTimeMs } }

    val (routesWithArrivals, routesWithoutArrivals) = routeShortNames
        .map { (routeId, shortName) -> WidgetArrivalSnapshot.Route(shortName, arrivalsByRoute[routeId].orEmpty()) }
        .partition { it.arrivals.isNotEmpty() }

    return WidgetArrivalSnapshot(
        stopId = stopId,
        routeIds = routeShortNames.keys,
        serverTimeAtFetchMs = serverNow.epochMs,
        receivedAtElapsedMs = receivedAt.ms,
        // Routes with arrivals sorted soonest-first; routes with no arrivals appended after.
        routes = routesWithArrivals.sortedBy { it.arrivals.first().displayTimeMs } + routesWithoutArrivals
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
        isPredicted = hasPrediction,
        isCanceled = status == Status.CANCELED
    )
}
