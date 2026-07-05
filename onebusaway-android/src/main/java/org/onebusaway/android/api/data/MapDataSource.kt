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
package org.onebusaway.android.api.data

import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.contract.ShapeEntry
import org.onebusaway.android.api.contract.StopGrouping
import org.onebusaway.android.models.NearbyStops
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.util.PolylineDecoder

/**
 * Fetches map data (stops-for-location / stops-for-route) from the modernized OBA REST client,
 * adapting the wire references to the [ObaStop]/[ObaRoute] model interfaces so the map never sees a
 * DTO. Returns `success(null)` when there is no OBA endpoint to contact yet (no current region and no
 * custom API URL) — the controllers treat null as a no-op — and `failure` on IO / HTTP / non-OK code.
 */
interface MapDataSource {

    /**
     * [maxCount] caps how many stops the server returns (the map's LRU cache size): a dense viewport
     * fills the cache in fewer pans. The server clamps this to its own hard limit and sets
     * `limitExceeded` when more stops matched than were returned; null leaves the server default.
     */
    suspend fun nearbyStops(
        lat: Double, lon: Double, latSpan: Double, lonSpan: Double, maxCount: Int? = null,
    ): Result<NearbyStops?>

    suspend fun routeMap(routeId: String): Result<RouteMapData?>
}

class DefaultMapDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : MapDataSource {

    override suspend fun nearbyStops(
        lat: Double, lon: Double, latSpan: Double, lonSpan: Double, maxCount: Int?,
    ): Result<NearbyStops?> = api.callOrNull { service ->
        val data = service.stopsForLocation(
            lat = lat, lon = lon, latSpan = latSpan, lonSpan = lonSpan, maxCount = maxCount,
        ).requireData()
        NearbyStops(
            stops = data.list.map(::DtoStop),
            routes = data.references.routes.map(::DtoRoute),
            outOfRange = data.outOfRange,
            limitExceeded = data.limitExceeded,
        )
    }

    override suspend fun routeMap(routeId: String): Result<RouteMapData?> = api.callOrNull { service ->
        val data = service.stopsForRoute(routeId, includePolylines = true).requireData()
        val route = data.references.route(routeId)?.let(::DtoRoute)
        // The route's selectable directions + each stop's direction membership, from one pass over the
        // direction stop groups.
        val dirs = directionsFrom(data.entry.stopGroupings)
        // Decoding the route's shape polylines is the one bit of non-trivial CPU work in this layer;
        // offload just it (the Retrofit calls are already main-safe, like the other sources). Both the
        // whole-route merged set and each direction's own (cleaner, travel-ordered) shape are decoded
        // here; the controller draws the merged set for the whole route and a direction's own shape once
        // one is selected.
        val (wholeRoute, byDirection) = withContext(Dispatchers.Default) {
            data.entry.polylines.map { it.decode() } to
                // distinct() drops a shape a direction listed under more than one group, so it isn't
                // drawn (and arrow-stamped) twice over itself.
                dirs.polylinesByDirection.mapValues { (_, shapes) -> shapes.distinct().map { it.decode() } }
        }
        RouteMapData(
            route = route,
            agencyName = route?.agencyId?.let { data.references.agency(it)?.name },
            stops = data.references.stops.map(::DtoStop)
                .map { RouteMapStop(it, dirs.directionsByStop[it.id].orEmpty()) },
            routes = data.references.routes.map(::DtoRoute),
            directions = dirs.directions,
            polylines = wholeRoute,
            polylinesByDirection = byDirection,
        )
    }

    private fun ShapeEntry.decode(): List<android.location.Location> =
        PolylineDecoder.decodeLine(points, length)
}

/**
 * A route's selectable [directions], the [directionsByStop] membership, and each direction's own
 * (still-encoded) [polylinesByDirection] shapes — all from one pass over the stop groups.
 */
internal data class RouteDirections(
    val directions: List<RouteMapDirection>,
    val directionsByStop: Map<String, Set<Int>>,
    val polylinesByDirection: Map<Int, List<ShapeEntry>>,
)

/**
 * Derives a route's selectable directions and each stop's direction membership from its "direction"
 * stop groups, in a single pass over the groups. Each numeric group id becomes a [RouteMapDirection]
 * labelled with the group's display name (the headsign; blank when the group had none), de-duplicated
 * by id (first group wins) and ordered by id so the header/picker is stable; the same id is added to
 * every stop the group lists (a stop shared by two groups gets both). Pure; unit-tested.
 *
 * ASSUMPTION (no exact source, documented per the repo's no-heuristics rule): the numeric group id is
 * treated as the GTFS direction id — the value a vehicle's trip `directionId` is matched against for
 * the vehicle filter. That holds for a conformant `type: "direction"` grouping (ids 0/1). A grouping
 * that instead exposes branch/variant ids beyond 0/1 will still list those directions and their stops,
 * but no vehicle's trip `directionId` will match, so selecting such a direction shows its stops with an
 * empty vehicle layer. Non-numeric ids are dropped (they can't be a GTFS direction).
 *
 * A direction's shapes are accumulated across every group that carries its id (so a direction split
 * over several groups keeps all its branches); a direction whose groups carried no polylines is absent
 * from [RouteDirections.polylinesByDirection], and the caller falls back to the whole-route shape.
 */
internal fun directionsFrom(stopGroupings: List<StopGrouping>): RouteDirections {
    val directions = mutableListOf<RouteMapDirection>()
    val seen = mutableSetOf<Int>()
    val byStop = mutableMapOf<String, MutableSet<Int>>()
    val polylinesByDir = mutableMapOf<Int, MutableList<ShapeEntry>>()
    stopGroupings.forEach { grouping ->
        grouping.stopGroups.forEach { group ->
            val dir = group.id?.toIntOrNull() ?: return@forEach
            if (seen.add(dir)) directions += RouteMapDirection(dir, group.displayName.orEmpty())
            group.stopIds.forEach { byStop.getOrPut(it) { mutableSetOf() }.add(dir) }
            if (group.polylines.isNotEmpty()) {
                polylinesByDir.getOrPut(dir) { mutableListOf() } += group.polylines
            }
        }
    }
    return RouteDirections(directions.sortedBy { it.directionId }, byStop, polylinesByDir)
}
