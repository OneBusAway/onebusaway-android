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
import org.onebusaway.android.models.NearbyStops
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
        // Invert the direction stop groups (type "direction") into a stop id -> direction ids map, so
        // each stop can carry its own direction. Only numeric group ids are kept — they're what a
        // vehicle's trip directionId matches; a stop listed under two groups gets both.
        val directionsByStop = mutableMapOf<String, MutableSet<Int>>()
        data.entry.stopGroupings.forEach { grouping ->
            grouping.stopGroups.forEach { group ->
                group.id?.toIntOrNull()?.let { dir ->
                    group.stopIds.forEach { directionsByStop.getOrPut(it) { mutableSetOf() }.add(dir) }
                }
            }
        }
        RouteMapData(
            route = route,
            agencyName = route?.agencyId?.let { data.references.agency(it)?.name },
            stops = data.references.stops.map(::DtoStop)
                .map { RouteMapStop(it, directionsByStop[it.id].orEmpty()) },
            routes = data.references.routes.map(::DtoRoute),
            // Decoding the route's shape polylines is the one bit of non-trivial CPU work in this
            // layer; offload just it (the Retrofit calls are already main-safe, like the other sources).
            polylines = withContext(Dispatchers.Default) {
                data.entry.polylines.map { PolylineDecoder.decodeLine(it.points, it.length) }
            },
        )
    }
}
