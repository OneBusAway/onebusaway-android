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

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.adapters.toObaTripSchedule
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.StopTime
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.api.contract.TripSchedule
import org.onebusaway.android.api.contract.TripStatus
import org.onebusaway.android.api.data.asRouteTrips
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.util.Polyline

/**
 * The cold polling Flow mechanics — interval, exponential backoff (longer after a 429),
 * no-emit-on-failure, and per-collector cancellation — plus the route poll's schedule budget (ask
 * trips-for-route for schedules only while one is missing; fetch a trip alone only when the route poll
 * can't carry it), driven against a fake fetcher under virtual time. Route responses are decoded from
 * typed wire DTOs through the real wire adapter (`asRouteTrips`), so the block-rollover case runs the real dedup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripObservationRepositoryTest {

    /** A fetcher that records its calls and answers from the given lambdas; by default every poll fails. */
    private class FakeFetcher(
        private val details: () -> Result<TripDetails> = { Result.failure(IOException("offline")) },
        /** Answers a trips-for-route poll, given whether it asked for schedules. */
        private val routeTrips: (includeSchedules: Boolean) -> Result<RouteTrips> = { Result.failure(IOException("offline")) },
        /** Resolves a tripId to its schedule; null (the default) means "no schedule". */
        private val scheduleFor: (String) -> ObaTripSchedule? = { null },
        /** Resolves a shapeId to its polyline; null (the default) means "no shape". */
        private val shapeFor: (String) -> Polyline? = { null },
        /** Resolves a tripId to its route info; null (the default) means "not found". */
        private val tripRouteInfoFor: (String) -> TripRouteInfo? = { null }
    ) : TripObservationFetcher {
        var tripDetailsCalls = 0
            private set
        var shapeCalls = 0
            private set
        var tripRouteInfoCalls = 0
            private set

        /** Each trips-for-route poll's `includeSchedules`, in order. */
        val tripsForRouteCalls = mutableListOf<Boolean>()

        /** Each per-trip schedule fetch's tripId, in order. */
        val tripScheduleCalls = mutableListOf<String>()

        override suspend fun tripDetails(tripId: String): Result<TripDetails> {
            tripDetailsCalls++
            return details()
        }

        override suspend fun tripsForRoute(routeId: String, includeSchedules: Boolean): Result<RouteTrips> {
            tripsForRouteCalls += includeSchedules
            return routeTrips(includeSchedules)
        }

        override suspend fun tripSchedule(tripId: String): ObaTripSchedule? {
            tripScheduleCalls += tripId
            return scheduleFor(tripId)
        }

        override suspend fun shape(shapeId: String): Polyline? {
            shapeCalls++
            return shapeFor(shapeId)
        }

        override suspend fun tripRouteInfo(tripId: String): TripRouteInfo? {
            tripRouteInfoCalls++
            return tripRouteInfoFor(tripId)
        }
    }

    /** One trips-for-route entry: the trip it describes, the trip its vehicle reports active, and whether it carries a schedule. */
    private data class Entry(val tripId: String, val activeTripId: String = tripId, val withSchedule: Boolean)

    /** A trips-for-route response holding [entries], adapted through the real wire→model seam. */
    private fun routeTrips(vararg entries: Entry): RouteTrips {
        val list = entries.map { e ->
            TripDetailsEntry(
                tripId = e.tripId,
                status = TripStatus(activeTripId = e.activeTripId, serviceDate = 1),
                schedule = TripSchedule(stopTimes = listOf(StopTime(stopId = "s1"))).takeIf { e.withSchedule }
            )
        }
        val trips = entries.flatMap { listOf(it.tripId, it.activeTripId) }.distinct().map { TripReference(id = it, routeId = "route1") }
        return ObaEnvelope(code = 200, currentTime = 1000, data = ListWithReferences(list, References(trips = trips))).asRouteTrips()
    }

    @Test
    fun `polls immediately, then backs off exponentially on consecutive failures`() = runTest {
        val fetcher = FakeFetcher() // every tick is a (non-429) failure
        val repo = DefaultTripObservationRepository(fetcher)

        repo.tripDetailsStream("trip1", intervalMs = 1_000L).launchIn(backgroundScope)

        runCurrent()
        assertEquals("first poll is immediate", 1, fetcher.tripDetailsCalls)

        advanceTimeBy(1_001)
        assertEquals("second poll after the first failure's 1s retry", 2, fetcher.tripDetailsCalls)

        advanceTimeBy(1_000) // t=2001
        assertEquals("no third poll before the 2s backoff elapses", 2, fetcher.tripDetailsCalls)

        advanceTimeBy(1_000) // past t=3000
        assertEquals("third poll after a 2s backoff", 3, fetcher.tripDetailsCalls)

        advanceTimeBy(4_000) // past t=7000
        assertEquals("fourth poll after a 4s backoff", 4, fetcher.tripDetailsCalls)
    }

    @Test
    fun `a successful poll resets the backoff to the interval`() = runTest {
        val outcomes = ArrayDeque(
            listOf(
                Result.failure(IOException("offline")),
                Result.success(TripDetails(emptyList(), null, null, null, null)),
                Result.failure(IOException("offline"))
            )
        )
        val fetcher = FakeFetcher(details = { outcomes.removeFirstOrNull() ?: Result.failure(IOException("offline")) })
        val repo = DefaultTripObservationRepository(fetcher)

        repo.tripDetailsStream("trip1", intervalMs = 10_000L).launchIn(backgroundScope)
        runCurrent()

        advanceTimeBy(1_001)
        assertEquals("the failure retries after 1s, and that retry succeeds", 2, fetcher.tripDetailsCalls)

        advanceTimeBy(9_998) // t=10_999
        assertEquals("after a success the next poll waits the full interval", 2, fetcher.tripDetailsCalls)

        advanceTimeBy(2) // past t=11_000
        assertEquals(3, fetcher.tripDetailsCalls)

        advanceTimeBy(1_000) // past t=12_000
        assertEquals("a fresh failure starts over at the 1s retry", 4, fetcher.tripDetailsCalls)
    }

    @Test
    fun `a 429 waits twice the interval before retrying`() = runTest {
        val fetcher = FakeFetcher(details = { Result.failure(ObaApiException(429)) })
        val repo = DefaultTripObservationRepository(fetcher)

        repo.tripDetailsStream("trip1", intervalMs = 1_000L).launchIn(backgroundScope)

        runCurrent()
        assertEquals("first poll is immediate", 1, fetcher.tripDetailsCalls)

        advanceTimeBy(1_999)
        assertEquals("no prompt retry into a rate limit", 1, fetcher.tripDetailsCalls)

        advanceTimeBy(2) // past t=2000
        assertEquals("the retry comes after two intervals", 2, fetcher.tripDetailsCalls)
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
    fun `ensureShape fetches a shape once and shares the instance across trips on the same route`() = runTest {
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
    fun `ensureShape short-circuits once the trip carries its polyline`() = runTest {
        val shape = Polyline(emptyList())
        val fetcher = FakeFetcher(shapeFor = { shape })
        val repo = DefaultTripObservationRepository(fetcher)

        val first = repo.ensureShape("tripA", "shape1")
        val second = repo.ensureShape("tripA", "shape1")

        assertEquals("the second call is served from the trip's recorded polyline", 1, fetcher.shapeCalls)
        assertSame("both calls resolve to the same instance", first, second)
    }

    @Test
    fun `ensureShape fetches each distinct shapeId independently`() = runTest {
        val shapeA = Polyline(emptyList())
        val shapeB = Polyline(emptyList())
        val fetcher = FakeFetcher(shapeFor = { if (it == "shapeA") shapeA else shapeB })
        val repo = DefaultTripObservationRepository(fetcher)

        val first = repo.ensureShape("tripA", "shapeA")
        val second = repo.ensureShape("tripB", "shapeB")

        assertEquals("distinct shapeIds each fetch", 2, fetcher.shapeCalls)
        assertSame("tripA resolves to its own shape", shapeA, first)
        assertSame("tripB resolves to its own shape", shapeB, second)
    }

    @Test
    fun `ensureShape caches nothing on a null fetch and refetches later`() = runTest {
        val shape = Polyline(emptyList())
        var next: Polyline? = null // first fetch yields null, then a real polyline
        val fetcher = FakeFetcher(shapeFor = { next })
        val repo = DefaultTripObservationRepository(fetcher)

        val first = repo.ensureShape("tripA", "shape1")
        assertEquals("first call attempts the fetch", 1, fetcher.shapeCalls)
        assertEquals("a null fetch resolves to null", null, first)

        next = shape
        val second = repo.ensureShape("tripA", "shape1")
        assertEquals("the null result poisoned nothing, so the second call refetches", 2, fetcher.shapeCalls)
        assertSame("the second call resolves to the real polyline", shape, second)
    }

    @Test
    fun `resolveNeighborTrip caches a successful resolution and skips the second fetch`() = runTest {
        val info = TripRouteInfo("tripB", "route75", "shapeB", "75", null)
        val fetcher = FakeFetcher(tripRouteInfoFor = { info })
        val repo = DefaultTripObservationRepository(fetcher)

        val first = repo.resolveNeighborTrip("tripB")
        val second = repo.resolveNeighborTrip("tripB")

        assertEquals("only the first call fetches", 1, fetcher.tripRouteInfoCalls)
        assertSame("both calls resolve to the same cached instance", first, second)
    }

    @Test
    fun `resolveNeighborTrip caches nothing on a null fetch and refetches later`() = runTest {
        val info = TripRouteInfo("tripB", "route75", "shapeB", "75", null)
        var next: TripRouteInfo? = null // first fetch yields null, then a real result
        val fetcher = FakeFetcher(tripRouteInfoFor = { next })
        val repo = DefaultTripObservationRepository(fetcher)

        val first = repo.resolveNeighborTrip("tripB")
        assertEquals("first call attempts the fetch", 1, fetcher.tripRouteInfoCalls)
        assertEquals("a null fetch resolves to null", null, first)

        next = info
        val second = repo.resolveNeighborTrip("tripB")
        assertEquals("the null result poisoned nothing, so the second call refetches", 2, fetcher.tripRouteInfoCalls)
        assertSame("the second call resolves to the real result", info, second)
    }

    @Test
    fun `failed fetches are never emitted`() = runTest {
        val fetcher = FakeFetcher() // failure -> nothing to emit
        val repo = DefaultTripObservationRepository(fetcher)

        val emissions = mutableListOf<Unit>()
        repo.tripDetailsStream("trip1", intervalMs = 1_000L)
            .onEach { emissions.add(it) }
            .launchIn(backgroundScope)

        advanceTimeBy(50_000)
        assertEquals("a failed fetch must not emit", 0, emissions.size)
    }

    @Test
    fun `the route poll asks for schedules only until every active trip has one`() = runTest {
        val fetcher = FakeFetcher(routeTrips = { include -> Result.success(routeTrips(Entry("tripA", withSchedule = include))) })
        val repo = DefaultTripObservationRepository(fetcher)

        repo.routeVehiclesStream("route1", intervalMs = 1_000L).launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(2_001)

        assertEquals("first poll asks for schedules, later ones don't", listOf(true, false, false), fetcher.tripsForRouteCalls)
        assertNotNull("the schedule the poll carried is stored", repo.lookupTripState("tripA")?.schedule)
        assertEquals("no per-trip schedule fetch", emptyList<String>(), fetcher.tripScheduleCalls)
    }

    @Test
    fun `a vehicle that appears without a schedule is covered by the next poll, not its own request`() = runTest {
        var polls = 0
        val fetcher = FakeFetcher(
            routeTrips = { include ->
                polls++
                // tripB starts its run on the second poll.
                val entries = listOfNotNull(Entry("tripA", withSchedule = include), Entry("tripB", withSchedule = include).takeIf { polls >= 2 })
                Result.success(routeTrips(*entries.toTypedArray()))
            }
        )
        val repo = DefaultTripObservationRepository(fetcher)

        repo.routeVehiclesStream("route1", intervalMs = 1_000L).launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(3_001)

        assertEquals(listOf(true, false, true, false), fetcher.tripsForRouteCalls)
        assertNotNull(repo.lookupTripState("tripB")?.schedule)
        assertEquals("no per-trip schedule fetch", emptyList<String>(), fetcher.tripScheduleCalls)
    }

    @Test
    fun `a schedule the route poll can't carry is fetched for that trip alone`() = runTest {
        // A block rollover: tripA's entry reports tripB active, and the wire dedup keeps it over tripB's
        // own entry, so the response carries tripA's schedule but not tripB's.
        val fetcher = FakeFetcher(
            routeTrips = { include ->
                Result.success(routeTrips(Entry("tripA", activeTripId = "tripB", withSchedule = include), Entry("tripB", withSchedule = include)))
            },
            scheduleFor = { TripSchedule().toObaTripSchedule() }
        )
        val repo = DefaultTripObservationRepository(fetcher)

        repo.routeVehiclesStream("route1", intervalMs = 1_000L).launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(1_001)

        assertEquals("only the trip the poll couldn't carry is fetched", listOf("tripB"), fetcher.tripScheduleCalls)
        assertNotNull(repo.lookupTripState("tripB")?.schedule)
        assertEquals("with it stored, the next poll doesn't ask again", listOf(true, false), fetcher.tripsForRouteCalls)
    }

    @Test
    fun `a fallback schedule fetch that keeps failing never turns the route poll heavy`() = runTest {
        // The rollover case again, but tripB's own schedule can't be had.
        val fetcher = FakeFetcher(
            routeTrips = { include ->
                Result.success(routeTrips(Entry("tripA", activeTripId = "tripB", withSchedule = include), Entry("tripB", withSchedule = include)))
            }
        )
        val repo = DefaultTripObservationRepository(fetcher)

        repo.routeVehiclesStream("route1", intervalMs = 1_000L).launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(3_001)

        assertEquals("only the first poll asks for schedules", listOf(true, false, false, false), fetcher.tripsForRouteCalls)
        assertEquals("the trip is retried on its own each poll", List(4) { "tripB" }, fetcher.tripScheduleCalls)
    }

    @Test
    fun `a failed route poll asks for schedules again on its retry`() = runTest {
        var polls = 0
        val fetcher = FakeFetcher(
            routeTrips = { include ->
                if (polls++ == 0) Result.failure(IOException("offline")) else Result.success(routeTrips(Entry("tripA", withSchedule = include)))
            }
        )
        val repo = DefaultTripObservationRepository(fetcher)

        repo.routeVehiclesStream("route1", intervalMs = 1_000L).launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(1_001)

        assertEquals(listOf(true, true), fetcher.tripsForRouteCalls)
    }
}
