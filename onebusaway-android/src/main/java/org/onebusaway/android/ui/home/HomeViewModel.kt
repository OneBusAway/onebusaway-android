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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.toGeoPoint
import org.onebusaway.android.map.render.MapViewport
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionStatus

/** How a tapped stop changes the map presentation already visible beneath the arrivals drawer. */
internal enum class StopFocusTransition {
    Unchanged,
    ContinuePresentation,
    ReplacePresentation,
}

/**
 * Owns the home screen's genuine coordination state — the focused stop/bike-station (persisted via
 * [SavedStateHandle]) — as a single [CurrentFocus], replaced through one focus transition, and drives
 * the startup region-resolve action through [viewModelScope]. The
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

    // The single source of truth, replaced atomically by replaceFocus(). Seeded from the
    // SavedStateHandle-restored focus so a recreation reflects the restore by construction.
    private val _currentFocus = MutableStateFlow(CurrentFocusPersistence.read(savedState))
    val currentFocus: StateFlow<CurrentFocus> = _currentFocus.asStateFlow()

    // Semantic map actions live on HOME and keep a local undo history of their preceding focus and,
    // when the action moved the camera, viewport. Only current focus is persisted, so reconstruct its
    // natural predecessors without viewport snapshots after process recreation.
    private data class MapUndoEntry(
        val focus: CurrentFocus,
        val viewport: MapViewport? = null,
    )

    private val mapUndoHistory = ArrayDeque<MapUndoEntry>().apply {
        when (val restored = _currentFocus.value) {
            CurrentFocus.None -> Unit
            is CurrentFocus.Stop -> {
                addLast(MapUndoEntry(CurrentFocus.None))
                if (restored.selectedRoute != null) {
                    addLast(MapUndoEntry(CurrentFocus.Stop(restored.stop)))
                }
            }
            is CurrentFocus.Route, is CurrentFocus.BikeStation, CurrentFocus.Directions ->
                addLast(MapUndoEntry(CurrentFocus.None))
        }
    }
    private val _canUndoMapAction = MutableStateFlow(mapUndoHistory.isNotEmpty())
    val canUndoMapAction: StateFlow<Boolean> = _canUndoMapAction.asStateFlow()

    // The map's bottom inset (driven by the arrivals sheet) — idempotent last-wins state, applied by
    // MapFeature. A StateFlow (not an event) so a re-entering map re-reads the latest value.
    private val _mapBottomPadding = MutableStateFlow(0)
    val mapBottomPadding: StateFlow<Int> = _mapBottomPadding.asStateFlow()

    // One-shot outbound map interactions (recenter / show route / adjacency / focus) that can't be
    // modeled as state. MapFeature collects these and calls the map view model — so this VM needs no
    // reference to the map's VM (the seam the old MapInteractionBus filled). A Channel, not a
    // replay-0 SharedFlow: MapFeature's collector lives in HOME's composition, so a directive emitted
    // before it (re)subscribes — e.g. a route reveal consumed right after popping back from the search
    // destination — must queue for the single consumer instead of vanishing. UNLIMITED (not a fixed
    // capacity): the whole point is to never drop a queued directive, and trySend on a bounded channel
    // fails silently once it fills — reviving the very drop bug this Channel replaced. Directives are
    // tiny and low-frequency, so an unbounded buffer costs nothing in practice.
    private val _mapDirectives = Channel<MapDirective>(capacity = Channel.UNLIMITED)
    val mapDirectives: Flow<MapDirective> = _mapDirectives.receiveAsFlow()

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
    private val _paymentWarning = MutableStateFlow<Region?>(null)
    val paymentWarning: StateFlow<Region?> = _paymentWarning.asStateFlow()

    // One-shot welcome-tutorial request (the TUTORIAL_WELCOME launch extra, the help "Show tutorials"
    // action, or the what's-new opt-out's "yes"); HomeScreen starts the Compose welcome + map-stop
    // spotlight sequence off this latch once composed.
    private val _showWelcomeTutorial = MutableStateFlow(false)
    val showWelcomeTutorial: StateFlow<Boolean> = _showWelcomeTutorial.asStateFlow()

    // The sheet's last resting position, reported up from the screen; drives the map padding/recenter
    // side-effects + the tutorial gate. Pure coordination state (no Compose reads it), so it's a plain
    // property rather than focus state — see [lastSettledSheet].
    private var settledSheet: ArrivalsSheetState = ArrivalsSheetState.Hidden

    /** The sheet's last resting position, for the activity's imperative map/tutorial side-effects. */
    val lastSettledSheet: ArrivalsSheetState get() = settledSheet
    // A restored/deep-linked focus the imperative map hasn't been told about yet (re-derived by the
    // host on each create from the restored focusedStop, so it needn't be persisted). Null when no
    // focus is pending. A single data class rather than a boolean pair (#1903): each mark atomically
    // replaces the whole latch, so a fresh reveal can no longer inherit a stale preserveViewport left
    // over from an earlier, still-loading restore.
    private data class PendingFocus(
        val preserveViewport: Boolean = false,
        // Whether the pending focus should animate the camera over to the stop (an in-session reveal —
        // a search/recents tap) rather than jump to it (a cold-start restore, where flying from a
        // default camera position would look wrong).
        val animate: Boolean = false,
    )
    private var pendingFocus: PendingFocus? = null

    // The last arrivals load's exact trips, paired with the stop they were loaded for. Set only by
    // [onArrivalsLoaded]; never cleared imperatively. [focusedTrips] scopes it to the *current* focus,
    // so any focus transition empties the exposed set by construction — no per-transition reset to
    // forget (the "forgot to clear on transition X" bug class this replaces).
    private var loadedTrips: LoadedTrips? = null

    /**
     * Exact displayed trips for the *currently focused* stop — the loaded set when it belongs to the
     * focused stop, else empty. Derived from [currentFocus] rather than mirrored into a manually-reset
     * field, so it can't outlive its stop: focusing another stop, a route, or nothing reads as empty
     * until that stop's own arrivals load.
     */
    val focusedTrips: Set<FocusedTrip>
        get() {
            val stopId = (_currentFocus.value as? CurrentFocus.Stop)?.stop?.id ?: return emptySet()
            val loaded = loadedTrips ?: return emptySet()
            return if (loaded.stopId == stopId) loaded.trips else emptySet()
        }

    // The route-directions currently drawn for the focused-stop presentation. Unlike [focusedTrips]
    // this deliberately *persists* across a continuing stop-to-stop handoff (the old routes stay on the
    // map until the new stop's arrivals replace them), so it stays an explicitly-managed field.
    private var presentedRoutes: Set<RouteDirectionKey> = emptySet()

    /** An arrivals load's exact trips, tagged with the stop id they were loaded for. */
    private data class LoadedTrips(val stopId: String, val trips: Set<FocusedTrip>)

    /**
     * A map stop gained focus. When it shares any presented route-direction with the current stop,
     * keep that presentation alive while the new arrivals replace it, including any selected route.
     */
    internal fun onStopFocused(
        stop: FocusedStop,
        continuingRoutes: Set<RouteDirectionKey> = emptySet(),
    ): StopFocusTransition {
        val previousId = _currentFocus.value.focusedStop?.id
        val sameStop = previousId == stop.id
        if (sameStop) return StopFocusTransition.Unchanged
        val current = _currentFocus.value as? CurrentFocus.Stop
        val continuePresentation = when (val selected = current?.selectedRoute) {
            null -> current != null && presentedRoutes.any(continuingRoutes::contains)
            else -> selected.currentLeg.routeDirection in continuingRoutes
        }
        if (!continuePresentation) {
            presentedRoutes = emptySet()
            emitMapDirective(MapDirective.ClearStopRoutes)
        }
        pushFocus(
            CurrentFocus.Stop(
                stop = stop,
                selectedRoute = if (continuePresentation) current?.selectedRoute else null,
            )
        )
        return if (continuePresentation) {
            StopFocusTransition.ContinuePresentation
        } else {
            StopFocusTransition.ReplacePresentation
        }
    }

    /**
     * A stop opened from navigation replaces any standalone route/bike view before it restores. An
     * in-session reveal (a search/recents tap while the map is already on screen) passes [animate] so
     * the camera pans over to the stop instead of jumping; a cold-start restore leaves it false.
     */
    fun revealStop(stop: FocusedStop, animate: Boolean = false) {
        emitMapDirective(MapDirective.ClearFocus)
        onStopFocused(stop)
        markPendingMapFocus(animate)
    }

    /**
     * The target for a "send feedback / report a problem" launch: the focused stop if one is focused,
     * else the last-known device location, else nothing. Deciding the variant is VM logic; the host just
     * opens `ReportActivity` for whichever [ReportTarget] it gets.
     */
    fun reportTarget(): ReportTarget {
        _currentFocus.value.focusedStop?.let { return ReportTarget.Stop(it) }
        return locationRepository.lastKnownLocation()
            ?.let { ReportTarget.Location(it.toGeoPoint()) }
            ?: ReportTarget.Generic
    }

    /** PAY_FARE needs a fare-payment warning shown before launching: record the [region] the dialog
     *  should warn about (the host's PaymentWarningDialog observes [paymentWarning]). */
    fun showPaymentWarning(region: Region) {
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

    fun onBikeStationFocused(id: String) {
        pushFocus(CurrentFocus.BikeStation(id))
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
        // The FAB lift is computed locally in HomeScreen now; this method drives the map's bottom padding
        // + the on-expand recenter. The map inset must track the sheet's resting height on *every* settle,
        // including the initial reveal (from Hidden) — otherwise an ETA tap right after focusing a stop
        // (the sheet's first appearance) frames the vehicle+stop against a stale 0 inset and lands them
        // under the drawer. Only the on-expand recenter is skipped on that initial reveal (matching the
        // legacy behavior: don't yank the camera onto the focused stop just because the sheet appeared).
        _mapBottomPadding.value = when (state) {
            ArrivalsSheetState.Expanded, ArrivalsSheetState.Collapsed -> peekPx
            ArrivalsSheetState.Hidden -> 0
        }
        if (state == ArrivalsSheetState.Expanded && previous != ArrivalsSheetState.Hidden) {
            recenterOnFocusedStop()
        }
    }

    /**
     * The host has a restored / deep-linked focus the imperative map hasn't been told about yet;
     * complete it once the arrivals load (see [onArrivalsLoaded]). A fresh map tap already centers the
     * stop, so it does not call this. [preserveViewport] keeps the current camera instead of recentering
     * on completion (a back-restore returning to a stop with no saved viewport of its own).
     */
    fun markPendingMapFocus(animate: Boolean = false, preserveViewport: Boolean = false) {
        pendingFocus = PendingFocus(preserveViewport = preserveViewport, animate = animate)
    }

    /**
     * Establishes the map's initial focus on create. A restored focus (SavedStateHandle) is kept as-is;
     * otherwise the deep-linked [intentFocus] (if any) is adopted. Either way, if there's now a focus it
     * is marked pending so the map recenters + adds the marker once arrivals load. No focus → nothing.
     */
    fun applyInitialFocus(intentFocus: FocusedStop?) {
        if (_currentFocus.value.focusedStop == null) {
            intentFocus?.let(::revealStop)
        } else {
            markPendingMapFocus()
        }
    }

    /**
     * Arrivals loaded for the focused [stop]. Records the drawer's exact [trips] on every load. Then,
     * if a restore/deep-link focus is pending, consume the latch and tell
     * the map to focus it (recenter + add the marker) via [mapDirectives]. Then activates adjacency for
     * the loaded route set; a fresh map tap already established the render focus, while a restore emits
     * [MapDirective.FocusStop] first so the map can validate the route-view request against that focus.
     */
    fun onArrivalsLoaded(
        stop: ObaStop,
        routes: List<ObaRoute>?,
        trips: Set<FocusedTrip> = emptySet(),
    ) {
        val focus = _currentFocus.value as? CurrentFocus.Stop ?: return
        if (focus.stop.id != stop.id) return
        loadedTrips = LoadedTrips(focus.stop.id, trips)
        presentedRoutes = trips.mapTo(linkedSetOf(), FocusedTrip::routeDirection)
        val pending = pendingFocus
        val preserveViewport = pending?.preserveViewport == true
        // Frame the selected route only when this load is the initial (restore/deep-link) focus
        // establishment; an ordinary arrivals poll must refresh the presentation without reframing the
        // camera to the whole route (#1895). A restore carrying a saved viewport already declines to frame.
        val frameSelectedRoute = pending != null && !preserveViewport
        if (pending != null) {
            pendingFocus = null
            emitMapDirective(
                MapDirective.FocusStop(
                    stop,
                    routes,
                    settledSheet == ArrivalsSheetState.Expanded,
                    recenter = !preserveViewport,
                    animate = pending.animate,
                )
            )
        }
        // FocusStop must be dispatched first on restore so the map can validate this stop as the
        // current rendered focus before starting its adjacency session.
        emitMapDirective(
            MapDirective.ShowStopRoutes(
                stopId = stop.id,
                routes = routes.orEmpty(),
                trips = focusedTrips,
            )
        )
        focus.selectedRoute?.let {
            emitMapDirective(
                MapDirective.ShowRoute(
                    it.target(focus.stop.id).toRequest(),
                    stopScoped = true,
                    frameRoute = frameSelectedRoute,
                )
            )
        }
    }

    /** Animate the map's camera back onto the focused stop, if one is focused (else a no-op). */
    fun recenterOnFocusedStop(undoViewport: MapViewport? = null) {
        _currentFocus.value.focusedStop?.let {
            recordViewportUndo(undoViewport)
            emitMapDirective(MapDirective.RecenterOnFocusedStop(GeoPoint(it.lat, it.lon)))
        }
    }

    /** Select a drawer route and dispatch its map command as one atomic focus transition. */
    fun selectArrivalRoute(
        request: ShowRouteRequest,
        shortName: String,
        headsign: String?,
        undoViewport: MapViewport? = null,
    ) {
        val stopFocus = _currentFocus.value as? CurrentFocus.Stop ?: return
        selectStopRoute(
            stopFocus = stopFocus,
            request = request,
            firstLeg = RouteLeg(request.routeId, shortName, request.initialDirectionId),
            originHeadsign = headsign,
            undoViewport = undoViewport,
        )
    }

    /** Enter route focus from a route-oriented surface (search, recents, deep link, route info). */
    fun focusStandaloneRoute(request: ShowRouteRequest, undoViewport: MapViewport? = null) {
        pushFocus(CurrentFocus.Route(request.toRouteTarget()), undoViewport)
        emitMapDirective(MapDirective.ShowRoute(request, stopScoped = false))
    }

    /** Persist a direction chosen from the standalone route banner. */
    fun selectStandaloneRouteDirection(directionId: Int) {
        val focus = _currentFocus.value as? CurrentFocus.Route ?: return
        replaceFocus(CurrentFocus.Route(focus.target.copy(directionId = directionId)))
    }

    /** Follow a block continuation while preserving the active focus kind. */
    fun advanceRouteContinuation(
        routeId: String,
        shortName: String,
        directionId: Int?,
        undoViewport: MapViewport? = null,
    ) {
        when (val focus = _currentFocus.value) {
            is CurrentFocus.Stop -> {
                val selected = focus.selectedRoute ?: return
                val next = selected.continueTo(RouteLeg(routeId, shortName, directionId))
                pushFocus(focus.copy(selectedRoute = next), undoViewport)
                emitMapDirective(
                    MapDirective.ShowRoute(next.target(focus.stop.id).toRequest(), stopScoped = true)
                )
            }
            is CurrentFocus.Route -> {
                val target = RouteTarget(routeId, directionId = directionId)
                pushFocus(CurrentFocus.Route(target), undoViewport)
                emitMapDirective(MapDirective.ShowRoute(target.toRequest(), stopScoped = false))
            }
            else -> Unit
        }
    }

    /** Clear only the route selected inside the current stop. */
    fun clearStopRouteSelection() {
        val stopFocus = _currentFocus.value as? CurrentFocus.Stop ?: return
        if (stopFocus.selectedRoute == null) return
        unfocusMapOneLevel()
    }

    /**
     * A focused-stop route badge behaves like its arrivals-drawer row: carry the focused stop as the
     * direction anchor so route mode preserves adjacency focus underneath it. The badge additionally
     * supplies the direction of the line it labels, which wins over stop-based direction resolution.
     */
    fun requestShowFocusedStopRouteOnMap(
        routeId: String,
        directionId: Int?,
        shortName: String = routeId,
        undoViewport: MapViewport? = null,
    ) {
        val stopFocus = _currentFocus.value as? CurrentFocus.Stop ?: return
        selectStopRoute(
            stopFocus = stopFocus,
            request = ShowRouteRequest(
                routeId = routeId,
                directionStopId = stopFocus.stop.id,
                initialDirectionId = directionId,
            ),
            firstLeg = RouteLeg(routeId, shortName, directionId),
            originHeadsign = null,
            undoViewport = undoViewport,
        )
    }

    private fun selectStopRoute(
        stopFocus: CurrentFocus.Stop,
        request: ShowRouteRequest,
        firstLeg: RouteLeg,
        originHeadsign: String?,
        undoViewport: MapViewport?,
    ) {
        val next = stopFocus.copy(
            selectedRoute = StopRouteSelection(
                originHeadsign = originHeadsign,
                legs = listOf(firstLeg),
            )
        )
        pushFocus(next, undoViewport)
        emitMapDirective(
            MapDirective.ShowRoute(
                request.copy(directionStopId = stopFocus.stop.id),
                stopScoped = true,
            )
        )
    }

    /**
     * A background-map tap drops one attention layer: route-over-stop becomes the plain stop; any
     * plain stop, standalone route, or bike focus returns to the unfocused root.
     */
    fun unfocusMapOneLevel() {
        val target = when (val focus = _currentFocus.value) {
            is CurrentFocus.Stop -> if (focus.selectedRoute == null) {
                CurrentFocus.None
            } else {
                CurrentFocus.Stop(focus.stop)
            }
            is CurrentFocus.Route, is CurrentFocus.BikeStation, CurrentFocus.Directions ->
                CurrentFocus.None
            CurrentFocus.None -> return
        }
        pushFocus(target)
        if (target is CurrentFocus.Stop) {
            emitMapDirective(MapDirective.ClearSelectedRoute)
        } else {
            presentedRoutes = emptySet()
            emitMapDirective(MapDirective.ClearFocus)
        }
    }

    /** Clears the complete focus hierarchy. Used by the focus banner's explicit close control. */
    fun clearMapFocus() {
        if (_currentFocus.value == CurrentFocus.None) return
        pushFocus(CurrentFocus.None)
        presentedRoutes = emptySet()
        // Drop any pending restore/deep-link latch too; otherwise a stop closed before its arrivals
        // load leaves it set, and the next stop's onArrivalsLoaded would consume it and recenter.
        pendingFocus = null
        emitMapDirective(MapDirective.ClearFocus)
    }

    /**
     * Enter trip-plan directions focus. The chrome swaps to the trip-plan form; the map draws whatever
     * itinerary the results VM selects (via [showItineraryOnMap]). A no-op if already in directions.
     */
    fun enterDirections(undoViewport: MapViewport? = null) {
        if (_currentFocus.value == CurrentFocus.Directions) return
        presentedRoutes = emptySet()
        pendingFocus = null
        pushFocus(CurrentFocus.Directions, undoViewport)
    }

    /** Draw [itinerary] on the home map (only meaningful while in [CurrentFocus.Directions]). */
    fun showItineraryOnMap(itinerary: TripItinerary) =
        emitMapDirective(MapDirective.ShowItinerary(itinerary))

    /** Clear the drawn itinerary while staying in directions (the plan became unsubmittable). */
    fun clearShownItineraryOnMap() = emitMapDirective(MapDirective.ClearItinerary)

    /** Show the resolved From/To endpoints as green/red pins (before a plan); a null endpoint drops it. */
    fun setDirectionsEndpointsOnMap(from: GeoPoint?, to: GeoPoint?) =
        emitMapDirective(MapDirective.SetDirectionsEndpoints(from, to))

    /** Leave directions focus, returning the map to nearby stops. */
    fun exitDirections() = clearMapFocus()

    /** Reframe the focused route as one undoable camera action. */
    fun reframeFocusedRoute(undoViewport: MapViewport?) {
        if (_currentFocus.value !is CurrentFocus.Route) return
        recordViewportUndo(undoViewport)
        emitMapDirective(MapDirective.FrameRoute)
    }

    /** Record a semantic camera-only action without treating manual pan/zoom as navigation history. */
    private fun recordViewportUndo(viewport: MapViewport?) {
        viewport ?: return
        mapUndoHistory.addLast(MapUndoEntry(_currentFocus.value, viewport))
        _canUndoMapAction.value = true
    }

    /** Undo the most recent semantic map action, returning false when no history remains. */
    fun navigateBackFocus(): Boolean {
        val from = _currentFocus.value
        if (mapUndoHistory.isEmpty()) return false
        val entry = mapUndoHistory.removeLast()
        _canUndoMapAction.value = mapUndoHistory.isNotEmpty()
        replaceFocus(entry.focus)
        restoreMapAfterBack(from, entry)
        return true
    }

    /** Report a nav-drawer / help-menu selection to analytics (by its label res); fired by the host's
     *  single HomeAnalyticsEffect so the imperative ObaAnalytics call doesn't live in the activity. */
    fun reportMenuAnalytics(@StringRes labelRes: Int) {
        _analyticsEvents.tryEmit(HomeAnalyticsEvent.MenuItem(labelRes))
    }

    // trySend keeps these low-frequency one-shot directives synchronous with their semantic action.
    // On an UNLIMITED channel it can't fail on a full buffer (the drop bug that motivated #1904); it
    // only returns failure once the channel is closed, which this VM never does.
    private fun emitMapDirective(directive: MapDirective) {
        _mapDirectives.trySend(directive)
    }

    private fun pushFocus(focus: CurrentFocus, undoViewport: MapViewport? = null) {
        if (focus == _currentFocus.value && undoViewport == null) return
        mapUndoHistory.addLast(MapUndoEntry(_currentFocus.value, undoViewport))
        _canUndoMapAction.value = true
        if (focus != _currentFocus.value) replaceFocus(focus)
    }

    private fun replaceFocus(focus: CurrentFocus) {
        _currentFocus.value = focus
        CurrentFocusPersistence.write(savedState, focus)
    }

    private fun restoreMapAfterBack(from: CurrentFocus, entry: MapUndoEntry) {
        val target = entry.focus
        val frameFocus = entry.viewport == null
        if (from == target) {
            entry.viewport?.let { emitMapDirective(MapDirective.RestoreViewport(it)) }
            return
        }
        val returnsToSameStop = from is CurrentFocus.Stop && target is CurrentFocus.Stop &&
            from.stop.id == target.stop.id
        if (returnsToSameStop) {
            val selected = target.selectedRoute
            emitMapDirective(
                if (selected == null) MapDirective.ClearSelectedRoute
                else MapDirective.ShowRoute(
                    selected.target(target.stop.id).toRequest(),
                    stopScoped = true,
                    frameRoute = frameFocus,
                )
            )
        } else {
            when (target) {
                is CurrentFocus.Stop -> {
                    markPendingMapFocus(preserveViewport = !frameFocus)
                    emitMapDirective(MapDirective.ClearFocus)
                }
                is CurrentFocus.Route -> {
                    emitMapDirective(
                        MapDirective.ShowRoute(
                            target.target.toRequest(),
                            stopScoped = false,
                            frameRoute = frameFocus,
                        )
                    )
                }
                // Directions redraws its itinerary from the results sheet's own reconcile when it
                // remounts, so restoring the focus just clears the map here (like None/BikeStation).
                CurrentFocus.None, is CurrentFocus.BikeStation, CurrentFocus.Directions ->
                    emitMapDirective(MapDirective.ClearFocus)
            }
        }
        entry.viewport?.let { emitMapDirective(MapDirective.RestoreViewport(it)) }
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

}

/**
 * A telemetry event the ViewModel emits ([HomeViewModel.analyticsEvents]) for the host's single
 * [HomeAnalyticsEffect] to report — keeping the imperative `ObaAnalytics` calls out of the activity
 * (mirroring `AccessibilityAnalyticsEffect`), since dispatch needs a `Context` but the decision doesn't.
 */
sealed interface HomeAnalyticsEvent {
    /** An auto-selected region change (a manual pick logs none, matching the legacy behavior). */
    data class RegionSelected(val regionName: String) : HomeAnalyticsEvent

    /** A nav-drawer / help-menu selection identified by its analytics label string resource. */
    data class MenuItem(@param:StringRes val labelRes: Int) : HomeAnalyticsEvent
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
    data class RecenterOnFocusedStop(val point: GeoPoint) : MapDirective

    /** Enter route mode for [request]'s route (the "show vehicles on map" action). */
    data class ShowRoute(
        val request: ShowRouteRequest,
        val stopScoped: Boolean,
        val frameRoute: Boolean = true,
    ) : MapDirective

    /** Restore the camera paired with an undone semantic action. */
    data class RestoreViewport(val viewport: MapViewport) : MapDirective

    /** Reframe the already-focused route without changing focus. */
    data object FrameRoute : MapDirective

    /** Draw all upcoming routes for the currently focused stop, without reframing the camera. */
    data class ShowStopRoutes(
        val stopId: String,
        val routes: List<ObaRoute>,
        val trips: Set<FocusedTrip> = emptySet(),
    ) : MapDirective

    /** Clear an active focused-stop route view when the focused stop changes or is removed. */
    object ClearStopRoutes : MapDirective

    /** Leave route mode while retaining the focused stop's adjacency session. */
    data object ClearSelectedRoute : MapDirective

    /** Clear the map's render focus (back-press from a peeking arrivals sheet). */
    object ClearFocus : MapDirective

    /**
     * Focus a restored / deep-linked stop once its arrivals load: ensure it's on the map, render-focus
     * it, and optionally recenter (route-header bias only when [overlayExpanded] in route mode).
     * [animate] pans the camera over to the stop (an in-session reveal) instead of jumping (a restore).
     */
    data class FocusStop(
        val stop: ObaStop,
        val routes: List<ObaRoute>?,
        val overlayExpanded: Boolean,
        val recenter: Boolean = true,
        val animate: Boolean = false,
    ) : MapDirective

    /** Draw [itinerary]'s legs + start/end pins on the home map (trip-plan directions focus). */
    data class ShowItinerary(val itinerary: TripItinerary) : MapDirective

    /** Clear the drawn itinerary but stay in directions mode (the plan became unsubmittable). */
    data object ClearItinerary : MapDirective

    /** Show the resolved trip-plan From/To endpoints as pins before an itinerary (null drops a pin). */
    data class SetDirectionsEndpoints(val from: GeoPoint?, val to: GeoPoint?) : MapDirective
}
