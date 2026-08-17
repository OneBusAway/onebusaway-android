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
import org.onebusaway.android.map.ItineraryPins
import org.onebusaway.android.map.RiddenSpan
import org.onebusaway.android.map.RideRouteGroup
import org.onebusaway.android.map.RouteFocusRelationship
import org.onebusaway.android.map.RouteFocusSegment
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.map.render.MapViewport
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.ui.tripresults.FocusedLeg
import org.onebusaway.android.ui.tripresults.RouteLegRef
import org.onebusaway.android.ui.tripresults.RouteStopRef
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.toGeoPoint

/** How a tapped stop changes the map presentation already visible beneath the arrivals drawer. */
internal enum class StopFocusTransition {
    Unchanged,
    ContinuePresentation,
    ReplacePresentation,

    /**
     * The focus was refused: directions owns the map, and a stop cannot be selected there (#2097).
     * Callers must not mark the stop selected on the map either — the refusal is the whole point.
     */
    Refused
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
    private val locationRepository: LocationRepository
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
        val viewport: MapViewport? = null
    )

    private val mapUndoHistory = ArrayDeque(
        // Walk the restored focus down to the root ([peeledOneLevel] — the same ladder a background tap
        // descends), drop the focus itself, and push the ancestors outermost-first. Viewports aren't
        // persisted, so the reconstructed entries carry none.
        generateSequence(_currentFocus.value) { it.peeledOneLevel() }
            .drop(1)
            .map { MapUndoEntry(it) }
            .toList()
            .asReversed()
    )
    private val _canUndoMapAction = MutableStateFlow(mapUndoHistory.isNotEmpty())
    val canUndoMapAction: StateFlow<Boolean> = _canUndoMapAction.asStateFlow()

    // The map's bottom inset from the arrivals sheet — idempotent last-wins state, applied by MapFeature.
    // A StateFlow (not an event) so a re-entering map re-reads the latest value. The directions results
    // sheet reports its own inset ([directionsBottomInset]); MapRenderState combines the two by max.
    private val _mapBottomPadding = MutableStateFlow(0)
    val mapBottomPadding: StateFlow<Int> = _mapBottomPadding.asStateFlow()

    // The map's bottom inset from the directions results sheet — its own source (not shared with the
    // arrivals inset above), so neither has to assume the two never coexist.
    private val _directionsBottomInset = MutableStateFlow(0)
    val directionsBottomInset: StateFlow<Int> = _directionsBottomInset.asStateFlow()

    /** The directions results sheet's height as the map's bottom inset (0 when it isn't shown). */
    fun setDirectionsResultsInset(px: Int) {
        _directionsBottomInset.value = px
    }

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
        val animate: Boolean = false
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
        continuingRoutes: Set<RouteDirectionKey> = emptySet()
    ): StopFocusTransition {
        // Directions owns the map while a trip is being planned, so a stop tapped there is refused
        // rather than allowed to take over (#2097). Letting it through pushed stop focus on top of the
        // trip, and coming back left the marker still reading as selected underneath a form that knows
        // nothing about it — a state easier to make unreachable than to unwind.
        if (_currentFocus.value is CurrentFocus.Directions) return StopFocusTransition.Refused
        return focusStop(stop, continuingRoutes)
    }

    /**
     * Focus [stop] unconditionally — the shared body of [onStopFocused] and [revealStop], minus the
     * rule about who may ask. Asking for a stop from elsewhere in the app (starred, recents, a deep
     * link) is a deliberate move to it rather than a stray tap on a crowded map, so it is never
     * refused. It also has to reach the focus change *without* the current focus being rewritten
     * first: [pushFocus] records the focus it replaces, so stepping through None on the way would
     * drop the trip out of the back stack and leave Back on an empty map instead of the itinerary.
     */
    private fun focusStop(
        stop: FocusedStop,
        continuingRoutes: Set<RouteDirectionKey> = emptySet()
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
                selectedRoute = if (continuePresentation) current?.selectedRoute else null
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
        focusStop(stop)
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

    /**
     * A bike dock was tapped on the map. Directions owns the map while a trip is being planned — and the
     * docks drawn there are the trip's *own*, since the bike layer runs seeded from the itinerary — so a
     * tap there is refused rather than allowed to take the screen, exactly as a stop tap is (#2097).
     * Letting it through discarded the whole plan in one tap on the very screen #2140 protects.
     *
     * Returns whether the focus was taken, so the map can leave its own render state alone when it wasn't.
     */
    fun onBikeStationFocused(id: String): Boolean {
        if (_currentFocus.value is CurrentFocus.Directions) return false
        pushFocus(CurrentFocus.BikeStation(id))
        return true
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
        trips: Set<FocusedTrip> = emptySet()
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
                    animate = pending.animate
                )
            )
        }
        // FocusStop must be dispatched first on restore so the map can validate this stop as the
        // current rendered focus before starting its adjacency session.
        emitMapDirective(
            MapDirective.ShowStopRoutes(
                stopId = stop.id,
                routes = routes.orEmpty(),
                trips = focusedTrips
            )
        )
        focus.selectedRoute?.let { showStopRoute(focus.stop.id, it, frameRoute = frameSelectedRoute) }
    }

    /**
     * Show [request]'s route with [selectedTripId] drilled into over it — the drilled-into trip rung of
     * the focus being shown (#2205, #2224), which the map projects as its vehicle selection (what draws
     * the extrapolation band, the exact shape/stops, the fast-estimate marker and the block
     * continuation).
     *
     * The two travel as one directive rather than a pair the caller has to remember to send, so the
     * selection cannot become a second, independently-mutated truth — see [MapDirective.ShowRoute].
     */
    private fun showRoute(
        request: ShowRouteRequest,
        selectedTripId: String?,
        stopScoped: Boolean = false,
        frameRoute: Boolean = true,
        withinDirections: Boolean = false
    ) = emitMapDirective(
        MapDirective.ShowRoute(
            request,
            selectedTripId = selectedTripId,
            stopScoped = stopScoped,
            frameRoute = frameRoute,
            withinDirections = withinDirections
        )
    )

    /**
     * Show a stop-scoped route on the map together with its trip level — the stop-shaped entry into
     * [showRoute], used by every entry into and replay of a stop's route selection.
     *
     * [request] defaults to the selection's own, which carries no `focusTripId` even when a trip *is*
     * focused: that field is a one-shot camera instruction (fit the vehicle with the stop, and ping it),
     * and the replaying callers run on every arrivals poll. Only the gesture that entered the trip level
     * passes a request asking for that fit — the same reason a poll must not reframe the route (#1895).
     */
    private fun showStopRoute(
        stopId: String,
        selection: StopRouteSelection,
        request: ShowRouteRequest = selection.target(stopId).toRequest(),
        frameRoute: Boolean = true
    ) = showRoute(request, selection.selectedTripId, stopScoped = true, frameRoute = frameRoute)

    /** Animate the map's camera back onto the focused stop, if one is focused (else a no-op). */
    fun recenterOnFocusedStop(undoViewport: MapViewport? = null) {
        _currentFocus.value.focusedStop?.let {
            recordViewportUndo(undoViewport)
            emitMapDirective(MapDirective.RecenterOnFocusedStop(it.point))
        }
    }

    /**
     * Dispatch a route reveal from the arrivals drawer as one atomic focus transition. A stop-scoped
     * [request] (it carries a `directionStopId`) becomes a route selection subordinate to the focused
     * stop; an unscoped one enters standalone route focus (where [shortName]/[headsign] are unused —
     * the standalone banner resolves the route itself).
     */
    fun selectArrivalRoute(
        request: ShowRouteRequest,
        shortName: String,
        headsign: String?,
        undoViewport: MapViewport? = null
    ) {
        if (request.directionStopId == null) {
            focusStandaloneRoute(request, undoViewport)
            return
        }
        val stopFocus = _currentFocus.value as? CurrentFocus.Stop ?: return
        selectStopRoute(
            stopFocus = stopFocus,
            request = request,
            firstLeg = RouteLeg(request.routeId, shortName, request.initialDirectionId),
            originHeadsign = headsign,
            undoViewport = undoViewport
        )
    }

    /** Enter route focus from a route-oriented surface (search, recents, deep link, route info) or an
     *  unscoped drawer reveal (the row menu's "Show route on map", via [selectArrivalRoute]). */
    fun focusStandaloneRoute(request: ShowRouteRequest, undoViewport: MapViewport? = null) {
        // A request naming a trip — an ETA pill, or a coach-number search hit drilling into the ride
        // that vehicle is running — lands one rung deeper, exactly as it does over a focused stop
        // (#2224). The request keeps its `focusTripId` so the camera still fits vehicle+stop; the rung
        // below is what the focus retains once that one-shot fit is spent.
        val focus = CurrentFocus.Route(request.toRouteTarget(), selectedTripId = request.focusTripId)
        pushFocus(focus, undoViewport)
        showRoute(request, focus.selectedTripId)
    }

    /** Persist a direction chosen from the standalone route banner (null = the whole route). */
    fun selectStandaloneRouteDirection(directionId: Int?) {
        val focus = _currentFocus.value as? CurrentFocus.Route ?: return
        // The trip rung does not survive the switch: the map filters a vehicle running the other
        // direction out of the overlay and drops its own selection (`RouteMapController.selectDirection`),
        // so the focus must stop claiming it rather than drift from what is drawn.
        replaceFocus(CurrentFocus.Route(focus.target.copy(directionId = directionId)))
    }

    /** Follow a block continuation while preserving the active focus kind. */
    fun advanceRouteContinuation(
        routeId: String,
        shortName: String,
        directionId: Int?,
        undoViewport: MapViewport? = null
    ) {
        when (val focus = _currentFocus.value) {
            is CurrentFocus.Stop -> {
                val selected = focus.selectedRoute ?: return
                val next = selected.continueTo(RouteLeg(routeId, shortName, directionId))
                pushFocus(focus.copy(selectedRoute = next), undoViewport)
                showStopRoute(focus.stop.id, next)
            }
            is CurrentFocus.Route -> {
                // No trip rung carries over: the continuation is a *different* trip of that block and
                // this tap doesn't name its id — the same rule [StopRouteSelection.continueTo] applies.
                val target = RouteTarget(routeId, directionId = directionId)
                pushFocus(CurrentFocus.Route(target), undoViewport)
                showRoute(target.toRequest(), selectedTripId = null)
            }
            else -> Unit
        }
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
        undoViewport: MapViewport? = null
    ) {
        val stopFocus = _currentFocus.value as? CurrentFocus.Stop ?: return
        selectStopRoute(
            stopFocus = stopFocus,
            request = ShowRouteRequest(
                routeId = routeId,
                directionStopId = stopFocus.stop.id,
                initialDirectionId = directionId
            ),
            firstLeg = RouteLeg(routeId, shortName, directionId),
            originHeadsign = null,
            undoViewport = undoViewport
        )
    }

    /**
     * A row of the transit-centre drawer was tapped (#2107): show that route on the map, scoped to the
     * bay the row departs from.
     *
     * Reaching the same place a rider gets by tapping the bay and then its row — `CurrentFocus.Stop`
     * with that route selected — but in **one** [pushFocus], so one Back returns straight to the nearby
     * list rather than parking them on a per-stop panel they never asked for. It can't go through
     * [selectArrivalRoute], which requires a stop focus to already exist; here there is none, which is
     * exactly the condition that put the nearby list on screen.
     *
     * [focusTripId] is what separates the drawer's two taps, exactly as it does in the per-stop drawer
     * (see [selectStopRoute]):
     *  - **null — the row body.** The camera only *pans* onto the bay. This drawer exists only at
     *    transit-centre zoom, so the rider is already looking at the place they are standing in; framing
     *    the whole line would zoom out to its full extent and throw that view away to answer a question
     *    they didn't ask. The route still draws, and the banner can reframe it on request.
     *  - **non-null — an ETA pill.** The pill names one trip, so this is the stop→route→trip level
     *    (#2205) and the map fits that trip's live vehicle together with this bay. `RouteMapController`
     *    performs that fit off `focusTripId` and ignores `frameRoute` while one is set, so the pill gets
     *    the vehicle+stop bounding box here for the same reason it does from a stop's own drawer.
     */
    fun showNearbyRouteOnMap(
        bay: FocusedStop,
        routeId: String,
        shortName: String,
        directionId: Int?,
        headsign: String?,
        focusTripId: String? = null,
        undoViewport: MapViewport? = null
    ) {
        presentedRoutes = emptySet()
        // Arm the latch for *this* bay, so the load that follows emits `MapDirective.FocusStop` and the
        // map renders the bay as the selected stop — the marker a rider expects after tapping a row, and
        // which nothing else here would set (`ShowRoute` draws the route, not the stop focus).
        //
        // `preserveViewport = true` is what makes arming it safe. Overwriting rather than clearing also
        // disposes of a stale restore/deep-link latch (one whose stop was unfocused before its arrivals
        // landed — exactly the state that puts this list on screen); clearing it was the old behaviour,
        // and the reason it was cleared was that consuming it would recenter on a focus the rider never
        // asked to return to. A preserved viewport can't: it suppresses both that recenter and the
        // route-framing, leaving this method's own camera move the only one.
        markPendingMapFocus(preserveViewport = true)
        val selection = StopRouteSelection(
            originHeadsign = headsign,
            legs = listOf(RouteLeg(routeId, shortName, directionId)),
            // An ETA-pill tap names the trip it belongs to, and that *is* the trip level (#2205) — the
            // same rule [selectStopRoute] applies to the per-stop drawer's two taps.
            selectedTripId = focusTripId
        )
        pushFocus(CurrentFocus.Stop(stop = bay, selectedRoute = selection), undoViewport)
        // Through [showStopRoute] rather than emitting ShowRoute directly, so this path can't be the one
        // that breaks its pairing invariant: the route directive always travels with a SelectVehicle,
        // null included, so a row-body tap clears any vehicle a previous pill left selected.
        showStopRoute(
            bay.id,
            selection,
            request = ShowRouteRequest(
                routeId = routeId,
                directionStopId = bay.id,
                focusTripId = focusTripId,
                initialDirectionId = directionId
            ),
            // Never frame the whole line from this drawer. With a focusTripId set the controller ignores
            // this anyway and fits vehicle+bay instead; without one, the pan below is the move we want.
            frameRoute = false
        )
        if (focusTripId == null) {
            // Row body only — the pill's vehicle+bay fit owns the camera and a recenter would fight it.
            // Emitted after the route so the recenter picks up route mode's header bias, landing the bay
            // where a stop focused in route mode always does. `CameraCommand.Recenter` keeps the current
            // zoom/bearing/tilt, so this is a pan and nothing more.
            emitMapDirective(MapDirective.RecenterOnFocusedStop(bay.point))
        }
    }

    private fun selectStopRoute(
        stopFocus: CurrentFocus.Stop,
        request: ShowRouteRequest,
        firstLeg: RouteLeg,
        originHeadsign: String?,
        undoViewport: MapViewport?
    ) {
        val selection = StopRouteSelection(
            originHeadsign = originHeadsign,
            legs = listOf(firstLeg),
            // An ETA-pill tap names the trip it belongs to, and that *is* the trip level (#2205): the
            // pill lands one level deeper than the row body, which names no trip and stops at the route.
            selectedTripId = request.focusTripId
        )
        pushFocus(stopFocus.copy(selectedRoute = selection), undoViewport)
        // The caller's own request, not the selection's: this is the drill-in gesture, so it keeps the
        // focusTripId that fits the camera to the vehicle.
        showStopRoute(
            stopFocus.stop.id,
            selection,
            request = request.copy(directionStopId = stopFocus.stop.id)
        )
    }

    /**
     * A vehicle tapped on the map enters the trip rung over whatever route is drawn (#2205, #2224) — the
     * same state its ETA pill reaches — so the band it raises is unwound by a background tap instead of
     * lingering as a render-only selection. Applies alike over a focused stop's route, a route opened on
     * its own, and a directions leg's route: vehicles are only ever drawn in one of those three, and a
     * tap now means the same thing in each.
     *
     * [tripId] is the tapped vehicle's active trip, which the wire leaves nullable: a vehicle running no
     * identifiable trip has nothing to drill into, so it lands back at the plain route level rather than
     * letting the focus and the map's selection drift apart.
     */
    fun selectFocusedRouteTrip(tripId: String?) {
        // Null only when the current focus draws no route — no vehicle can have been tapped there, so
        // there is nothing to record and nothing to tell the map.
        val target = _currentFocus.value.withSelectedTrip(tripId) ?: return
        pushFocus(target)
        emitMapDirective(MapDirective.SelectVehicle(tripId))
    }

    /**
     * A background-map tap drops one attention layer: a drilled-into vehicle becomes the plain route it
     * runs on — over a focused stop, a standalone route, or a directions leg alike (#2224);
     * route-over-stop becomes the plain stop; a focused itinerary leg becomes the plain itinerary
     * overview; any plain stop, standalone route, or bike focus returns to the unfocused root.
     */
    fun unfocusMapOneLevel() {
        val focus = _currentFocus.value
        val target = focus.peeledOneLevel() ?: return
        // A stray background tap is the cheapest gesture on the map; where it would take a planned trip
        // off it, it asks first (#2140) — judged on the transition the `when` above just decided.
        if (stageDirectionsExitConfirmation(focus, target)) return
        pushFocus(target)
        when {
            // Peeled the trip only: the route is still drawn, so drop the vehicle selection (and with it
            // the band) rather than tearing route mode down. Asked of the focus we came *from*, which is
            // what makes it one branch for all three route-bearing focuses instead of one per kind.
            focus.selectedTripId != null -> emitMapDirective(MapDirective.SelectVehicle(null))
            target is CurrentFocus.Stop -> emitMapDirective(MapDirective.ClearSelectedRoute)
            // Popped a leg sub-focus back to the whole trip.
            target is CurrentFocus.Directions -> emitMapDirective(itineraryOverviewDirective(focus))
            else -> {
                presentedRoutes = emptySet()
                emitMapDirective(MapDirective.ClearFocus)
            }
        }
    }

    /**
     * How the map returns to the itinerary overview from [from]: with the trip still drawn (an on-street
     * leg focus only receded its other legs) restoring their weight and reframing the trip is enough;
     * otherwise it has to be redrawn — and where there is nothing to redraw (a fresh entry, before any
     * itinerary was shown) the map is cleared rather than left in whatever the sub-focus made of it; the
     * sheet's own remount reconcile draws the selected trip back.
     */
    private fun itineraryOverviewDirective(from: CurrentFocus): MapDirective = if (from.keepsDrawnItinerary) {
        MapDirective.ClearItineraryLegFocus
    } else {
        shownItinerary ?: MapDirective.ClearFocus
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
        if (_currentFocus.value is CurrentFocus.Directions) return
        presentedRoutes = emptySet()
        pendingFocus = null
        // A fresh entry has nothing drawn yet — the previous visit's trip is long off the map, and a
        // stale record of it would make the first Back out of an empty form ask to discard it (#2140).
        shownItinerary = null
        pushFocus(CurrentFocus.Directions(), undoViewport)
    }

    // The draw of the itinerary currently shown in directions mode, cached so returning from a route
    // sub-focus (a map-background tap) can redraw it — the results VM's selection flow doesn't re-emit on
    // its own. Held as the whole directive rather than just the itinerary, so a redraw reproduces the
    // terminus pins the trip was drawn with instead of quietly restoring a suppressed one. Read together
    // with a directions focus it also answers "has the rider a trip to lose?" — the #2140 gate — which is
    // why it is now cleared whenever the trip leaves the map. On its own it does not: it deliberately
    // outlives the exit (see [exitDirections]), so the gate always pairs it with the focus.
    private var shownItinerary: MapDirective.ShowItinerary? = null

    /**
     * Draw [itinerary] on the home map and frame the whole trip (only meaningful in
     * [CurrentFocus.Directions]). Showing the full itinerary drops any leg route focus, so an option-card
     * tap returns to the overview. [pins] withholds the terminus pin of an endpoint the rider set to
     * their current location, which the blue dot already marks (#2111).
     */
    fun showItineraryOnMap(itinerary: TripItinerary, pins: ItineraryPins = ItineraryPins()) {
        val draw = MapDirective.ShowItinerary(itinerary, pins)
        shownItinerary = draw
        // Showing the whole trip drops any leg sub-focus; the ShowItinerary below is the redraw. Guarded so
        // a call from outside directions can't push a directions focus.
        if (directionsSubFocus != null) pushFocus(CurrentFocus.Directions())
        emitMapDirective(draw)
    }

    /** The leg the user has drilled into from the itinerary overview, if any. */
    private val directionsSubFocus: DirectionsSubFocus?
        get() = (_currentFocus.value as? CurrentFocus.Directions)?.subFocus

    // A route label tapped on the drawn itinerary (#2101), carrying the itinerary legs that label names.
    // Inbound (map -> the directions drawer), so it runs the other way from [mapDirectives] and can't be a
    // directive: turning a tapped ride into a focus needs the drawer's own resolved leg refs (route ids,
    // board stops), which live in the results VM, not here. So this VM only relays the tap to the sheet,
    // which spends it through exactly the handler its own row tap uses.
    //
    // A SharedFlow rather than a Channel — the opposite call from [mapDirectives], for the opposite
    // reason: an itinerary label only exists while the sheet showing that itinerary is composed, so a tap
    // with no collector is a tap with no trip to focus into, and dropping it is the honest outcome. A
    // queued one would land on whatever the rider had moved on to.
    private val _itineraryRideBadgeTaps = MutableSharedFlow<Set<Int>>(extraBufferCapacity = 1)
    val itineraryRideBadgeTaps: SharedFlow<Set<Int>> = _itineraryRideBadgeTaps.asSharedFlow()

    /** A route label tapped on the drawn itinerary: relay the legs it names to the directions sheet. */
    fun onItineraryRideBadgeTapped(legIndices: Set<Int>) {
        _itineraryRideBadgeTaps.tryEmit(legIndices)
    }

    /** Recenter the map on a tapped itinerary step's point (only while in [CurrentFocus.Directions]). */
    fun focusItineraryPointOnMap(point: GeoPoint) = emitMapDirective(MapDirective.FocusItineraryPoint(point))

    /**
     * Focus a whole tapped itinerary leg on the map: frame it, with the rest of the trip receding to context
     * around it (#2048). Recorded as the overview's [DirectionsSubFocus.Leg], so tapping the map background
     * (or Back) returns to the whole trip exactly as it does from a transit leg's route focus, instead of
     * leaving directions (#2075). Only the directions drawer offers leg taps, so anywhere else there is no
     * trip to focus into and this is a no-op.
     */
    fun focusItineraryLegOnMap(leg: FocusedLeg) {
        val from = _currentFocus.value as? CurrentFocus.Directions ?: return
        pushFocus(CurrentFocus.Directions(DirectionsSubFocus.Leg(leg)))
        applyLegFocus(leg, from)
    }

    /**
     * Recede the trip's other legs around [leg] and frame it — redrawing the itinerary first when the focus
     * we come from ([from]) tore it down, since the framing would otherwise no-op in route mode
     * (directionsActive is false). Shared by the drawer's leg tap and the back-restore of one.
     */
    private fun applyLegFocus(leg: FocusedLeg, from: CurrentFocus) {
        if (!from.keepsDrawnItinerary) {
            shownItinerary?.let { emitMapDirective(it) }
        }
        emitMapDirective(MapDirective.FocusItineraryLeg(leg.points, leg.legIndices))
    }

    /**
     * Tap a transit leg from the directions overview: highlight its route on the map (the whole route +
     * the traveled [fallbackLeg] drawn thick), recording the overview as the back target so a
     * map-background tap (or Back) returns to the itinerary. [routeLeg]'s ids are already OBA-format
     * (resolved at build time); an unresolved route degrades to framing the leg. The per-stop ETAs are
     * shown inline in the drawer's Board/Alight rows, not here.
     */
    fun focusItineraryRouteLeg(routeLeg: RouteLegRef, fallbackLeg: FocusedLeg) {
        val routeId = routeLeg.routeId
        if (routeId == null) {
            focusItineraryLegOnMap(fallbackLeg)
            return
        }
        // Anchor to the boarding stop so the route shows only the ridden direction. A folded interline
        // (#2000) carries its extra ridden legs so the map draws each continued-onto route/direction and
        // the shared vehicle across them. An interchangeable ride (#2042) adds every alternative route,
        // anchored to that same platform, so focusing the joined badge shows the whole corridor rather
        // than whichever route OTP happened to choose for the itinerary. Unresolved alternatives remain
        // useful in the badge/ETA UI but cannot be loaded on the map, so they are omitted here.
        val routes = routeLeg.routesForFocus(routeId)
        focusItineraryRouteLegOnMap(
            routeId,
            spans = routeLeg.riddenSpansOr(fallbackLeg.points),
            directionStopId = routeLeg.board?.stopId,
            extraSegments = routes.extraSegments,
            alightStopId = routeLeg.alight?.stopId,
            directionHeadsign = routes.leaderHeadsign,
            boardStop = routeLeg.board?.asFocusedStop()
        )
    }

    /**
     * A pill tap in a directions leg's inline ETA strip: enter that leg's route focus and focus/animate/
     * ping the tapped trip's live vehicle — [request] already carries the route, direction-anchor stop,
     * and focusTripId (built by the shared arrivals handler), so this just rides the same ShowRoute path
     * as the arrivals drawer, adding the ride [routeLeg] is travelled on over the route and that leg's
     * own end-of-ride stop (the arrivals handler knows the route and stop tapped, not the planned ride).
     * Takes the same ref-plus-fallback pair the drawer's leg tap does, so a pill and the leg card it sits
     * in draw the same ride.
     *
     * The route set is re-centered on the tapped route without narrowing the ride to it: every other
     * interchangeable option stays loaded and selected from the same arrivals queue.
     */
    fun focusDirectionsRouteVehicle(
        request: ShowRouteRequest,
        routeLeg: RouteLegRef,
        fallbackPoints: List<GeoPoint>
    ) {
        val routes = routeLeg.routesForFocus(request.routeId)
        enterDirectionsRouteFocus(
            request.copy(
                riddenSpans = routeLeg.riddenSpansOr(fallbackPoints),
                extraSegments = routes.extraSegments,
                alightStopId = routeLeg.alight?.stopId,
                directionHeadsign = routes.leaderHeadsign
            ),
            boardStop = routeLeg.board?.asFocusedStop()
        )
    }

    /**
     * A pill tap in the *focused* leg's ETA strip, which reads HOME's hoisted session rather than one of
     * its own. That session sits above the itinerary and so cannot close over the leg's row — but it does
     * not need to: a tap there is by definition within the focused leg, whose ride (its spans, extra
     * segments, alighting stop and headsign) is already captured on the current focus. Re-enter with
     * [request]'s tapped route/trip carried over that same ride.
     */
    fun focusDirectionsRouteVehicleInFocusedLeg(request: ShowRouteRequest) {
        val current = (_currentFocus.value as? CurrentFocus.Directions)?.subFocus as? DirectionsSubFocus.Route ?: return
        enterDirectionsRouteFocus(
            current.request.releader(request),
            boardStop = current.boardStop
        )
    }

    /**
     * All routes the rider may board, expressed relative to whichever one [leaderRouteId] the focused
     * ETA belongs to. Keeping the leader out of the focused ride's segments is significant: the route
     * controller treats it as the primary poll and polls only the *other* segment ids.
     */
    private fun RouteLegRef.routesForFocus(leaderRouteId: String): FocusedRideRoutes {
        val boardStopId = board?.stopId
        val boardable = buildList {
            routeId?.let {
                add(
                    RouteFocusSegment(
                        routeId = it,
                        anchorStopId = boardStopId,
                        relationship = RouteFocusRelationship.INTERCHANGEABLE,
                        directionHeadsign = headsign
                    )
                )
            }
            alternatives.mapNotNullTo(this) { alternative ->
                val id = alternative.routeId ?: return@mapNotNullTo null
                RouteFocusSegment(
                    routeId = id,
                    anchorStopId = boardStopId,
                    relationship = RouteFocusRelationship.INTERCHANGEABLE,
                    directionHeadsign = alternative.headsign
                )
            }
        }.distinctBy { it.routeId }
        return FocusedRideRoutes(
            leaderHeadsign = boardable.firstOrNull { it.routeId == leaderRouteId }?.directionHeadsign,
            extraSegments = extraSegments + boardable.filterNot { it.routeId == leaderRouteId }
        )
    }

    /**
     * Move an already-focused interchangeable ride onto [next]'s leader without losing the route that
     * used to lead it. A pill tap changes which route supplies the primary poll; it does not change the
     * set of routes the rider may board.
     */
    private fun ShowRouteRequest.releader(next: ShowRouteRequest): ShowRouteRequest {
        if (next.routeId == routeId) {
            return next.copy(
                riddenSpans = riddenSpans,
                extraSegments = extraSegments,
                alightStopId = alightStopId,
                directionHeadsign = directionHeadsign
            )
        }
        val nextLeader = extraSegments.firstOrNull {
            it.routeId == next.routeId && it.relationship == RouteFocusRelationship.INTERCHANGEABLE
        }
        val previousLeader = RouteFocusSegment(
            routeId = routeId,
            anchorStopId = directionStopId,
            relationship = RouteFocusRelationship.INTERCHANGEABLE,
            directionHeadsign = directionHeadsign
        )
        return next.copy(
            riddenSpans = riddenSpans,
            extraSegments = (extraSegments.filterNot { it.routeId == next.routeId } + previousLeader)
                .distinctBy { it.routeId },
            alightStopId = alightStopId,
            directionHeadsign = nextLeader?.directionHeadsign
        )
    }

    private data class FocusedRideRoutes(
        val leaderHeadsign: String?,
        val extraSegments: List<RouteFocusSegment>
    )

    /**
     * This planned stop as a focusable one, or null when it cannot be: the arrivals session the map's
     * ride selection reads is keyed by OBA stop id and needs a location, so a leg whose OTP→OBA stop
     * resolution failed simply has no boarding stop to poll.
     */
    private fun RouteStopRef.asFocusedStop(): FocusedStop? {
        val id = stopId ?: return null
        val at = point ?: return null
        return FocusedStop(id = id, name = name, code = stopCode, point = at)
    }

    /**
     * The boarding stop's arrivals landed for the focused leg — hand the map the rows the ride's routes
     * are listed in, so it can select which vehicles belong to the ride (#2124).
     *
     * Reported by the hoisted session in HOME rather than by the leg card's strip, so the map's vehicle
     * set does not depend on which itinerary rows happen to be composed. Dropped unless a leg is focused
     * and the load is for *its* boarding stop, matching how [onArrivalsLoaded] guards the stop-focus path.
     */
    fun onRideArrivals(stopId: String, groups: List<RideRouteGroup>) {
        val focus = (_currentFocus.value as? CurrentFocus.Directions)?.subFocus as? DirectionsSubFocus.Route ?: return
        if (focus.boardStop?.id != stopId) return
        emitMapDirective(MapDirective.SetRideArrivals(stopId, groups))
    }

    /**
     * The ride to draw over the route, preferring the per-route spans the leg was built with (#2127) and
     * falling back to the tapped row's own joined geometry ([fallbackPoints]) as one undivided span —
     * exactly what the map drew before there were spans to divide, and (carrying no
     * [RiddenSpan.plannedColor]) in the shown route's colour as it did then.
     *
     * Defensive rather than a live path: the results repository emits one span per ridden leg, so a ref it
     * built always has them, and the ref the drawer synthesizes when it couldn't resolve one
     * (`TripLogBuilder.fallbackRouteLeg`) names neither a route nor a boarding stop — so it never reaches a
     * route focus at all, by either entry. A leg tap degrades to framing the leg ([focusItineraryRouteLeg]),
     * and the inline ETA strip that owns the other entry draws nothing without an OBA stop id to poll. This
     * stands so a ref that somehow arrives without spans still draws its ride rather than nothing; nothing
     * that reaches it today has a planned colour to lose.
     */
    private fun RouteLegRef.riddenSpansOr(fallbackPoints: List<GeoPoint>): List<RiddenSpan> = riddenSpans.ifEmpty { listOf(RiddenSpan(fallbackPoints)) }

    /**
     * Recontextualizes the map onto [routeId] with the traveled ride ([spans]) drawn thick over it, and
     * records the overview as the back target so a map-background tap (or Back) returns to the itinerary.
     * Ids are already OBA-format.
     */
    fun focusItineraryRouteLegOnMap(
        routeId: String,
        spans: List<RiddenSpan> = emptyList(),
        directionStopId: String? = null,
        directionId: Int? = null,
        extraSegments: List<RouteFocusSegment> = emptyList(),
        alightStopId: String? = null,
        directionHeadsign: String? = null,
        boardStop: FocusedStop? = null,
        undoViewport: MapViewport? = null
    ) {
        enterDirectionsRouteFocus(
            ShowRouteRequest(
                routeId = routeId,
                directionStopId = directionStopId,
                initialDirectionId = directionId,
                riddenSpans = spans,
                extraSegments = extraSegments,
                alightStopId = alightStopId,
                directionHeadsign = directionHeadsign
            ),
            boardStop = boardStop,
            undoViewport = undoViewport
        )
    }

    /**
     * Enter the route-subordinate-to-directions focus for [request]: push the itinerary overview as the
     * back target (with [undoViewport] to restore) and load the route with its ridden segment. Shared by
     * the leg-row tap and the inline-ETA vehicle tap so both spend the same request faithfully.
     *
     * A request naming a trip (the ETA-pill tap) also enters the trip rung over that route, so a pill
     * tapped in a directions leg reaches the same state one tapped in a stop's drawer does (#2224): the
     * band, the exact shape/stops, the outlined pill, and a background tap that gives the vehicle up
     * before the leg.
     */
    private fun enterDirectionsRouteFocus(
        request: ShowRouteRequest,
        boardStop: FocusedStop? = null,
        undoViewport: MapViewport? = null
    ) {
        val subFocus = DirectionsSubFocus.Route(
            // The *stored* request drops the focusTripId: it is the one-shot "fit this vehicle and ping
            // it" instruction this gesture asked for, and replaying it from the back stack would re-fly
            // and re-ping a vehicle the rider is merely returning to. The rung below is what survives.
            request = request.copy(focusTripId = null),
            boardStop = boardStop,
            selectedTripId = request.focusTripId
        )
        pushFocus(CurrentFocus.Directions(subFocus), undoViewport)
        // withinDirections: this route is drilled into *from* a trip plan, so the map keeps the rest of
        // the trip beneath it as de-emphasized context (#2048). The request emitted is the caller's own,
        // focusTripId included — this is the drill-in gesture, so it gets its fit.
        showRoute(request, subFocus.selectedTripId, withinDirections = true)
        // A stop id is enough to anchor the route direction, but the hoisted arrivals session also
        // needs a point because it is built from FocusedStop. Classify that no-session case explicitly
        // as Unserved instead of leaving ride selection Pending (which would hide every vehicle).
        if (boardStop == null && request.directionStopId != null) {
            emitMapDirective(MapDirective.SetRideArrivals(request.directionStopId, emptyList()))
        }
    }

    /** Clear the drawn itinerary while staying in directions (the plan became unsubmittable). */
    fun clearShownItineraryOnMap() {
        shownItinerary = null
        // The trip this asked about is gone, so the question goes with it rather than being left on
        // screen offering to discard something the rider can no longer see.
        _pendingDirectionsExit.value = false
        emitMapDirective(MapDirective.ClearItinerary)
    }

    /** Show the resolved From/To endpoints as green/red pins (before a plan); a null endpoint drops it. */
    fun setDirectionsEndpointsOnMap(from: GeoPoint?, to: GeoPoint?) = emitMapDirective(MapDirective.SetDirectionsEndpoints(from, to))

    // Leave directions focus, returning the map to nearby stops. Private: the only way out is
    // [navigateBackInDirections]'s last step, so a control can't jump straight out of a focused leg —
    // exactly the behaviour #2075 removed.
    // [shownItinerary] deliberately survives this: backing *into* the directions focus we just left has to
    // put the trip back on the map. A later fresh entry resets it (see [enterDirections]).
    private fun exitDirections() = clearMapFocus()

    // Whether a gesture that would leave directions is waiting on the rider's confirmation. Set only by
    // [stageDirectionsExitConfirmation]; the host shows a modal dialog off it and answers with
    // [confirmExitDirections] / [dismissDirectionsExit]. It carries no "which gesture asked", because
    // there is only one answer to give: every gesture that reaches it wants the same exit.
    private val _pendingDirectionsExit = MutableStateFlow(false)
    val pendingDirectionsExit: StateFlow<Boolean> = _pendingDirectionsExit.asStateFlow()

    /**
     * Intercept a focus change that would take a drawn trip off the map, staging a confirmation instead
     * of making it — and reporting whether it did, so the caller stops where it is (#2140).
     *
     * Judged on the transition ([from] → [to]), not on which gesture asked: every caller already decides
     * where its gesture lands, so reading that decision keeps this from re-deriving — and later drifting
     * from — the level rules they encode. Any move out of directions while a trip is drawn costs the
     * rider their whole plan; every move that stays inside it, such as stepping back out of a
     * drilled-into leg, costs them nothing. An unplanned form has no trip to lose, so it leaves free —
     * and so does a *recoverable* one, which is what pinning makes a trip ([setDrawnTripRecoverable]).
     */
    private fun stageDirectionsExitConfirmation(from: CurrentFocus, to: CurrentFocus): Boolean {
        val leavesDrawnTrip = from is CurrentFocus.Directions &&
            to !is CurrentFocus.Directions &&
            shownItinerary != null
        if (!leavesDrawnTrip || drawnTripRecoverable) return false
        _pendingDirectionsExit.value = true
        return true
    }

    // Whether the drawn trip can be got back after leaving, which is the whole question #2140's dialog
    // asks. False by default, so the confirmation stands exactly where it did before.
    //
    // It mirrors a fact this VM cannot derive, so it holds an invariant worth stating: there is exactly
    // **one writer**, and it re-asserts on the same condition it reports ("is the drawn plan the pinned
    // one"). Do not also clear it from `enterDirections`/`clearShownItineraryOnMap` — that looks like
    // prudent housekeeping and is the opposite: neither of those changes the writer's condition, so
    // nothing would restore the flag, and a pinned trip would start demanding the confirmation again.
    // Any second writer has to come with a reason the first one cannot already express.
    private var drawnTripRecoverable = false

    /**
     * States whether the trip currently drawn can be recovered after leaving directions — today, whether
     * it is the pinned one (#2053).
     *
     * The gesture the confirmation guards costs the rider nothing when the answer is yes, so the question
     * stops being worth asking and the exit goes through on the first Back or background tap. That is the
     * point of pinning: it is supposed to make leaving *cheap*, and a dialog that still demanded a
     * decision would have kept the toll it was meant to remove.
     *
     * Deliberately a boolean rather than the pin itself. This ViewModel has no business knowing what a
     * pinned trip is — only whether what it drew is safe to walk away from — so the one fact it needs
     * arrives as that fact, and pinning stays free to change shape without reaching in here.
     */
    fun setDrawnTripRecoverable(recoverable: Boolean) {
        drawnTripRecoverable = recoverable
    }

    /**
     * The rider confirmed the staged exit: leave directions, dropping the trip. The guard is what keeps
     * this from becoming a public door around [exitDirections] being private — without it a control
     * could call it from a drilled-into leg and jump straight out, the behaviour #2075 removed.
     */
    fun confirmExitDirections() {
        if (!_pendingDirectionsExit.value) return
        exitDirections()
    }

    /** The rider declined the staged exit (or dismissed the dialog): stay on the trip. */
    fun dismissDirectionsExit() {
        _pendingDirectionsExit.value = false
    }

    /**
     * Leave directions outright, asking nothing — the answer to a modal the rider declined rather than
     * to a gesture (today, backing out of the safety notice, #2218).
     *
     * Deliberately *not* [navigateBackInDirections]: that is a three-rung ladder (pop a sub-focus, else
     * stage the #2140 confirmation, else exit), and only its last rung is what declining means. Borrowed
     * for a modal it misbehaves the moment there is anything to pop or to confirm — a pinned-trip resume
     * puts a drawn plan behind the notice on the rider's very first visit, and the ladder would answer
     * Back by raising a confirm dialog *underneath* the full-screen notice still covering the screen.
     * Unlike [confirmExitDirections] this needs no staged-exit guard: it isn't a control on the trip
     * surface (#2075) but the dismissal of a modal that is itself the only thing on screen.
     */
    fun leaveDirections() = exitDirections()

    /**
     * The system Back gesture while in directions. A leg the user drilled into — its route, or an
     * on-street leg framed on its own — steps back out of that leg first, so Back reverses the drill-in
     * (restoring the camera it moved) instead of leaving the trip behind (#2075); only from the plain
     * itinerary overview does Back exit directions — and with a trip drawn there, only after the rider
     * confirms it (#2140). The map-background tap drops a level the same way, but jumps straight to the
     * overview, while Back walks the legs the user visited in order — matching how a stop's route focus
     * already behaves under each gesture.
     */
    fun navigateBackInDirections() {
        // The false guard is belt-and-braces: reaching a leg sub-focus always pushed the overview it was
        // entered from, so there is history to pop.
        if (directionsSubFocus != null && navigateBackFocus()) return
        if (stageDirectionsExitConfirmation(_currentFocus.value, CurrentFocus.None)) return
        exitDirections()
    }

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
        // A staged "leave the trip?" question is only ever asked of the focus that raised it, so any
        // move off directions — the confirmed exit itself, or a deep link taking the map — answers it.
        if (focus !is CurrentFocus.Directions) _pendingDirectionsExit.value = false
        CurrentFocusPersistence.write(savedState, focus)
    }

    private fun restoreMapAfterBack(from: CurrentFocus, entry: MapUndoEntry) {
        val target = entry.focus
        val frameFocus = entry.viewport == null
        if (from == target) {
            entry.viewport?.let { emitMapDirective(MapDirective.RestoreViewport(it)) }
            return
        }
        // Only the trip rung moved (#2205, #2224) — the two focuses are identical but for which vehicle
        // is drilled into. The route beneath is already drawn, so just move the selection rather than
        // reloading and reframing it. Asked of the whole focus rather than per kind, which is what stops
        // a Back out of a drilled-into vehicle in directions or standalone route focus from yanking the
        // camera out to the route's extent (these arrive with no saved viewport, so [frameFocus] is true).
        if (from.withSelectedTrip(target.selectedTripId) == target) {
            emitMapDirective(MapDirective.SelectVehicle(target.selectedTripId))
            return
        }
        val returnsToSameStop = from is CurrentFocus.Stop &&
            target is CurrentFocus.Stop &&
            from.stop.id == target.stop.id
        if (returnsToSameStop) {
            val selected = target.selectedRoute
            if (selected == null) {
                emitMapDirective(MapDirective.ClearSelectedRoute)
            } else {
                showStopRoute(target.stop.id, selected, frameRoute = frameFocus)
            }
        } else {
            when (target) {
                is CurrentFocus.Stop -> {
                    markPendingMapFocus(preserveViewport = !frameFocus)
                    emitMapDirective(MapDirective.ClearFocus)
                }
                is CurrentFocus.Route -> showRoute(
                    // No focusTripId: the restored rung below draws the vehicle back without re-flying
                    // the camera to it or re-pinging it, exactly as a stop's route replay does.
                    target.target.toRequest(),
                    target.selectedTripId,
                    frameRoute = frameFocus
                )
                is CurrentFocus.Directions -> when (val subFocus = target.subFocus) {
                    // Back into a route sub-focus: re-show that leg's route on the map, over the trip
                    // it belongs to (still drawn, or restored by the sheet's own reconcile), with
                    // whichever vehicle was drilled into selected again. The stored request carries no
                    // focusTripId, so this redraws without re-flying or re-pinging (#2224).
                    is DirectionsSubFocus.Route -> showRoute(
                        subFocus.request,
                        subFocus.selectedTripId,
                        frameRoute = frameFocus,
                        withinDirections = true
                    )
                    // Back into an on-street leg focus: re-apply it exactly as the drawer's tap does.
                    is DirectionsSubFocus.Leg -> applyLegFocus(subFocus.leg, from)
                    // Back to the itinerary overview: restore it over whatever the leg focus did.
                    null -> emitMapDirective(itineraryOverviewDirective(from))
                }
                CurrentFocus.None, is CurrentFocus.BikeStation ->
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

    /**
     * Enter route mode for [request]'s route (the "show vehicles on map" action). [withinDirections]
     * marks a drill-in from a trip plan's leg, which keeps the rest of the trip drawn beneath the route
     * as de-emphasized context (#2048).
     *
     * [selectedTripId] is which of that route's vehicles the focus is drilled into (#2205, #2224), null
     * for none. **It carries no default on purpose.** The route and the vehicle drilled into over it are
     * one map state, and sending them apart is how they drifted: the stop path paired them and the
     * standalone-route and directions-leg paths did not, leaving the map's selection an unowned second
     * truth on those two. Making it a required field means a surface added later cannot show a route
     * without answering the question — the compiler asks, so no comment has to.
     */
    data class ShowRoute(
        val request: ShowRouteRequest,
        val selectedTripId: String?,
        val stopScoped: Boolean,
        val frameRoute: Boolean = true,
        val withinDirections: Boolean = false
    ) : MapDirective

    /** Restore the camera paired with an undone semantic action. */
    data class RestoreViewport(val viewport: MapViewport) : MapDirective

    /** Reframe the already-focused route without changing focus. */
    data object FrameRoute : MapDirective

    /** Draw all upcoming routes for the currently focused stop, without reframing the camera. */
    data class ShowStopRoutes(
        val stopId: String,
        val routes: List<ObaRoute>,
        val trips: Set<FocusedTrip> = emptySet()
    ) : MapDirective

    /**
     * The focused leg's boarding stop has fresh arrivals: the (route, direction) rows they were grouped
     * into, for the map's ride vehicle selection (#2124).
     *
     * Carries the rows rather than a finished verdict because the map is what authoritatively knows the
     * ride — which routes are boardable, how many stay-aboard continuations it has, where it alights —
     * so deriving the queue there keeps that knowledge in one place. Distinct from [ShowStopRoutes],
     * which drives the *focused-stop* layer and is gated on stop focus that directions refuses (#2097).
     */
    data class SetRideArrivals(
        val stopId: String,
        val groups: List<RideRouteGroup>
    ) : MapDirective

    /** Clear an active focused-stop route view when the focused stop changes or is removed. */
    object ClearStopRoutes : MapDirective

    /** Leave route mode while retaining the focused stop's adjacency session. */
    data object ClearSelectedRoute : MapDirective

    /**
     * Move the vehicle selection — what draws the extrapolation confidence band — to [tripId], or clear
     * it with null, over a route the map is *already* showing. The trip rung of the focus (#2205, #2224)
     * is the authority here; entering or replaying a route carries its own selection on [ShowRoute], so
     * this is only for the transitions that move the rung without redrawing the route beneath it.
     */
    data class SelectVehicle(val tripId: String?) : MapDirective

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
        val animate: Boolean = false
    ) : MapDirective

    /** Draw [itinerary]'s legs + start/end pins on the home map (trip-plan directions focus). */
    data class ShowItinerary(
        val itinerary: TripItinerary,
        /** Which terminus pins the trip wears — a current-location endpoint claims none (#2111). */
        val pins: ItineraryPins = ItineraryPins()
    ) : MapDirective

    /** Recenter the map on a tapped itinerary step's point (recenter + zoom to street level). */
    data class FocusItineraryPoint(val point: GeoPoint) : MapDirective

    /**
     * Focus a whole tapped itinerary leg: fit its polyline within the map's content padding, and recede
     * the trip's other legs — everything outside [legIndices] — to context (#2048).
     */
    data class FocusItineraryLeg(val points: List<GeoPoint>, val legIndices: Set<Int>) : MapDirective

    /**
     * Drop a [FocusItineraryLeg] focus back to the itinerary overview: every leg to full weight again and
     * the whole trip reframed. The lighter counterpart to redrawing via [ShowItinerary], which a leg focus
     * doesn't need — it never tore the trip down (unlike a leg's route sub-focus).
     */
    data object ClearItineraryLegFocus : MapDirective

    /** Clear the drawn itinerary but stay in directions mode (the plan became unsubmittable). */
    data object ClearItinerary : MapDirective

    /** Show the resolved trip-plan From/To endpoints as pins before an itinerary (null drops a pin). */
    data class SetDirectionsEndpoints(val from: GeoPoint?, val to: GeoPoint?) : MapDirective
}
