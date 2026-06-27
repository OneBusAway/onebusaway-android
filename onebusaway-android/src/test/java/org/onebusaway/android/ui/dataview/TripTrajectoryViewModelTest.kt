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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.testing.MainDispatcherRule
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
}
