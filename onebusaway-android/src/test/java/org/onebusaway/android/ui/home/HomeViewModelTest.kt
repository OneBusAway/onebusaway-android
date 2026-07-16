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
package org.onebusaway.android.ui.home

import org.onebusaway.android.api.adapters.ObaStopElement

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.location.FakeLocationRepository
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule

private class FakeStartupPreferencesRepository(
    var initial: Boolean = false
) : StartupPreferencesRepository {
    var cleared = 0
    override fun isInitialStartup(): Boolean = initial
    override fun clearInitialStartup() { cleared++; initial = false }
}

/**
 * Collects the map directives a [HomeViewModel] emits (and reads its bottom-padding state) so the
 * outbound Home→Map interactions can be asserted directly — the role the old MapInteractionBus fake
 * filled, now just reading the VM's own outputs. Launch [collect] inside the test's scope first.
 */
private class MapDirectiveRecorder(private val vm: HomeViewModel) {
    val sent = mutableListOf<MapDirective>()

    val recenters get() = sent.filterIsInstance<MapDirective.RecenterOnFocusedStop>().map { it.lat to it.lon }
    val routeRequests get() = sent.filterIsInstance<MapDirective.ShowRoute>().map { it.request }
    val routeCommands get() = sent.filterIsInstance<MapDirective.ShowRoute>()
    val routesShown get() = sent.filterIsInstance<MapDirective.ShowRoute>().map { it.request.routeId }
    val stopRoutes get() = sent.filterIsInstance<MapDirective.ShowStopRoutes>()
    val clearStopRoutesCount get() = sent.count { it is MapDirective.ClearStopRoutes }
    val clearFocusCount get() = sent.count { it is MapDirective.ClearFocus }
    val focusStops get() = sent.filterIsInstance<MapDirective.FocusStop>()
    val lastBottomPadding get() = vm.mapBottomPadding.value

    suspend fun collect() {
        vm.mapDirectives.collect { sent.add(it) }
    }
}

