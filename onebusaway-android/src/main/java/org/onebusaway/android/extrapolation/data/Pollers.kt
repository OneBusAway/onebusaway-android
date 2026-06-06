/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
@file:JvmName("Pollers")

package org.onebusaway.android.extrapolation.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripsForRouteRequest
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/*
 * The store-hydrating side of the trip data layer: everything in this file fetches from the
 * network and records the result into the trip store, translated through the response adapters
 * in Adapters.kt. Recurring pollers re-fetch volatile vehicle
 * status — it ages in seconds — on an interval, with lifecycles bound to the screen that is
 * watching (start in onResume, stop in onPause); fetchTripDetailsOnce is the one-shot variant
 * for explicit user refreshes; and the route poller's backfill hydrates immutable resources by
 * composing the pure fetchers in Fetchers.kt with TripStore.
 */

private const val DEFAULT_POLL_INTERVAL_MS = 10_000L
private const val TAG = "Pollers"

/** Process-lifetime scope for fire-and-forget one-shot fetches; never cancelled. */
private val oneShotScope = MainScope()

/**
 * Fetches trip details for [tripId] once and records everything the response carries: the
 * response writeback, schedule, and service date on the polled trip, plus the observation of
 * the vehicle's active trip. The writebacks happen even for status-less responses — a
 * schedule-only trip still renders its schedule from the cached response. This is the single
 * hydration point for details responses; UI code only reads. Shared by [TripDetailsPoller] and
 * [fetchTripDetailsOnce]. Failures are logged and swallowed; the next attempt retries.
 */
private suspend fun fetchAndRecordTripDetails(ctx: Context, tripId: String) {
    try {
        val localTimeMs = System.currentTimeMillis()
        val response =
                withContext(Dispatchers.IO) {
                    ObaTripDetailsRequest.newRequest(ctx, tripId).call()
                }
        if (response.code == ObaApi.OBA_OK) {
            putTripDetailsResponse(tripId, response.status?.activeTripId, response)
            putSchedule(tripId, response.schedule)
            putServiceDate(tripId, response.status?.serviceDate ?: 0)
            response.toObservations().forEach { record(it, localTimeMs) }
        }
    } catch (e: CancellationException) {
        throw e // poller stopped mid-fetch — not a failure
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch trip details for $tripId", e)
    }
}

/**
 * Polls trip details every [intervalMs] and records responses into the trip store. Lifecycle is
 * owned by the caller: call [start] in onResume, [stop] in onPause.
 */
class TripDetailsPoller
@JvmOverloads
constructor(private val tripId: String, private val intervalMs: Long = DEFAULT_POLL_INTERVAL_MS) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val ctx = Application.get().applicationContext
        job =
                MainScope().launch {
                    while (isActive) {
                        fetchAndRecordTripDetails(ctx, tripId)
                        delay(intervalMs)
                    }
                }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Polls trips-for-route every [intervalMs], records responses into the trip store, backfills
 * schedules and shapes for the observed trips, and delivers each response to an optional callback
 * on the main thread. Lifecycle is owned by the caller; stopping the poller also cancels its
 * in-progress backfills.
 */
class RoutePoller
@JvmOverloads
constructor(
        private val routeId: String,
        private val callback: ResponseCallback? = null,
        private val intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    fun interface ResponseCallback {
        fun onResponse(response: ObaTripsForRouteResponse)
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val ctx = Application.get().applicationContext
        job =
                MainScope().launch {
                    while (isActive) {
                        try {
                            val localTimeMs = System.currentTimeMillis()
                            val response =
                                    withContext(Dispatchers.IO) {
                                        ObaTripsForRouteRequest.Builder(ctx, routeId)
                                                .setIncludeStatus(true)
                                                .build()
                                                .call()
                                    }
                            if (response.code == ObaApi.OBA_OK) {
                                response.toObservations().forEach { record(it, localTimeMs) }
                                prefetchSchedulesAndShapes(response)
                                callback?.onResponse(response)
                            }
                        } catch (e: CancellationException) {
                            throw e // poller stopped mid-fetch — not a failure
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch trips for route $routeId", e)
                        }
                        delay(intervalMs)
                    }
                }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Backfills schedules and shapes for every active trip in a trips-for-route response that doesn't
 * already have them, fetching via Fetchers.kt and hydrating the trip store. Launched into the
 * route poller's scope, so backfills are cancelled with the poller.
 */
private fun CoroutineScope.prefetchSchedulesAndShapes(response: ObaTripsForRouteResponse) {
    response.forEachActiveTrip { tripId, _, activeTrip ->
        val state = lookupTripState(tripId)
        if (state?.schedule == null) {
            launch { fetchTripSchedule(tripId)?.let { putSchedule(tripId, it) } }
        }
        val shapeId = activeTrip.shapeId
        if (shapeId != null) {
            launch { ensureShape(tripId, shapeId) } // no-op when the polyline is already cached
        }
    }
}

/**
 * Returns the trip's polyline, fetching it via [fetchShape] and recording it when absent.
 * Shared by the route poller's backfill and the trip map's on-demand activation.
 */
suspend fun ensureShape(tripId: String, shapeId: String): Polyline? =
        lookupTripState(tripId)?.polyline
                ?: fetchShape(shapeId)?.also { putPolyline(tripId, it) }

/**
 * Fire-and-forget one-shot trip details fetch for UI refresh actions. Records the result into
 * the trip store; does not notify callers on success or failure. The one-shot sibling of
 * [TripDetailsPoller], for when the user explicitly asks for fresh status.
 */
fun fetchTripDetailsOnce(tripId: String) {
    val ctx = Application.get().applicationContext
    oneShotScope.launch { fetchAndRecordTripDetails(ctx, tripId) }
}
