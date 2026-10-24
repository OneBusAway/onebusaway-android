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
import org.onebusaway.android.api.isRateLimited
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.Polyline

const val DEFAULT_POLL_INTERVAL_MS = 10_000L
private const val MAX_BACKOFF_MULTIPLIER = 8
private const val FIRST_RETRY_DELAY_MS = 1_000L

/**
 * Next poll delay after [consecutiveFailures] failed polls in a row: [intervalMs] when it is 0,
 * otherwise an exponential retry doubling per failure, capped at [MAX_BACKOFF_MULTIPLIER] times
 * [intervalMs]. Pure, so it is unit-testable without coroutine machinery.
 *
 * Where the retry starts depends on [rateLimited] — whether the latest failure was an HTTP 429:
 * - **Otherwise** (a dropped connection, a 5xx) it starts at [FIRST_RETRY_DELAY_MS], below the
 *   interval: the failed poll may be a session's first, with nothing on screen yet, and a prompt retry
 *   usually lands.
 * - **Rate limited** it starts at twice [intervalMs]. A prompt retry is refused too and may count
 *   against the quota: probing Puget Sound's edge limiter (2026-10-23) found a refusal still standing
 *   10 s after the first 429 and lifted by 20 s.
 */
internal fun nextPollDelayMs(consecutiveFailures: Int, intervalMs: Long, rateLimited: Boolean = false): Long {
    if (consecutiveFailures <= 0) return intervalMs
    val cap = intervalMs * MAX_BACKOFF_MULTIPLIER
    val first = if (rateLimited) intervalMs * 2 else FIRST_RETRY_DELAY_MS
    // Bound the shift so a long outage can't overflow; the cap is reached long before 2^30 s.
    val retry = first shl (consecutiveFailures - 1).coerceAtMost(30)
    return retry.coerceAtMost(cap)
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
     * stream). Consecutive failures back off exponentially, longer for a 429 (see [nextPollDelayMs]).
     */
    fun tripDetailsStream(
        tripId: String,
        intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Flow<Unit>

    /**
     * Polls trips-for-route for [routeId] every [intervalMs] while collected, records each OK
     * response into the store, backfills schedules and shapes for the observed trips, and emits
     * each OK response. Backfills are children of the collection, so they stop with it.
     *
     * Schedules ride in the poll itself: while some active trip lacks one, the next poll asks
     * trips-for-route to include them, so a route's schedules cost one request instead of one
     * trip-details per vehicle. That per-vehicle burst was what tripped the server's rate limit.
     * Failures back off as [tripDetailsStream]'s do.
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

    /** Returns the immutable trip schedule, fetching and recording it on the first cache miss. */
    suspend fun ensureSchedule(tripId: String): ObaTripSchedule?

    /**
     * Resolves [tripId]'s route identity + shapeId from the standalone `trip` endpoint — for a trip
     * the current route's poll never fetched (the interlining continuation's neighbor trip, #1691).
     * Tap-driven (called once per vehicle selection by [org.onebusaway.android.map.RouteMapController],
     * not polled): a successful resolution is cached by trip id (a trip's route never changes), so
     * re-selecting the same vehicle later is free; a failure isn't cached, so the next selection retries.
     */
    suspend fun resolveNeighborTrip(tripId: String): TripRouteInfo?
}

