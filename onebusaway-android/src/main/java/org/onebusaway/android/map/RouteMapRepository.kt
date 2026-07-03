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

import org.onebusaway.android.api.data.MapDataSource

import javax.inject.Inject
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.map.render.GeoPoint

/**
 * A route's stops (narrowed to a single direction when the load was direction-focused), the serving
 * routes (for stop-marker icons), the route + agency name, its shape, and the resolved [directionId]
 * the vehicle layer keeps (null = both directions / whole route).
 */
data class RouteMap(
    val route: ObaRoute?,
    val agencyName: String?,
    val stops: List<ObaStop>,
    val routes: List<ObaRoute>,
    val polylines: List<List<GeoPoint>>,
    val directionId: Int?,
)

/**
 * Loads a route's stops + shapes (one-shot, stops-for-route with polylines) for the route/stop
 * overlays, via the api [MapDataSource]. Turns the source's [android.location.Location] shape
 * points into the render [GeoPoint]s the overlay consumes, and — when the caller passes a
 * `directionStopId` — narrows the stops to that stop's direction. (Route-mode real-time vehicles are
 * polled via the trip-observation repository's `routeVehiclesStream`.)
 */
interface RouteMapRepository {
    /**
     * @param directionStopId when non-null, narrow the returned stops (and [RouteMap.directionId]) to
     *   the single direction serving that stop; null (or an ambiguous stop) returns the whole route.
     * @return the route's stops/shape, or `success(null)` when there is no API endpoint.
     */
    suspend fun getRoute(routeId: String, directionStopId: String? = null): Result<RouteMap?>
}

class DefaultRouteMapRepository @Inject constructor(
    private val mapDataSource: MapDataSource,
) : RouteMapRepository {

    override suspend fun getRoute(routeId: String, directionStopId: String?): Result<RouteMap?> =
        mapDataSource.routeMap(routeId).map { data ->
            data?.let {
                val focus = it.stops.focusDirection(directionStopId)
                RouteMap(
                    route = it.route,
                    agencyName = it.agencyName,
                    stops = focus.stops,
                    routes = it.routes,
                    polylines = it.polylines.map { line ->
                        line.map { p -> GeoPoint(p.latitude, p.longitude) }
                    },
                    directionId = focus.directionId,
                )
            }
        }
}

/** The direction-narrowed [stops] plus the resolved vehicle [directionId] (null = show all). */
internal data class DirectionFocus(val stops: List<ObaStop>, val directionId: Int?)

/**
 * Narrows a route's [RouteMapStop]s to the single direction serving [anchorStopId] — the stop a
 * "show vehicles on map" launch anchored to. Returns the whole route (all stops, null [directionId])
 * when there's no anchor, the anchor isn't among the stops, or its direction is ambiguous (it belongs
 * to zero or several direction groups — e.g. an ungrouped or shared/loop stop). When the anchor sits
 * in exactly one direction, the stops are filtered to that direction (shared stops that also serve it
 * included) and its id returned as the vehicle filter. Pure; unit-tested.
 */
internal fun List<RouteMapStop>.focusDirection(anchorStopId: String?): DirectionFocus {
    val allStops = map { it.stop }
    if (anchorStopId == null) return DirectionFocus(allStops, null)
    val target = firstOrNull { it.stop.id == anchorStopId }?.directionIds?.singleOrNull()
        ?: return DirectionFocus(allStops, null)
    return DirectionFocus(filter { target in it.directionIds }.map { it.stop }, target)
}
