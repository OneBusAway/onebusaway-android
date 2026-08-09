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
package org.onebusaway.android.map

import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.onebusaway.android.extrapolation.ExtrapolatedVehicle
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.extrapolation.data.forEachActiveTrip
import org.onebusaway.android.extrapolation.extrapolatedVehicles
import org.onebusaway.android.extrapolation.extrapolationFromState
import org.onebusaway.android.map.render.ContinuationArrow
import org.onebusaway.android.map.render.ContinuationBadge
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.FramingIntent
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.MapVehicles
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RouteContinuation
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineWidthProfile
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.TripOverlay
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.toGeoPoint

/**
 * The part of a [ShowRouteRequest] that describes **the ride drawn on the route** — the trip-plan leg a
 * rider drilled into — as one value, so [RouteMapController.reframe] can decide "is this a different
 * ride?" with a single comparison instead of a hand-written chain over four fields it also has to
 * assign by hand (#2149).
 *
 * [RouteMapController.start] and [RouteMapController.reframe] both take it from a request via [of], so
 * the two entries cannot honour different fields — the divergence #1797 fixed one layer up, and the
 * reason `reframe`'s KDoc used to warn that `start`'s parameter list needed a matching update.
 *
 * **Adding a field here is the whole change**: it joins the guard, both entries and the redraw
 * together. `RouteMapGuardInputsTest` pins this against [ShowRouteRequest]'s own field set, so a new
 * request field has to be sorted into "part of the ride" (here) or "handled by name in `reframe`"
 * rather than silently ignored by one of them.
 *
 * Compared by value, not identity: the spans/segments are rebuilt per request by the launcher, so an
 * identity compare would report a change on every re-tap and redraw the same ride.
 */
internal data class RouteFocusSpec(
    val riddenSpans: List<RiddenSpan>,
    val extraSegments: List<RouteFocusSegment>,
    val alightStopId: String?,
    val directionHeadsign: String?
) {
    companion object {
        /** No leg drilled in — every non-directions route launch, and the state [RouteMapController.stop] returns to. */
        val None = RouteFocusSpec(emptyList(), emptyList(), null, null)

        fun of(request: ShowRouteRequest) = RouteFocusSpec(
            riddenSpans = request.riddenSpans,
            extraSegments = request.extraSegments,
            alightStopId = request.alightStopId,
            directionHeadsign = request.directionHeadsign
        )
    }
}

/**
 * Everything a drawn vehicle's colour is resolved from, as one value (#2149).
 *
 * The memo behind [RouteMapController.displayedRouteColor] is invalidated by comparing this whole, and
 * the resolution it guards reads its inputs from nowhere else — so "an input the guard forgot" is not a
 * mistake that can be made here. It used to be three separate fields compared by a hand-written `||`
 * chain, while the resolution took one input as a parameter and reached for another as a field: a
 * fourth input had two places to register and neither was enforced. A miss shows up as vehicles drawn
 * in a stale route colour until the next poll — a plausible-looking wrong answer, not a visible failure.
 *
 * The `response` a vehicle resolves against is not a field here because it is a *projection* of this
 * value: every vehicle is paired with the poll it came out of, which is either [poll] or one of
 * [extras] (an extra route's vehicle resolves its trip/route out of that route's references, #2043).
 *
 * Compared by value, not identity: [poll]/[extras] hold instances a landed poll replaces wholesale and
 * [palette] is set once per session, so value and identity agree for them, while [assignment] is a
 * small map the adjacency layer republishes — for it, identity would clear the memo more often than
 * the colours actually change.
 */
internal data class RouteColorInputs(
    val poll: VehiclePoll,
    val extras: Map<String, VehiclePoll>,
    val assignment: Map<RouteDirectionKey, Int>,
    val palette: RouteLinePalette
)

/**
 * Drives the home map while it is showing a single route. Given a route id (via [start]), it loads the
 * route's shape and metadata, draws the shape into [MapHost.renderState], hands the route's stops to the
 * shared [stopsController] (so they accumulate and focus like nearby stops), and continuously tracks the
 * route's real-time vehicles.
 *
 * [routeId] is the single source of truth for "the map is in route mode"; there is no separate mode flag.
 *
 * **Vehicles.** [RouteVehiclePoller] keeps a fresh trips-for-route response coming on a fixed cadence;
 * this controller turns each one into the drawn layer. It installs a per-frame sampler in
 * [MapHost.renderState] that the renderer pulls each display frame to dead-reckon every vehicle forward
 * to the frame's clock — so vehicles glide smoothly between the comparatively slow polls. The poll is
 * suspended and resumed with the map via [onPause]/[onResume].
 *
 * **Stop focus.** The "exact trips arriving at this stop" layer belongs to [StopFocusController]; this
 * controller merges its [StopFocusPresentation] with the route's own geometry in
 * [publishMapPresentation] and forwards the focus calls to it.
 *
 * **Display-free.** The controller deals in route data, not presentation: it publishes the raw
 * [loadedRoute] (loading, then the loaded [ObaRoute] and agency) and passes each route's raw GTFS color
 * straight into [RoutePolyline]. The view model turns those into the header text and the line-color
 * fallback, keeping all display policy out of here.
 *
 * [stop] leaves route mode: it cancels the load and the poll and clears the route's shape, vehicle layer,
 * and selection.
 */
