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

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.data.TripVehiclesDataSource
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.time.ServiceDate
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.SingleFlight

/**
 * The network I/O seam of the trip data layer: every OBA call the repository needs, and nothing
 * else. It knows nothing about the [TripStateCache] — it takes IDs, returns values, and leaves
 * caching and hydration to [DefaultTripObservationRepository]. Pulling the calls behind this
 * interface lets the repository's polling Flows be unit-tested against a fake fetcher with no
 * network.
 *
 * Volatile status ([tripDetails], [tripsForRoute]) is re-fetched every poll tick, so it isn't
 * deduplicated. Immutable resources ([tripSchedule], [shape]) are coalesced with [SingleFlight] —
 * concurrent callers for the same resource share one fetch — so a route backfill watching dozens
 * of trips can't fan out into dozens of duplicate requests.
 *
 * The polled calls return [Result] so a poll loop can tell *why* it failed (a 429 wants a longer wait
 * than a dropped connection — see [nextPollDelayMs]); the one-shot fetches resolve failures to null.
 * The data source logs every failure, and callers retry on their next attempt.
 */
interface TripObservationFetcher {

    suspend fun tripDetails(tripId: String): Result<TripDetails>

    /** See [TripVehiclesDataSource.tripsForRoute] for [includeSchedules]. */
    suspend fun tripsForRoute(routeId: String, includeSchedules: Boolean): Result<RouteTrips>

    suspend fun tripSchedule(tripId: String): ObaTripSchedule?

    suspend fun shape(shapeId: String): Polyline?

    /**
     * A trip's route identity + shapeId, independent of the trip-details/trips-for-route polls above —
     * for a trip that may be on a different route entirely (the interlining continuation's neighbor
     * trip, #1691). Immutable like [tripSchedule]/[shape], so it's [SingleFlight]-coalesced too.
     */
    suspend fun tripRouteInfo(tripId: String): TripRouteInfo?
}

/**
 * What one trip-details poll distilled for the store: the vehicle [observations], the trip [schedule]
 * and [serviceDate], the [shapeId] of the polled trip (for on-demand shape activation), and the trip
 * the vehicle currently reports active ([vehicleActiveTripId], null without a status).
 */
data class TripDetails(
    val observations: List<TripObservation>,
    val schedule: ObaTripSchedule?,
    val serviceDate: ServiceDate?,
    val vehicleActiveTripId: String?,
    val shapeId: String?
)

private const val MAX_CONCURRENT_FETCHES = 2
private const val TAG = "TripObservationFetcher"

@Singleton
class DefaultTripObservationFetcher @Inject constructor(
    private val dataSource: TripVehiclesDataSource
) : TripObservationFetcher {

    /**
     * Process-lifetime scope the coalesced fetches run on; the network calls hop to
     * [Dispatchers.IO] under [fetchPermits].
     */
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Admits at most [MAX_CONCURRENT_FETCHES] schedule/shape requests in flight at once, so a backfill
     * observing dozens of trips can't take over the shared HTTP client's per-host connections. This
     * bounds concurrency, not the request count the server's rate limit counts: that is held down by
     * the callers asking for less. A permit is held for the whole request; a dispatcher's
     * `limitedParallelism` can't do this, because a suspended Retrofit call gives its thread back
     * while the request is still on the network.
     */
    private val fetchPermits = Semaphore(MAX_CONCURRENT_FETCHES)

    private val scheduleFetches = SingleFlight<String, ObaTripSchedule>(fetchScope)
    private val shapeFetches = SingleFlight<String, Polyline>(fetchScope)
    private val tripRouteInfoFetches = SingleFlight<String, TripRouteInfo>(fetchScope)

    override suspend fun tripDetails(tripId: String): Result<TripDetails> = dataSource.tripDetails(tripId).map { routeTrips ->
        // A single trip-details fetch yields one trip; its ObaTripDetails carries the
        // schedule + (unfiltered) status the distillation needs.
        val details = routeTrips.trips.firstOrNull()
        TripDetails(
            observations = routeTrips.toObservations(),
            schedule = details?.schedule,
            serviceDate = serviceDateOrNull(details?.status?.serviceDate ?: 0),
            vehicleActiveTripId = details?.status?.activeTripId,
            shapeId = routeTrips.trip(tripId)?.shapeId?.takeIf { it.isNotEmpty() }
        )
    }

    override suspend fun tripsForRoute(routeId: String, includeSchedules: Boolean): Result<RouteTrips> = dataSource.tripsForRoute(routeId, includeSchedules)

    override suspend fun tripSchedule(tripId: String): ObaTripSchedule? = scheduleFetches.run(tripId) {
        boundedFetch("schedule for $tripId") { dataSource.tripSchedule(tripId) }
    }

    override suspend fun shape(shapeId: String): Polyline? = shapeFetches.run(shapeId) {
        // The data source does the (shared-algorithm) decode, so it runs off the main thread here.
        boundedFetch("shape $shapeId") { dataSource.shape(shapeId) }
    }

    override suspend fun tripRouteInfo(tripId: String): TripRouteInfo? = tripRouteInfoFetches.run(tripId) {
        dataSource.trip(tripId).getOrNull()
    }

    /**
     * Runs [fetch] on [Dispatchers.IO] under one of [fetchPermits], resolving a failure (already logged
     * by the data source) or an empty answer to null.
     */
    private suspend fun <T : Any> boundedFetch(what: String, fetch: suspend () -> Result<T?>): T? = fetchPermits
        .withPermit { withContext(Dispatchers.IO) { fetch() } }
        .getOrNull()
        .also { if (it == null) Log.w(TAG, "Fetch of $what yielded nothing") }
}
