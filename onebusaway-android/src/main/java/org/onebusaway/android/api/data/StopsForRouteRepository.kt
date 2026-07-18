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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ShapeEntry
import org.onebusaway.android.api.contract.StopGrouping
import org.onebusaway.android.api.contract.StopsForRoute
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.extrapolation.data.BoundedLruCache
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.models.RouteStopGroup
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.PolylineDecoder
import org.onebusaway.android.util.SingleFlight
import org.onebusaway.android.util.runCatchingCancellable
import java.io.IOException

/**
 * The single caching entry point for stops-for-route. `stops-for-route` was formerly fetched down two
 * independent paths — the route/focused-stop stop list (`RouteStopsDataSource`, its own cache) and the
 * route map with shapes (`MapDataSource.routeMap`, uncached) — so "focus a stop, then open one of its
 * routes" hit the wire twice and the two projections could come from different fetches. This repository
 * fetches each route **once** (with polylines, the superset), coalescing concurrent requests and caching
 * the parsed result, and hands out both projections from that one fetch:
 *
 * - [routeStopGroups] — the per-direction stop list (route info + a focused stop's trips).
 * - [routeMap] — the full route map: stops tagged with direction membership, selectable directions,
 *   and decoded shapes (the route overlay).
 *
 * The wire DTO stays encapsulated here; callers only ever see the model projections.
 */
interface StopsForRouteRepository {

    /**
     * The route's stops grouped by direction. [Result.failure] on IO / HTTP / non-OK code **and** when
     * there is no endpoint to contact (no current region and no custom API URL) — the stop-list callers
     * treat "no region" as an error to surface, matching the legacy `RouteStopsDataSource`.
     */
    suspend fun routeStopGroups(routeId: String): Result<List<RouteStopGroup>>

    /**
     * The route's map data (stops + direction membership + decoded shapes), or `success(null)` when
     * there is no endpoint to contact yet (the map treats that as nothing-to-show). [Result.failure] on
     * IO / HTTP / non-OK code, matching the legacy `MapDataSource.routeMap`.
     */
    suspend fun routeMap(routeId: String): Result<RouteMapData?>
}

/**
 * A route's stop/shape topology (stops-per-direction and shapes) is effectively immutable within a
 * session and shared by every consumer of that route, so one bounded LRU keyed by routeId serves them
 * all with **no TTL** — a stale entry could only appear across a mid-session GTFS feed rollover (rare),
 * which a re-focus/reload re-fetches. The bound caps memory (each entry holds the route's stop
 * references and encoded shapes); evicting the least-recently-viewed route merely re-fetches it on
 * return, and a handful of routes covers ordinary browsing. (This retires the former 10-minute TTL on
 * the focused-stop route-stop cache, an unexplained freshness threshold on the same immutable data.)
 */
private const val MAX_CACHED_ROUTE_STOPS = 32