class RouteMapController(
    private val host: MapHost,
    private val renderState: MapRenderState,
    private val routeRepository: RouteMapRepository,
    private val stopsController: StopsMapController,
    private val tripObservationRepository: TripObservationRepository,
    focusedTripRepository: FocusedTripRepository,
    private val scope: CoroutineScope
) {

    // The real-time vehicle poll for this session (jobs + retained responses). Every landed poll
    // republishes the vehicle set, which is also where a pending arrivals focus resolves.
    private val vehiclePoller = RouteVehiclePoller(
        tripObservationRepository = tripObservationRepository,
        scope = scope,
        onPoll = ::onVehiclePoll
    )

    // The focused-stop layer (the exact trips arriving at one stop). It owns its own load job and
    // geometry and calls back here to republish; [publishMapPresentation] reads its presentation.
    private val stopFocus = StopFocusController(
        focusedTripRepository = focusedTripRepository,
        startNearbyStops = stopsController::start,
        stopNearbyStops = stopsController::stop,
        scope = scope,
        isRouteActive = { isActive },
        onPresentationChanged = ::publishMapPresentation
    )

    // The raw route load, published while in route mode (the view model formats it into the display
    // header overlay). Loading on entry, the loaded route once it resolves, null outside route mode.
    private val _loadedRoute = MutableStateFlow<LoadedRoute?>(null)

    val loadedRoute: StateFlow<LoadedRoute?> = _loadedRoute.asStateFlow()

    /** The route currently shown (null when no route is active) — the single source of "route mode". */
    var routeId: String? = null
        private set

    // The stop the "show vehicles on map" launch anchored to (null for a whole-route launch). Set in
    // start(); resolved against the loaded route's direction grouping in onRouteLoaded to the
    // directionFilter below. Exposed so a re-tap that keeps the route but changes the direction anchor
    // re-enters (rather than just reframing).
    var directionStopId: String? = null
        private set

    // The trip-plan leg this session draws, as one value: which spans are ridden, which extra routes ride
    // along, where the rider gets off, which direction they board. [RouteFocusSpec.None] for every
    // non-directions route launch. Set from a [ShowRouteRequest] in start() and reframe() — one
    // assignment, so no redraw can key off a fresh span list against a stale alighting bound — and
    // restored to None in stop(). The four reads below are derived from it, never separately assigned.
    private var routeFocus: RouteFocusSpec = RouteFocusSpec.None

    // The board→alight ride of a trip-plan transit leg drilled into route focus, drawn thick over the full
    // route (empty for every non-directions route launch) — one span per route it is ridden on, so a
    // stay-aboard interline keeps each route's own colour and its cutover marks (see [RiddenSpan]).
    // Overlaid last in publishMapPresentation so it survives re-publishes (vehicle polls, direction changes).
    private val riddenSpans: List<RiddenSpan> get() = routeFocus.riddenSpans

    // The same ride as one path — where the rider boards and alights, what to frame, which stops and
    // vehicles are on it. Those questions are about the ride, not about which route each part of it is
    // ridden as, so they read this rather than the spans. Derived on read: the spans change rarely and
    // this is short.
    private val riddenPath: List<GeoPoint> get() = riddenSpans.riddenPath()

    // How this session's route colours are rendered: the basemap palette for an ordinary route launch, the
    // directions one for a leg drilled into from a trip plan, so the corridor keeps the leg's badge colour.
    // Set in start(); restored to the default in stop(), since a palette belongs to the view that asked for it.
    private var palette: RouteLinePalette = BASEMAP_ROUTE_LINE_PALETTE

    // The trip whose leg was drilled into, already reduced to its middle journey-context weight (#2048).
    // It draws above unused route geometry but below the ridden segment, keeping the rest of the journey
    // visible around the selected leg. Empty for every non-directions launch. Set in start(); cleared in
    // stop(), since the trip doesn't outlive the view that drilled into it.
    private var itineraryContext: List<RoutePolyline> = emptyList()

    // Additional route/directions shown with the primary route: either stay-aboard continuations (#2000)
    // or interchangeable routes (#2042). Their relationship controls vehicle filtering; both contribute
    // their relevant shape and stops. The maps/polls below are rebuilt as routes load.
    private val extraSegments: List<RouteFocusSegment> get() = routeFocus.extraSegments

    // The OBA stop where the rider leaves the whole ride (null outside leg focus, or when the OTP→OBA
    // resolution failed). The one bound the queue-driven selection needs: admission comes from the
    // boarding stop's arrivals, so this only has to answer "is this ride over yet".
    private val alightStopId: String? get() = routeFocus.alightStopId

    // The planned leg's headsign, used to pick the ridden direction group among the boarding stop's
    // arrival rows — the same pick the leg card's ETA strip makes.
    private val directionHeadsign: String? get() = routeFocus.directionHeadsign

    // Distinct other route ids to load and poll alongside the leader. A self-interline segment reuses the
    // leader route, so it is excluded (its shape/stops/vehicles already come from the leader).
    private val extraRouteIds: List<String>
        get() = extraSegments.map { it.routeId }.distinct().filterNot { it == routeId }

    // Loaded route maps for the extra segments' *other* routes, keyed by routeId (a self-interline
    // segment reuses the leader's [routeShape], so only distinct cross-route ids land here). (Re)built by
    // onRouteLoaded; read by the extra-segment shape/stop helpers.
    private var extraRouteMaps: Map<String, RouteMap> = emptyMap()

    // A restore/deep-link override for the initial direction (the user-selected direction persisted
    // across process death); when set and still valid it wins over the anchor stop's direction.
    private var initialDirectionOverride: Int? = null

    // The full route retained after load so [selectDirection] can re-filter stops to any direction
    // without a network reload. routeStops carries each stop's direction membership; directions is the
    // selectable set (id + headsign) the header offers.
    private var routeStops: List<RouteMapStop> = emptyList()

    private var routeStopRoutes: List<ObaRoute> = emptyList()

    private var directions: List<RouteMapDirection> = emptyList()

    // The loaded route's shape (whole-route merged set + each direction's own travel-ordered shape),
    // retained so a direction switch re-narrows the drawn line without a reload. Null until first load.
    private var routeShape: RouteMap? = null

    // The single-route presentation is retained even while a focused stop temporarily owns the
    // geometry/stop layer, so closing the stop or changing directions restores it without a reload.
    private var basePolylines: List<RoutePolyline> = emptyList()
    private var baseStopPresentation: RouteStopPresentation? = null

    // Which live vehicles belong to the focused ride (#2124): the boarding stop's arrivals select
    // them, this poll says where they are. Owns its own queue/admitted/continuation state and the
    // guards that keep the decision off the frame loop.
    private val rideSelection = RideSelectionController(
        lookupTripState = tripObservationRepository::lookupTripState,
        neighbourRouteOf = { tripObservationRepository.resolveNeighborTrip(it)?.routeId },
        scope = scope,
        onSelectionChanged = ::publishVehicleSet
    )

    private data class FocusedRouteLines(
        val routeId: String,
        val relationship: RouteFocusRelationship?,
        val polylines: List<RoutePolyline>
    )

    /** The synthesized hue per focused route/direction — see [StopFocusController.routeColors]. */
    val focusedRouteColors: StateFlow<Map<RouteDirectionKey, Int>> get() = stopFocus.routeColors

    /** The stop whose trips are drawn, or null when no stop is focused. */
    val focusedStopId: String? get() = stopFocus.focusedStopId

    // The direction shown now (null = whole route). Derived from [directionState] rather than stored
    // separately, so the stop filter and the vehicle filter can never disagree. Meaningful only once
    // resolved (it reads null during the Pending window, which no caller relies on).
    private val currentDirectionId: Int?
        get() = (directionState as? DirectionState.Resolved)?.directionId

    /** True while a stay-aboard interline is drilled in — the ride spans more than the leader leg (#2000);
     *  the focus then draws each extra segment's route/direction and shows the shared vehicle across all. */
    private val isInterlineComposite: Boolean
        get() = extraSegments.any { it.relationship == RouteFocusRelationship.STAY_ABOARD }

    /** The loaded [RouteMap] a piece of the ride naming route [id] is drawn from — an extra segment, or a
     *  ridden span ([spanColor]): the leader's own route when the ids match (a self-interline's other
     *  direction), else that route's separately-loaded map. Null until that load lands, which is what a
     *  caller reads as "not loaded yet". Takes an id a piece actually names: a piece naming *no* route has
     *  no map of its own to wait for, and answering the leader's for it would say a route it isn't ridden
     *  as is the one that colours it. */
    private fun loadedRouteMap(id: String): RouteMap? = if (id == routeId) routeShape else extraRouteMaps[id]

    private fun RouteFocusSegment.routeMap(): RouteMap? = loadedRouteMap(routeId)

    private fun RouteFocusSegment.directionId(): Int? = routeMap()?.let { route -> resolveRouteFocusSegmentDirection(this, route) }

    private val presentationDirectionId: Int?
        get() = when (val state = directionState) {
            DirectionState.Pending -> initialDirectionOverride
            is DirectionState.Resolved -> state.directionId
        }

    // What direction the vehicle layer should show — the single source of truth the per-frame sampler
    // reads (so it's a field, not a captured value; the sampler is installed in start() before the route
    // loads). [DirectionState.Pending] holds the layer back while a direction-anchored launch waits for
    // the (slower) stops-for-route load — otherwise every vehicle would flash for those seconds and then
    // the opposite direction would cull. [DirectionState.Resolved] carries the filter to apply (null =
    // both directions). One value, not a filter plus a "resolved yet?" boolean, so the two can't disagree.
    private var directionState: DirectionState = DirectionState.Resolved(null)

    /** Whether a route is currently shown (drives the home map's route-header focus bias). */
    val isActive: Boolean get() = routeId != null

    // Memo behind [displayedRouteColor], which the per-frame vehicle sampler calls for every vehicle.
    // Keyed by active trip id (unique across the polls in play); holds nulls, so absence means "not yet
    // resolved" rather than "no color". Invalidated wholesale by [refreshRouteColorMemo] when any input
    // is replaced — including extraPolls, since a composite's vehicles resolve out of several responses
    // and a per-call identity check would clear the memo on every alternation.
    private val routeColorMemo = HashMap<String, Int?>()

    /** What [routeColorMemo] holds colours for; see [RouteColorInputs] for why it is one value. */
    private var routeColorInputs: RouteColorInputs? = null

    // The one-shot route shape/stops/header load. The long-running periodic vehicle poll lives in
    // [vehiclePoller], suspended/resumed independently with the map lifecycle.
    private var routeJob: Job? = null

    // Drives everything a tap-selected vehicle shows: its exact shape + scheduled stops
    // ([showSelectionPresentation]), the trip overlay (the uncertainty band + fast-estimate marker,
    // [showSelectionOverlay]), and the route continuation (#1691, [showContinuation]).
    // A tap selects a vehicle (renderState.selectedVehicleTripId); collectLatest so a fast reselect
    // cancels an in-flight continuation resolution (a suspending network fetch) for the previous
    // selection instead of racing it — [showSelectionOverlay] has no suspension point, so it's unaffected
    // by collectLatest either way. Cancelled with the route session in [stop].
    private var selectionJob: Job? = null

    // The colour the last publish drew the selected trip's line in — the uncertainty band's contrast
    // basis, so the band contrasts the line that is on screen rather than the route's wire colour
    // (#1990). Recorded from the plan ([RouteMapPresentation.selectedTripColor]) rather than re-derived,
    // and falling back to the shown route's colour on a publish that drew no selected-trip line, since
    // the band then lies over the base route instead. Read per frame by [showSelectionOverlay]'s sampler,
    // so a republish that recolours the line recolours the band with it.
    private var selectedTripLineColor: Int = DEFAULT_ROUTE_LINE_COLOR

    private val _selectedTripBandColor = MutableStateFlow<Int?>(null)

    /**
     * The selected trip's uncertainty-band tint — the contrast of the line as drawn ([contrastingColor])
     * — or null when no vehicle is selected.
     *
     * Published because the band's colour is no longer only the map's: the arrivals row outlines the
     * focused trip's ETA pill in it (#1990), so the pill and the band it names are one object. Exposed as
     * the resolved tint rather than as the line colour it derives from, so there is exactly one place
     * that decides what contrasts what — a second caller applying [contrastingColor] itself would be free
     * to apply it to a different basis, which is the bug this issue started as.
     */
    val selectedTripBandColor: StateFlow<Int?> = _selectedTripBandColor.asStateFlow()

    // A pending arrivals ETA-pill focus: the trip id whose live vehicle should be fit together with the
    // originating stop once it appears. Held until the first vehicle set arrives, then resolved once by
    // [tryFocusVehicle]: if a marker for the trip is on the map the camera fits the vehicle↔stop box;
    // otherwise it's dropped silently — the vehicle isn't running that trip right now, so we neither move
    // the camera nor toast (#1992: a miss must not yank the map out to the whole-route extent, and the
    // arrivals ETA strip's "on the map" pin already tells the user which pills reframe on tap). Only ever
    // set from an ETA-pill tap ([ShowRouteRequest.focusTripId]).
    private var pendingFocus: String? = null

    /**
     * Show [request]'s route: load the route + header and start the vehicle poll. [zoomToRoute] frames
     * the shape once it loads (consumed once). [ShowRouteRequest.directionStopId], when non-null,
     * narrows the overlay to the single direction that serves that stop (the arrivals "show vehicles on
     * map" launch); null shows the whole route. [ShowRouteRequest.initialDirectionId] is an explicit
     * badge/restore override that wins over the anchor stop when it's still a valid direction.
     * [ShowRouteRequest.focusTripId], when non-null, asks the map to fit that trip's live vehicle
     * together with the originating stop once the vehicle appears; the on-load framing is deferred until
     * that decision so a successful vehicle+stop fit isn't first yanked out to the whole-route extent,
     * and a route frame is the fallback when no such vehicle exists.
     *
     * Takes the whole [request], like [reframe] — the two entries into a route session then honour one
     * field set by construction, rather than a parameter list here that has to be kept in step with the
     * request by hand (#1797, #2149).
     *
     * [itineraryContext] and [palette] are not part of the request: they are what the *view* the drill-in
     * came from hands down. [palette] renders this session's route colours, and is the one thing a
     * drill-in from the directions drawer changes about how the route is drawn: the leg the rider tapped
     * keeps the badge colour it had in the itinerary, so the whole corridor it belongs to is drawn in
     * that colour too rather than shifting to the basemap palette the moment the rider drills in.
     */
    fun start(
        request: ShowRouteRequest,
        zoomToRoute: Boolean,
        itineraryContext: List<RoutePolyline> = emptyList(),
        palette: RouteLinePalette
    ) {
        val routeId = request.routeId
        val focusTripId = request.focusTripId
        val directionStopId = request.directionStopId
        val initialDirectionId = request.initialDirectionId
        this.routeId = routeId
        this.directionStopId = directionStopId
        this.routeFocus = RouteFocusSpec.of(request)
        this.itineraryContext = itineraryContext
        this.palette = palette
        // (Re)built as the extra routes load in onRouteLoaded; cleared here so a prior focus's routes
        // can't leak into this one during the load window (the poller clears its own polls in start).
        this.extraRouteMaps = emptyMap()
        this.initialDirectionOverride = initialDirectionId
        this.pendingFocus = focusTripId
        rideSelection.start(focusTripId)
        // A whole-route launch has no direction to wait for, so its vehicles show as soon as they poll;
        // a direction-anchored launch (an anchor stop or a restored direction) stays Pending until the
        // route load resolves the filter (below).
        this.directionState =
            if (directionStopId == null && initialDirectionId == null) {
                DirectionState.Resolved(null)
            } else {
                DirectionState.Pending
            }
        // When stop focus survives an arrivals-row or route-badge tap, emphasize this route
        // immediately from the already-loaded adjacency geometry instead of waiting for route load.
        publishMapPresentation()
        _loadedRoute.value = LoadedRoute.Loading
        host.setProgress(true)
        // The live vehicle layer: the renderer pulls a frame from this sampler each display frame,
        // dead-reckoning every vehicle in the latest poll forward to the frame's clock (the icon
        // decision lives here, not in the producer). Yields nothing until the first poll lands. The
        // route-id filter set is built once here, not per frame, since it's constant for this route;
        // directionState is read live since it's resolved only once the route loads.
        renderState.setVehiclesSampler { nowMs -> sampleVehicles(WallTime(nowMs)) }
        // A tapped vehicle enters a focused state that shows its exact shape/stops, extrapolation band
        // + fast-estimate marker, and route continuation (#1691); react to the selection state here (a
        // tap sets it, a map/background tap clears it). Cancel any collector from a prior start() so a
        // re-entered session doesn't leak one that stop() can no longer reach.
        selectionJob?.cancel()
        selectionJob = scope.launch {
            renderState.selectedVehicleTripId.collectLatest { tripId ->
                showSelectionOverlay(tripId)
                showSelectionPresentation(tripId)
                // Re-stamp the vehicle discs: the newly selected one takes the band's colour and the
                // previously selected one gives it back (#1990). After the presentation, because that is
                // what settles [selectedTripLineColor] — the tint's basis — for this selection.
                publishVehicleSet()
                showContinuation(tripId)
            }
        }
        // Defer the on-load framing while a focus is pending: the focus decision (below, once the first
        // set arrives) either fits the vehicle+stop box or, failing that, frames the route itself.
        loadRoute(zoomToRoute && focusTripId == null)
        vehiclePoller.start(routeId, extraRouteIds)
    }

    /**
     * Ask to fit trip [tripId]'s live vehicle + the originating stop on the already-shown route (the ETA
     * pill tapped for an arrival whose route the map is already parked on). Resolves immediately against
     * the current vehicle set: when the vehicle isn't there, [tryFocusVehicle] drops the focus silently (no
     * camera move, no toast); a vehicle that only appears on a later poll still frames then, via
     * [publishVehicleSet]. We never yank the camera to the whole-route extent just because a specific
     * vehicle is missing (#1992).
     */
    fun requestFocus(tripId: String) {
        pendingFocus = tripId
        rideSelection.seed(tripId)
        tryFocusVehicle(currentVehicleLayer())
    }

    /**
     * Re-apply [request] to the route this controller already has open (MapViewModel.toRoute's reframe
     * branch — see its doc for the reframe-vs-reenter split). An instant, local-only reaction: must NOT
     * call [start] (no network reload, no vehicle-poll reset). [selectDirection]'s no-op guard is fine
     * here since reframing the already-shown direction is exactly the "not a real switch" case it exists
     * to skip. [requestFocus] falls through into the same FIT/DROP resolution ([tryFocusVehicle]) the
     * initial-load tail ([onRouteLoaded]) uses; absent a focus, [frameRoute] controls whether to reframe
     * now via [MapHost.frameRoute]. Undo restoration passes false before applying its captured viewport.
     * Takes the whole [request] rather than picking fields, so a new [ShowRouteRequest] field is at least
     * reachable here — and [start] now takes the whole request too, so the two no longer have to be kept
     * in step by hand (#1797, #2149).
     */
    fun reframe(request: ShowRouteRequest, frameRoute: Boolean = true) {
        // [request] carries the whole desired focus state, so a previous request's focus ends here —
        // and it must end *before* the segment refresh below, which republishes the vehicle set and so
        // reads both fields: against the new geometry, a stale exemption would admit the old leg's
        // vehicle and a stale pending focus could fly the camera to it. [requestFocus] in the tail
        // installs this request's own focus; a request carrying none leaves focus cleared, the same
        // unconditional set [start] does.
        pendingFocus = null
        rideSelection.clearSeed()
        // A reframe onto the same route+direction-stop can still carry a different (or empty) ride —
        // e.g. tapping a different leg of the same route. Re-emphasize the polyline and re-filter the
        // shown stops so a stale segment doesn't linger. start() sets this unconditionally; here we only
        // republish when it actually changes.
        //
        // One value, one comparison, one assignment ([RouteFocusSpec]): a redraw cannot key off fresh
        // riddenSpans while the alighting bound stays stale, and a field added to the ride cannot be
        // left out of the guard while the redraw below reads it (#2149). No reload here (reframe
        // contract), so extra routes aren't re-fetched — in practice this path is only hit for a
        // same-route+direction re-tap where changed extras are already loaded (a new cross-route id
        // requires a full re-enter).
        val focus = RouteFocusSpec.of(request)
        if (focus != routeFocus) {
            routeFocus = focus
            rideSelection.rideChanged()
            // Re-draw against the already-loaded routes so a stale segment doesn't linger; each show*
            // call publishes.
            showDirectionStops()
            showDirectionPolylines()
        }
        request.initialDirectionId?.let { selectDirection(it) }
        request.focusTripId?.let { requestFocus(it) } ?: if (frameRoute) frameRouteOrSegment() else Unit
    }

    // Resolve a pending focus against [layer] (the just-built vehicle set, threaded in so the poll path
    // doesn't re-run the extrapolation), once we actually have vehicles to check. With a marker for the
    // trip present, fit the vehicle together with the originating stop; absent, the pending focus is
    // dropped silently — no live vehicle runs this trip right now, so we neither move the camera nor toast
    // (#1992). A no-op until there's a resolved direction and a landed poll, so the decision waits for real
    // data rather than firing on the empty pre-poll set.
    private fun tryFocusVehicle(layer: MapVehicles?) {
        val focus = pendingFocus ?: return
        val marker = layer?.markers?.firstOrNull { it.activeTripId == focus }
        when (resolveVehicleFocus(directionState is DirectionState.Resolved, vehiclePoller.leaderPoll != null, marker != null)) {
            FocusResolution.WAIT -> Unit
            FocusResolution.FIT -> {
                pendingFocus = null
                // Fit the vehicle and its originating stop together (the stop is dropped only if it's
                // somehow not in the loaded route, leaving the vehicle framed alone at a comfortable zoom).
                host.frame(FramingIntent.Points(listOfNotNull(marker?.point, originatingStopPoint())))
                // Radiate a ping from the vehicle so it's clear which one this arrival is querying (#1764);
                // keyed by trip id so the ripple follows the marker as it settles onto its projected spot.
                renderState.emitPing(focus)
            }
            // No live vehicle runs this trip right now: drop the focus silently. We deliberately neither
            // frame the whole route (#1992: a pill tap must not yank the camera out to the route extent) nor
            // toast — the arrivals ETA strip's "on the map" pin already signals which pills reframe on tap.
            FocusResolution.DROP -> pendingFocus = null
        }
    }

    /** The originating stop's position ([directionStopId], looked up in the loaded route), or null. */
    private fun originatingStopPoint(): GeoPoint? {
        val stopId = directionStopId ?: return null
        return routeStops.firstOrNull { it.stop.id == stopId }?.stop?.location?.toGeoPoint()
    }

    // Builds the vehicle markers for the current poll + direction filter at [nowMs]. Returns null while
    // the direction is still [DirectionState.Pending] (a direction launch holds the layer back until the
    // route load resolves the filter, so it never flashes the opposite-direction vehicles) or before the
    // first poll lands. Shared by the per-frame motion sampler and the discrete [currentVehicleLayer] push;
    // [includeDataFixPoint] is set only on the latter, so the selected vehicle's most-recent-data dot is
    // projected onto the shape once per poll rather than every frame (the renderer reads it from the set).
    private fun sampleVehicles(now: WallTime, includeDataFixPoint: Boolean = false): MapVehicles? {
        val id = routeId ?: return null
        val resolved = directionState as? DirectionState.Resolved ?: return null
        val poll = vehiclePoller.leaderPoll ?: return null
        val extraPolls = vehiclePoller.extraPolls
        // Unlike [refreshRouteColorMemo] (bottom of this function), this must run before the
        // filterToFocusedRide calls below — they are what reads the selection.
        rideSelection.refresh(rideFocusOrNull(), id, poll, extraPolls)
        // A stay-aboard interline is one shared block vehicle running consecutive trips across the ridden
        // routes/directions, so don't direction-filter and merge each extra route's poll — the vehicle
        // must show through every phase, not vanish when it flips direction/route at a seam (#2000).
        val directionFilter = if (isInterlineComposite) null else resolved.directionId
        // Each vehicle is paired with the poll it came out of: an extra route's vehicle resolves its
        // trip/route out of *that* route's references, not the leader's, which is the only place its
        // color can be found (#2043).
        val leaderVehicles = extrapolatedVehicles(
            poll.response,
            setOf(id),
            now,
            directionFilter,
            includeDataFixPoint,
            tripObservationRepository::lookupTripState
        ).filterToFocusedRide().map { it to poll.response }
        val extraVehicles = if (extraSegments.isNotEmpty()) {
            extraPolls.flatMap { (extraRouteId, extraPoll) ->
                val segment = extraSegments.singleOrNull { it.routeId == extraRouteId }
                val extraDirectionFilter = if (segment?.relationship == RouteFocusRelationship.STAY_ABOARD) {
                    null
                } else {
                    segment?.directionId()
                }
                extrapolatedVehicles(
                    extraPoll.response,
                    setOf(extraRouteId),
                    now,
                    extraDirectionFilter,
                    includeDataFixPoint,
                    tripObservationRepository::lookupTripState
                ).filterToFocusedRide().map { it to extraPoll.response }
            }
        } else {
            emptyList()
        }
        val merged = leaderVehicles + extraVehicles
        // Extra route polls can briefly repeat the same active trip, especially across an interline seam.
        val vehicles = if (extraSegments.isNotEmpty()) {
            merged.distinctBy { (vehicle, _) -> vehicle.status.activeTripId }
        } else {
            merged
        }
        // Gathered once, then both the memo guard and the resolution it guards read this and nothing else
        // — that is what keeps a new colour input from being registered in one place and forgotten in the
        // other (#2149).
        val colors = RouteColorInputs(poll, extraPolls, stopFocus.routeColors.value, palette)
        refreshRouteColorMemo(colors)
        // Resolved once per sample, not per vehicle: at most one vehicle is selected, and the tint is an
        // HSV round trip we'd otherwise repeat for every marker on the 20 Hz sampler.
        val selectedId = renderState.selectedVehicleTripId.value
        val bandColor = selectedId?.let { contrastingColor(selectedTripLineColor) }
        return MapVehicles(
            markers = vehicles.map { (vehicle, source) -> vehicle.toMarker(source, colors, selectedId, bandColor) },
            response = poll.response
        )
    }

    // The vehicle set to push whenever it changes (a poll, a direction switch, the load resolving the
    // filter). Seeds marker positions at the device wall clock the per-frame sampler extrapolates
    // against (matching TripState.anchorLocalTimeMs); the next frame supersedes the seed either way. This
    // is the discrete set the renderer keeps, so it carries the shape-projected most-recent-data point.
    private fun currentVehicleLayer(): MapVehicles? = sampleVehicles(WallTime.now(), includeDataFixPoint = true)

    /**
     * During selected-leg focus, keep only the vehicles the ride selected (#2124) — a set-membership
     * test, because the selection itself was decided once per poll by [refreshRideSelection].
     *
     * Outside leg focus, and whenever the boarding stop's arrivals cannot answer for this ride, every
     * vehicle the poll reports is kept.
     */
    private fun List<ExtrapolatedVehicle>.filterToFocusedRide(): List<ExtrapolatedVehicle> = when (val visibility = rideSelection.visibility) {
        RideVisibility.All -> this
        is RideVisibility.Only -> filter { it.status.activeTripId in visibility.tripIds }
    }

    /** This session's ride, or null when no directions leg is focused (a plain route selects nothing). */
    private fun rideFocusOrNull(): RideFocus? = if (riddenSpans.isDrawableRide()) rideFocus() else null

    /** This session's ride, as the pure selection layer needs to see it. */
    private fun rideFocus(): RideFocus = RideFocus(
        leaderRouteId = routeId.orEmpty(),
        leaderHeadsign = directionHeadsign,
        segments = extraSegments,
        alightStopId = alightStopId
    )

    /**
     * The focused leg's boarding stop reported fresh arrivals: re-derive the ride's queue and re-select.
     *
     * The rows come from the arrivals session HOME hoists for this stop — the same session the leg
     * card's ETA strip renders — so the map and the strip are looking at one poll, and the map's vehicle
     * set does not depend on which itinerary rows are composed.
     */
    fun setRideArrivals(groups: List<RideRouteGroup>) {
        rideSelection.setArrivals(groups, rideFocus())
        // Order as in [onVehiclePoll]: the push re-selects, the approach then reads the new selection.
        publishVehicleSet()
        refreshApproachLines()
    }

    /**
     * Install (or clear) the selected vehicle's trip overlay. When a vehicle is selected ([tripId]
     * non-null), drive a per-frame sampler that extrapolates its trip and draws the uncertainty band +
     * fast-estimate marker, tinted to contrast the trip line as drawn ([selectedTripLineColor], #1990) —
     * a tint the band's own bounding markers are then filled with. The live vehicle disc is already the
     * best (median) estimate and route mode already shows the most-recent-data dot on selection, so the
     * overlay's own vehicle-point and data-age markers are suppressed — a focused vehicle gains only the
     * band + fast marker (#1752). Null (a deselect) clears the sampler.
     */
    private fun showSelectionOverlay(tripId: String?) {
        if (tripId == null) {
            renderState.setTripOverlaySampler(null)
            return
        }
        renderState.setTripOverlaySampler { nowMs ->
            // Derived per frame off [selectedTripLineColor], not captured once here: the line the band
            // lies over recolours under the selection (the route's own load landing, a stop-focus
            // session opening or closing reassigning its adjacency hue), and a colour snapshotted at
            // selection would stay contrasting the line that was drawn then (#1990).
            val bandColor = contrastingColor(selectedTripLineColor)
            val extrapolation =
                extrapolationFromState(tripObservationRepository.lookupTripState(tripId), WallTime(nowMs), includeMarkers = false)
            // A frame with no extrapolation at all (no shape, or no fix yet) still publishes the colour:
            // the most-recent-data dot is drawn from the selection rather than from the band, and takes
            // the band's colour whether or not there is a band to draw beside it.
            extrapolation?.toTripOverlay(bandColor) ?: TripOverlay(markerColorArgb = bandColor)
        }
    }

    /** Replace the direction line + stops with the selected vehicle's exact trip presentation. */
    private suspend fun showSelectionPresentation(tripId: String?) {
        if (tripId != null) {
            val state = tripObservationRepository.lookupTripState(tripId)
            val shapeId = state?.shapeId ?: vehiclePoller.leaderPoll?.response?.trip(tripId)?.shapeId
            // Warm both resources concurrently; supervisorScope joins them and keeps one failing
            // fetch from cancelling the other. selectedTripPresentation() re-reads the results.
            supervisorScope {
                launch { shapeId?.let { tripObservationRepository.ensureShape(tripId, it) } }
                launch { tripObservationRepository.ensureSchedule(tripId) }
            }
        }
        publishMapPresentation()
    }

    /**
     * The shown route's colour as this map draws it (also the band tint's basis): the agency's hue put
     * through this session's [palette], or the default when the route carries no usable colour. The palette
     * is what makes a drill-in land on the colour the itinerary's own line had, which is why it travels with
     * it; a ridden segment resolves its own colour per span ([spanColor]) and reaches this only for a span
     * that names no route at all.
     */
    private fun currentRouteColor(): Int = palette.lineColor((_loadedRoute.value as? LoadedRoute.Loaded)?.route?.color) ?: DEFAULT_ROUTE_LINE_COLOR

    /**
     * The colour one ridden span draws in: its own route's, through this session's [palette] — the same
     * resolution [directionPolylines] gives that route's full corridor, so a span and the line it is ridden
     * over can't disagree. A stay-aboard interline therefore changes colour at its cutover exactly as the
     * itinerary map and the drawer's badges do, instead of drawing two routes as one.
     *
     * Which colour it asks the palette for is [riddenSpanColorSource]: until the span's route has loaded
     * there is none to ask for, and the span draws in the one the plan gave it instead. The load
     * republishes, so a span reaches its own colour as soon as there is one.
     *
     * Null — nothing loaded and no planned colour, or a loaded route publishing nothing usable — is left
     * for the renderer to resolve ([RoutePolyline.resolvedColor]), which is what [directionPolylines] does
     * with its own null. That is the whole point of declining to stand in for a loaded route here: the
     * corridor beneath the span is drawn from that same route, so both must reach the fallback together or
     * the span is a line its own approach can't match.
     *
     * A span naming **no** route is the exception, and takes the other branch entirely: no load will ever
     * answer for it, so the plan has the only colour it will ever have — permanently, not for a window. Such
     * a span is either a ride leg whose route couldn't be resolved to an OBA id (dropped from
     * [extraSegments] too, so no corridor of its own is drawn to match) or the whole undivided ride the
     * drawer falls back to, which has no plan colour either and draws in the shown route's, as that ride
     * always has. Never the leader's loaded route: it is not ridden as that route, and borrowing its colour
     * would draw the mid-ride change of route away.
     *
     * The load is read as [LoadedSpanRoute] off the *map*, not off its route: a [RouteMap] whose response
     * carried no route in its references has still landed, and answers with the colour it has (none) — the
     * very colour [directionPolylines] draws its corridor in. Reading `route` for the landing instead would
     * make that span the one case where the two disagree. A load that *failed* is a different thing and
     * stays absent: nothing will republish for it, so the plan keeps that span for good.
     */
    private fun spanColor(span: RiddenSpan): Int? = when (val id = span.routeId) {
        null -> palette.lineColor(riddenSpanColorSource(span, loaded = null)) ?: currentRouteColor()
        else -> palette.lineColor(riddenSpanColorSource(span, loadedRouteMap(id)?.let { LoadedSpanRoute(it.route?.color) }))
    }

    /**
     * Resolve (or clear) the selected vehicle's route continuation (#1691); driven by [selectionJob]'s
     * collectLatest, so a slow fetch for a superseded selection is cancelled, not raced.
     */
    private suspend fun showContinuation(tripId: String?) {
        renderState.setRouteContinuation(resolveContinuation(tripId))
    }

    /**
     * Whether the selected vehicle's block continues onto a different route on its next scheduled trip,
     * and if so, the dashed line + arrow + badge to draw for it. Returns null at any step that isn't
     * (yet) available: no selection, no schedule, no neighbor trip, the neighbor is on the *same* route
     * (an ordinary same-route direction reversal — see [isRouteContinuation]), or the neighbor's shape
     * isn't resolvable.
     */
    private suspend fun resolveContinuation(tripId: String?): RouteContinuation? {
        val currentRouteId = routeId ?: return null
        val id = tripId ?: return null
        val state = tripObservationRepository.lookupTripState(id) ?: return null
        val anchor = state.polyline?.points?.lastOrNull() ?: return null
        val nextTripId = state.schedule?.nextTripId ?: return null
        val neighbor = tripObservationRepository.resolveNeighborTrip(nextTripId) ?: return null
        if (!isRouteContinuation(currentRouteId, neighbor.routeId)) return null
        val shapeId = neighbor.shapeId ?: return null
        val neighborShape = tripObservationRepository.ensureShape(nextTripId, shapeId) ?: return null
        return buildRouteContinuation(anchor, neighborShape, neighbor)
    }

    /**
     * [anchor] is the selected trip's own shape's last point (its last stop — not the vehicle's live
     * position, which hasn't started the next route yet). Draws a dashed line, in the neighbor route's
     * own color, from [anchor] through the neighbor shape's first [CONTINUATION_LINE_LENGTH_METERS],
     * terminated with an arrowhead oriented along the shape's travel direction there; the badge sits
     * halfway between [anchor] and the arrowhead.
     */
    private fun buildRouteContinuation(
        anchor: GeoPoint,
        neighborShape: Polyline,
        neighbor: TripRouteInfo
    ): RouteContinuation? {
        val tail = neighborShape.subPolyline(0.0, CONTINUATION_LINE_LENGTH_METERS) ?: return null
        val badgePoint = neighborShape.interpolate(CONTINUATION_LINE_LENGTH_METERS / 2) ?: return null
        val endSeg = neighborShape.segmentIndex(CONTINUATION_LINE_LENGTH_METERS)
        val arrowPoint = neighborShape.interpolate(CONTINUATION_LINE_LENGTH_METERS, endSeg) ?: return null
        val lineColor = palette.lineColor(neighbor.routeColor) ?: CONTINUATION_FALLBACK_LINE_COLOR
        return RouteContinuation(
            polyline = RoutePolyline(
                color = lineColor,
                points = listOf(anchor) + tail,
                widthProfile = CONTINUATION_LINE_WIDTH_PROFILE,
                dash = RouteLineDash.HINT
            ),
            arrow = ContinuationArrow(arrowPoint, neighborShape.bearingAt(endSeg)),
            badge = ContinuationBadge(
                badgePoint,
                neighbor.routeId,
                neighbor.routeShortName.orEmpty(),
                neighbor.directionId
            )
        )
    }

    /**
     * A landed poll: re-derive the drawn approach, then push the vehicle set.
     *
     * A poll is the only thing that can change the approach without also redrawing the route — it is what
     * lands the schedule/shape backfill the symbolic derivation reads, and what changes which trips are
     * active. Every other [publishVehicleSet] caller (load, direction switch, reframe) runs
     * [showDirectionPolylines], which derives the approach itself, so hanging this off the poll rather
     * than off the push keeps those paths from deriving it twice.
     */
    private fun onVehiclePoll() {
        // Push first: the vehicle set is what re-runs the ride selection, and the approach is drawn from
        // its result ([visibleRideTrips]). Deriving the approach first would clip this poll's lines from
        // the *previous* poll's selected trips.
        publishVehicleSet()
        refreshApproachLines()
    }

    /**
     * Re-derive the drawn approach as the poll's schedule/shape backfill lands. The symbolic inputs are
     * fetched asynchronously behind the poll that reports the vehicles, so the draw at load time usually
     * has only geometry to answer with; this upgrades it in place the moment a trip can answer instead of
     * leaving the ride's approach on the fallback until the next direction switch.
     *
     * Republishes only on an actual change, compared on the drawn lines themselves rather than on a poll
     * counter — most polls carry the same active trips and so redraw nothing.
     */
    private fun refreshApproachLines() {
        if (routeShape == null) return
        val leaderRouteId = routeId ?: return
        // Only a focused ride has an approach; a plain route draws its whole shape and never re-derives.
        if (!riddenSpans.isDrawableRide()) return
        val next = approachLines(focusedRouteLines(leaderRouteId), riddenPath)
        if (next != basePolylines) {
            basePolylines = next
            publishMapPresentation()
        }
    }

    /** Recompute + push the vehicle set (the renderer reconciles markers from it), then resolve any pending focus. */
    private fun publishVehicleSet() {
        val layer = currentVehicleLayer()
        renderState.setVehicleSet(layer)
        // Resolve a pending arrivals-row focus against the same set — fitting the vehicle+stop the moment
        // the vehicle first appears, whether that's the load-resolve republish or a subsequent poll.
        tryFocusVehicle(layer)
    }

    /** Leave single-route mode. A stop-focus layer, when present, remains visible. */
    fun stop() {
        routeJob?.cancel()
        vehiclePoller.stop()
        selectionJob?.cancel()
        routeJob = null
        selectionJob = null
        routeId = null
        directionStopId = null
        routeFocus = RouteFocusSpec.None
        itineraryContext = emptyList()
        palette = BASEMAP_ROUTE_LINE_PALETTE
        extraRouteMaps = emptyMap()
        initialDirectionOverride = null
        routeStops = emptyList()
        routeStopRoutes = emptyList()
        directions = emptyList()
        routeShape = null
        basePolylines = emptyList()
        baseStopPresentation = null
        directionState = DirectionState.Resolved(null)
        pendingFocus = null
        rideSelection.stop()
        publishMapPresentation()
        // setVehicleSet(null) is what clears the vehicle markers (the renderer reconciles the empty set);
        // it must be nulled together with the motion sampler, which only moves already-reconciled markers.
        renderState.setVehicleSet(null)
        renderState.setVehiclesSampler(null)
        renderState.setSelectedVehicle(null)
        // The selection collector is cancelled above, so clear its overlays explicitly. The band's
        // contrast basis goes back to the default with them — the publish above set it from the route
        // this session is leaving, and the next session's first publish is what should decide it.
        renderState.setTripOverlaySampler(null)
        selectedTripLineColor = DEFAULT_ROUTE_LINE_COLOR
        _selectedTripBandColor.value = null
        renderState.setRouteContinuation(null)
        _loadedRoute.value = null
    }

    /** Show the exact trips currently displayed for a stop — see [StopFocusController.focus]. */
    fun focusStop(
        stopId: String,
        trips: Set<FocusedTrip>,
        routes: List<ObaRoute>
    ) = stopFocus.focus(stopId, trips, routes)

    /** Clear focused-stop trips and reveal the base route (or ordinary nearby stops). */
    fun clearStopFocus() = stopFocus.clear()

    // Assemble the render plan from the current controller state (pure — see
    // [assembleRouteMapPresentation]) and forward each field to the render/stop layers. IO/retained
    // state is resolved into plain data first ([selectedTripRenderInput]); the mode-merging policy lives
    // in the pure function, so this stays a plumb-through.
    private fun publishMapPresentation() {
        // The shown route's colour, for the selected trip to fall back to. A ridden span resolves its own
        // ([spanColor]) and reaches the renderer's default the way every other route line does, so it reads
        // this only in the one case that has no route to resolve through — and asks for it there itself,
        // rather than every publish resolving it for a span that usually doesn't want it.
        val routeColor = currentRouteColor()
        // One snapshot of the focused-stop layer for the whole publish, so the plan can't read a
        // half-swapped focus (its geometry and stops resolve independently).
        val focus = stopFocus.presentation
        val plan = assembleRouteMapPresentation(
            isActive = isActive,
            emphasizedRoute = routeId?.let { RouteDirectionKey(it, presentationDirectionId) },
            basePolylines = basePolylines,
            baseStopPresentation = baseStopPresentation,
            focusTrips = focus.trips,
            focusedGeometry = focus.geometry,
            focusedStops = focus.stops,
            focusedRoutes = focus.routes,
            routeColors = focus.routeColors,
            selected = selectedTripRenderInput(routeColor),
            projectedFocusStops = focus::projectedStops
        )
        val recoloured = selectedTripLineColor != (plan.selectedTripColor ?: routeColor)
        selectedTripLineColor = plan.selectedTripColor ?: routeColor
        // Republished here rather than from the selection collector alone, because both of its inputs land
        // here: this publish is what settles the line's colour, and it is also what every selection change
        // runs through ([showSelectionPresentation]). A deselect passes through it too and clears this.
        _selectedTripBandColor.value = renderState.selectedVehicleTripId.value?.let { contrastingColor(selectedTripLineColor) }
        renderState.setRoutePolylines(
            // Over a highlighted leg segment: the route's approach to the boarding point first, the rider's
            // remaining itinerary at a middle weight, then the ridden span(s) cased on top (#2048, #2082).
            // The trip stays out of [framingPolylines] — drilling into a leg frames that leg, not the journey.
            polylines = routePolylinesWithSegment(
                plan.polylines,
                riddenSpans,
                colorOf = ::spanColor,
                itineraryContext = itineraryContext
            ),
            framingPolylines = plan.framingPolylines,
            routeModeScalesStopsWithZoom = plan.routeModeScalesStopsWithZoom
        )
        renderState.setRouteBadges(plan.badges)
        stopsController.setRoutePresentation(plan.stopPresentation)
        // The band's tint is derived from the colour just settled above, and the selected vehicle's disc
        // is drawn in it (#1990) — so a publish that recolours the trip's line has to re-stamp that disc
        // too, or the vehicle keeps a tint of the line it *used* to be drawn over. Gated on an actual
        // change (and on there being a selection to re-stamp), which is also what keeps this from
        // bouncing: the republish reaches [publishVehicleSet], never back here.
        if (recoloured && renderState.selectedVehicleTripId.value != null) publishVehicleSet()
    }

    /**
     * The selected vehicle's render inputs, or null when no vehicle is selected or its exact shape +
     * schedule haven't both resolved yet. Resolves the IO-backed pieces ([selectedTripPresentation],
     * the direction underlay, the projected stop presentation) so the pure assembler gets plain data.
     * [routeColor] is the shown route's drawn colour, passed in rather than re-resolved so one publish
     * puts the route through the colour policy once.
     */
    private fun selectedTripRenderInput(routeColor: Int): SelectedTripRenderInput? {
        val selected = selectedTripPresentation() ?: return null
        return SelectedTripRenderInput(
            presentation = selected,
            routeColorFallback = routeColor,
            // Deferred: the assembler resolves the underlay only when selectedTripStyle keeps it (stop
            // focus inactive) and the stop projection only for a drawable trip, so neither is computed on
            // the branches that skip it.
            directionUnderlay = { selectedDirectionUnderlay(selected.routeDirection.directionId) },
            stopPresentation = { selectedTripStopPresentation(selected) }
        )
    }

    /** Current selected-trip data, read fresh after selection's shape/schedule activation. */
    private fun selectedTripPresentation(): SelectedTripPresentation? {
        val tripId = renderState.selectedVehicleTripId.value ?: return null
        val currentRouteId = routeId ?: return null
        val state = tripObservationRepository.lookupTripState(tripId)
        // Keep the base route visible until both the exact shape and schedule resolve; a
        // half-loaded presentation would blank the base stops behind an empty selected trip.
        val polyline = state?.polyline ?: return null
        val schedule = state.schedule ?: return null
        // The marker is keyed by activeTripId, which resolves directly through the poll references.
        val activeTrip = vehiclePoller.leaderPoll?.response?.trip(tripId)
        return SelectedTripPresentation(
            points = polyline.points,
            stopIds = schedule.stopTimes.map { it.stopId },
            routeDirection = RouteDirectionKey(
                currentRouteId,
                activeTrip?.directionId ?: currentDirectionId
            )
        )
    }

    /**
     * The selected trip's own direction shape, thinned to sit beneath its exact trip line. In
     * whole-route mode [basePolylines] is the merged both-directions geometry, so drawing that as
     * the underlay would surface the opposite direction; resolve the direction shape instead.
     */
    private fun selectedDirectionUnderlay(directionId: Int?): List<RoutePolyline> = directionPolylines(directionId).asDeemphasizedRouteUnderlay()

    /** The selected trip's scheduled stops, projected onto its exact shape in schedule order. */
    private fun selectedTripStopPresentation(selected: SelectedTripPresentation): RouteStopPresentation {
        val stops = routeStops.stopsForTrip(selected.stopIds)
        return RouteStopPresentation(
            stops = stops,
            routes = routeStopRoutes,
            routeDirectionsByStopId = stops.associate { it.id to setOf(selected.routeDirection) },
            projectedPoints = projectStopsOntoPolylines(stops, listOf(selected.points))
        )
    }

    /** Restart the vehicle poll if a route is shown and the poll isn't running (the host's onResume). */
    fun onResume() {
        val id = routeId ?: return
        vehiclePoller.resume(id, extraRouteIds)
    }

    /** Stop the vehicle poll while the map is paused (the host's onPause). */
    fun onPause() = vehiclePoller.pause()

    private fun loadRoute(zoomToRoute: Boolean) {
        val id = routeId ?: return
        routeJob?.cancel()
        routeJob = scope.launch {
            // Fetch the leader and every *other* interline route (a self-interline segment reuses the
            // leader's map, so [extraRouteIds] excludes it) concurrently, so an N-route interline is one
            // round trip's wait rather than N+1 sequential ones (#2000). The repository narrows the leader's
            // stops (and the vehicle direction id) to directionStopId's direction when set; a whole-route
            // launch passes null and gets the full route back. A failed extra load is dropped — the leg
            // still frames its geometry.
            val (result, extras) = coroutineScope {
                val leader = async { routeRepository.getRoute(id, directionStopId) }
                val extraLoads = extraRouteIds.map { extraRouteId ->
                    async { routeRepository.getRoute(extraRouteId).getOrNull()?.let { extraRouteId to it } }
                }
                leader.await() to extraLoads.awaitAll().filterNotNull().toMap()
            }
            // Clear the spinner once the load resolves — before dispatching, so it's cleared on the
            // error path too (mirrors StopsMapController). A cancelled load never reaches here; the
            // view transition that cancelled it clears progress via leaveCurrentView().
            host.setProgress(false)
            onRouteLoaded(result, extras, zoomToRoute)
        }
    }

    private fun onRouteLoaded(result: Result<RouteMap?>, extraRoutes: Map<String, RouteMap>, zoomToRoute: Boolean) {
        // The route load has resolved (success or failure), so release the vehicle layer the sampler held
        // back in direction mode: fall back to Resolved(null) — vehicles unfiltered — and publish the set
        // so the renderer reconciles them, rather than leaving them hidden until the next poll. The
        // success path below narrows the filter and re-publishes.
        directionState = DirectionState.Resolved(null)
        val routeMap = result.getOrElse {
            host.emitEffect(MapEffect.ShowError.from(it))
            publishVehicleSet()
            return
        }
        // A null result (no endpoint) or an unresolved route reads as an error, like the legacy
        // null-response path.
        val route = routeMap?.route ?: run {
            host.emitEffect(MapEffect.ShowError(HttpURLConnection.HTTP_INTERNAL_ERROR))
            publishVehicleSet()
            return
        }
        // Retain the full route so a later direction switch re-filters locally (no reload). The shown
        // direction prefers a valid restore override (a persisted user switch), else the anchor stop's.
        routeStops = routeMap.stops
        // Retain the interline's other loaded routes so the extra-segment shape/stop/vehicle helpers can
        // read them (a self-interline segment reads the leader's routeShape instead) (#2000).
        extraRouteMaps = extraRoutes
        routeStopRoutes = routeMap.routes
        directions = routeMap.directions
        routeShape = routeMap
        val override = initialDirectionOverride
        val resolved =
            if (override != null && directions.any { it.directionId == override }) {
                override
            } else {
                routeMap.initialDirectionId
            }
        directionState = DirectionState.Resolved(resolved)
        _loadedRoute.value = LoadedRoute.Loaded(route, routeMap.agencyName, directions, resolved)
        showDirectionStops()
        showDirectionPolylines()
        // The load resolved the filter (Pending -> Resolved); push the now-visible vehicle set in case a
        // poll already landed while it was held back.
        publishVehicleSet()
        if (zoomToRoute) {
            frameRouteOrSegment()
        }
    }

    /**
     * Frame the highlighted board→alight ride ([riddenPath]) when one is set (a trip-plan leg
     * drilled in — zoom to just the ridden part), else the whole route's bounding box.
     */
    private fun frameRouteOrSegment() {
        val segment = riddenPath
        if (segment.isDrawableSegment()) host.frameItineraryLeg(segment) else host.frameRoute()
    }

    /**
     * Retain the ride's stops as the base route presentation: the leader route's stops narrowed to
     * [currentDirectionId], plus — for a stay-aboard interline (#2000) — each extra segment's route/
     * direction stops, all clipped to the ridden [riddenPath]. Each stop keeps its own
     * route+direction key so cross-route stops colour correctly.
     */
    private fun showDirectionStops() {
        val leaderRoute = routeId ?: return
        val stopsById = LinkedHashMap<String, ObaStop>()
        val keysById = HashMap<String, MutableSet<RouteDirectionKey>>()
        fun collect(stops: List<ObaStop>, key: RouteDirectionKey) {
            for (stop in stops) {
                stopsById.putIfAbsent(stop.id, stop)
                keysById.getOrPut(stop.id) { mutableSetOf() }.add(key)
            }
        }
        collect(
            routeStops.stopsForDirection(currentDirectionId).onSegment(riddenPath),
            RouteDirectionKey(leaderRoute, currentDirectionId)
        )
        // Every extra contributes its relevant route/direction's stops on the ridden segment. A
        // self-interline reads the leader route; a cross-route interline or alternative reads its own.
        for (segment in extraSegments) {
            val route = segment.routeMap() ?: continue
            val directionId = segment.directionId()
            collect(
                route.stops.stopsForDirection(directionId).onSegment(riddenPath),
                RouteDirectionKey(segment.routeId, directionId)
            )
        }
        val stops = stopsById.values.toList()
        baseStopPresentation = RouteStopPresentation(
            stops = stops,
            routes = (routeStopRoutes + extraRouteMaps.values.flatMap { it.routes }).distinctBy { it.id },
            routeDirectionsByStopId = keysById.mapValues { it.value.toSet() },
            // An interline ride spans multiple direction shapes, so project onto the ridden line (the
            // emphasized one every leg's stops sit on); a plain leg keeps projecting onto its own shape.
            // Span by span rather than onto the joined path, so no stop can land on the jump between two
            // spans — that join is a seam in the geometry, not somewhere a vehicle travels.
            projectedPoints = if (isInterlineComposite) {
                projectStopsOntoPolylines(stops, riddenSpans.map { it.points })
            } else {
                projectStopsOntoShape(stops)
            }
        )
        publishMapPresentation()
    }

    /**
     * The [stops]' positions projected onto the current direction's drawn shape, keyed by stop id. Each
     * stop is snapped to the nearest point across the direction's polyline segments; a stop that can't be
     * placed (no drawable shape) falls back to its own location, so it still shows (just off the line).
     */
    private fun projectStopsOntoShape(stops: List<ObaStop>): Map<String, GeoPoint> {
        val route = routeShape ?: return emptyMap()
        return projectStopsOntoPolylines(stops, route.shapeForDirection(currentDirectionId).polylines)
    }

    /**
     * The drawn lines for [directionId]'s shape: the selected direction's own travel-ordered shape
     * (with direction arrows), or the whole-route merged shape drawn undirected when [directionId] is
     * null. Puts the route's GTFS colour through this session's [palette]; the render layer picks the
     * fallback when the route has no usable colour of its own. Uses [focusedRoutePolyline], the same complete line presentation as a route selected
     * from focused-stop mode. shapeForDirection pairs the drawn shape with its directionality, so
     * arrows are stamped only when the direction's own travel-ordered shape is used — never on the
     * whole-route merged fallback (a direction that carried no shape on the wire).
     */
    private fun directionPolylines(directionId: Int?): List<RoutePolyline> = routeShape?.let { directionPolylines(it, directionId) }.orEmpty()

    /** [directionPolylines] against an explicit [route] map — used to draw an interline's extra routes. */
    private fun directionPolylines(route: RouteMap, directionId: Int?): List<RoutePolyline> {
        val shape = route.shapeForDirection(directionId)
        // One colour for the whole shape — a gapped route draws several polylines, all the same route.
        val color = palette.lineColor(route.route?.color)
        return shape.polylines.map { points ->
            focusedRoutePolyline(color = color, points = points, directional = shape.directional)
        }
    }

    /** The leader plus every extra segment's drawn lines — the routes a focused ride is drawn from. */
    private fun focusedRouteLines(leaderRouteId: String): List<FocusedRouteLines> {
        fun RouteFocusSegment.polylines(): List<RoutePolyline> = routeMap()?.let { directionPolylines(it, directionId()) }.orEmpty()
        return listOf(FocusedRouteLines(leaderRouteId, null, directionPolylines(currentDirectionId))) +
            extraSegments.map { FocusedRouteLines(it.routeId, it.relationship, it.polylines()) }
    }

    /** Where the rider boards [focusRouteId] — the anchor its approach is clipped at. */
    private fun boardStopIdFor(focusRouteId: String): String? = if (focusRouteId == routeId) {
        directionStopId
    } else {
        extraSegments.firstOrNull { it.routeId == focusRouteId }?.anchorStopId
    }

    /**
     * [approachPolylines] over the trips the ride actually selected on [focusRouteId], read from the
     * observation cache the poll's backfill warms. Empty whenever no selected trip can decide it (none
     * running, the backfill hasn't landed, no trip serves the boarding stop exactly once), which is the
     * caller's signal to fall back to the geometric approach.
     *
     * Drawn from the *selected* trips rather than every active trip on the route, so the line beneath a
     * focused ride shows where the rider's own options are coming from — the same set as the markers.
     */
    private fun scheduledApproaches(focusRouteId: String): List<List<GeoPoint>> = approachPolylines(rideSelection.visibleTrips.filter { it.routeId == focusRouteId }, boardStopIdFor(focusRouteId))

    /**
     * The upstream context drawn beneath a focused ride, per route: each active trip's own shape clipped
     * at the boarding stop (#2124's symbolic method, see [RouteApproach]), falling back to projecting the
     * boarding point onto the direction's geometry when no trip can answer. The fallback is the older
     * path unchanged — it just stopped being the primary one, because a direction's geometry can arrive
     * as disjoint pieces and the projection then clips the wrong one.
     *
     * The symbolic lines inherit the route's own drawn style by copying one of its polylines, so an
     * approach looks identical however it was derived.
     *
     * Takes the whole [riddenPath] rather than the boarding point and focus flag separately: both are
     * derived from it here, so they cannot be passed in disagreeing, and callers that already hold the
     * path don't rebuild it.
     */
    private fun approachLines(routeLines: List<FocusedRouteLines>, riddenPath: List<GeoPoint>): List<RoutePolyline> {
        val boardPoint = riddenPath.firstOrNull()
        val isLegFocus = riddenPath.isDrawableSegment()
        return routeLines
            // Interchangeable routes approach the same platform. Stay-aboard continuations begin after
            // boarding, so they are part of the selected ride rather than its upstream context.
            .filter { route ->
                !isLegFocus || route.relationship != RouteFocusRelationship.STAY_ABOARD
            }
            .groupBy { it.routeId }
            .mapValues { (focusRouteId, group) ->
                val scheduled = if (isLegFocus) scheduledApproaches(focusRouteId) else emptyList()
                // Resolved only where it is used: on the whole-route path [scheduled] is empty by
                // construction, and flattening every drawn polyline just to read the first is waste.
                val template = if (scheduled.isEmpty()) null else group.firstNotNullOfOrNull { it.polylines.firstOrNull() }
                if (template != null) {
                    scheduled.map { template.copy(points = it) }
                } else {
                    group.flatMap { it.polylines.upstreamTo(boardPoint) }
                }
            }
            .values
            .flatten()
    }

    /** Re-draw the base route for [currentDirectionId]. Called on load and on every direction switch. */
    private fun showDirectionPolylines() {
        if (routeShape == null) return
        val leaderRouteId = routeId ?: return
        // Built once and shared: [riddenPath] re-flattens the ride's spans on every read.
        val ridePath = riddenPath
        val isLegFocus = ridePath.isDrawableSegment()
        val focusedRouteLines = focusedRouteLines(leaderRouteId)
        val approaches = approachLines(focusedRouteLines, ridePath)
        // In leg focus, only the approaches to the boarding point remain as route context. Stay-aboard
        // continuations happen after boarding and are already represented by the selected leg overlay.
        basePolylines = approaches
        publishMapPresentation()
        publishVehicleSet()
    }

    /**
     * Switch the shown direction to [directionId] (one of [directions]' ids, or null for the whole
     * route) without a network reload: re-filter the stops and the vehicle set against the current poll,
     * republish both, and update the header. The renderer reconciles markers from the pushed vehicle set,
     * so the swap is immediate. Returns true if the direction actually changed (false = no route, or
     * already showing [directionId]), so the caller only persists a real switch.
     */
    fun selectDirection(directionId: Int?): Boolean {
        if (routeId == null || directionId == currentDirectionId) return false
        directionState = DirectionState.Resolved(directionId)
        // A vehicle selected in the old direction is now filtered out — drop the selection, any
        // still-pending vehicle+stop focus, and the pill trip's geometry-filter exemption (the switch
        // is a deliberate "show the other direction" action).
        renderState.setSelectedVehicle(null)
        pendingFocus = null
        rideSelection.clearSeed()
        showDirectionStops()
        showDirectionPolylines()
        // Re-filter the vehicle set against the same poll and push it — the renderer reconciles markers on
        // this emission, so the swap is immediate instead of waiting for the next poll.
        publishVehicleSet()
        (_loadedRoute.value as? LoadedRoute.Loaded)?.let {
            _loadedRoute.value = it.copy(currentDirectionId = directionId)
        }
        return true
    }

    /**
     * Builds the render [VehicleMarker] from a display-free [ExtrapolatedVehicle], carrying the
     * draw-time live-vs-scheduled flag through (the renderer picks its icon from it) and the color its
     * route is currently drawn with (the disc rides its own line's color, #2043).
     *
     * [response] is the poll this vehicle came out of — one of [colors]' own polls, since that pairing is
     * what [sampleVehicles] builds the vehicle list from.
     *
     * [bandColor] is the uncertainty band's tint, and reaches only the vehicle running [selectedTripId] —
     * the one vehicle that has a band — so its disc joins the band's colour rather than its route's
     * (#1990). Both are resolved once by [sampleVehicles] rather than per vehicle here.
     */
    private fun ExtrapolatedVehicle.toMarker(
        response: RouteTrips,
        colors: RouteColorInputs,
        selectedTripId: String?,
        bandColor: Int?
    ): VehicleMarker = VehicleMarker(
        // Vehicles are only built for trips with a resolvable active id, so this is non-null here.
        activeTripId = status.activeTripId.orEmpty(),
        point = point,
        isRealtime = isRealtime,
        status = status,
        fixTimeMs = fixTimeMs,
        bearing = bearing,
        dataFixPoint = dataFixPoint,
        routeColor = displayedRouteColor(colors, response, status.activeTripId),
        bandColor = bandColor?.takeIf { status.activeTripId == selectedTripId }
    )

    /**
     * The color the map is currently drawing [activeTripId]'s route with — see
     * [VehicleMarker.routeColor] for why that is deliberately not the route's GTFS color.
     *
     * The lookup mirrors what the polylines do (`routeColors[key] ?: mapRouteLineColorOrNull(gtfs)`,
     * see RouteViewGeometry), so a vehicle and its line resolve the same way and can't disagree.
     *
     * Both reference hops are nullable by contract (the poll carries whatever `references` it carries,
     * and a block-interlined vehicle can report a trip this route's poll never fetched), so an
     * unresolvable route yields null and the renderer falls back like any uncolored line.
     *
     * Memoized (see [refreshRouteColorMemo]) because this sits on the **per-frame** sampler —
     * [sampleVehicles] runs at 20 Hz on Google and the display rate on MapLibre, for every vehicle.
     * Uncached, each call re-materializes reference DTOs and re-parses the route's hex color string, so
     * a 15-vehicle route would churn thousands of throwaway allocations a second on the frame loop to
     * recompute a value that can only change when a new poll lands (every 10-30 s).
     */
    private fun displayedRouteColor(
        colors: RouteColorInputs,
        response: RouteTrips,
        activeTripId: String?
    ): Int? {
        val tripId = activeTripId ?: return null
        // Not getOrPut: a legitimately-null color must stay memoized rather than re-resolving forever.
        if (!routeColorMemo.containsKey(tripId)) {
            routeColorMemo[tripId] = resolveDisplayedRouteColor(colors, response, tripId)
        }
        return routeColorMemo[tripId]
    }

    /** Drops the [routeColorMemo] when anything it was derived from has been replaced — see [RouteColorInputs]. */
    private fun refreshRouteColorMemo(colors: RouteColorInputs) {
        if (colors != routeColorInputs) {
            routeColorInputs = colors
            routeColorMemo.clear()
        }
    }

    private fun resolveDisplayedRouteColor(
        colors: RouteColorInputs,
        response: RouteTrips,
        activeTripId: String
    ): Int? {
        val trip = response.trip(activeTripId) ?: return null
        val assigned = colors.assignment[RouteDirectionKey(trip.routeId, trip.directionId)]
        // Exactly what RouteViewGeometry does for the lines: an adjacency-assigned hue is already a
        // drawn color and passes through, while an agency's GTFS color goes through the map's route-line
        // policy (#2041) first. Skipping that policy here would put the disc on the raw agency color
        // while its line drew the normalized one — the disagreement this resolution exists to prevent.
        return assigned ?: colors.palette.lineColor(response.route(trip.routeId)?.color)
    }
}

