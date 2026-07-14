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
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.models.NearbyStops
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.time.ElapsedTime

/**
 * The multi-route shape fetch's bounded concurrency, single-flight de-dup, completed-result cache,
 * and partial-failure tolerance, driven against a fake [MapDataSource] under virtual time. Fixtures
 * use empty polyline lists so no unmockable [android.location.Location] is constructed (JVM tests
 * can't touch Android statics); point conversion is a trivial `GeoPoint(lat, lon)` map covered
 * implicitly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjacencyRouteShapeRepositoryTest {

    /**
     * A [MapDataSource] whose `routeMap` counts per-id calls, tracks peak concurrency, and (when a
     * gate is registered for the id) parks until that gate completes — so a test can hold N fetches
     * in flight and observe the bound. [scriptFor] decides each id's [Result].
     */
    private class FakeMapDataSource(
        private val scriptFor: (String) -> Result<RouteMapData?> = { Result.success(routeMapData(it)) },
    ) : MapDataSource {

        val callCounts = mutableMapOf<String, Int>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        private var active = 0
        var maxActive = 0
            private set

        override suspend fun nearbyStops(
            lat: Double, lon: Double, latSpan: Double, lonSpan: Double, maxCount: Int?,
        ): Result<NearbyStops?> = throw UnsupportedOperationException("not used")

        override suspend fun routeMap(routeId: String): Result<RouteMapData?> {
            callCounts[routeId] = (callCounts[routeId] ?: 0) + 1
            active++
            maxActive = maxOf(maxActive, active)
            try {
                gates[routeId]?.await()
                return scriptFor(routeId)
            } finally {
                active--
            }
        }
    }

    private fun repo(
        source: MapDataSource,
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

    // Bounded concurrency ------------------------------------------------------------------------

    @Test
    fun boundsConcurrentFetchesToTwoAndDrainsInWaves() = runTest {
        val ids = (0 until 5).map { it.toString() }.toSet()
        val fake = FakeMapDataSource()
        ids.forEach { fake.gates[it] = CompletableDeferred() }
        val repo = repo(fake, backgroundScope)

        val fetch = async { repo.getShapes(ids) }

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
        assertTrue(result.failedRouteIds.isEmpty())
        assertTrue(fake.callCounts.values.all { it == 1 })
    }

    // Single-flight de-dup + completed cache -----------------------------------------------------

    @Test
    fun concurrentGetShapesForSameRouteShareOneFetch() = runTest {
        val fake = FakeMapDataSource()
        fake.gates["A"] = CompletableDeferred()
        val repo = repo(fake, backgroundScope)

        backgroundScope.launch { repo.getShapes(setOf("A")) }
        backgroundScope.launch { repo.getShapes(setOf("A")) }
        runCurrent()

        fake.gates["A"]!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, fake.callCounts["A"])
    }

    @Test
    fun sequentialGetShapesForSameRouteReuseCompletedSuccess() = runTest {
        val fake = FakeMapDataSource()
        val repo = repo(fake, backgroundScope)

        repo.getShapes(setOf("A"))
        val second = repo.getShapes(setOf("A"))

        assertEquals(setOf("A"), second.shapes.keys)
        assertEquals(1, fake.callCounts["A"])
    }

    @Test
    fun completedCacheEvictsLeastRecentlyUsedShapePastBound() = runTest {
        val fake = FakeMapDataSource()
        val repo = repo(fake, backgroundScope, cacheSize = 2)

        repo.getShapes(setOf("A"))
        repo.getShapes(setOf("B"))
        repo.getShapes(setOf("A")) // promote A, making B the eviction victim
        repo.getShapes(setOf("C"))
        repo.getShapes(setOf("B"))

        assertEquals(1, fake.callCounts["A"])
        assertEquals(2, fake.callCounts["B"])
        assertEquals(1, fake.callCounts["C"])
    }

    @Test
    fun completedCacheRefetchesAtExpiry() = runTest {
        var nowMs = 0L
        val fake = FakeMapDataSource()
        val repo = repo(
            fake,
            backgroundScope,
            cacheTtl = 10.seconds,
            now = { ElapsedTime(nowMs) },
        )

        repo.getShapes(setOf("A"))
        nowMs = 9_999L
        repo.getShapes(setOf("A"))
        assertEquals(1, fake.callCounts["A"])

        nowMs = 10_000L
        repo.getShapes(setOf("A"))
        assertEquals(2, fake.callCounts["A"])
    }

    @Test
    fun failedResultIsNotCachedAndLaterSuccessIsCached() = runTest {
        var attempts = 0
        val fake = FakeMapDataSource { id ->
            if (attempts++ == 0) Result.failure(RuntimeException("network"))
            else Result.success(routeMapData(id))
        }
        val repo = repo(fake, backgroundScope)

        assertEquals(setOf("A"), repo.getShapes(setOf("A")).failedRouteIds)
        assertEquals(setOf("A"), repo.getShapes(setOf("A")).shapes.keys)
        assertEquals(setOf("A"), repo.getShapes(setOf("A")).shapes.keys)
        assertEquals(2, fake.callCounts["A"])
    }

    // Partial failure ----------------------------------------------------------------------------

    @Test
    fun partialFailure_returnsSuccessesAndListsFailures() = runTest {
        val fake = FakeMapDataSource { id ->
            when (id) {
                "ok" -> Result.success(routeMapData("ok"))
                "boom" -> Result.failure(RuntimeException("network"))
                else -> Result.success(null) // no API endpoint
            }
        }
        val repo = repo(fake, backgroundScope)

        val result = repo.getShapes(setOf("ok", "boom", "none"))

        assertEquals(setOf("ok"), result.shapes.keys)
        assertEquals(setOf("boom", "none"), result.failedRouteIds)
    }

    @Test
    fun emptyInput_makesNoCalls() = runTest {
        val fake = FakeMapDataSource()
        val repo = repo(fake, backgroundScope)

        val result = repo.getShapes(emptySet())

        assertTrue(result.shapes.isEmpty())
        assertTrue(result.failedRouteIds.isEmpty())
        assertTrue(fake.callCounts.isEmpty())
    }

    // Cancellation -------------------------------------------------------------------------------

    @Test
    fun cancellingOneJoinerNeitherKillsTheSharedFetchNorLeaksThePermit() = runTest {
        val fake = FakeMapDataSource()
        fake.gates["A"] = CompletableDeferred()
        val repo = repo(fake, backgroundScope)

        val doomed = backgroundScope.launch { repo.getShapes(setOf("A")) }
        val survivor = async { repo.getShapes(setOf("A")) }
        runCurrent()

        doomed.cancel() // one joiner gives up; the shared fetch runs on the repo's own scope
        fake.gates["A"]!!.complete(Unit)

        // The surviving joiner still gets the shape from the single shared fetch.
        assertEquals(setOf("A"), survivor.await().shapes.keys)
        assertEquals(1, fake.callCounts["A"])

        // A different later fetch still acquires a permit (none leaked).
        repo.getShapes(setOf("B"))
        assertEquals(1, fake.callCounts["B"])
    }

    // Mapping ------------------------------------------------------------------------------------

    @Test
    fun toAdjacencyShape_mapsRouteIdAndStopIds() {
        val data = routeMapData("R", stopIds = listOf("s1", "s2"))
        val shape = data.toAdjacencyShape("R")

        assertEquals("R", shape.routeId)
        assertNull(shape.route)
        assertEquals(setOf("s1", "s2"), shape.stopIds)
        assertTrue(shape.polylines.isEmpty())
    }

    companion object {
        /** A minimal [RouteMapData] with empty shapes (no [android.location.Location] built). */
        // routeId is a call-site label documenting which route this fake data stands in for;
        // RouteMapData carries no route-id field (the id is applied later by toAdjacencyShape), so
        // the body has no use for it. Test-only helper; no tracking issue.
        private fun routeMapData(
            @Suppress("UNUSED_PARAMETER") routeId: String,
            stopIds: List<String> = emptyList(),
        ) = RouteMapData(
            route = null,
            agencyName = null,
            stops = stopIds.map { RouteMapStop(ObaStopElement(id = it), emptySet()) },
            routes = emptyList(),
            directions = emptyList(),
            polylines = emptyList(),
            polylinesByDirection = emptyMap(),
        )
    }
}
