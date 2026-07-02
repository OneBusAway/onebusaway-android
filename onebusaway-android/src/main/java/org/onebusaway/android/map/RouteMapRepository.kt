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
import org.onebusaway.android.map.render.GeoPoint

/** A route's stops, the serving routes (for stop-marker icons), the route + agency name, and its shape. */
data class RouteMap(
    val route: ObaRoute?,
    val agencyName: String?,
    val stops: List<ObaStop>,
    val routes: List<ObaRoute>,
    val polylines: List<List<GeoPoint>>,
)

/**
 * Loads a route's stops + shapes (one-shot, stops-for-route with polylines) for the route/stop
 * overlays, via the io.client [MapDataSource]. Turns the source's [android.location.Location] shape
 * points into the render [GeoPoint]s the overlay consumes. (Route-mode real-time vehicles are polled
 * via the trip-observation repository's `routeVehiclesStream`.)
 */
interface RouteMapRepository {
    /** @return the route's stops/shape, or `success(null)` when there is no API endpoint. */
    suspend fun getRoute(routeId: String): Result<RouteMap?>
}

class DefaultRouteMapRepository @Inject constructor(
    private val mapDataSource: MapDataSource,
) : RouteMapRepository {

    override suspend fun getRoute(routeId: String): Result<RouteMap?> =
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
                )
            }
        }
}