/**
 * The outcome of resolving a pending "fit this trip's vehicle + its stop" request against the current
 * state: [WAIT] until there's real vehicle data to check, then [FIT] the vehicle+stop box if the vehicle
 * is on the map or [DROP] it if not. Kept as an enum + [resolveVehicleFocus] pure function so the (subtle)
 * "don't decide before a poll lands" rule is unit-tested without standing up the whole controller.
 */
internal enum class FocusResolution { WAIT, FIT, DROP }

/**
 * Decide a pending focus: hold ([WAIT]) until the route's direction has resolved *and* a vehicle poll
 * has landed (so there's a real set to check, not the empty pre-poll one); once there is, [FIT] when a
 * marker for the trip is present, else [DROP]. Pure so the decision table is testable in isolation.
 */
internal fun resolveVehicleFocus(
    directionResolved: Boolean,
    pollLanded: Boolean,
    markerPresent: Boolean
): FocusResolution = when {
    !directionResolved || !pollLanded -> FocusResolution.WAIT
    markerPresent -> FocusResolution.FIT
    else -> FocusResolution.DROP
}

/**
 * True when [neighborRouteId] genuinely differs from [currentRouteId] — the interlining continuation
 * test (#1691). Deliberately just a routeId compare, not "does the block continue" (true at nearly
 * every trip boundary — almost every trip has *a* next trip) and not a headsign/directionId change
 * (also true at an ordinary same-route direction reversal): verified live against KCM block
 * `1_8094451` that trip `1_664701340`'s `nextTripId` stays on route 45 with only a directionId flip
 * 0→1 (not interlining), while its `previousTripId`'s routeId genuinely differs (75 vs 45) and is.
 * [neighborRouteId] must come from the neighbor trip's own resolved record ([TripRouteInfo.routeId]),
 * never guessed from headsign/direction.
 */
