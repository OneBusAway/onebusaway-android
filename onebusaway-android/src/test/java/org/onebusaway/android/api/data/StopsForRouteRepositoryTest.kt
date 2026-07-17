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

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.StopGroup
import org.onebusaway.android.api.contract.StopGroupName
import org.onebusaway.android.api.contract.StopGrouping
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.StopsForRoute

/**
 * Caching/coalescing + projection routing for [DefaultStopsForRouteRepository] — the single stops-for-route
 * path both the route map and the route-stop list project from. The wire fetch is faked so the store's
 * behavior (one fetch feeds both projections; successes cache; failures/no-endpoint don't) is exercised
 * without a live client. The two projections themselves are covered by the pure-mapper tests
 * (`RouteStopGroupsMapperTest`, `MapDataSourceDirectionsTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopsForRouteRepositoryTest {

    private class FakeFetch {
        val calls = mutableListOf<String>()
        val results = mutableMapOf<String, Result<EntryWithReferences<StopsForRoute>?>>()
        var default: Result<EntryWithReferences<StopsForRoute>?> = Result.success(null)
        suspend fun get(routeId: String): Result<EntryWithReferences<StopsForRoute>?> {
            calls += routeId
            return results[routeId] ?: default
        }
    }

    @Test
    fun `one wire fetch feeds both the stop-group and route-map projections`() = runTest {
        val fake = FakeFetch().apply { results["r"] = Result.success(entryWith(listOf("a", "b"))) }
        val repository = DefaultStopsForRouteRepository(backgroundScope, fetch = fake::get)

        val groups = repository.routeStopGroups("r").getOrThrow()
        val map = repository.routeMap("r").getOrThrow()

        assertEquals(listOf("a", "b"), groups.single().stops.map { it.id })
        assertEquals(listOf("a", "b"), map!!.stops.map { it.stop.id })
        // The route was fetched once; the second projection came from the cache — no double fetch.
        assertEquals(listOf("r"), fake.calls)
    }

    @Test
    fun `a cached route is not refetched`() = runTest {
        val fake = FakeFetch().apply { results["r"] = Result.success(entryWith(listOf("a"))) }
        val repository = DefaultStopsForRouteRepository(backgroundScope, fetch = fake::get)

        repository.routeStopGroups("r")
        repository.routeStopGroups("r")

        assertEquals(listOf("r"), fake.calls)
    }

    @Test
    fun `a failure is not cached and preserves its exception`() = runTest {
        val fake = FakeFetch().apply { default = Result.failure(IOException("boom")) }
        val repository = DefaultStopsForRouteRepository(backgroundScope, fetch = fake::get)

        val first = repository.routeStopGroups("r")
        val second = repository.routeMap("r")

        assertEquals("boom", first.exceptionOrNull()?.message)
        assertTrue(second.isFailure)
        // Neither call cached the failure, so each re-fetched.
        assertEquals(listOf("r", "r"), fake.calls)
    }

    @Test
    fun `no endpoint yields null map but a stop-list failure`() = runTest {
        val fake = FakeFetch() // default success(null) = no endpoint
        val repository = DefaultStopsForRouteRepository(backgroundScope, fetch = fake::get)

        // The map treats "no region" as nothing-to-show; the stop list treats it as an error to surface.
        assertNull(repository.routeMap("r").getOrThrow())
        assertTrue(repository.routeStopGroups("r").isFailure)
    }

    // Not covered here: an unexpected crash *inside* the fetch block (the fetch lambda throwing rather
    // than returning Result.failure) coalesces to a null out of SingleFlight, which `entry` now surfaces
    // as a Result.failure instead of masking it as the benign no-endpoint success(null). That path logs
    // via android.util.Log.e inside SingleFlight, which is unmocked in plain JVM tests (this module
    // avoids Robolectric/mocking), so — like the same branch in SingleFlightTest — it stays untested.

    private fun entryWith(stopIds: List<String>): EntryWithReferences<StopsForRoute> =
        EntryWithReferences(
            entry = StopsForRoute(
                stopGroupings = listOf(
                    StopGrouping(
                        stopGroups = listOf(
                            StopGroup(
                                id = "0",
                                name = StopGroupName(names = listOf("Outbound")),
                                stopIds = stopIds,
                            )
                        )
                    )
                )
            ),
            references = References(
                stops = stopIds.map {
                    StopReference(id = it, name = it, direction = null, lat = 47.0, lon = -122.0)
                }
            ),
        )
}
