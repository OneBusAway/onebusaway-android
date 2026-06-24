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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.io.request.ObaStopsForRouteRequest
import org.onebusaway.android.io.request.ObaStopsForRouteResponse

/**
 * Loads a route's stops + shapes (one-shot) for the route/stop overlays. Replaces the `RoutesLoader`
 * `AsyncTaskLoader` formerly nested in `RouteMapController`. (Route-mode real-time vehicles are polled
 * via the trip-observation repository's `routeVehiclesStream`, which also backfills each trip's
 * schedule + shape for extrapolation.)
 */
interface RouteMapRepository {
    /** @return the stops-for-route response, or `success(null)` when there is no API endpoint. */
    suspend fun getRoute(routeId: String): Result<ObaStopsForRouteResponse?>
}

class DefaultRouteMapRepository @Inject constructor(@ApplicationContext private val context: Context) : RouteMapRepository {

    override suspend fun getRoute(routeId: String): Result<ObaStopsForRouteResponse?> =
        obaApiCall {
            ObaStopsForRouteRequest.Builder(context, routeId)
                .setIncludeShapes(true)
                .build()
                .call()
        }
}
