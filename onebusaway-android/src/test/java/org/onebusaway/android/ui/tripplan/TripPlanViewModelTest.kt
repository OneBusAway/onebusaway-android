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
package org.onebusaway.android.ui.tripplan

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.TimeProvider
import org.opentripplanner.api.model.Itinerary

@OptIn(ExperimentalCoroutinesApi::class)
class TripPlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = AdvancedSettings(modeId = 4, maxWalkMeters = 1600.0, optimizeTransfers = true, wheelchair = false)
    private val origin = PlaceItem("Origin", lat = 47.6, lon = -122.3)
    private val destination = PlaceItem("Destination", lat = 47.7, lon = -122.2)

    private class FakeGeocodeRepository(var result: Result<List<PlaceItem>>) : GeocodeRepository {
        var lastQuery: String? = null
        override suspend fun suggest(query: String): Result<List<PlaceItem>> {
            lastQuery = query
            return result
        }
    }

    private class FakeTripPlanRepository(var result: Result<List<Itinerary>>) : TripPlanRepository {
        var calls = 0
        override suspend fun plan(params: TripPlanParams): Result<List<Itinerary>> {
            calls++
            return result
        }
    }

    private inner class FakeAdvancedSettingsRepository : AdvancedSettingsRepository {
        override fun load() = settings
    }

    private fun viewModel(
        geocode: GeocodeRepository = FakeGeocodeRepository(Result.success(emptyList())),
        plan: TripPlanRepository = FakeTripPlanRepository(Result.success(listOf(Itinerary())))
    ) = TripPlanViewModel(geocode, plan, TimeProvider { 0L }, FakeAdvancedSettingsRepository())

    @Test
    fun `initial state carries the injected settings and cannot submit`() = runTest {
        val vm = viewModel()
        val state = vm.formState.value
        assertEquals(4, state.modeId)
        assertEquals(1600.0, state.maxWalkMeters)
        assertTrue(state.optimizeTransfers)
        assertFalse(state.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `a query change populates suggestions after the debounce`() = runTest {
        val geocode = FakeGeocodeRepository(Result.success(listOf(origin, destination)))
        val vm = viewModel(geocode = geocode)
        vm.onFromQueryChange("down")
        advanceUntilIdle()
        assertEquals("down", geocode.lastQuery)
        assertEquals(listOf(origin, destination), vm.formState.value.fromSuggestions)
    }

    @Test
    fun `setting both endpoints with coordinates auto-submits the plan`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(Itinerary())))
        val vm = viewModel(plan = plan)

        vm.setFrom(origin)
        advanceUntilIdle()
        assertEquals(0, plan.calls) // destination still missing

        vm.setTo(destination)
        advanceUntilIdle()
        assertTrue(vm.formState.value.canSubmit)
        assertEquals(1, plan.calls)
        assertTrue(vm.planState.value is PlanResult.Success)
    }

    @Test
    fun `an endpoint without coordinates does not enable submit`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(Itinerary())))
        val vm = viewModel(plan = plan)

        vm.setFrom(PlaceItem("Contact A", lat = null, lon = null))
        vm.setTo(PlaceItem("Contact B", lat = null, lon = null))
        advanceUntilIdle()

        assertFalse(vm.formState.value.canSubmit)
        assertEquals(0, plan.calls)
    }

    @Test
    fun `reverseTrip swaps origin and destination`() = runTest {
        val vm = viewModel()
        vm.setFrom(origin)
        vm.setTo(destination)
        advanceUntilIdle()

        vm.reverseTrip()
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(destination, state.from)
        assertEquals(origin, state.to)
    }

    @Test
    fun `applyAdvancedSettings updates the form`() = runTest {
        val vm = viewModel()
        val updated = AdvancedSettings(modeId = 1, maxWalkMeters = null, optimizeTransfers = false, wheelchair = true)
        vm.applyAdvancedSettings(updated)
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(1, state.modeId)
        assertTrue(state.wheelchair)
        assertFalse(state.optimizeTransfers)
        assertEquals(null, state.maxWalkMeters)
    }

    @Test
    fun `a plan failure surfaces Error with the message`() = runTest {
        val vm = viewModel(plan = FakeTripPlanRepository(Result.failure(IOException("no route"))))
        vm.setFrom(origin)
        vm.setTo(destination)
        advanceUntilIdle()
        assertEquals(PlanResult.Error("no route"), vm.planState.value)
    }

    @Test
    fun `setDateTime refreshes the date and time labels`() = runTest {
        val vm = viewModel()
        vm.setDateTime(1_700_000_000_000L)
        val state = vm.formState.value
        assertTrue(state.dateLabel.isNotBlank())
        assertTrue(state.timeLabel.isNotBlank())
        assertEquals(1_700_000_000_000L, state.dateTimeMillis)
    }
}
