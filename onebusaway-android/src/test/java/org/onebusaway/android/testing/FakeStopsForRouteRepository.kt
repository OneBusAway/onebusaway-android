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
package org.onebusaway.android.testing

import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.models.RouteStopGroup

/**
 * Shared stub for [StopsForRouteRepository]: stub the projection a test cares about via [stopGroups] /
 * [stopIds], and leave the rest. One stub rather than one per test class so a new method on the
 * interface costs one edit here, and so the two suites can't drift on what an unstubbed route does.
 *
 * An unstubbed route is deliberately a **failure**, not an empty success — every projection's contract
 * distinguishes "the route has no such data" from "the route couldn't be reached", and a test that
 * forgets to stub should see the latter rather than silently exercising the empty path.
 */
class FakeStopsForRouteRepository(
    val stopGroups: MutableMap<String, Result<List<RouteStopGroup>>> = mutableMapOf(),
    val stopIds: MutableMap<String, List<String>> = mutableMapOf()
) : StopsForRouteRepository {

    /** Route ids passed to [routeStopIds], in call order — for asserting fetches are not repeated. */
    val stopIdCalls = mutableListOf<String>()

    override suspend fun routeStopGroups(routeId: String): Result<List<RouteStopGroup>> = stopGroups[routeId] ?: Result.success(emptyList())

    override suspend fun routeMap(routeId: String): Result<RouteMapData?> = Result.success(null)

    override suspend fun routeStopIds(routeId: String): Result<List<String>> {
        stopIdCalls += routeId
        return stopIds[routeId]?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no stops stubbed for $routeId"))
    }
}
