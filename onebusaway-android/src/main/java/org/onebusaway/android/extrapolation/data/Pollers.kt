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
import org.onebusaway.android.extrapolation.data.TripStore.lookupTripState
import org.onebusaway.android.extrapolation.data.TripStore.putPolyline
import org.onebusaway.android.extrapolation.data.TripStore.putSchedule
import org.onebusaway.android.extrapolation.data.TripStore.putServiceDate
import org.onebusaway.android.extrapolation.data.TripStore.putTripDetailsResponse
import org.onebusaway.android.extrapolation.data.TripStore.record
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteRequest
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/*
 * The store-hydrating side of the trip data layer: everything in this file fetches from the
 * network and records the result into the trip store, translated through the response adapters
 * in Adapters.kt. Recurring pollers re-fetch volatile vehicle
 * status — it ages in seconds — on an interval, with lifecycles bound to the screen that is
 * watching (start in onResume, stop in onPause); the route poller's backfill hydrates immutable
 * resources by composing the pure fetchers in Fetchers.kt with TripStore.
 */

private const val DEFAULT_POLL_INTERVAL_MS = 10_000L
private const val MAX_BACKOFF_MULTIPLIER = 8
private const val TAG = "Pollers"

/**
 * Next poll delay for a simple exponential backoff: [intervalMs] after a success, otherwise
 * double [previousDelayMs], floored at [intervalMs] and capped at [MAX_BACKOFF_MULTIPLIER]
 * times it. Pure so it is unit-testable without coroutine machinery.
 */
internal fun nextPollDelayMs(previousDelayMs: Long, succeeded: Boolean, intervalMs: Long): Long {
    if (succeeded) return intervalMs
    return (previousDelayMs * 2).coerceIn(intervalMs, intervalMs * MAX_BACKOFF_MULTIPLIER)
}

/**
 * Fetches trip details for [tripId] once and records everything the response carries: the
 * response writeback, schedule, and service date on the polled trip, plus the observation of
 * the vehicle's active trip. The writebacks happen even for status-less responses — a
 * schedule-only trip still renders its schedule from the cached response — but only for OK
 * responses: error-coded responses never enter the store, whose consumers assume usability.
 * This is the single hydration point for details responses; UI code only reads.
 *
 * Returns the response (whatever its code) so [TripDetailsPoller] can deliver it and pace its
 * retries, or null when the fetch threw.
 */
private suspend fun fetchAndRecordTripDetails(ctx: Context, tripId: String): ObaTripDetailsResponse? {
    return try {
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
        } else {
            Log.w(TAG, "Trip details fetch for $tripId returned code ${response.code}:" +
                    " ${response.text}")
        }
        response
    } catch (e: CancellationException) {
        throw e // poller stopped mid-fetch — not a failure
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch trip details for $tripId", e)
        null
    }
}

/**
 * Polls trip details every [intervalMs], records responses into the trip store, and delivers
 * every completed response — error-coded ones included, so callers can render failures — to an
 * optional callback on the main thread. Consecutive failures back the polling off
 * exponentially, up to [MAX_BACKOFF_MULTIPLIER] times the interval. Lifecycle is owned by the
 * caller: call [start] in onResume, [stop] in onPause.
 */
class TripDetailsPoller
@JvmOverloads
constructor(
        private val tripId: String,
        private val callback: ResponseCallback? = null,
        private val intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    fun interface ResponseCallback {
        fun onResponse(response: ObaTripDetailsResponse)
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val ctx = Application.get().applicationContext
        job =
                MainScope().launch {
                    var delayMs = intervalMs
                    while (isActive) {
                        val response = fetchAndRecordTripDetails(ctx, tripId)
                        // Delivered outside the fetch's try/catch: a bug in the callback's UI
                        // code propagates loudly instead of being swallowed every poll tick.
                        if (response != null) callback?.onResponse(response)
                        val succeeded = response != null && response.code == ObaApi.OBA_OK
                        delayMs = nextPollDelayMs(delayMs, succeeded, intervalMs)
                        delay(delayMs)
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
                    var delayMs = intervalMs
                    while (isActive) {
                        var response: ObaTripsForRouteResponse? = null
                        try {
                            val localTimeMs = System.currentTimeMillis()
                            response =
                                    withContext(Dispatchers.IO) {
                                        ObaTripsForRouteRequest.Builder(ctx, routeId)
                                                .setIncludeStatus(true)
                                                .build()
                                                .call()
                                    }
                            if (response.code == ObaApi.OBA_OK) {
                                response.toObservations().forEach { record(it, localTimeMs) }
                                prefetchSchedulesAndShapes(response)
                            } else {
                                Log.w(TAG, "Trips-for-route fetch for $routeId returned code" +
                                        " ${response.code}: ${response.text}")
                            }
                        } catch (e: CancellationException) {
                            throw e // poller stopped mid-fetch — not a failure
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch trips for route $routeId", e)
                            response = null
                        }
                        val succeeded = response != null && response.code == ObaApi.OBA_OK
                        // Delivered outside the try, so a bug in the map-overlay callback
                        // propagates loudly. OK-only — unlike TripDetailsPoller, this caller
                        // renders vehicles, not errors.
                        if (succeeded) callback?.onResponse(response!!)
                        delayMs = nextPollDelayMs(delayMs, succeeded, intervalMs)
                        delay(delayMs)
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
