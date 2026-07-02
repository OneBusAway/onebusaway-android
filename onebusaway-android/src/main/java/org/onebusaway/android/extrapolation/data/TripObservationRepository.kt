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
import org.onebusaway.android.models.RouteTrips
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
     * Keeps [tripId]'s data fresh in the store: while collected, polls trip details every [intervalMs]
     * and records each successful poll (status/schedule/shape) into the store, which callers read via
     * [lookupTripState]. Collect it to drive the polling for the trip's lifetime on screen; each
     * emission is a "polled" tick (the payload is [Unit] — the data lives in the store, not the
     * stream). Consecutive failures back off exponentially (see [nextPollDelayMs]).
     */
    fun tripDetailsStream(
            tripId: String,
            intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Flow<Unit>

    /**
     * Polls trips-for-route for [routeId] every [intervalMs] while collected, records each OK
     * response into the store, backfills schedules and shapes for the observed trips, and emits
     * each OK response. Backfills are children of the collection, so they stop with it.
     */
    fun routeVehiclesStream(
            routeId: String,
            intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Flow<RouteTrips>

    /**
     * Returns the trip's polyline, fetching and recording it when absent. Shared by the route
     * backfill and the trip map's on-demand activation. Trips on the same route share one shapeId,
     * so the resolved [Polyline] is deduplicated by shape: every such trip records the same instance
     * rather than its own copy.
     */
    suspend fun ensureShape(tripId: String, shapeId: String): Polyline?
}

@Singleton
class DefaultTripObservationRepository @Inject constructor(
        private val fetcher: TripObservationFetcher
) : TripObservationRepository {

    private val cache = TripStateCache()
    private val shapeCache = ShapeCache()

    override fun lookupTripState(tripId: String?): TripState? = cache.lookupTripState(tripId)

    override fun tripDetailsStream(tripId: String, intervalMs: Long): Flow<Unit> =
            flow {
                var delayMs = intervalMs
                while (true) {
                    // The fetcher resolves failures and non-OK codes to null (logged once).
                    val details = fetcher.tripDetails(tripId)
                    val ok = details != null
                    if (details != null) {
                        recordTripDetails(tripId, details)
                        // Emit a tick so a collector can react to a fresh poll (the store holds the data).
                        emit(Unit)
                    }
                    delayMs = nextPollDelayMs(delayMs, ok, intervalMs)
                    delay(delayMs)
                }
            }

    override fun routeVehiclesStream(routeId: String, intervalMs: Long): Flow<RouteTrips> =
            channelFlow {
                var delayMs = intervalMs
                while (true) {
                    // The fetcher resolves failures and non-OK codes to null (logged once).
                    val response = fetcher.tripsForRoute(routeId)
                    val ok = response != null
                    if (response != null) {
                        recordTripsForRoute(response)
                        prefetchSchedulesAndShapes(response) // launched into this channelFlow scope
                        send(response)
                    }
                    delayMs = nextPollDelayMs(delayMs, ok, intervalMs)
                    delay(delayMs)
                }
            }

    override suspend fun ensureShape(tripId: String, shapeId: String): Polyline? {
        // The trip already carries its shape — nothing to fetch or hydrate.
        cache.lookupTripState(tripId)?.polyline?.let { return it }
        // Reuse the shape shared by every trip on this route, fetching it once on the first miss.
        // Concurrent in-flight first-misses on the fetcher's confined dispatcher coalesce in its
        // SingleFlight and resolve to the same instance, so they store the same Polyline here too.
        val polyline = shapeCache.get(shapeId)
                ?: fetcher.shape(shapeId)?.also { shapeCache.put(shapeId, it) }
        return polyline?.also { cache.putPolyline(tripId, it) }
    }

    /** Records everything a trip details poll carries: shapeId/active-trip, schedule, service date, observations. */
    private fun recordTripDetails(tripId: String, details: TripDetails) {
        val localTimeMs = System.currentTimeMillis()
        cache.putTripDetails(tripId, details.vehicleActiveTripId, details.shapeId)
        cache.putSchedule(tripId, details.schedule)
        cache.putServiceDate(tripId, details.serviceDate)
        details.observations.forEach { cache.record(it, localTimeMs) }
    }

    private fun recordTripsForRoute(response: RouteTrips) {
        val localTimeMs = System.currentTimeMillis()
        response.toObservations().forEach { cache.record(it, localTimeMs) }
    }

    /**
     * Backfills schedules and shapes for every active trip in a trips-for-route response that
     * doesn't already have them, launching the fetches into the receiver scope (the channelFlow's)
     * so they're cancelled with the collection.
     */
    private fun CoroutineScope.prefetchSchedulesAndShapes(response: RouteTrips) {
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
