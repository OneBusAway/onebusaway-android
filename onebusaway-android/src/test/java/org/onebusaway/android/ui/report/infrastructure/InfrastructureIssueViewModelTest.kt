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
package org.onebusaway.android.ui.report.infrastructure

import org.onebusaway.android.api.adapters.ObaStopElement

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.testing.MainDispatcherRule

private class FakeServiceListRepository(private val result: ServiceListResult) : ServiceListRepository {
    override suspend fun loadServices(latitude: Double, longitude: Double) = Result.success(result)
}

private class FakeGeocodeAddressRepository(
    private val reverse: String = "123 Main St",
    private val forward: GeoPoint? = GeoPoint(40.0, -80.0)
) : GeocodeAddressRepository {
    override suspend fun reverseGeocode(latitude: Double, longitude: Double) = Result.success(reverse)
    override suspend fun forwardGeocode(query: String) =
        forward?.let { Result.success(it) } ?: Result.failure(IOException())
}

@OptIn(ExperimentalCoroutinesApi::class)
class InfrastructureIssueViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // index 2 = stop, 3 = trip, 5 = Open311 (pothole)
    private val items = listOf(
        ServiceListItem.Hint("Choose a Problem"),
        ServiceListItem.Section("Transit"),
        ServiceListItem.Category(code = null, name = "Stop Problem", group = "Transit", type = "stop", raw = "s"),
        ServiceListItem.Category(code = null, name = "Arrival Time Problem", group = "Transit", type = "trip", raw = "t"),
        ServiceListItem.Section("Streets"),
        ServiceListItem.Category(code = "p1", name = "Pothole", group = "Streets", type = "static", raw = "p")
    )

    private val stop: ObaStop = ObaStopElement("1_75403", 47.6, -122.3, "Pine St & 3rd Ave", "75403")

    private fun viewModel(
        stop: ObaStop? = null,
        default: DefaultIssueType = DefaultIssueType.NONE,
        areaManaged: Boolean = true,
        heuristic: Boolean = false
    ) = InfrastructureIssueViewModel(
        serviceListRepository = FakeServiceListRepository(
            ServiceListResult(items, open311 = "endpoint", areaManaged, heuristic)
        ),
        geocodeRepository = FakeGeocodeAddressRepository(),
        initialLocation = GeoPoint(47.6, -122.3),
        initialStop = stop,
        defaultIssueType = default,
        arrivalInfo = null,
        agencyName = null,
        blockId = null
    )

    @Test
    fun `init loads services and reverse-geocodes the address`() = runTest {
        val vm = viewModel()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(items, state.services)
        assertEquals("123 Main St", state.address)
        assertTrue(state.servicesVisible)
        assertNotNull("a free-location open311 area should show a marker", state.markerLocation)
        assertEquals("endpoint", vm.open311)
    }

    @Test
    fun `selecting an Open311 category routes to the Open311 target`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onServiceSelected(5)

        val target = vm.uiState.value.target
        assertTrue(target is ReportTarget.Open311)
        assertEquals("Pothole", (target as ReportTarget.Open311).category.name)
        assertNull(target.arrival)
    }

    @Test
    fun `stop category with a focused stop routes to the stop form`() = runTest {
        val vm = viewModel(stop = stop)
        advanceUntilIdle()

        vm.onServiceSelected(2)

        assertTrue(vm.uiState.value.target is ReportTarget.StopProblem)
    }

    @Test
    fun `stop category with no stop prompts, then routes once a stop is tapped`() = runTest {
        val vm = viewModel(stop = null)
        advanceUntilIdle()

        vm.onServiceSelected(2)
        assertEquals(ReportTarget.None, vm.uiState.value.target)
        assertTrue(vm.uiState.value.showStopPrompt)

        vm.onMapFocusChanged(stop, 47.61, -122.33)

        assertTrue(vm.uiState.value.target is ReportTarget.StopProblem)
    }

    @Test
    fun `trip category with a stop but no arrival routes to the trip form`() = runTest {
        val vm = viewModel(stop = stop)
        advanceUntilIdle()

        vm.onServiceSelected(3)

        assertTrue(vm.uiState.value.target is ReportTarget.TripProblem)
    }

    @Test
    fun `default stop issue type auto-selects the stop category`() = runTest {
        val vm = viewModel(stop = stop, default = DefaultIssueType.STOP)

        advanceUntilIdle()

        assertTrue(vm.uiState.value.target is ReportTarget.StopProblem)
        assertEquals(2, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `a heuristic-only transit match suppresses default stop auto-selection`() = runTest {
        val vm = viewModel(stop = stop, default = DefaultIssueType.STOP, heuristic = true)

        advanceUntilIdle()

        assertEquals(ReportTarget.None, vm.uiState.value.target)
        assertEquals(0, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `resetting to the hint clears the target`() = runTest {
        val vm = viewModel(stop = stop)
        advanceUntilIdle()
        vm.onServiceSelected(2)
        assertTrue(vm.uiState.value.target is ReportTarget.StopProblem)

        vm.onResetToHint()

        assertEquals(ReportTarget.None, vm.uiState.value.target)
        assertEquals(0, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `address search recenters the map and reloads`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAddressSearch("Space Needle")
        advanceUntilIdle()

        assertEquals(40.0, vm.uiState.value.location.latitude, 0.0001)
        assertEquals(-80.0, vm.uiState.value.location.longitude, 0.0001)
    }
}
