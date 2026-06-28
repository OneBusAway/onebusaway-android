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

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.onebusaway.android.io.elements.ObaRegion
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the home screen's genuine coordination state — the focused stop/bike-station (persisted via
 * [SavedStateHandle]) — as a single [HomeUiState], mutated via
 * `_uiState.update { it.copy(...) }`, and drives the startup region-resolve action through [viewModelScope]. The
 * chrome gates, drawer gating, weather, donation, wide alert, regionReady, and the arrivals-sheet
 * measurement are each owned elsewhere now (a feature VM / a HomeScreen-local remember), not here.
 *
 * The arrivals sheet's settle is reported back via [onSheetSettled], which drives the Compose map's
 * bottom padding + recenter. This VM holds no reference to the map's view model: its outbound map
 * interactions are exposed as [mapBottomPadding] state + a [mapDirectives] event stream that
 * [org.onebusaway.android.ui.home.map.MapFeature] (the composable that holds both VMs) bridges to the
 * [org.onebusaway.android.map.MapViewModel].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val startupRepo: StartupPreferencesRepository,
    // The current region's resolution action (refresh / manual-pick). The reactive region subscription
    // (alerts, regionReady) moved to WideAlertViewModel / SurveyViewModel; this VM only drives the resolve
    // action via [refresh]/[choose] (resolution lives in the repository).
    private val regionRepo: RegionRepository,
    // The last-known device location: the report-target fallback reads it here, so the
    // focused-stop-vs-location decision lives with the focused stop instead of in the activity.
    private val locationRepository: LocationRepository,
) : ViewModel() {

    // The single source of truth, mutated via _uiState.update { it.copy(...) }. Seeded from the
    // SavedStateHandle-restored focus so a recreation reflects the restore by construction.
    private val _uiState = MutableStateFlow(
        HomeUiState(
            focusedStop = readFocusedStop(savedState),
            focusedBikeStationId = savedState[KEY_BIKE_STATION],
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sheetCommands = MutableSharedFlow<SheetCommand>(extraBufferCapacity = 4)
    val sheetCommands: SharedFlow<SheetCommand> = _sheetCommands.asSharedFlow()

    // The map's bottom inset (driven by the arrivals sheet) — idempotent last-wins state, applied by
    // MapFeature. A StateFlow (not an event) so a re-entering map re-reads the latest value.
    private val _mapBottomPadding = MutableStateFlow(0)
    val mapBottomPadding: StateFlow<Int> = _mapBottomPadding.asStateFlow()

    // One-shot outbound map interactions (recenter / show route / focus / clear focus) that can't be
    // modeled as state. MapFeature collects these and calls the map view model — so this VM needs no
    // reference to the map's VM (the seam the old MapInteractionBus filled). Buffered like sheetCommands
    // so a directive issued just before the collector is active isn't dropped.
    private val _mapDirectives = MutableSharedFlow<MapDirective>(extraBufferCapacity = 8)
    val mapDirectives: SharedFlow<MapDirective> = _mapDirectives.asSharedFlow()

    // Telemetry events the host's single HomeAnalyticsEffect reports (region auto-selects, nav/help menu
    // selections) — so the imperative ObaAnalytics calls live in one Compose effect, not scattered here.
    private val _analyticsEvents = MutableSharedFlow<HomeAnalyticsEvent>(extraBufferCapacity = 8)
    val analyticsEvents: SharedFlow<HomeAnalyticsEvent> = _analyticsEvents.asSharedFlow()

    // The "Found X region" announcement (auto-select only): a one-shot event the screen turns into a
    // snackbar, rather than retained state with a manual clear.
    private val _regionFound = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val regionFound: SharedFlow<String> = _regionFound.asSharedFlow()

    // The region whose fare-payment warning dialog is showing (PAY_FARE), or null when none — dialog UI
    // state the host's PaymentWarningDialog observes; set by [showPaymentWarning], cleared by [dismissPaymentWarning].
    private val _paymentWarning = MutableStateFlow<ObaRegion?>(null)
    val paymentWarning: StateFlow<ObaRegion?> = _paymentWarning.asStateFlow()

    // One-shot welcome-tutorial request (the TUTORIAL_WELCOME launch extra, the help "Show tutorials"
    // action, or the what's-new opt-out's "yes"); HomeScreen starts the Compose welcome + map-stop
    // spotlight sequence off this latch once composed.
    private val _showWelcomeTutorial = MutableStateFlow(false)
    val showWelcomeTutorial: StateFlow<Boolean> = _showWelcomeTutorial.asStateFlow()

    // The sheet's last resting position, reported up from the screen; drives the map padding/recenter
    // side-effects + the tutorial gate. Pure coordination state (no Compose reads it), so it's a plain
    // property rather than a HomeUiState field — see [lastSettledSheet].
    private var settledSheet: ArrivalsSheetState = ArrivalsSheetState.Hidden

    /** The sheet's last resting position, for the activity's imperative map/tutorial side-effects. */
    val lastSettledSheet: ArrivalsSheetState get() = settledSheet
    // A restored/deep-linked focus the imperative map hasn't been told about yet (re-derived by the
    // host on each create from the restored focusedStop, so it needn't be persisted).
    private var pendingMapFocus: Boolean = false

    /** A map stop gained focus (non-null) or focus was cleared (null). Persists across process death. */
    fun onStopFocused(stop: FocusedStop?) {
        savedState[KEY_STOP_ID] = stop?.id
        savedState[KEY_STOP_NAME] = stop?.name
        savedState[KEY_STOP_CODE] = stop?.code
        savedState[KEY_STOP_LAT] = stop?.lat
        savedState[KEY_STOP_LON] = stop?.lon
        if (stop != null) {
            // Focusing a stop clears any bike-station focus (mirrors the legacy onFocusChanged).
            savedState[KEY_BIKE_STATION] = null
        }
        _uiState.update {
            it.copy(
                focusedStop = stop,
                focusedBikeStationId = if (stop != null) null else it.focusedBikeStationId,
            )
        }
    }

    /**
     * The target for a "send feedback / report a problem" launch: the focused stop if one is focused,
     * else the last-known device location, else nothing. Deciding the variant is VM logic; the host just
     * opens `ReportActivity` for whichever [ReportTarget] it gets.
     */
    fun reportTarget(): ReportTarget {
        _uiState.value.focusedStop?.let { return ReportTarget.Stop(it) }
        return locationRepository.lastKnownLocation()
            ?.let { ReportTarget.Location(it.latitude, it.longitude) }
            ?: ReportTarget.Generic
    }

    /** PAY_FARE needs a fare-payment warning shown before launching: record the [region] the dialog
     *  should warn about (the host's PaymentWarningDialog observes [paymentWarning]). */
    fun showPaymentWarning(region: ObaRegion) {
        _paymentWarning.value = region
    }

    /** The fare-payment warning dialog was confirmed or dismissed; clear the dialog state. */
    fun dismissPaymentWarning() {
        _paymentWarning.value = null
    }

    /** Stage the welcome tutorial for the host to show once composed (the launching intent requested it). */
    fun requestWelcomeTutorial() {
        _showWelcomeTutorial.value = true
    }

    /** HomeScreen started the welcome sequence; clear the latch so it isn't re-started. */
    fun onWelcomeTutorialConsumed() {
        _showWelcomeTutorial.value = false
    }

    fun onBikeStationFocused(id: String?) {
        savedState[KEY_BIKE_STATION] = id
        _uiState.update { it.copy(focusedBikeStationId = id) }
    }

    /**
     * The arrivals sheet settled at [state] (reported from the screen's live SheetState). Tracks the
     * resting position and drives the map's bottom padding + (on Expanded) a recenter on the focused
     * stop, via [mapBottomPadding]/[mapDirectives]. The initial reveal (from Hidden) is skipped,
     * matching the legacy behavior.
     */
    fun onSheetSettled(state: ArrivalsSheetState, peekPx: Int) {
        val previous = settledSheet
        settledSheet = state
        // The FAB lift is computed locally in HomeScreen now; this method only drives the map's bottom
        // padding + the on-expand recenter. The initial reveal (from Hidden) is skipped.
        if (previous == ArrivalsSheetState.Hidden) {
            return
        }
        when (state) {
            ArrivalsSheetState.Expanded -> {
                _mapBottomPadding.value = peekPx
                _uiState.value.focusedStop?.let {
                    emitMapDirective(MapDirective.RecenterOnFocusedStop(it.lat, it.lon))
                }
            }
            ArrivalsSheetState.Collapsed -> _mapBottomPadding.value = peekPx
            ArrivalsSheetState.Hidden -> _mapBottomPadding.value = 0
        }
    }

    /** Chevron tap — ask the screen to toggle the sheet (it holds the live SheetState). */
    fun requestToggleSheet() = emit(SheetCommand.ToggleSheet)

    /**
     * The host has a restored / deep-linked focus the imperative map hasn't been told about yet;
     * complete it once the arrivals load (see [onArrivalsLoaded]). A fresh map tap already centers the
     * stop, so it does not call this.
     */
    fun markPendingMapFocus() {
        pendingMapFocus = true
    }

    /**
     * Establishes the map's initial focus on create. A restored focus (SavedStateHandle) is kept as-is;
     * otherwise the deep-linked [intentFocus] (if any) is adopted. Either way, if there's now a focus it
     * is marked pending so the map recenters + adds the marker once arrivals load. No focus → nothing.
     */
    fun applyInitialFocus(intentFocus: FocusedStop?) {
        if (_uiState.value.focusedStop == null) {
            intentFocus?.let { onStopFocused(it) }
        }
        if (_uiState.value.focusedStop != null) {
            markPendingMapFocus()
        }
    }

    /**
     * Arrivals loaded for the focused [stop]. If a restore/deep-link focus is pending, consume the latch
     * and tell the map to focus it (recenter + add the marker) via [mapDirectives] — so the map reacts
     * to a directive rather than the activity relaying one VM's decision into another's method. A fresh
     * map tap already centered the stop and set no pending focus, so this is then a no-op.
     */
    fun onArrivalsLoaded(stop: ObaStop, routes: List<ObaRoute>?) {
        if (!pendingMapFocus) {
            return
        }
        pendingMapFocus = false
        emitMapDirective(MapDirective.FocusStop(stop, routes, settledSheet == ArrivalsSheetState.Expanded))
    }

    /** "Show vehicles on map" — collapse the sheet (screen), then switch the map to route mode. */
    fun requestShowRouteOnMap(routeId: String) {
        emit(SheetCommand.CollapseSheet)
        emitMapDirective(MapDirective.ShowRoute(routeId))
    }

    /**
     * Back-press from a peeking sheet — clear the focus. The VM owns the focused stop, so clearing it
     * here hides the sheet; the ClearFocus directive clears the map's render focus. (The old path
     * only told the map, relying on a focus-listener round-trip the host's setFocusStop(null) doesn't
     * make — so the sheet never hid; clearing the VM state directly is both correct and the
     * declarative source of truth.)
     */
    fun requestClearMapFocus() {
        onStopFocused(null)
        emitMapDirective(MapDirective.ClearFocus)
    }

    /** Report a nav-drawer / help-menu selection to analytics (by its label res); fired by the host's
     *  single HomeAnalyticsEffect so the imperative ObaAnalytics call doesn't live in the activity. */
    fun reportMenuAnalytics(@StringRes labelRes: Int) {
        _analyticsEvents.tryEmit(HomeAnalyticsEvent.MenuItem(labelRes))
    }

    // tryEmit (not a launched emit): the buffer (capacity 4, 0 replay) has room for these low-frequency
    // one-shot commands, matching the codebase effect-flow idiom (MapViewModel etc.).
    private fun emit(command: SheetCommand) {
        _sheetCommands.tryEmit(command)
    }

    // The outbound-map-directive counterpart of [emit] (buffered, replay-less; see [mapDirectives]).
    private fun emitMapDirective(directive: MapDirective) {
        _mapDirectives.tryEmit(directive)
    }

    /**
     * Resolves the current region at startup (replaces HomeActivity.checkRegionStatus + ObaRegionsTask) and
     * announces an auto-selected change via the one-shot [regionFound] event + analytics. The repository
     * performs the writes on Dispatchers.IO; the map re-zoom and region-derived state are driven reactively
     * by their own region collectors, and the forced-choice picker is driven reactively off the repository
     * state ([org.onebusaway.android.ui.home.RegionPickerViewModel]) — so only the auto-select announcement
     * remains here.
     */
    fun refreshRegions() {
        viewModelScope.launch {
            val status = regionRepo.refresh()
            // A manual-pick / NeedsManualSelection outcome announces nothing (matching the legacy behavior);
            // only an auto-select change raises the "Found X region" snackbar + analytics.
            if (status is RegionStatus.Changed) {
                _regionFound.tryEmit(status.region.name)
                _analyticsEvents.tryEmit(HomeAnalyticsEvent.RegionSelected(status.region.name))
            }
        }
    }

    /**
     * Home was created. On the very first launch ever we defer the region check until the map's
     * location-permission result (so an auto-select has a location to work with); otherwise — or once
     * permission is already granted — check now. [hasLocationPermission] is read by the activity
     * (it needs a Context); the decision lives here.
     */
    fun onHomeStarted(hasLocationPermission: Boolean) {
        if (startupRepo.isInitialStartup() && !hasLocationPermission) {
            return
        }
        refreshRegions()
    }

    /**
     * The map host reported the first-launch location-permission result (granted or denied). Complete
     * the deferred first launch: mark it done and check the region (a denial leads to the manual picker).
     */
    fun onLocationPermissionResult() {
        if (startupRepo.isInitialStartup()) {
            startupRepo.clearInitialStartup()
            refreshRegions()
        }
    }

    private companion object {
        const val KEY_STOP_ID = "home.focusedStop.id"
        const val KEY_STOP_NAME = "home.focusedStop.name"
        const val KEY_STOP_CODE = "home.focusedStop.code"
        const val KEY_STOP_LAT = "home.focusedStop.lat"
        const val KEY_STOP_LON = "home.focusedStop.lon"
        const val KEY_BIKE_STATION = "home.focusedBikeStation.id"

        fun readFocusedStop(s: SavedStateHandle): FocusedStop? {
            val id = s.get<String>(KEY_STOP_ID) ?: return null
            return FocusedStop(
                id = id,
                name = s[KEY_STOP_NAME],
                code = s[KEY_STOP_CODE],
                lat = s.get<Double>(KEY_STOP_LAT) ?: 0.0,
                lon = s.get<Double>(KEY_STOP_LON) ?: 0.0,
            )
        }
    }
}

/**
 * The home screen's genuine coordination state: the focused stop/bike-station (the only retained domain
 * state, persisted via [SavedStateHandle]). Deliberately small — everything else the home screen renders is
 * owned by a feature VM, a HomeScreen-local `remember`, or a one-shot event (see [HomeViewModel]'s KDoc for
 * what moved).
 */
data class HomeUiState(
    // Map focus — survives config change + process death via SavedStateHandle.
    val focusedStop: FocusedStop? = null,
    val focusedBikeStationId: String? = null,
)

/**
 * A telemetry event the ViewModel emits ([HomeViewModel.analyticsEvents]) for the host's single
 * [HomeAnalyticsEffect] to report — keeping the imperative `ObaAnalytics` calls out of the activity
 * (mirroring `AccessibilityAnalyticsEffect`), since dispatch needs a `Context` but the decision doesn't.
 */
sealed interface HomeAnalyticsEvent {
    /** An auto-selected region change (a manual pick logs none, matching the legacy behavior). */
    data class RegionSelected(val regionName: String) : HomeAnalyticsEvent

    /** A nav-drawer / help-menu selection identified by its analytics label string resource. */
    data class MenuItem(@StringRes val labelRes: Int) : HomeAnalyticsEvent
}

/**
 * One-shot sheet commands driven from the ViewModel, consumed by [HomeScreen] (which alone holds the
 * live `SheetState`) off its own [HomeViewModel.sheetCommands] flow. (The drawer is opened directly by
 * [org.onebusaway.android.ui.home.chrome.HomeTopBar]'s hamburger, so it needs no command.)
 */
sealed interface SheetCommand {
    /** The arrivals-sheet chevron was tapped — toggle peek <-> full. */
    object ToggleSheet : SheetCommand

    /** Collapse the sheet to its peek (e.g. after "show vehicles on map"). */
    object CollapseSheet : SheetCommand
}

/**
 * One-shot outbound Home→Map interactions emitted on [HomeViewModel.mapDirectives] and bridged to the
 * [org.onebusaway.android.map.MapViewModel] by [org.onebusaway.android.ui.home.map.MapFeature] (the
 * composable that holds both view models). Keeping these on [HomeViewModel] — rather than a shared
 * command bus — means neither view model references the other: Home only emits, the map only exposes
 * public methods, and the neutral composable wires them. The map's *bottom padding* is plain
 * last-wins state ([HomeViewModel.mapBottomPadding]), not a directive.
 */
sealed interface MapDirective {
    /** Animate the camera to recenter on the currently focused stop (sheet expanded). */
    data class RecenterOnFocusedStop(val lat: Double, val lon: Double) : MapDirective

    /** Enter route mode for the given route (the "show vehicles on map" action). */
    data class ShowRoute(val routeId: String) : MapDirective

    /** Clear the map's render focus (back-press from a peeking arrivals sheet). */
    object ClearFocus : MapDirective

    /**
     * Focus a restored / deep-linked stop once its arrivals load: ensure it's on the map, render-focus
     * it, and recenter (route-header bias only when [overlayExpanded] in route mode). A fresh map tap
     * already centers the stop, so it doesn't issue this.
     */
    data class FocusStop(
        val stop: ObaStop,
        val routes: List<ObaRoute>?,
        val overlayExpanded: Boolean,
    ) : MapDirective
}
