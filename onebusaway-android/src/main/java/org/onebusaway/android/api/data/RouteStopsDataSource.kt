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

import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.requireData

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.contract.StopsForRoute

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.models.RouteStopGroup

/**
 * Fetches a route's stops grouped by direction (stops-for-route) from the modernized OBA REST client,
 * adapting the wire references to [ObaStop] so callers never see the DTOs. Returns [Result.failure]
 * (IO / HTTP / non-OK code) rather than throwing.
 */
interface RouteStopsDataSource {

    suspend fun stopsForRoute(routeId: String): Result<List<RouteStopGroup>>
}

class DefaultRouteStopsDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : RouteStopsDataSource {

    override suspend fun stopsForRoute(routeId: String): Result<List<RouteStopGroup>> = api.call {
        it.stopsForRoute(routeId).requireData().toRouteStopGroups()
    }.onFailure { Log.e(TAG, "stopsForRoute($routeId) failed", it) }

    private companion object {
        const val TAG = "RouteStopsDataSource"
    }
}

/**
 * Maps the stops-for-route payload to per-direction [RouteStopGroup]s, resolving each group's stop
 * ids against the references pool (ids with no resolvable stop are skipped). Pure, so it is
 * JVM-unit-tested.
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
