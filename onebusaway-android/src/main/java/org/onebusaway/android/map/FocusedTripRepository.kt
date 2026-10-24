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
package org.onebusaway.android.map

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.models.RouteStopGroup
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.SingleFlight

data class FocusedTripShape(
    val shapeId: String,
    val routeId: String,
    val routeColor: Int?,
    val points: List<GeoPoint>,
    val directionId: Int? = null
) {
    val routeDirection: RouteDirectionKey get() = RouteDirectionKey(routeId, directionId)
}

data class FocusedTripGeometry(val shapes: List<FocusedTripShape>) {
    companion object {
        /** No geometry — the pre-load state, and what a failed load falls back to. */
        val EMPTY = FocusedTripGeometry(emptyList())
    }
}

data class FocusedTripStops(
    val stopIdsByTripId: Map<String, List<String>>,
    val stopsById: Map<String, ObaStop>
) {
    companion object {
        /** No stops — the pre-load state, and what a failed load falls back to. */
        val EMPTY = FocusedTripStops(emptyMap(), emptyMap())
    }
}

/** Exact shape and scheduled-stop pass-throughs for the trips displayed at a focused stop. */
interface FocusedTripRepository {
    suspend fun getGeometry(trips: Set<FocusedTrip>): FocusedTripGeometry
    suspend fun getStops(trips: Set<FocusedTrip>): FocusedTripStops
}

@Singleton
class DefaultFocusedTripRepository internal constructor(
    private val observations: TripObservationRepository,
    private val stopsForRoute: StopsForRouteRepository,
    fetchScope: CoroutineScope,
    // Injectable so the JVM tests (which exercise the failure path) don't hit android.util.Log.
    private val logFailure: (message: String, cause: Exception) -> Unit = { message, cause ->
        Log.w(TAG, message, cause)
    }
) : FocusedTripRepository {

    @Inject
    constructor(
        observations: TripObservationRepository,
        stopsForRoute: StopsForRouteRepository
    ) : this(
        observations,
        stopsForRoute,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    private val shapeFetches = SingleFlight<String, List<GeoPoint>>(fetchScope)

    override suspend fun getGeometry(trips: Set<FocusedTrip>): FocusedTripGeometry = coroutineScope {
        val tripsWithShapes = ArrayList<FocusedTrip>()
        val fetchTripByShapeId = LinkedHashMap<String, FocusedTrip>()
        for (trip in trips) {
            trip.shapeId?.let { shapeId ->
                tripsWithShapes += trip
                fetchTripByShapeId.putIfAbsent(shapeId, trip)
            }
        }
        val pointsByShapeId = fetchTripByShapeId.values.map { trip ->
            async {
                val shapeId = checkNotNull(trip.shapeId)
                shapeId to fetchShape(trip.tripId, shapeId)
            }
        }.awaitAll().toMap()
        FocusedTripGeometry(
            tripsWithShapes
                .distinctBy { checkNotNull(it.shapeId) to it.routeDirection }
                .mapNotNull { trip ->
                    val shapeId = checkNotNull(trip.shapeId)
                    pointsByShapeId[shapeId]?.let { points ->
                        FocusedTripShape(
                            shapeId,
                            trip.routeId,
                            trip.routeColor,
                            points,
                            trip.directionId
                        )
                    }
                }
        )
    }

    override suspend fun getStops(trips: Set<FocusedTrip>): FocusedTripStops = coroutineScope {
        val schedules = async {
            stopListGroups(trips).map { group ->
                async {
                    val stopIds = groupStopList(group)
                    group.map { it.tripId to stopIds }
                }
            }.awaitAll().flatten()
        }
        val catalogs = async {
            trips.mapTo(LinkedHashSet()) { it.routeId }
                .map { routeId -> async { resolveOrNull("route stops $routeId") { fetchRouteStops(routeId) } } }
                .awaitAll()
        }
        val stopIdsByTripId = buildMap {
            for ((tripId, stopIds) in schedules.await()) {
                if (stopIds != null) put(tripId, stopIds)
            }
        }
        val stopsById = buildMap {
            for (groups in catalogs.await()) {
                groups?.forEach { group -> group.stops.forEach { putIfAbsent(it.id, it) } }
            }
        }
        FocusedTripStops(stopIdsByTripId, stopsById)
    }

    /**
     * The focused [trips] grouped so each group needs one schedule fetch for its stop list.
     *
     * HEURISTIC, human-signed-off in PR #2405. The assumption: trips of one route-direction that share
     * a shapeId stop at the same stops, so the first one's schedule stands in for the rest. Why: a
     * stop list is only in a trip's own schedule, one `trip-details` request each, and a focused stop
     * shows every trip due in the next hour, about 20–30 requests at once. That burst tripped the
     * server's rate limit, and no endpoint returns stop lists for a stop's trips in one request.
     * Failure mode: GTFS doesn't promise it. A trip that runs the same path but skips stops (an
     * express on the same street) is drawn with its group-mate's stops, so a few stops it passes
     * without stopping get markers. A trip with no shapeId is never grouped.
     */
    private fun stopListGroups(trips: Set<FocusedTrip>): List<List<FocusedTrip>> {
        val (shaped, unshaped) = trips.partition { it.shapeId != null }
        return shaped.groupBy { it.routeDirection to it.shapeId }.values + unshaped.map { listOf(it) }
    }

    /**
     * The stop list of [group]: a member's already-stored schedule if one has it (the route poll stores
     * active trips'), else its first member's, fetched. One fetch per group, never a retry through the
     * rest: a failure is usually a 429 or a lost connection, which every member would hit too.
     */
    private suspend fun groupStopList(group: List<FocusedTrip>): List<String>? {
        val schedule = group.firstNotNullOfOrNull { observations.lookupTripState(it.tripId)?.schedule }
            ?: group.first().let { trip -> resolveOrNull("schedule ${trip.tripId}") { observations.ensureSchedule(trip.tripId) } }
        return schedule?.stopTimes?.map { it.stopId }
    }

    // Caching + coalescing live in the shared StopsForRouteRepository, so the same fetch backs the
    // route overlay and route-info screen — a stop's routes are never fetched twice. A failure is
    // rethrown here so resolveOrNull catches and logs it (keeping a real API failure distinguishable
    // from genuinely empty data), like the schedule sibling above.
    private suspend fun fetchRouteStops(routeId: String): List<RouteStopGroup> = stopsForRoute.routeStopGroups(routeId).getOrThrow()

    /**
     * A failed trip/route is omitted (logged, so it stays distinguishable from genuinely absent data)
     * without cancelling successful siblings; cancellation still propagates.
     */
    private suspend fun <T> resolveOrNull(what: String, block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure("Focused-trip $what fetch failed", e)
        null
    }

    /**
     * Decoded points for a shape. Retention and fetch dedup are owned by the shared [Polyline] cache
     * inside [TripObservationRepository.ensureShape] (keyed by shapeId, no TTL — GTFS shapes are
     * immutable), so this holds no shape cache of its own: it coalesces concurrent decodes for one
     * shape and re-decodes the (already-cached) polyline per focus. The map is cheap and runs on a
     * focus change, not the per-frame path. Concurrent network fetches are bounded by the fetcher.
     */
    private suspend fun fetchShape(tripId: String, shapeId: String): List<GeoPoint>? = shapeFetches.run(shapeId) {
        // The cached [Polyline] already holds decoded [GeoPoint]s — just hand back its points.
        resolveOrNull("shape $shapeId") { observations.ensureShape(tripId, shapeId) }?.points
    }
}

private const val TAG = "FocusedTripRepository"
