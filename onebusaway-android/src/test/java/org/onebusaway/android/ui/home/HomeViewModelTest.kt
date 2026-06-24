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

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.io.elements.ObaRegion
import org.onebusaway.android.io.elements.ObaStopElement
import org.onebusaway.android.location.FakeLocationRepository
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
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
    val routesShown get() = sent.filterIsInstance<MapDirective.ShowRoute>().map { it.routeId }
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
        prefsRepo: FakePreferencesRepository = FakePreferencesRepository(),
        savedState: SavedStateHandle = SavedStateHandle(),
        locationRepo: FakeLocationRepository = FakeLocationRepository(),
    ) = HomeViewModel(
        savedState, startupRepo, regionRepo, prefsRepo, locationRepo
    )

    // The raw stop payload onArrivalsLoaded forwards to the map; its identity is irrelevant to the
    // pending-focus gate, so one shared fixture suffices.
    private val obaStop = ObaStopElement("1", 47.6, -122.3, "Main St", "100")

    // --- one-shot sheet / drawer commands ---

    @Test
    fun `the chevron tap emits ToggleSheet`() = runTest {
        val vm = viewModel()
        val events = mutableListOf<SheetCommand>()
        val job = launch { vm.sheetCommands.collect { events.add(it) } }
        advanceUntilIdle()

        vm.requestToggleSheet()
        advanceUntilIdle()

        assertEquals(listOf<SheetCommand>(SheetCommand.ToggleSheet), events)
        job.cancel()
    }

    // --- arrivals sheet settled -> map padding / recenter ---

    @Test
    fun `the initial sheet reveal from hidden emits no map effects`() = runTest {
        val vm = viewModel()
        val events = mutableListOf<SheetCommand>()
        val job = launch { vm.sheetCommands.collect { events.add(it) } }
        advanceUntilIdle()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // previous == Hidden -> skip
        advanceUntilIdle()

        assertTrue(events.isEmpty())
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
        assertEquals(stop, vm.uiState.value.focusedStop)
        vm.onArrivalsLoaded(obaStop, null)
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
        assertEquals(restored, vm.uiState.value.focusedStop) // unchanged
        vm.onArrivalsLoaded(obaStop, null)
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
        assertNull(vm.uiState.value.focusedStop)
        vm.onArrivalsLoaded(obaStop, null)
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
        vm.markPendingMapFocus()
        // Pending -> dispatch FocusStop (sheet not expanded -> overlayExpanded false); latch then clears.
        vm.onArrivalsLoaded(obaStop, null)
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        assertEquals(false, map.focusStops.single().overlayExpanded)
        vm.onArrivalsLoaded(obaStop, null)         // latch cleared -> no further dispatch
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
        vm.onArrivalsLoaded(obaStop, null)
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
        vm.onArrivalsLoaded(obaStop, null)
        advanceUntilIdle()
        assertEquals(true, map.focusStops.single().overlayExpanded)
        job.cancel()
    }

    @Test
    fun `show route on map collapses the sheet and shows the route`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        val events = mutableListOf<SheetCommand>()
        val eventJob = launch { vm.sheetCommands.collect { events.add(it) } }
        advanceUntilIdle()

        vm.requestShowRouteOnMap("42")
        advanceUntilIdle()

        assertEquals(listOf<SheetCommand>(SheetCommand.CollapseSheet), events)
        assertEquals(listOf("42"), map.routesShown)
        mapJob.cancel()
        eventJob.cancel()
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

        assertNull(vm.uiState.value.focusedStop)
        assertEquals(1, map.clearFocusCount)
        job.cancel()
    }

    // --- focus + SavedStateHandle ---

    @Test
    fun `onStopFocused sets and clears the focused stop`() = runTest {
        val vm = viewModel()
        val stop = FocusedStop("1", "Main St", "100", 47.6, -122.3)
        vm.onStopFocused(stop)
        assertEquals(stop, vm.uiState.value.focusedStop)
        vm.onStopFocused(null)
        assertNull(vm.uiState.value.focusedStop)
    }

    @Test
    fun `focusing a stop clears the focused bike station`() = runTest {
        val vm = viewModel()
        vm.onBikeStationFocused("bike-7")
        assertEquals("bike-7", vm.uiState.value.focusedBikeStationId)
        vm.onStopFocused(FocusedStop("1", null, null, 1.0, 2.0))
        assertNull(vm.uiState.value.focusedBikeStationId)
    }

    @Test
    fun `focused stop is restored from SavedStateHandle on recreation`() = runTest {
        val handle = SavedStateHandle()
        val stop = FocusedStop("42", "Pike St", "577", 47.61, -122.34)
        viewModel(savedState = handle).onStopFocused(stop)
        // A fresh ViewModel over the same handle simulates process-death recreation.
        assertEquals(stop, viewModel(savedState = handle).uiState.value.focusedStop)
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
    fun `needing manual selection raises the chooser dialog and reports no analytics`() = runTest {
        val regions = listOf(region(1), region(2))
        val vm = viewModel(regionStatus = RegionStatus.NeedsManualSelection(regions))
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertEquals(HomeDialog.ChooseRegion(regions), vm.uiState.value.dialog)
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

    @Test
    fun `onRegionChosen selects the region and dismisses the dialog (no analytics)`() = runTest {
        val regions = listOf(region(1), region(2))
        val repo = FakeRegionRepository().apply {
            refreshResult = RegionStatus.NeedsManualSelection(regions)
        }
        val vm = viewModel(regionRepo = repo)
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()
        val chosen = regions[1]
        vm.onRegionChosen(chosen)
        advanceUntilIdle()

        assertEquals(listOf(chosen), repo.chosen)
        assertEquals(HomeDialog.None, vm.uiState.value.dialog)
        // A manual pick passes a null region name, so it reports no analytics (matching legacy).
        assertTrue(events.isEmpty())
        job.cancel()
    }

    // --- experimental-regions toggle + restore completion effects ---

    @Test
    fun `the experimental-regions toggle resets the OTP API version on a real change`() = runTest {
        val prefs = FakePreferencesRepository().apply {
            setBoolean(R.string.preference_key_otp_api_url_version, true)
        }
        val vm = viewModel(regionStatus = RegionStatus.Changed(region(1)), prefsRepo = prefs)
        advanceUntilIdle()

        vm.onExperimentalRegionsToggled()
        advanceUntilIdle()

        assertFalse(prefs.getBoolean(R.string.preference_key_otp_api_url_version, true))
    }

    @Test
    fun `the experimental-regions toggle leaves the OTP API version untouched when unchanged`() = runTest {
        val prefs = FakePreferencesRepository().apply {
            setBoolean(R.string.preference_key_otp_api_url_version, true)
        }
        val vm = viewModel(regionStatus = RegionStatus.Unchanged, prefsRepo = prefs)
        advanceUntilIdle()

        vm.onExperimentalRegionsToggled()
        advanceUntilIdle()

        assertTrue(prefs.getBoolean(R.string.preference_key_otp_api_url_version, false))
    }

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

