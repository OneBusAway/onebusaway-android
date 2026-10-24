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
package org.onebusaway.android.extrapolation.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.onebusaway.android.api.adapters.toObaTripSchedule
import org.onebusaway.android.api.contract.TripSchedule
import org.onebusaway.android.api.data.TripVehiclesDataSource
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.util.Polyline

/**
 * The fetcher's in-flight bound holds across suspension: a request that is waiting on the network
 * still counts. (A `limitedParallelism` dispatcher didn't, which let a focused stop's schedule
 * fetches go out all at once.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripObservationFetcherConcurrencyTest {

    /** Holds every schedule request open until [release], recording how many were in flight at once. */
    private class GatedDataSource : TripVehiclesDataSource {
        val release = CompletableDeferred<Unit>()
        val started = AtomicInteger()
        private val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()

        override suspend fun tripSchedule(tripId: String): Result<ObaTripSchedule?> {
            started.incrementAndGet()
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            release.await()
            inFlight.decrementAndGet()
            return Result.success(TripSchedule().toObaTripSchedule())
        }

        override suspend fun tripsForRoute(routeId: String, includeSchedules: Boolean): Result<RouteTrips> = error("unused")
        override suspend fun tripDetails(tripId: String): Result<RouteTrips> = error("unused")
        override suspend fun shape(shapeId: String): Result<Polyline?> = error("unused")
        override suspend fun trip(tripId: String): Result<TripRouteInfo?> = error("unused")
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no more than two schedule requests are in flight at once`() = runBlocking {
        val source = GatedDataSource()
        val fetcher = DefaultTripObservationFetcher(source)

        val fetches = (1..5).map { async(Dispatchers.Default) { fetcher.tripSchedule("trip$it") } }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (source.started.get() < 2) yield() }
            delay(200) // room for any request that would wrongly slip past the bound
        }
        assertEquals("only two requests start while both are held", 2, source.started.get())

        source.release.complete(Unit)
        fetches.awaitAll()
        assertEquals("all five eventually run", 5, source.started.get())
        assertEquals("never more than two at once", 2, source.maxInFlight.get())
    }
}
