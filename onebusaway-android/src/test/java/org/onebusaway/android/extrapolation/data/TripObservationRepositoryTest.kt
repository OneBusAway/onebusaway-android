/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline
import org.junit.Test

/**
 * The cold polling Flow mechanics — interval, exponential backoff, no-emit-on-failure, and
 * per-collector cancellation — driven against a fake fetcher under virtual time. (Recording OK
 * responses into the store is covered by TripStateCacheTest + AdaptersTest, which have the
 * response/Android fixtures this JVM test deliberately avoids.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripObservationRepositoryTest {

    /** A fetcher whose trip-details call counts invocations and returns whatever [details] yields. */
    private class FakeFetcher(
            private val details: () -> ObaTripDetailsResponse? = { null },
            /** Resolves a shapeId to its polyline; null (the default) means "no shape". */
            private val shapeFor: (String) -> Polyline? = { null }
    ) : TripObservationFetcher {
        var tripDetailsCalls = 0
            private set
        var shapeCalls = 0
            private set

        override suspend fun tripDetails(tripId: String): ObaTripDetailsResponse? {
            tripDetailsCalls++
            return details()
        }

        override suspend fun tripsForRoute(routeId: String): ObaTripsForRouteResponse? = null

        override suspend fun tripSchedule(tripId: String): ObaTripSchedule? = null

        override suspend fun shape(shapeId: String): Polyline? {
            shapeCalls++
            return shapeFor(shapeId)
        }
    }

    @Test
    fun `polls immediately, then backs off exponentially on consecutive failures`() = runTest {
        val fetcher = FakeFetcher() // always null -> every tick is a failure
        val repo = DefaultTripObservationRepository(fetcher)

        repo.tripDetailsStream("trip1", intervalMs = 1_000L).launchIn(backgroundScope)

        runCurrent()
        assertEquals("first poll is immediate", 1, fetcher.tripDetailsCalls)

        advanceTimeBy(1_000)
        assertEquals("no second poll before the 2s backoff elapses", 1, fetcher.tripDetailsCalls)

        advanceTimeBy(1_001) // now past t=2000
        assertEquals("second poll after the first failure's 2s backoff", 2, fetcher.tripDetailsCalls)

        advanceTimeBy(4_000) // next failure doubles to 4s -> t=6000
        assertEquals("third poll after a 4s backoff", 3, fetcher.tripDetailsCalls)
    }

    @Test
    fun `cancelling the collection stops polling (per-view lifecycle)`() = runTest {
        val fetcher = FakeFetcher()
        val repo = DefaultTripObservationRepository(fetcher)

        val job = repo.tripDetailsStream("trip1", intervalMs = 1_000L).launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, fetcher.tripDetailsCalls)

        job.cancel()
        advanceTimeBy(100_000)
        assertEquals("no polling after the collector is cancelled", 1, fetcher.tripDetailsCalls)
    }

    @Test
    fun `ensureShape fetches a shape once and shares the instance across trips on the same route`() =
            runTest {
                val shape = Polyline(emptyList())
                val fetcher = FakeFetcher(shapeFor = { shape })
                val repo = DefaultTripObservationRepository(fetcher)

                val first = repo.ensureShape("tripA", "shape1")
                val second = repo.ensureShape("tripB", "shape1")

                assertEquals("the shared shape is fetched only once", 1, fetcher.shapeCalls)
                assertSame("both trips resolve to the same instance", shape, first)
                assertSame("the second trip reuses the cached instance", shape, second)
                assertSame(
                        "the shared instance is recorded on the first trip",
                        shape,
                        repo.lookupTripState("tripA")?.polyline
                )
                assertSame(
                        "the shared instance is recorded on the second trip",
                        shape,
                        repo.lookupTripState("tripB")?.polyline
                )
            }

    @Test
    fun `failed fetches are never emitted`() = runTest {
        val fetcher = FakeFetcher() // null -> failure -> nothing to emit
        val repo = DefaultTripObservationRepository(fetcher)

        val emissions = mutableListOf<ObaTripDetailsResponse>()
        repo.tripDetailsStream("trip1", intervalMs = 1_000L)
                .onEach { emissions.add(it) }
                .launchIn(backgroundScope)

        advanceTimeBy(50_000)
        assertEquals("a null fetch must not emit", 0, emissions.size)
    }
}