internal fun isRouteContinuation(currentRouteId: String, neighborRouteId: String): Boolean = neighborRouteId.isNotEmpty() && neighborRouteId != currentRouteId

// How far (meters) the route-continuation line (#1691) is drawn into the next route's shape before it
// terminates in an arrowhead — a visual design choice (how much of the next route to preview), tunable
// to taste. Not a data heuristic: it doesn't affect the interlining decision itself (that's the exact
// routeId compare in [isRouteContinuation]) — only how much of an already-confirmed continuation is
// drawn, no different from the existing ROUTE_LINE_WIDTH_DP constant.
private const val CONTINUATION_LINE_LENGTH_METERS = 900.0

// Drawn narrower than the shown route's own line ([ROUTE_LINE_WIDTH_DP]) so a continuation reads as a
// preview/hint rather than as equally-weighted with the route the rider is actually looking at.
private const val CONTINUATION_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 0.7f
private val CONTINUATION_LINE_WIDTH_PROFILE = RouteLineWidthProfile(
    thicknessDp = CONTINUATION_LINE_WIDTH_DP,
    distantThicknessMultiplier = 1f
)

// Used only when the neighbor route carries no GTFS color to draw the continuation line in its own
// color with.
private const val CONTINUATION_FALLBACK_LINE_COLOR = 0xFF9E9E9E.toInt() // opaque gray

/**
 * What the vehicle layer should show while in route mode — the single value the per-frame sampler reads
 * to decide whether (and how) to draw vehicles, so a filter and a "resolved yet?" flag can't disagree.
 */
private sealed interface DirectionState {
    /** A direction-anchored launch is still waiting for the route load; the sampler draws nothing yet. */
    data object Pending : DirectionState

    /** The direction is known: keep only [directionId] (null = both directions / whole route). */
    data class Resolved(val directionId: Int?) : DirectionState
}

/**
 * The raw route-load state [RouteMapController] publishes (null when not in route mode); [MapViewModel]
 * formats it into the display [RouteHeader].
 */
sealed interface LoadedRoute {
    /** The route is still loading. */
    data object Loading : LoadedRoute

    /**
     * The route resolved: its [route] and serving [agencyName], the route's selectable [directions]
     * (id + headsign, for the header's direction menu), and the [currentDirectionId] shown now
     * (null = whole route).
     */
    data class Loaded(
        val route: ObaRoute,
        val agencyName: String?,
        val directions: List<RouteMapDirection>,
        val currentDirectionId: Int?
    ) : LoadedRoute
}
