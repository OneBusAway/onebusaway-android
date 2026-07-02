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
package org.onebusaway.android.ui.dataview

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.testing.testTripStatus
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.Polyline
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripTrajectoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepo(private val state: TripState?) : TripObservationRepository {
        var detailsCollections = 0
            private set

        override fun lookupTripState(tripId: String?): TripState? = state

        override fun tripDetailsStream(tripId: String, intervalMs: Long): Flow<Unit> =
            flow {
                detailsCollections++
                awaitCancellation()
            }

        override fun routeVehiclesStream(routeId: String, intervalMs: Long): Flow<RouteTrips> =
            emptyFlow()

        override suspend fun ensureShape(tripId: String, shapeId: String): Polyline? = null
    }

    /**
     * A repository whose details stream, once collected, records each pushed snapshot into the store
     * that [lookupTripState] serves — the one-way record path the screen relies on. [record] pushes
     * a snapshot; the collected stream applies it, so a refresh() afterward sees exactly what the
     * stream recorded.
     */
    private class HydratingRepo : TripObservationRepository {
        // Capacity 1 so record()'s emit doesn't suspend waiting for the collector on the test thread.
        private val records = MutableSharedFlow<TripState>(extraBufferCapacity = 1)

        @Volatile
        private var stored: TripState? = null

        suspend fun record(state: TripState) {
            records.emit(state)
        }

        override fun lookupTripState(tripId: String?): TripState? = stored

        override fun tripDetailsStream(tripId: String, intervalMs: Long): Flow<Unit> =
            // Records on collection; emits nothing — the ViewModel only collects for the side effect.
            records.transform<TripState, Unit> { stored = it }

        override fun routeVehiclesStream(routeId: String, intervalMs: Long): Flow<RouteTrips> =
            emptyFlow()

        override suspend fun ensureShape(tripId: String, shapeId: String): Polyline? = null
    }

    private fun viewModel(repo: TripObservationRepository) = TripTrajectoryViewModel(
        SavedStateHandle(mapOf(NavRoutes.ARG_TRIP_ID to "trip1")),
        repo,
    )

    @Test
    fun `collects the trip-details stream to keep the store fresh`() = runTest {
        val repo = FakeRepo(null)
        viewModel(repo)
        runCurrent()
        assertEquals(1, repo.detailsCollections)
    }

    @Test
    fun `refresh rebuilds the ui state from the store snapshot`() = runTest {
        val vm = viewModel(FakeRepo(TripState("trip1")))
        runCurrent()

        vm.refresh(nowMs = 10_000L)

        val state = vm.state.value
        assertEquals("trip1", state.tripId)
        assertEquals(0, state.sampleCount)
        assertTrue(state.trajectory.observations.isEmpty())
        assertTrue("a drawable viewport even with no data", state.trajectory.bounds.maxDist > state.trajectory.bounds.minDist)
    }

    @Test
    fun `refresh with no snapshot yields the empty state`() = runTest {
        val vm = viewModel(FakeRepo(null))
        runCurrent()

        vm.refresh(nowMs = 10_000L)

        assertEquals(0, vm.state.value.sampleCount)
    }

    @Test
    fun `end-to-end - collecting the stream hydrates the store that refresh reads`() = runTest {
        val repo = HydratingRepo()
        val vm = viewModel(repo)
        runCurrent() // init's collector subscribes to the details stream

        // Nothing recorded yet: the store is empty, so refresh reflects an empty trajectory.
        vm.refresh(nowMs = 10_000L)
        assertEquals(0, vm.state.value.sampleCount)

        // A poll records a vehicle status into the store via the collected stream.
        repo.record(
            TripState("trip1").withStatus(
                testTripStatus(distanceAlongTrip = 500.0, lastUpdateTime = 5_000L, vehicleId = "bus7"),
                serverTimeMs = 5_000L,
                localTimeMs = 5_000L,
            )
        )
        runCurrent()

        // refresh now rebuilds the ui state from the hydrated snapshot.
        vm.refresh(nowMs = 10_000L)
        val state = vm.state.value
        assertEquals("trip1", state.tripId)
        assertEquals("bus7", state.vehicleId)
        assertEquals(1, state.sampleCount)
        assertEquals(1, state.trajectory.observations.size)
        assertEquals(500.0, state.trajectory.observations.first().distanceMeters, 0.0)
    }
}
