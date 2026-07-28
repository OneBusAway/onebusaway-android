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

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.report.ReportContext
import org.onebusaway.android.ui.report.TripReportContext
import org.onebusaway.android.util.GeoPoint

private class FakeServiceListRepository(private val result: ServiceListResult) : ServiceListRepository {
    override suspend fun loadServices(latitude: Double, longitude: Double) = Result.success(result)
}

private class FakeGeocodeAddressRepository(
    private val reverse: String = "123 Main St",
    private val forward: GeoPoint? = GeoPoint(40.0, -80.0)
) : GeocodeAddressRepository {
    override suspend fun reverseGeocode(latitude: Double, longitude: Double) = Result.success(reverse)
    override suspend fun forwardGeocode(query: String) = forward?.let { Result.success(it) } ?: Result.failure(IOException())
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

    private val arrival = TripReportContext(
        tripId = "1_trip",
        routeId = "1_100",
        shortName = "40",
        routeLongName = "Downtown",
        headsign = "DOWNTOWN SEATTLE",
        vehicleId = "1_4321",
        stopId = "1_75403",
        serviceDate = 1_700_000_000_000L,
        predicted = true,
        predictedArrivalTime = 1_700_000_600_000L,
        predictedDepartureTime = 1_700_000_600_000L,
        scheduledArrivalTime = 1_700_000_500_000L,
        scheduledDepartureTime = 1_700_000_500_000L,
        hasTripStatus = true,
        scheduleDeviation = 60L,
        lastKnownLat = 47.61,
        lastKnownLon = -122.33
    )

    /**
     * The nav-args the INFRASTRUCTURE_ISSUE route carries. [issueTypeArg] is the raw string so tests
     * can cover the absent and unrecognized cases, not just a well-formed [DefaultIssueType] name.
     */
    private fun navArgs(
        stop: ObaStop? = null,
        issueTypeArg: String? = DefaultIssueType.NONE.name,
        trip: TripReportContext? = null,
        agencyName: String? = null,
        blockId: String? = null
    ) = SavedStateHandle(
        mapOf(
            NavRoutes.ARG_ISSUE_TYPE to issueTypeArg,
            NavRoutes.ARG_REPORT_CONTEXT to ReportContext(
                stopId = stop?.id,
                stopName = stop?.name,
                stopCode = stop?.stopCode,
                lat = 47.6,
                lon = -122.3,
                agencyName = agencyName,
                blockId = blockId,
                trip = trip
            ).encode()
        )
    )

    /**
     * Builds the VM the way the NavHost does — through its nav-args. The launch context goes in as the
     * encoded [ReportContext] the destination's route carries, so these tests exercise the real
     * SavedStateHandle decode rather than a constructor the app no longer uses. Pass [savedState]
     * explicitly to re-create the VM over a handle a previous instance wrote to (a process-death
     * restore).
     */
    private fun viewModel(
        stop: ObaStop? = null,
        default: DefaultIssueType = DefaultIssueType.NONE,
        areaManaged: Boolean = true,
        heuristic: Boolean = false,
        savedState: SavedStateHandle = navArgs(stop, default.name)
    ) = InfrastructureIssueViewModel(
        savedState = savedState,
        serviceListRepository = FakeServiceListRepository(
            ServiceListResult(items, open311 = "endpoint", areaManaged, heuristic)
        ),
        geocodeRepository = FakeGeocodeAddressRepository()
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
    fun `the launch context's trip and agency ids ride through to the trip context`() = runTest {
        val vm = viewModel(
            savedState = navArgs(stop, trip = arrival, agencyName = "Metro", blockId = "1_block")
        )

        advanceUntilIdle()

        assertEquals(Triple(arrival, "Metro", "1_block"), vm.tripContext())
    }

    @Test
    fun `a launch trip context pre-fills the trip form without the picker`() = runTest {
        val vm = viewModel(savedState = navArgs(stop, trip = arrival))
        advanceUntilIdle()

        vm.onServiceSelected(3)

        val target = vm.uiState.value.target
        assertTrue(target is ReportTarget.TripProblem)
        assertEquals(arrival, (target as ReportTarget.TripProblem).arrival)
    }

    @Test
    fun `a picked arrival survives a process-death restore`() = runTest {
        val savedState = navArgs(stop)
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()
        vm.onArrivalSelected(arrival)

        // Same handle, new instance: what Hilt hands the destination after the process is restored.
        val restored = viewModel(savedState = savedState)
        advanceUntilIdle()

        assertEquals(arrival, restored.tripContext().first)
    }

    @Test
    fun `an absent issue type pre-selects nothing`() = runTest {
        val vm = viewModel(savedState = navArgs(stop, issueTypeArg = null))

        advanceUntilIdle()

        assertEquals(ReportTarget.None, vm.uiState.value.target)
        assertEquals(0, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `an unrecognized issue type is a programming error, not a silent NONE`() {
        // "stop" is the retired resource token the route used to carry; a producer still writing it
        // should be a hard failure, not a category that quietly stops pre-selecting.
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            viewModel(savedState = navArgs(stop, issueTypeArg = "stop"))
        }
        assertTrue(requireNotNull(thrown.message).contains("stop"))
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
