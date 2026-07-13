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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.api.data.TripVehiclesDataSource
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.models.TripPatternGeometry
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.Polyline

/**
 * The multi-route shape fetch's bounded concurrency, single-flight de-dup, completed-result cache,
 * and partial-failure tolerance, driven against a fake [TripVehiclesDataSource] under virtual time.
 * Fixtures use empty polylines so no unmockable [android.location.Location] is constructed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjacencyRouteShapeRepositoryTest {

    /**
     * A fake shape source that counts per-id calls, tracks peak concurrency, and (when a
     * gate is registered for the id) parks until that gate completes — so a test can hold N fetches
     * in flight and observe the bound. [scriptFor] decides each id's [Result].
     */
    private class FakeTripVehiclesDataSource(
        private val scriptFor: (String) -> Result<Polyline?> = {
            Result.success(Polyline(emptyList()))
        },
    ) : TripVehiclesDataSource {

        val callCounts = mutableMapOf<String, Int>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        private var active = 0
        var maxActive = 0
            private set

        override suspend fun shape(shapeId: String): Result<Polyline?> {
            callCounts[shapeId] = (callCounts[shapeId] ?: 0) + 1
            active++
            maxActive = maxOf(maxActive, active)
            try {
                gates[shapeId]?.await()
                return scriptFor(shapeId)
            } finally {
                active--
            }
        }

        override suspend fun tripsForRoute(routeId: String): Result<RouteTrips> = unused()
        override suspend fun tripDetails(tripId: String): Result<RouteTrips> = unused()
        override suspend fun tripSchedule(tripId: String): Result<ObaTripSchedule?> = unused()
        override suspend fun trip(tripId: String): Result<TripRouteInfo?> = unused()

        private fun <T> unused(): Result<T> =
            throw UnsupportedOperationException("not used")
    }

    private fun repo(
        source: TripVehiclesDataSource,
        scope: kotlinx.coroutines.CoroutineScope,
        cacheSize: Int = 32,
        cacheTtl: Duration = 10.seconds,
        now: () -> ElapsedTime = { ElapsedTime(0L) },
    ) = DefaultAdjacencyRouteShapeRepository(
        source,
        scope,
        log = {},
        cacheSize = cacheSize,
        cacheTtl = cacheTtl,
        now = now,
    )

    private fun patterns(ids: Iterable<String>): Set<TripPatternGeometry> =
        ids.mapTo(LinkedHashSet()) { TripPatternGeometry(it, "route-$it", null) }

    private fun pattern(shapeId: String, routeId: String = "route-$shapeId", color: Int? = null) =
        TripPatternGeometry(shapeId, routeId, color)

    // Bounded concurrency ------------------------------------------------------------------------

    @Test
    fun boundsConcurrentFetchesToTwoAndDrainsInWaves() = runTest {
        val ids = (0 until 5).map { it.toString() }.toSet()
        val fake = FakeTripVehiclesDataSource()
        ids.forEach { fake.gates[it] = CompletableDeferred() }
        val repo = repo(fake, backgroundScope)

        val fetch = async { repo.getShapes(patterns(ids)) }

        runCurrent()
        // 5 requested, 2 permits -> at most 2 in flight at once.
        assertEquals(2, fake.maxActive)

        // Releasing two lets the next wave in; the bound holds throughout.
        fake.gates["0"]!!.complete(Unit)
        fake.gates["1"]!!.complete(Unit)
        runCurrent()
        assertEquals(2, fake.maxActive)

        ids.forEach { fake.gates[it]!!.complete(Unit) }
        val result = fetch.await()
        assertEquals(5, result.shapes.size)
        assertTrue(result.failedShapeIds.isEmpty())
        assertTrue(fake.callCounts.values.all { it == 1 })
    }

    // Single-flight de-dup + completed cache -----------------------------------------------------

    @Test
    fun concurrentGetShapesForSameShapeShareOneFetch() = runTest {
        val fake = FakeTripVehiclesDataSource()
        fake.gates["A"] = CompletableDeferred()
        val repo = repo(fake, backgroundScope)

        backgroundScope.launch { repo.getShapes(setOf(pattern("A"))) }
        backgroundScope.launch { repo.getShapes(setOf(pattern("A"))) }
        runCurrent()

        fake.gates["A"]!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, fake.callCounts["A"])
    }

    @Test
    fun sequentialGetShapesForSameShapeReuseCompletedSuccess() = runTest {
        val fake = FakeTripVehiclesDataSource()
        val repo = repo(fake, backgroundScope)

        repo.getShapes(setOf(pattern("A")))
        val second = repo.getShapes(setOf(pattern("A")))

        assertEquals(setOf("A"), second.shapes.keys)
        assertEquals(1, fake.callCounts["A"])
    }

    @Test
    fun completedCacheEvictsLeastRecentlyUsedShapePastBound() = runTest {
        val fake = FakeTripVehiclesDataSource()
        val repo = repo(fake, backgroundScope, cacheSize = 2)

        repo.getShapes(setOf(pattern("A")))
        repo.getShapes(setOf(pattern("B")))
        repo.getShapes(setOf(pattern("A"))) // promote A, making B the eviction victim
        repo.getShapes(setOf(pattern("C")))
        repo.getShapes(setOf(pattern("B")))

        assertEquals(1, fake.callCounts["A"])
        assertEquals(2, fake.callCounts["B"])
        assertEquals(1, fake.callCounts["C"])
    }

    @Test
    fun completedCacheRefetchesAtExpiry() = runTest {
        var nowMs = 0L
        val fake = FakeTripVehiclesDataSource()
        val repo = repo(
            fake,
            backgroundScope,
            cacheTtl = 10.seconds,
            now = { ElapsedTime(nowMs) },
        )

        repo.getShapes(setOf(pattern("A")))
        nowMs = 9_999L
        repo.getShapes(setOf(pattern("A")))
        assertEquals(1, fake.callCounts["A"])

        nowMs = 10_000L
        repo.getShapes(setOf(pattern("A")))
        assertEquals(2, fake.callCounts["A"])
    }

    @Test
    fun failedResultIsNotCachedAndLaterSuccessIsCached() = runTest {
        var attempts = 0
        val fake = FakeTripVehiclesDataSource {
            if (attempts++ == 0) Result.failure(RuntimeException("network"))
            else Result.success(Polyline(emptyList()))
        }
        val repo = repo(fake, backgroundScope)

        assertEquals(setOf("A"), repo.getShapes(setOf(pattern("A"))).failedShapeIds)
        assertEquals(setOf("A"), repo.getShapes(setOf(pattern("A"))).shapes.keys)
        assertEquals(setOf("A"), repo.getShapes(setOf(pattern("A"))).shapes.keys)
        assertEquals(2, fake.callCounts["A"])
    }

    // Partial failure ----------------------------------------------------------------------------

    @Test
    fun partialFailure_returnsSuccessesAndListsFailures() = runTest {
        val fake = FakeTripVehiclesDataSource { id ->
            when (id) {
                "ok" -> Result.success(Polyline(emptyList()))
                "boom" -> Result.failure(RuntimeException("network"))
                else -> Result.success(null) // no API endpoint
            }
        }
        val repo = repo(fake, backgroundScope)

        val result = repo.getShapes(patterns(setOf("ok", "boom", "none")))

        assertEquals(setOf("ok"), result.shapes.keys)
        assertEquals(setOf("boom", "none"), result.failedShapeIds)
    }

    @Test
    fun emptyInput_makesNoCalls() = runTest {
        val fake = FakeTripVehiclesDataSource()
        val repo = repo(fake, backgroundScope)

        val result = repo.getShapes(emptySet())

        assertTrue(result.shapes.isEmpty())
        assertTrue(result.failedShapeIds.isEmpty())
        assertTrue(fake.callCounts.isEmpty())
    }

    // Cancellation -------------------------------------------------------------------------------

    @Test
    fun cancellingOneJoinerNeitherKillsTheSharedFetchNorLeaksThePermit() = runTest {
        val fake = FakeTripVehiclesDataSource()
        fake.gates["A"] = CompletableDeferred()
        val repo = repo(fake, backgroundScope)

        val doomed = backgroundScope.launch { repo.getShapes(setOf(pattern("A"))) }
        val survivor = async { repo.getShapes(setOf(pattern("A"))) }
        runCurrent()

        doomed.cancel() // one joiner gives up; the shared fetch runs on the repo's own scope
        fake.gates["A"]!!.complete(Unit)

        // The surviving joiner still gets the shape from the single shared fetch.
        assertEquals(setOf("A"), survivor.await().shapes.keys)
        assertEquals(1, fake.callCounts["A"])

        // A different later fetch still acquires a permit (none leaked).
        repo.getShapes(setOf(pattern("B")))
        assertEquals(1, fake.callCounts["B"])
    }

    // Mapping ------------------------------------------------------------------------------------

    @Test
    fun returnedShapeKeepsRequestedShapeRouteAndColor() = runTest {
        val fake = FakeTripVehiclesDataSource()
        val repo = repo(fake, backgroundScope)

        val result = repo.getShapes(setOf(pattern("served-shape", "shared-route", 0xFF336699.toInt())))
        val shape = result.shapes.getValue("served-shape")

        assertEquals("served-shape", shape.shapeId)
        assertEquals("shared-route", shape.routeId)
        assertEquals(0xFF336699.toInt(), shape.routeColor)
        assertTrue(shape.points.isEmpty())
        assertEquals(setOf("served-shape"), fake.callCounts.keys)
    }

    @Test
    fun routeStopMappingPreservesDirectionMembershipWithoutGeometry() {
        val data = RouteMapData(
            route = null,
            agencyName = null,
            stops = listOf(
                RouteMapStop(ObaStopElement(id = "outbound"), setOf(0)),
                RouteMapStop(ObaStopElement(id = "inbound"), setOf(1)),
                RouteMapStop(ObaStopElement(id = "shared"), setOf(0, 1)),
            ),
            routes = emptyList(),
            directions = emptyList(),
            polylines = emptyList(),
            polylinesByDirection = emptyMap(),
        )

        val membership = data.toAdjacencyRouteStops()

        assertEquals(setOf("outbound", "inbound", "shared"), membership.stopIds)
        assertEquals(setOf("outbound", "shared"), membership.stopIdsByDirection[0])
        assertEquals(setOf("inbound", "shared"), membership.stopIdsByDirection[1])
        assertNull(membership.stopIdsByDirection[2])
    }
}