@Singleton
class DefaultTripObservationRepository @Inject constructor(
    private val fetcher: TripObservationFetcher
) : TripObservationRepository {

    private val cache = TripStateCache()
    private val shapeCache = ShapeCache()
    private val neighborTripCache = NeighborTripCache()

    override fun lookupTripState(tripId: String?): TripState? = cache.lookupTripState(tripId)

    override fun tripDetailsStream(tripId: String, intervalMs: Long): Flow<Unit> = flow {
        pollWithBackoff(intervalMs, fetch = { fetcher.tripDetails(tripId) }) { details ->
            recordTripDetails(tripId, details)
            // Emit a tick so a collector can react to a fresh poll (the store holds the data).
            emit(Unit)
        }
    }

    override fun routeVehiclesStream(routeId: String, intervalMs: Long): Flow<RouteTrips> = channelFlow {
        // Nothing is known about the route's trips yet, so the first poll asks for their schedules.
        var includeSchedules = true
        // Trips a schedules-included poll has already had its chance at (see backfillSchedulesAndShapes).
        val offeredSchedules = HashSet<String>()
        pollWithBackoff(intervalMs, fetch = { fetcher.tripsForRoute(routeId, includeSchedules) }) { response ->
            recordTripsForRoute(response)
            // Backfills launch into this channelFlow scope.
            includeSchedules = backfillSchedulesAndShapes(response, includeSchedules, offeredSchedules)
            send(response)
        }
    }

    /**
     * Polls [fetch] until cancelled, handing each OK result to [onSuccess]. The next poll waits
     * [intervalMs] after a success, or [nextPollDelayMs]'s backoff after a failure (longer for a 429).
     */
    private suspend inline fun <T> pollWithBackoff(intervalMs: Long, fetch: () -> Result<T>, onSuccess: (T) -> Unit): Nothing {
        var failures = 0
        while (true) {
            val result = fetch()
            result.onSuccess {
                failures = 0
                onSuccess(it)
            }.onFailure { failures++ }
            delay(nextPollDelayMs(failures, intervalMs, rateLimited = result.exceptionOrNull()?.isRateLimited == true))
        }
    }

    override suspend fun ensureShape(tripId: String, shapeId: String): Polyline? {
        // The trip already carries its shape — nothing to fetch or hydrate.
        cache.lookupTripState(tripId)?.polyline?.let { return it }
        // Reuse the shape shared by every trip on this route, fetching it once on the first miss.
        // Concurrent in-flight first-misses coalesce in the fetcher's SingleFlight and resolve to the
        // same instance, so they store the same Polyline here too.
        val polyline = shapeCache.get(shapeId)
            ?: fetcher.shape(shapeId)?.also { shapeCache.put(shapeId, it) }
        return polyline?.also { cache.putPolyline(tripId, it) }
    }

    override suspend fun ensureSchedule(tripId: String): ObaTripSchedule? {
        cache.lookupTripState(tripId)?.schedule?.let { return it }
        return fetcher.tripSchedule(tripId)?.also { cache.putSchedule(tripId, it) }
    }

    override suspend fun resolveNeighborTrip(tripId: String): TripRouteInfo? = neighborTripCache.get(tripId)
        ?: fetcher.tripRouteInfo(tripId)?.also { neighborTripCache.put(tripId, it) }

    /** Records everything a trip details poll carries: shapeId/active-trip, schedule, service date, observations. */
    private fun recordTripDetails(tripId: String, details: TripDetails) {
        val localTimeMs = WallTime.now()
        cache.putTripDetails(tripId, details.vehicleActiveTripId, details.shapeId)
        cache.putSchedule(tripId, details.schedule)
        cache.putServiceDate(tripId, details.serviceDate)
        details.observations.forEach { cache.record(it, localTimeMs) }
    }

    /** Records a trips-for-route response's observations, and the schedules it carries when it was asked for them. */
    private fun recordTripsForRoute(response: RouteTrips) {
        val localTimeMs = WallTime.now()
        response.toObservations().forEach { cache.record(it, localTimeMs) }
        // An entry's schedule is its own trip's (the entry id), not necessarily the active trip's.
        // Reading it converts the wire schedule, so skip the trips already holding one.
        response.trips.forEach { if (cache.lookupTripState(it.id)?.schedule == null) cache.putSchedule(it.id, it.schedule) }
    }

    /**
     * Backfills what the store still lacks for each active trip in a trips-for-route response,
     * launching the fetches into the receiver scope (the channelFlow's) so they're cancelled with the
     * collection. Returns whether the next poll should ask for schedules.
     *
     * A missing schedule normally waits for that next poll, which carries every trip's in one request.
     * A trip still without one after a [schedulesIncluded] response is one whose own entry wasn't in
     * it — e.g. during a block rollover two entries report the same active trip, and the wire dedup
     * (`dedupeByActiveTripKeepingBestFix`) may keep the other trip's entry, schedule and all. Asking
     * again wouldn't help, so such a trip goes into [offered] and is fetched on its own from then on;
     * otherwise one whose fetch failed would turn every other poll into a schedules-included one.
     */
    private fun CoroutineScope.backfillSchedulesAndShapes(
        response: RouteTrips,
        schedulesIncluded: Boolean,
        offered: MutableSet<String>
    ): Boolean {
        var needSchedules = false
        response.forEachActiveTrip { tripId, _, activeTrip ->
            if (cache.lookupTripState(tripId)?.schedule == null) {
                if (schedulesIncluded) offered += tripId
                if (tripId in offered) {
                    launch { ensureSchedule(tripId) }
                } else {
                    needSchedules = true
                }
            }
            activeTrip.shapeId?.let { shapeId ->
                launch { ensureShape(tripId, shapeId) } // no-op when the polyline is already cached
            }
        }
        return needSchedules
    }
}