@Singleton
class DefaultStopsForRouteRepository internal constructor(
    fetchScope: CoroutineScope,
    cacheSize: Int = MAX_CACHED_ROUTE_STOPS,
    // The one wire call, injected so the JVM tests can exercise caching/coalescing without a live
    // ObaApiProvider. `success(null)` = no endpoint; `failure` = IO / HTTP / non-OK code.
    private val fetch: suspend (routeId: String) -> Result<EntryWithReferences<StopsForRoute>?>,
) : StopsForRouteRepository {

    @Inject
    constructor(api: ObaApiProvider) : this(
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        fetch = { routeId ->
            api.callOrNull { it.stopsForRoute(routeId, includePolylines = true).requireData() }
        },
    )

    // Only successful entries are cached; failures/no-endpoint aren't, so they re-fetch. The
    // SingleFlight coalesces concurrent first-misses for one route into a single wire call.
    private val cache = BoundedLruCache<String, EntryWithReferences<StopsForRoute>>(cacheSize)
    private val fetches = SingleFlight<String, Result<EntryWithReferences<StopsForRoute>?>>(fetchScope)

    override suspend fun routeStopGroups(routeId: String): Result<List<RouteStopGroup>> =
        entry(routeId).mapCatching { it?.toRouteStopGroups() ?: throw noEndpoint() }

    override suspend fun routeMap(routeId: String): Result<RouteMapData?> {
        val fetched = entry(routeId).getOrElse { return Result.failure(it) }
            ?: return Result.success(null) // no endpoint — nothing to show
        // Decoding the route's shape polylines is the one bit of non-trivial CPU work in this layer.
        // runCatchingCancellable rethrows a CancellationException (raised by withContext if the caller is
        // cancelled mid-decode) rather than reporting the abandoned work as a failure.
        return runCatchingCancellable { withContext(Dispatchers.Default) { fetched.toRouteMapData(routeId) } }
    }

    /**
     * The parsed stops-for-route entry for [routeId] — cached, coalesced, fetched with polylines.
     * `success(null)` = no endpoint; `failure` = IO / HTTP / non-OK code. `SingleFlight` carries the
     * outcome as a [Result] value (not by throwing) so a failure's original exception — e.g. the OBA
     * code the route-info error string reads — survives the coalescing rather than collapsing to null.
     * The block therefore always returns a non-null [Result]; a null back from [SingleFlight] means the
     * block itself crashed unexpectedly, which is surfaced as a failure (not masked as no-endpoint).
     */
    private suspend fun entry(routeId: String): Result<EntryWithReferences<StopsForRoute>?> {
        cache.get(routeId)?.let { return Result.success(it) }
        return fetches.run(routeId) {
            cache.get(routeId)?.let { return@run Result.success(it) }
            fetch(routeId).onSuccess { fetched -> fetched?.let { cache.put(routeId, it) } }
        } ?: Result.failure(IllegalStateException("stops-for-route fetch failed unexpectedly for $routeId"))
    }

    private fun noEndpoint() =
        IOException("No OBA API endpoint: no current region and no custom API URL set")
}

/**
 * Maps the stops-for-route payload to per-direction [RouteStopGroup]s, resolving each group's stop
 * ids against the references pool (ids with no resolvable stop are skipped), preserving group and stop
 * order. Pure, so it is JVM-unit-tested.
 */
internal fun EntryWithReferences<StopsForRoute>.toRouteStopGroups(): List<RouteStopGroup> {
    val stopsById = references.stops.associateBy { it.id }
    return entry.stopGroupings.flatMap { grouping ->
        grouping.stopGroups.map { group ->
            RouteStopGroup(
                name = group.displayName,
                stops = group.stopIds.mapNotNull { stopsById[it] }.map(::DtoStop),
            )
        }
    }
}

/**
 * Maps the stops-for-route payload (fetched with polylines) to the [RouteMapData] the map overlay draws:
 * the route + agency name, the stops each tagged with the direction(s) serving them, the selectable
 * directions, the whole-route merged shape, and each direction's own (travel-ordered) shape. Decoding
 * the polylines is the one bit of non-trivial CPU work — the caller runs this off the main thread. Pure,
 * so it is JVM-unit-tested.
 */
internal fun EntryWithReferences<StopsForRoute>.toRouteMapData(routeId: String): RouteMapData {
    val route = references.route(routeId)?.let(::DtoRoute)
    // The route's selectable directions + each stop's direction membership, from one pass over the
    // direction stop groups.
    val dirs = directionsFrom(entry.stopGroupings)
    // Both the whole-route merged set and each direction's own (cleaner, travel-ordered) shape are
    // decoded; the controller draws the merged set for the whole route and a direction's own shape once
    // one is selected.
    val wholeRoute = entry.polylines.map { it.decode() }
    // distinct() drops a shape a direction listed under more than one group, so it isn't drawn (and
    // arrow-stamped) twice over itself.
    val byDirection = dirs.polylinesByDirection.mapValues { (_, shapes) -> shapes.distinct().map { it.decode() } }
    return RouteMapData(
        route = route,
        agencyName = route?.agencyId?.let { references.agency(it)?.name },
        stops = references.stops.map(::DtoStop)
            .map { RouteMapStop(it, dirs.directionsByStop[it.id].orEmpty()) },
        routes = references.routes.map(::DtoRoute),
        directions = dirs.directions,
        polylines = wholeRoute,
        polylinesByDirection = byDirection,
    )
}

private fun ShapeEntry.decode(): List<GeoPoint> = PolylineDecoder.decode(points, length)

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
