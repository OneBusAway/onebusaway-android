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

import org.onebusaway.android.api.data.TripVehiclesDataSource

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
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
 * Failures resolve to null (logged once); callers retry on their next attempt.
 */
interface TripObservationFetcher {

    suspend fun tripDetails(tripId: String): TripDetails?

    suspend fun tripsForRoute(routeId: String): RouteTrips?

    suspend fun tripSchedule(tripId: String): ObaTripSchedule?

    suspend fun shape(shapeId: String): Polyline?
}

/**
 * What one trip-details poll distilled for the store: the vehicle [observations], the trip [schedule]
 * and [serviceDate], the [shapeId] of the polled trip (for on-demand shape activation), and the trip
 * the vehicle currently reports active ([vehicleActiveTripId], null without a status).
 */
data class TripDetails(
    val observations: List<TripObservation>,
    val schedule: ObaTripSchedule?,
    val serviceDate: Long,
    val vehicleActiveTripId: String?,
    val shapeId: String?,
)

private const val MAX_CONCURRENT_FETCHES = 2
private const val TAG = "TripObservationFetcher"

@Singleton
class DefaultTripObservationFetcher @Inject constructor(
        private val dataSource: TripVehiclesDataSource
) : TripObservationFetcher {

    /**
     * Process-lifetime scope the coalesced fetches run on; the network calls hop to
     * [fetchDispatcher].
     */
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * All fetches run on this IO-dispatcher view, which admits at most [MAX_CONCURRENT_FETCHES] at
     * once — so a route backfill observing dozens of trips can't issue dozens of simultaneous API
     * requests.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val fetchDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_FETCHES)

    private val scheduleFetches = SingleFlight<String, ObaTripSchedule>(fetchScope)
    private val shapeFetches = SingleFlight<String, Polyline>(fetchScope)

    override suspend fun tripDetails(tripId: String): TripDetails? =
            guarded("trip details for $tripId") {
                // The data source returns Result; getOrThrow re-raises a non-OK code so guarded maps it to null.
                val routeTrips = dataSource.tripDetails(tripId).getOrThrow()
                // A single trip-details fetch yields one trip; its ObaTripDetails carries the
                // schedule + (unfiltered) status the distillation needs.
                val details = routeTrips.trips.firstOrNull()
                TripDetails(
                    observations = routeTrips.toObservations(),
                    schedule = details?.schedule,
                    serviceDate = details?.status?.serviceDate ?: 0,
                    vehicleActiveTripId = details?.status?.activeTripId,
                    shapeId = routeTrips.trip(tripId)?.shapeId?.takeIf { it.isNotEmpty() },
                )
            }

    override suspend fun tripsForRoute(routeId: String): RouteTrips? =
            guarded("trips for route $routeId") {
                // The data source returns Result; getOrThrow re-raises a non-OK code so guarded maps it to null.
                dataSource.tripsForRoute(routeId).getOrThrow()
            }

    /**
     * Runs [block], resolving any failure to null (logged) so a transient network error becomes a
     * skipped poll tick rather than a crashed Flow. [CancellationException] propagates — a stopped
     * poll is not a failure. (The SingleFlight-coalesced fetches guard themselves the same way.)
     */
    private suspend fun <T : Any> guarded(what: String, block: suspend () -> T?): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch $what", e)
                null
            }

    override suspend fun tripSchedule(tripId: String): ObaTripSchedule? =
            scheduleFetches.run(tripId) {
                guarded("schedule for $tripId") {
                    dataSource.tripSchedule(tripId).getOrThrow()
                }.also {
                    if (it == null) Log.w(TAG, "Schedule fetch for $tripId yielded no schedule")
                }
            }

    override suspend fun shape(shapeId: String): Polyline? =
            shapeFetches.run(shapeId) {
                // Bound concurrent fetches so a route backfill can't fan out into dozens at once;
                // the data source does the (shared-algorithm) decode. A failed Result re-raises via
                // getOrThrow and resolves to null in guarded, like the old null-coalescing path did.
                withContext(fetchDispatcher) {
                    guarded("shape for $shapeId") { dataSource.shape(shapeId).getOrThrow() }
                }.also {
                    if (it == null) Log.w(TAG, "Shape fetch for $shapeId yielded no polyline")
                }
            }
}
