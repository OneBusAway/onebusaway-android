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
package org.onebusaway.android.extrapolation.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

const val DEFAULT_POLL_INTERVAL_MS = 10_000L
private const val MAX_BACKOFF_MULTIPLIER = 8

/**
 * Next poll delay for a simple exponential backoff: [intervalMs] after a success, otherwise
 * double [previousDelayMs], floored at [intervalMs] and capped at [MAX_BACKOFF_MULTIPLIER] times
 * it. Pure, so it is unit-testable without coroutine machinery.
 */
internal fun nextPollDelayMs(previousDelayMs: Long, succeeded: Boolean, intervalMs: Long): Long {
    if (succeeded) return intervalMs
    return (previousDelayMs * 2).coerceIn(intervalMs, intervalMs * MAX_BACKOFF_MULTIPLIER)
}

/**
 * The read-and-poll surface over the trip store. Consumers get the current snapshot synchronously
 * via [lookupTripState] (cheap, fine for per-frame loops) and keep it fresh by collecting one of
 * the polling Flows.
 *
 * The Flows are **cold and per-collector**: each poll loop runs only while its Flow is collected
 * and stops the moment collection is cancelled, so a screen that collects in `viewModelScope` gets
 * exactly the view-scoped polling the old `TripDetailsPoller`/`RoutePoller` gave — no shared
 * singleton poller, no manual start/stop. Records flow one way into the shared store; consumers
 * read.
 */
interface TripObservationRepository {

    /** The current snapshot for [tripId], or null if it has never been recorded (or was evicted). */
    fun lookupTripState(tripId: String?): TripState?

    /**
     * Polls trip details for [tripId] every [intervalMs] while collected, records each OK response
     * into the store, and emits every completed response — error-coded ones included, so a
     * collector can render failures. Consecutive failures back off exponentially (see
     * [nextPollDelayMs]).
     */
    fun tripDetailsStream(
            tripId: String,
            intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Flow<ObaTripDetailsResponse>

    /**
     * Polls trips-for-route for [routeId] every [intervalMs] while collected, records each OK
     * response into the store, backfills schedules and shapes for the observed trips, and emits
     * each OK response. Backfills are children of the collection, so they stop with it.
     */
    fun routeVehiclesStream(
            routeId: String,
            intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Flow<ObaTripsForRouteResponse>

    /**
     * Returns the trip's polyline, fetching and recording it when absent. Shared by the route
     * backfill and the trip map's on-demand activation.
     */
    suspend fun ensureShape(tripId: String, shapeId: String): Polyline?
}

@Singleton
class DefaultTripObservationRepository @Inject constructor(
        private val fetcher: TripObservationFetcher
) : TripObservationRepository {

    private val cache = TripStateCache()

    override fun lookupTripState(tripId: String?): TripState? = cache.lookupTripState(tripId)

    override fun tripDetailsStream(tripId: String, intervalMs: Long): Flow<ObaTripDetailsResponse> =
            flow {
                var delayMs = intervalMs
                while (true) {
                    val response = fetcher.tripDetails(tripId)
                    val ok = response?.code == ObaApi.OBA_OK
                    if (response != null && ok) recordTripDetails(tripId, response)
                    // Emitted outside the recording, error responses included: a collector pacing
                    // its own UI can render the failure, and a bug in it propagates loudly.
                    if (response != null) emit(response)
                    delayMs = nextPollDelayMs(delayMs, ok, intervalMs)
                    delay(delayMs)
                }
            }

    override fun routeVehiclesStream(routeId: String, intervalMs: Long): Flow<ObaTripsForRouteResponse> =
            channelFlow {
                var delayMs = intervalMs
                while (true) {
                    val response = fetcher.tripsForRoute(routeId)
                    val ok = response?.code == ObaApi.OBA_OK
                    if (response != null && ok) {
                        recordTripsForRoute(response)
                        prefetchSchedulesAndShapes(response) // launched into this channelFlow scope
                        send(response)
                    }
                    delayMs = nextPollDelayMs(delayMs, ok, intervalMs)
                    delay(delayMs)
                }
            }

    override suspend fun ensureShape(tripId: String, shapeId: String): Polyline? =
            cache.lookupTripState(tripId)?.polyline
                    ?: fetcher.shape(shapeId)?.also { cache.putPolyline(tripId, it) }

    /** Records everything a trip details response carries: response, schedule, service date, status. */
    private fun recordTripDetails(tripId: String, response: ObaTripDetailsResponse) {
        val localTimeMs = System.currentTimeMillis()
        cache.putTripDetailsResponse(tripId, response.status?.activeTripId, response)
        cache.putSchedule(tripId, response.schedule)
        cache.putServiceDate(tripId, response.status?.serviceDate ?: 0)
        response.toObservations().forEach { cache.record(it, localTimeMs) }
    }

    private fun recordTripsForRoute(response: ObaTripsForRouteResponse) {
        val localTimeMs = System.currentTimeMillis()
        response.toObservations().forEach { cache.record(it, localTimeMs) }
    }

    /**
     * Backfills schedules and shapes for every active trip in a trips-for-route response that
     * doesn't already have them, launching the fetches into the receiver scope (the channelFlow's)
     * so they're cancelled with the collection.
     */
    private fun CoroutineScope.prefetchSchedulesAndShapes(response: ObaTripsForRouteResponse) {
        response.forEachActiveTrip { tripId, _, activeTrip ->
            if (cache.lookupTripState(tripId)?.schedule == null) {
                launch { fetcher.tripSchedule(tripId)?.let { cache.putSchedule(tripId, it) } }
            }
            activeTrip.shapeId?.let { shapeId ->
                launch { ensureShape(tripId, shapeId) } // no-op when the polyline is already cached
            }
        }
    }
}