/**
 * Unit tests for [HomeViewModel]: focus coordination, the arrivals-sheet → map effects, and region resolution.
 * Mirrors the established ViewModel test pattern (MainDispatcherRule + runTest + hand-written fakes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        regionStatus: RegionStatus = RegionStatus.Unchanged,
        startupRepo: FakeStartupPreferencesRepository = FakeStartupPreferencesRepository(),
        regionRepo: FakeRegionRepository = FakeRegionRepository().apply { refreshResult = regionStatus },
        savedState: SavedStateHandle = SavedStateHandle(),
        locationRepo: FakeLocationRepository = FakeLocationRepository(),
    ) = HomeViewModel(
        savedState, startupRepo, regionRepo, locationRepo
    )

    // The raw stop payload onArrivalsLoaded forwards to the map; its identity is irrelevant to the
    // pending-focus gate, so one shared fixture suffices.
    private val obaStop = ObaStopElement("1", 47.6, -122.3, "Main St", "100")

    // --- arrivals sheet settled -> map padding / recenter ---

    @Test
    fun `the initial sheet reveal from hidden emits no map effects`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // previous == Hidden -> skip
        advanceUntilIdle()

        assertTrue(map.sent.isEmpty())
        assertEquals(ArrivalsSheetState.Collapsed, vm.lastSettledSheet)
        job.cancel()
    }

    @Test
    fun `expanding over a focused stop sets padding and recenters`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Expanded, 120)
        advanceUntilIdle()

        assertEquals(120, vm.mapBottomPadding.value)
        assertEquals(listOf(47.6 to -122.3), map.recenters)
        job.cancel()
    }

    @Test
    fun `expanding with no focused stop only sets padding`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Expanded, 120)
        advanceUntilIdle()

        assertEquals(120, vm.mapBottomPadding.value)
        assertTrue(map.recenters.isEmpty())
        job.cancel()
    }

    @Test
    fun `collapsing and hiding set the map padding`() = runTest {
        // Bottom padding is plain state, so no directive collector is needed.
        val vm = viewModel()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 80)
        assertEquals(80, vm.mapBottomPadding.value)
        vm.onSheetSettled(ArrivalsSheetState.Hidden, 80)
        assertEquals(0, vm.mapBottomPadding.value)
        assertEquals(ArrivalsSheetState.Hidden, vm.lastSettledSheet)
    }

    // --- initial focus (restored vs intent deep-link) ---

    @Test
    fun `applyInitialFocus adopts an intent stop and marks it pending`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("1", "Main St", "100", 47.6, -122.3)
        vm.applyInitialFocus(stop)
        assertEquals(stop, vm.currentFocus.value.focusedStop)
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size) // pending was marked -> focus dispatched to the map
        job.cancel()
    }

    @Test
    fun `applyInitialFocus keeps a restored focus and marks it pending`() = runTest {
        val handle = SavedStateHandle()
        val restored = FocusedStop("42", "Pike St", "577", 47.61, -122.34)
        viewModel(savedState = handle).onStopFocused(restored)
        val vm = viewModel(savedState = handle) // recreation: focus restored from the handle
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.applyInitialFocus(null) // intent carries no stop
        assertEquals(restored, vm.currentFocus.value.focusedStop) // unchanged
        vm.onArrivalsLoaded(ObaStopElement("42", 47.61, -122.34, "Pike St", "577"), null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size) // pending was marked
        job.cancel()
    }

    @Test
    fun `applyInitialFocus with no restored or intent focus does nothing`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.applyInitialFocus(null)
        assertNull(vm.currentFocus.value.focusedStop)
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(0, map.focusStops.size) // not pending -> nothing dispatched
        job.cancel()
    }

    // --- pending map focus / route mode / clear focus ---

    @Test
    fun `a pending focus is dispatched once on arrivals load`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))
        advanceUntilIdle()
        map.sent.clear()
        vm.markPendingMapFocus()
        // Pending -> dispatch FocusStop (sheet not expanded -> overlayExpanded false); latch then clears.
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        assertEquals(false, map.focusStops.single().overlayExpanded)
        vm.onArrivalsLoaded(obaStop, null, emptySet())         // latch cleared -> no further dispatch
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        job.cancel()
    }

    @Test
    fun `arrivals load with no pending focus dispatches nothing`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(0, map.focusStops.size)
        job.cancel()
    }

    @Test
    fun `a pending focus dispatches overlay-expanded when the sheet is expanded`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))
        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Expanded, 120)

        vm.markPendingMapFocus()
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(true, map.focusStops.single().overlayExpanded)
        job.cancel()
    }

    @Test
    fun `standalone route focus shows the route`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()

        vm.focusStandaloneRoute(ShowRouteRequest("42"))
        advanceUntilIdle()

        assertEquals(listOf("42"), map.routesShown)
        assertEquals(CurrentFocus.Route(RouteTarget("42")), vm.currentFocus.value)
        mapJob.cancel()
    }

    @Test
    fun `focused stop route badge preserves stop focus and line direction`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", 47.6, -122.3))

        vm.requestShowFocusedStopRouteOnMap("42", directionId = 1)
        advanceUntilIdle()

        assertEquals(
            ShowRouteRequest(routeId = "42", directionStopId = "stop", initialDirectionId = 1),
            map.routeRequests.single(),
        )
        assertTrue(map.routeCommands.single().stopScoped)
        mapJob.cancel()
    }

    @Test
    fun `drawer route selection and continuation remain subordinate to stop focus`() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        advanceUntilIdle()
        map.sent.clear()

        vm.selectArrivalRoute(
            request = ShowRouteRequest("65", directionStopId = "stop", initialDirectionId = 0),
            shortName = "65",
            headsign = "Downtown",
        )
        vm.advanceRouteContinuation("75", "75", directionId = 1)
        advanceUntilIdle()

        val focus = vm.currentFocus.value as CurrentFocus.Stop
        assertEquals(stop, focus.stop)
        assertEquals("Downtown", focus.selectedRoute?.originHeadsign)
        assertEquals(listOf("65", "75"), focus.selectedRoute?.legs?.map { it.shortName })
        assertEquals(listOf(true, true), map.routeCommands.map { it.stopScoped })
        val restored = viewModel(savedState = savedState).currentFocus.value as CurrentFocus.Stop
        assertEquals(focus.selectedRoute, restored.selectedRoute)
        mapJob.cancel()
    }

    @Test
    fun `clearing a subordinate route retains stop focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", null, "65")

        vm.clearStopRouteSelection()
        advanceUntilIdle()

        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(1, map.sent.count { it is MapDirective.ClearSelectedRoute })
        mapJob.cancel()
    }

    @Test
    fun `focus back stack returns from stop route to stop to none`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0, shortName = "65")
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(1, map.sent.count { it is MapDirective.ClearSelectedRoute })

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)
        assertEquals(false, vm.navigateBackFocus())
        mapJob.cancel()
    }

    @Test
    fun `switching selected routes replaces one route focus layer`() = runTest {
        val vm = viewModel()
        val stop = FocusedStop("stop", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0, shortName = "65")
        vm.requestShowFocusedStopRouteOnMap("75", directionId = 1, shortName = "75")

        assertTrue(vm.navigateBackFocus())

        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
    }

    @Test
    fun `back from standalone route restores its previous stop after arrivals load`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("1", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        vm.focusStandaloneRoute(ShowRouteRequest("65"))
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)

        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        assertEquals(1, map.stopRoutes.size)
        mapJob.cancel()
    }

    @Test
    fun `explicit clear discards focus history`() = runTest {
        val vm = viewModel()
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", 47.6, -122.3))
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0)

        vm.requestClearMapFocus()

        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertEquals(false, vm.navigateBackFocus())
    }

    @Test
    fun `restored stop route reconstructs its stop and root parents`() = runTest {
        val state = SavedStateHandle()
        val stop = FocusedStop("stop", "Main St", "100", 47.6, -122.3)
        viewModel(savedState = state).apply {
            onStopFocused(stop)
            requestShowFocusedStopRouteOnMap("65", directionId = 0)
        }
        val restored = viewModel(savedState = state)

        assertTrue(restored.navigateBackFocus())
        assertEquals(CurrentFocus.Stop(stop), restored.currentFocus.value)
        assertTrue(restored.navigateBackFocus())
        assertEquals(CurrentFocus.None, restored.currentFocus.value)
    }

    @Test
    fun `standalone route replaces stop focus and is restored`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(savedState = handle)
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", 47.6, -122.3))

        vm.focusStandaloneRoute(ShowRouteRequest("65", initialDirectionId = 1))

        val expected = CurrentFocus.Route(RouteTarget("65", directionId = 1))
        assertEquals(expected, vm.currentFocus.value)
        assertEquals(expected, viewModel(savedState = handle).currentFocus.value)
    }

    @Test
    fun `clear map focus clears the focused stop and the map focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))

        vm.requestClearMapFocus()
        advanceUntilIdle()

        assertNull(vm.currentFocus.value.focusedStop)
        assertEquals(1, map.clearFocusCount)
        job.cancel()
    }

    // --- focused-stop exact trips (#1827) ---

    @Test
    fun `arrivals load records exact displayed trips even without a pending focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        // No pending restored focus: the load still starts the route view for the already-tapped stop.
        val trips = setOf(
            FocusedTrip("trip-40", "40", "shape-40-express", 0xFF112233.toInt()),
            FocusedTrip("trip-44", "44", "shape-44-local", null),
        )
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))
        advanceUntilIdle()
        map.sent.clear()
        vm.onArrivalsLoaded(obaStop, null, trips)
        advanceUntilIdle()

        assertEquals(trips, vm.focusedTrips)
        assertEquals(0, map.focusStops.size)
        assertEquals(listOf(trips), map.stopRoutes.map { it.trips })
        job.cancel()
    }

    @Test
    fun `arrivals load dispatches map focus before focused trips`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))
        advanceUntilIdle()
        map.sent.clear()
        vm.markPendingMapFocus()

        val trips = setOf(FocusedTrip("trip-7", "7", "shape-7", null))
        vm.onArrivalsLoaded(obaStop, null, trips)
        advanceUntilIdle()

        assertEquals(trips, vm.focusedTrips)
        assertEquals(1, map.focusStops.size) // pending focus still dispatched
        assertTrue(map.sent.indexOfFirst { it is MapDirective.FocusStop } <
            map.sent.indexOfFirst { it is MapDirective.ShowStopRoutes })
        job.cancel()
    }

    @Test
    fun `focusing a different stop resets exact trips`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))
        advanceUntilIdle()
        map.sent.clear()
        vm.onArrivalsLoaded(obaStop, null, setOf(FocusedTrip("trip", "40", "shape", null)))
        assertTrue(vm.focusedTrips.isNotEmpty())

        vm.onStopFocused(FocusedStop("2", "2nd Ave", "200", 47.6, -122.3))
        advanceUntilIdle()
        assertEquals(emptySet<FocusedTrip>(), vm.focusedTrips)
        assertEquals(1, map.clearStopRoutesCount)
        job.cancel()
    }

    @Test
    fun `clearing map focus resets exact trips`() = runTest {
        val vm = viewModel()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", 47.6, -122.3))
        vm.onArrivalsLoaded(obaStop, null, setOf(FocusedTrip("trip", "40", "shape", null)))
        assertTrue(vm.focusedTrips.isNotEmpty())

        vm.requestClearMapFocus()
        assertEquals(emptySet<FocusedTrip>(), vm.focusedTrips)
    }

    // --- focus + SavedStateHandle ---

    @Test
    fun `onStopFocused sets and clears the focused stop`() = runTest {
        val vm = viewModel()
        val stop = FocusedStop("1", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        assertEquals(stop, vm.currentFocus.value.focusedStop)
        vm.onStopFocused(null)
        assertNull(vm.currentFocus.value.focusedStop)
    }

    @Test
    fun `focusing a stop clears the focused bike station`() = runTest {
        val vm = viewModel()
        vm.onBikeStationFocused("bike-7")
        assertEquals("bike-7", vm.currentFocus.value.focusedBikeStationId)
        vm.onStopFocused(FocusedStop("1", null, null, 1.0, 2.0))
        assertNull(vm.currentFocus.value.focusedBikeStationId)
    }

    @Test
    fun `focused stop is restored from SavedStateHandle on recreation`() = runTest {
        val handle = SavedStateHandle()
        val stop = FocusedStop("42", "Pike St", "577", 47.61, -122.34)
        viewModel(savedState = handle).onStopFocused(stop)
        // A fresh ViewModel over the same handle simulates process-death recreation.
        assertEquals(stop, viewModel(savedState = handle).currentFocus.value.focusedStop)
    }

    // --- region refresh (events + manual-picker dialog) ---

    @Test
    fun `a changed region reports a region-selected analytics event`() = runTest {
        val region = region(1)
        val vm = viewModel(regionStatus = RegionStatus.Changed(region))
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertEquals(listOf<HomeAnalyticsEvent>(HomeAnalyticsEvent.RegionSelected(region.name)), events)
        job.cancel()
    }

    @Test
    fun `an auto-selected region is announced via the regionFound event`() = runTest {
        val region = region(1)
        val vm = viewModel(regionStatus = RegionStatus.Changed(region))
        val found = mutableListOf<String>()
        val job = launch { vm.regionFound.collect { found.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertEquals(listOf(region.name), found)
        job.cancel()
    }

    @Test
    fun `an unchanged region is not announced`() = runTest {
        val vm = viewModel(regionStatus = RegionStatus.Unchanged)
        val found = mutableListOf<String>()
        val job = launch { vm.regionFound.collect { found.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertTrue(found.isEmpty())
        job.cancel()
    }

    @Test
    fun `an unchanged region reports no analytics event`() = runTest {
        val vm = viewModel(regionStatus = RegionStatus.Unchanged)
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `needing manual selection reports no analytics (the picker is driven off the repository)`() = runTest {
        val regions = listOf(region(1), region(2))
        val vm = viewModel(regionStatus = RegionStatus.NeedsManualSelection(regions))
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `skipped, fixed, and failed statuses emit no event`() = runTest {
        val statuses = listOf(RegionStatus.Skipped, RegionStatus.Fixed(region(1)), RegionStatus.Failed)
        for (status in statuses) {
            val vm = viewModel(regionStatus = status)
            val events = mutableListOf<HomeAnalyticsEvent>()
            val job = launch { vm.analyticsEvents.collect { events.add(it) } }
            advanceUntilIdle()

            vm.refreshRegions()
            advanceUntilIdle()

            assertTrue("$status should emit no event", events.isEmpty())
            job.cancel()
        }
    }

    // (The forced-choice picker + the experimental-regions OTP-reset rule moved off HomeViewModel — see
    // RegionPickerViewModelTest and AdvancedSettingsViewModelTest.)

    // --- report-target derivation (send feedback / contact us) ---
    // The "no stop, but a location is known" branch isn't unit-tested: android.location.Location can't be
    // constructed in plain JVM tests (no Robolectric / mocking lib), so it's left to instrumented coverage.

    @Test
    fun `reportTarget is the focused stop when one is focused`() {
        val vm = viewModel()
        val stop = FocusedStop("1_123", "Main St & 1st", "123", 47.6, -122.3)
        vm.onStopFocused(stop)

        assertEquals(ReportTarget.Stop(stop), vm.reportTarget())
    }

    @Test
    fun `reportTarget is Generic with no focused stop and no known location`() {
        val vm = viewModel(locationRepo = FakeLocationRepository(last = null))

        assertEquals(ReportTarget.Generic, vm.reportTarget())
    }

    // --- startup region-check gate ---

    @Test
    fun `first launch without permission defers the region check`() = runTest {
        val region = FakeRegionRepository()
        viewModel(regionRepo = region, startupRepo = FakeStartupPreferencesRepository(initial = true))
            .onHomeStarted(hasLocationPermission = false)
        advanceUntilIdle()
        assertEquals(0, region.refreshCount)
    }

    @Test
    fun `first launch with permission checks the region now`() = runTest {
        val region = FakeRegionRepository()
        viewModel(regionRepo = region, startupRepo = FakeStartupPreferencesRepository(initial = true))
            .onHomeStarted(hasLocationPermission = true)
        advanceUntilIdle()
        assertEquals(1, region.refreshCount)
    }

    @Test
    fun `a later launch checks the region regardless of permission`() = runTest {
        val region = FakeRegionRepository()
        viewModel(regionRepo = region, startupRepo = FakeStartupPreferencesRepository(initial = false))
            .onHomeStarted(hasLocationPermission = false)
        advanceUntilIdle()
        assertEquals(1, region.refreshCount)
    }

    @Test
    fun `the first-launch permission result clears the flag and checks the region`() = runTest {
        val region = FakeRegionRepository()
        val startup = FakeStartupPreferencesRepository(initial = true)
        val vm = viewModel(regionRepo = region, startupRepo = startup)
        vm.onLocationPermissionResult()
        advanceUntilIdle()
        assertEquals(1, startup.cleared)
        assertEquals(1, region.refreshCount)
    }

    @Test
    fun `a permission result after the first launch does nothing`() = runTest {
        val region = FakeRegionRepository()
        val startup = FakeStartupPreferencesRepository(initial = false)
        viewModel(regionRepo = region, startupRepo = startup).onLocationPermissionResult()
        advanceUntilIdle()
        assertEquals(0, startup.cleared)
        assertEquals(0, region.refreshCount)
    }
}
