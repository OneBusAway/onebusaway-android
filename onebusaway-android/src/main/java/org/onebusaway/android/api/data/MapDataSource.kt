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
import org.onebusaway.android.models.NearbyStops

/**
 * Fetches stops-for-location (the map's nearby-stops layer) from the modernized OBA REST client,
 * adapting the wire references to the [org.onebusaway.android.models.ObaStop]/[org.onebusaway.android.models.ObaRoute]
 * model interfaces so the map never sees a DTO. Returns `success(null)` when there is no OBA endpoint to
 * contact yet (no current region and no custom API URL) — the controllers treat null as a no-op — and
 * `failure` on IO / HTTP / non-OK code.
 *
 * Stops-for-route (the route overlay + a focused stop's route stops) is fetched and cached separately by
 * [StopsForRouteRepository], the single path both the route map and the route-stop list project from.
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
}
