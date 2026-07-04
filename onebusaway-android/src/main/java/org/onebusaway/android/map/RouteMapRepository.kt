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
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.map.render.GeoPoint

/**
 * A route's full stops (each tagged with the direction(s) it serves, so the controller can re-filter
 * to any direction without a reload), the serving routes (for stop-marker icons), the route + agency
 * name, its shape, the route's selectable [directions] (id + headsign), and the
 * [initialDirectionId] resolved from the launch's anchor stop (null = whole route). The shape is
 * never narrowed by direction — both directions share it.
 */
data class RouteMap(
    val route: ObaRoute?,
    val agencyName: String?,
    val stops: List<RouteMapStop>,
    val routes: List<ObaRoute>,
    val polylines: List<List<GeoPoint>>,
    val directions: List<RouteMapDirection>,
    val initialDirectionId: Int?,
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
     * @param directionStopId when non-null, resolve it to the single direction serving that stop and
     *   report it as [RouteMap.initialDirectionId]; null (or an ambiguous stop) yields a null id
     *   (whole route). The returned [RouteMap.stops] are always the full route — the controller
     *   filters to a direction (and re-filters on a switch) without a reload.
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
                RouteMap(
                    route = it.route,
                    agencyName = it.agencyName,
                    stops = it.stops,
                    routes = it.routes,
                    polylines = it.polylines.map { line ->
                        line.map { p -> GeoPoint(p.latitude, p.longitude) }
                    },
                    directions = it.directions,
                    initialDirectionId = it.stops.anchorDirectionId(directionStopId),
                )
            }
        }
}

/**
 * Resolves the "show vehicles on map" launch's anchor stop to the single direction it serves — the
 * initial direction filter. Returns null (whole route) when there's no anchor, the anchor isn't among
 * the stops, or its direction is ambiguous (it belongs to zero or several direction groups — e.g. an
 * ungrouped or shared/loop stop). Pure; unit-tested.
 */
internal fun List<RouteMapStop>.anchorDirectionId(anchorStopId: String?): Int? {
    if (anchorStopId == null) return null
    return firstOrNull { it.stop.id == anchorStopId }?.directionIds?.singleOrNull()
}

/**
 * Narrows a route's [RouteMapStop]s to a single [directionId] (null = whole route). Source order is
 * preserved and stops shared between directions that also serve [directionId] are included. Pure;
 * unit-tested.
 */
internal fun List<RouteMapStop>.stopsForDirection(directionId: Int?): List<ObaStop> =
    if (directionId == null) map { it.stop }
    else filter { directionId in it.directionIds }.map { it.stop }
