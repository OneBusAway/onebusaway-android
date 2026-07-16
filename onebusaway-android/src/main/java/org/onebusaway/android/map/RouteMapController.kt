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

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.onebusaway.android.extrapolation.ExtrapolatedVehicle
import org.onebusaway.android.extrapolation.extrapolatedVehicles
import org.onebusaway.android.extrapolation.extrapolationFromState
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.time.WallTime
import java.net.HttpURLConnection
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.map.render.ContinuationArrow
import org.onebusaway.android.map.render.ContinuationBadge
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.FramingIntent
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.MapVehicles
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RouteContinuation
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.VehicleMarker

/**
 * Drives the home map while it is showing a single route. Given a route id (via [start]), it loads the
 * route's shape and metadata, draws the shape into [MapHost.renderState], hands the route's stops to the
 * shared [stopsController] (so they accumulate and focus like nearby stops), and continuously tracks the
 * route's real-time vehicles.
 *
 * [routeId] is the single source of truth for "the map is in route mode"; there is no separate mode flag.
 *
 * **Vehicles.** A background job polls trips-for-route on a fixed cadence and stashes each response. The
 * controller installs a per-frame sampler in [MapHost.renderState] that the renderer pulls each display
 * frame to dead-reckon every vehicle forward to the frame's clock — so vehicles glide smoothly between
 * the comparatively slow polls. The poll is suspended and resumed with the map via [onPause]/[onResume].
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
    private val focusedTripRepository: FocusedTripRepository,
    private val scope: CoroutineScope,
) {

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

    private data class StopFocusSession(
        val stopId: String,
        val trips: Set<FocusedTrip>,
    )

    private var stopFocusSession: StopFocusSession? = null
    private var stopFocusJob: Job? = null
    private var focusedGeometry = FocusedTripGeometry(emptyMap())
    private var focusedStops = FocusedTripStops(emptyMap(), emptyMap())
    private var focusedRoutes: List<ObaRoute> = emptyList()
    private var focusedRouteColors: Map<String, Int> = emptyMap()

    val focusedStopId: String? get() = stopFocusSession?.stopId

    // The direction shown now (null = whole route). Derived from [directionState] rather than stored
    // separately, so the stop filter and the vehicle filter can never disagree. Meaningful only once
    // resolved (it reads null during the Pending window, which no caller relies on).
    private val currentDirectionId: Int?
        get() = (directionState as? DirectionState.Resolved)?.directionId

    // What direction the vehicle layer should show — the single source of truth the per-frame sampler
    // reads (so it's a field, not a captured value; the sampler is installed in start() before the route
    // loads). [DirectionState.Pending] holds the layer back while a direction-anchored launch waits for
    // the (slower) stops-for-route load — otherwise every vehicle would flash for those seconds and then
    // the opposite direction would cull. [DirectionState.Resolved] carries the filter to apply (null =
    // both directions). One value, not a filter plus a "resolved yet?" boolean, so the two can't disagree.
    private var directionState: DirectionState = DirectionState.Resolved(null)

    /** Whether a route is currently shown (drives the home map's route-header focus bias). */
    val isActive: Boolean get() = routeId != null

    // The most recent trips-for-route poll and when it landed (nanos). The sampler reads the response to
    // dead-reckon every vehicle to the frame's clock; the load time lets a resume mid-period wait only
    // the remainder. Null until the first poll lands (the sampler then yields nothing to draw).
    private var latestPoll: VehiclePoll? = null

    // routeJob is the one-shot route shape/stops/header load; vehicleJob is the long-running periodic
    // vehicle poll, suspended/resumed independently with the map lifecycle (onPause cancels only it).
    private var routeJob: Job? = null

    private var vehicleJob: Job? = null

    // Drives everything a tap-selected vehicle shows: the trip overlay (the uncertainty band +
    // fast-estimate marker, [showSelectionOverlay]) and the route continuation (#1691, [showContinuation]).
    // A tap selects a vehicle (renderState.selectedVehicleTripId); collectLatest so a fast reselect
    // cancels an in-flight continuation resolution (a suspending network fetch) for the previous
    // selection instead of racing it — [showSelectionOverlay] has no suspension point, so it's unaffected
    // by collectLatest either way. Cancelled with the route session in [stop].
    private var selectionJob: Job? = null

    // A pending arrivals ETA-pill focus: fit trip [tripId]'s live vehicle together with the originating
    // stop once the vehicle appears. Held until the first vehicle set arrives, then resolved once by
    // [tryFocusVehicle]: if a marker for the trip is on the map the camera fits the vehicle↔stop box,
    // otherwise it's dropped (the vehicle isn't running that trip right now) — we raise the
    // [MapEffect.VehicleNotOnMap] toast so the tap isn't silently ignored, and, if [frameFallback], frame
    // the whole route instead (the initial framing the launch deferred pending this decision). A pending
    // focus only ever comes from the ETA-pill tap ([ShowRouteRequest.focusTripId]), so a DROP always maps
    // to that toast. Held as one value (like [DirectionState]) so the id and the fallback flag can't drift.
    private data class PendingFocus(val tripId: String, val frameFallback: Boolean)

    private var pendingFocus: PendingFocus? = null

    /**
     * Show route [routeId]: load the route + header and start the vehicle poll. [zoomToRoute] frames
     * the shape once it loads (consumed once). [directionStopId], when non-null, narrows the overlay to
     * the single direction that serves that stop (the arrivals "show vehicles on map" launch); null
     * shows the whole route. [initialDirectionId] is an explicit badge/restore override that wins over
     * the anchor stop when it's still a valid direction.
     * [focusTripId], when non-null, asks the map to fit that trip's live vehicle together with the
     * originating [directionStopId] once the vehicle appears; the on-load framing is deferred until that
     * decision so a successful vehicle+stop fit isn't first yanked out to the whole-route extent, and a
     * route frame is the fallback when no such vehicle exists.
     */
    fun start(
        routeId: String,
        zoomToRoute: Boolean,
        directionStopId: String? = null,
        initialDirectionId: Int? = null,
        focusTripId: String? = null,
    ) {
        this.routeId = routeId
        // When stop focus survives an arrivals-row or route-badge tap, emphasize this route
        // immediately from the already-loaded adjacency geometry instead of waiting for route load.
        publishMapPresentation()
        this.directionStopId = directionStopId
        this.initialDirectionOverride = initialDirectionId
        this.pendingFocus = focusTripId?.let { PendingFocus(it, frameFallback = zoomToRoute) }
        // A whole-route launch has no direction to wait for, so its vehicles show as soon as they poll;
        // a direction-anchored launch (an anchor stop or a restored direction) stays Pending until the
        // route load resolves the filter (below).
        this.directionState =
            if (directionStopId == null && initialDirectionId == null) DirectionState.Resolved(null)
            else DirectionState.Pending
        _loadedRoute.value = LoadedRoute.Loading
        host.setProgress(true)
        // The live vehicle layer: the renderer pulls a frame from this sampler each display frame,
        // dead-reckoning every vehicle in the latest poll forward to the frame's clock (the icon
        // decision lives here, not in the producer). Yields nothing until the first poll lands. The
        // route-id filter set is built once here, not per frame, since it's constant for this route;
        // directionState is read live since it's resolved only once the route loads.
        renderState.setVehiclesSampler { nowMs -> sampleVehicles(WallTime(nowMs)) }
        // A tapped vehicle enters a focused state that shows its extrapolation band + fast-estimate
        // marker, and resolves its route continuation (#1691); react to the selection state here (a tap
        // sets it, a map/background tap clears it). Cancel any collector from a prior start() so a
        // re-entered session doesn't leak one that stop() can no longer reach.
        selectionJob?.cancel()
        selectionJob = scope.launch {
            renderState.selectedVehicleTripId.collectLatest { tripId ->
                showSelectionOverlay(tripId)
                showContinuation(tripId)
            }
        }
        // Defer the on-load framing while a focus is pending: the focus decision (below, once the first
        // set arrives) either fits the vehicle+stop box or, failing that, frames the route itself.
        loadRoute(zoomToRoute && focusTripId == null)
        startVehiclePolling(0L)
    }

    /**
     * Ask to fit trip [tripId]'s live vehicle + the originating stop on the already-shown route (the ETA
     * pill tapped for an arrival whose route the map is already parked on). Resolves immediately against
     * the current vehicle set: when the vehicle isn't there, [tryFocusVehicle] raises the "vehicle isn't on
     * the map" toast; a vehicle that only appears on a later poll still frames then, via [publishVehicleSet].
     * The map already shows the route here, so there's no fallback frame ([PendingFocus.frameFallback] is
     * false) — we don't yank the camera to the whole-route extent just because a specific vehicle is missing.
     */
    fun requestFocus(tripId: String) {
        pendingFocus = PendingFocus(tripId, frameFallback = false)
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
     * reachable here — though [start]'s own parameter list still needs a matching update (#1797).
     */
    fun reframe(request: ShowRouteRequest, frameRoute: Boolean = true) {
        request.initialDirectionId?.let { selectDirection(it) }
        request.focusTripId?.let { requestFocus(it) } ?: if (frameRoute) host.frameRoute() else Unit
    }

    // Resolve a pending focus against [layer] (the just-built vehicle set, threaded in so the poll path
    // doesn't re-run the extrapolation), once we actually have vehicles to check. With a marker for the
    // trip present, fit the vehicle together with the originating stop; absent, the pending focus is
    // dropped — we raise the "vehicle isn't on the map" toast (the pill tap must not be silently ignored)
    // and, when [PendingFocus.frameFallback], frame the route as the deferred fallback. A no-op until
    // there's a resolved direction and a landed poll, so the decision waits for real data rather than
    // firing on the empty pre-poll set.
    private fun tryFocusVehicle(layer: MapVehicles?) {
        val focus = pendingFocus ?: return
        val marker = layer?.markers?.firstOrNull { it.activeTripId == focus.tripId }
        when (resolveVehicleFocus(directionState is DirectionState.Resolved, latestPoll != null, marker != null)) {
            FocusResolution.WAIT -> Unit
            FocusResolution.FIT -> {
                pendingFocus = null
                // Fit the vehicle and its originating stop together (the stop is dropped only if it's
                // somehow not in the loaded route, leaving the vehicle framed alone at a comfortable zoom).
                host.frame(FramingIntent.Points(listOfNotNull(marker?.point, originatingStopPoint())))
                // Radiate a ping from the vehicle so it's clear which one this arrival is querying (#1764);
                // keyed by trip id so the ripple follows the marker as it settles onto its projected spot.
                renderState.emitPing(focus.tripId)
            }
            FocusResolution.DROP -> {
                pendingFocus = null
                if (focus.frameFallback) host.frameRoute()
                host.emitEffect(MapEffect.VehicleNotOnMap)
            }
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
        val poll = latestPoll ?: return null
        return MapVehicles(
            markers = extrapolatedVehicles(
                poll.response, setOf(id), now, resolved.directionId,
                includeDataFixPoint, tripObservationRepository::lookupTripState,
            ).map { it.toMarker() },
            response = poll.response,
        )
    }

    // The vehicle set to push whenever it changes (a poll, a direction switch, the load resolving the
    // filter). Seeds marker positions at the device wall clock the per-frame sampler extrapolates
    // against (matching TripState.anchorLocalTimeMs); the next frame supersedes the seed either way. This
    // is the discrete set the renderer keeps, so it carries the shape-projected most-recent-data point.
    private fun currentVehicleLayer(): MapVehicles? = sampleVehicles(WallTime.now(), includeDataFixPoint = true)

    /**
     * Install (or clear) the selected vehicle's trip overlay. When a vehicle is selected ([tripId]
     * non-null), drive a per-frame sampler that extrapolates its trip and draws the uncertainty band +
     * fast-estimate marker, tinted to contrast the route line. The live vehicle disc is already the
     * best (median) estimate and route mode already shows the most-recent-data dot on selection, so the
     * overlay's own vehicle-point and data-age markers are suppressed — a focused vehicle gains only the
     * band + fast marker (#1752). Null (a deselect) clears the sampler.
     */
    private fun showSelectionOverlay(tripId: String?) {
        if (tripId == null) {
            renderState.setTripOverlaySampler(null)
            return
        }
        val bandColor = contrastingColor(currentRouteColor())
        renderState.setTripOverlaySampler { nowMs ->
            extrapolationFromState(tripObservationRepository.lookupTripState(tripId), WallTime(nowMs), includeMarkers = false)
                ?.toTripOverlay(bandColor)
        }
    }

    /** The shown route's GTFS color (the band tint's basis), or the default when it carries none. */
    private fun currentRouteColor(): Int =
        (_loadedRoute.value as? LoadedRoute.Loaded)?.route?.color ?: DEFAULT_ROUTE_LINE_COLOR

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
        val anchor = state.polyline?.points?.lastOrNull()?.toGeoPoint() ?: return null
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
        neighbor: TripRouteInfo,
    ): RouteContinuation? {
        val tail = neighborShape.subPolyline(0.0, CONTINUATION_LINE_LENGTH_METERS) ?: return null
        val badgePoint = neighborShape.interpolate(CONTINUATION_LINE_LENGTH_METERS / 2) ?: return null
        val endSeg = neighborShape.segmentIndex(CONTINUATION_LINE_LENGTH_METERS)
        val arrowPoint = neighborShape.interpolate(CONTINUATION_LINE_LENGTH_METERS, endSeg) ?: return null
        val lineColor = neighbor.routeColor ?: CONTINUATION_FALLBACK_LINE_COLOR
        return RouteContinuation(
            polyline = RoutePolyline(
                color = lineColor,
                points = listOf(anchor) + tail.map { it.toGeoPoint() },
                widthDp = CONTINUATION_LINE_WIDTH_DP,
                dashed = true,
            ),
            arrow = ContinuationArrow(arrowPoint.toGeoPoint(), neighborShape.bearingAt(endSeg)),
            badge = ContinuationBadge(
                badgePoint.toGeoPoint(),
                neighbor.routeId,
                neighbor.routeShortName.orEmpty(),
                neighbor.directionId,
            ),
        )
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
        vehicleJob?.cancel()
        selectionJob?.cancel()
        routeJob = null
        vehicleJob = null
        selectionJob = null
        routeId = null
        directionStopId = null
        initialDirectionOverride = null
        routeStops = emptyList()
        routeStopRoutes = emptyList()
        directions = emptyList()
        routeShape = null
        basePolylines = emptyList()
        baseStopPresentation = null
        directionState = DirectionState.Resolved(null)
        latestPoll = null
        pendingFocus = null
        publishMapPresentation()
        // setVehicleSet(null) is what clears the vehicle markers (the renderer reconciles the empty set);
        // it must be nulled together with the motion sampler, which only moves already-reconciled markers.
        renderState.setVehicleSet(null)
        renderState.setVehiclesSampler(null)
        renderState.setSelectedVehicle(null)
        // The selection collector is cancelled above, so clear its overlays explicitly.
        renderState.setTripOverlaySampler(null)
        renderState.setRouteContinuation(null)
        _loadedRoute.value = null
    }

    /**
     * Show the exact trips currently displayed for a stop. Shape and schedule loading are deliberately
     * independent: either result is useful on its own, and an empty trip set is a valid focused-stop-only view.
     */
    fun focusStop(
        stopId: String,
        trips: Set<FocusedTrip>,
        routes: List<ObaRoute>,
    ) {
        val next = StopFocusSession(stopId, LinkedHashSet(trips))
        focusedRoutes = routes
        if (stopFocusSession == next) {
            // Route wrappers may be recreated on every arrivals poll; refresh marker metadata without
            // restarting unchanged shape/schedule work.
            publishMapPresentation()
            return
        }
        stopFocusJob?.cancel()
        stopFocusSession = next
        focusedRouteColors = adjacencyRouteColors(next.trips.map(FocusedTrip::routeId))
        focusedGeometry = FocusedTripGeometry(emptyMap())
        focusedStops = FocusedTripStops(emptyMap(), emptyMap())
        stopsController.start()
        publishMapPresentation()
        stopFocusJob = scope.launch {
            supervisorScope {
                launch {
                    val geometry = runCatching { focusedTripRepository.getGeometry(next.trips) }
                        .getOrElse {
                            if (it is CancellationException) throw it
                            FocusedTripGeometry(emptyMap())
                        }
                    if (stopFocusSession == next) {
                        focusedGeometry = geometry
                        publishMapPresentation()
                    }
                }
                launch {
                    val stops = runCatching { focusedTripRepository.getStops(next.trips) }
                        .getOrElse {
                            if (it is CancellationException) throw it
                            FocusedTripStops(emptyMap(), emptyMap())
                        }
                    if (stopFocusSession == next) {
                        focusedStops = stops
                        publishMapPresentation()
                    }
                }
            }
        }
    }

    /** Clear focused-stop trips and reveal the base route (or ordinary nearby stops). */
    fun clearStopFocus() {
        if (stopFocusSession == null) return
        stopFocusSession = null
        stopFocusJob?.cancel()
        stopFocusJob = null
        focusedGeometry = FocusedTripGeometry(emptyMap())
        focusedStops = FocusedTripStops(emptyMap(), emptyMap())
        focusedRoutes = emptyList()
        focusedRouteColors = emptyMap()
        if (isActive) stopsController.stop() else stopsController.start()
        publishMapPresentation()
    }

    private fun publishMapPresentation() {
        val focus = stopFocusSession
        if (focus == null) {
            renderState.setRoutePolylines(basePolylines)
            renderState.setRouteBadges(emptyList())
            stopsController.setRoutePresentation(baseStopPresentation)
            return
        }
        val emphasizedRouteId = routeId
        val showBaseRoute = isActive && emphasizedRouteId != null &&
            focus.trips.none { it.routeId == emphasizedRouteId }
        val visibleStopIds = focusedStops.stopIdsForRoute(focus.trips, emphasizedRouteId)
        renderState.setRoutePolylines(
            polylines = focusedGeometry.toRoutePolylines(emphasizedRouteId, focusedRouteColors) +
                if (showBaseRoute) basePolylines else emptyList(),
            // Adjacency remains visible underneath route mode, but the active route's fully loaded
            // shape alone defines FramingIntent.Route. Using the displayed adjacency lines here would
            // fit the union of every route serving the focused stop.
            framingPolylines = if (isActive) basePolylines else emptyList(),
        )
        renderState.setRouteBadges(
            if (emphasizedRouteId == null) {
                focusedGeometry.toRouteBadges(focusedRoutes, focusedRouteColors)
            }
            else emptyList()
        )
        stopsController.setRoutePresentation(
            if (showBaseRoute) {
                baseStopPresentation
            } else {
                RouteStopPresentation(
                    stops = visibleStopIds.mapNotNull(focusedStops.stopsById::get),
                    routes = focusedRoutes,
                    routeStopIds = visibleStopIds,
                    projectedPoints = projectFocusedStops(focus.trips, focusedGeometry, focusedStops),
                )
            }
        )
    }

    /** Snap each exact scheduled stop to the closest successful shape of a trip that serves it. */
    private fun projectFocusedStops(
        trips: Set<FocusedTrip>,
        geometry: FocusedTripGeometry,
        stops: FocusedTripStops,
    ): Map<String, GeoPoint> {
        val tripById = trips.associateBy(FocusedTrip::tripId)
        val candidates = LinkedHashMap<String, MutableList<Polyline>>()
        for ((tripId, stopIds) in stops.stopIdsByTripId) {
            val shapeId = tripById[tripId]?.shapeId ?: continue
            val points = geometry.shapes[shapeId]?.points ?: continue
            if (points.size < 2) continue
            val polyline = Polyline(points.map(GeoPoint::toLocation))
            stopIds.forEach { candidates.getOrPut(it, ::mutableListOf).add(polyline) }
        }
        return buildMap {
            for ((stopId, shapes) in candidates) {
                val stop = stops.stopsById[stopId] ?: continue
                val location = stop.location
                val point = shapes.mapNotNull { it.nearestPoint(location.latitude, location.longitude) }
                    .minByOrNull(location::distanceTo)
                if (point != null) put(stopId, point.toGeoPoint())
            }
        }
    }

    /** Restart the vehicle poll if a route is shown and the poll isn't running (the host's onResume). */
    fun onResume() {
        if (routeId != null && vehicleJob?.isActive != true) {
            startVehiclePolling(nextVehicleDelay(latestPoll?.loadNanos ?: 0L, SystemClock.elapsedRealtimeNanos()))
        }
    }

    /** Stop the vehicle poll while the map is paused (the host's onPause). */
    fun onPause() {
        vehicleJob?.cancel()
    }

    private fun loadRoute(zoomToRoute: Boolean) {
        val id = routeId ?: return
        routeJob?.cancel()
        routeJob = scope.launch {
            // The repository narrows the stops (and the vehicle direction id) to directionStopId's
            // direction when set; a whole-route launch passes null and gets the full route back.
            val result = routeRepository.getRoute(id, directionStopId)
            // Clear the spinner once the load resolves — before dispatching, so it's cleared on the
            // error path too (mirrors StopsMapController). A cancelled load never reaches here; the
            // view transition that cancelled it clears progress via leaveCurrentView().
            host.setProgress(false)
            onRouteLoaded(result, zoomToRoute)
        }
    }

    private fun onRouteLoaded(result: Result<RouteMap?>, zoomToRoute: Boolean) {
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
        routeStopRoutes = routeMap.routes
        directions = routeMap.directions
        routeShape = routeMap
        val override = initialDirectionOverride
        val resolved =
            if (override != null && directions.any { it.directionId == override }) override
            else routeMap.initialDirectionId
        directionState = DirectionState.Resolved(resolved)
        _loadedRoute.value = LoadedRoute.Loaded(route, routeMap.agencyName, directions, resolved)
        showDirectionStops()
        showDirectionPolylines()
        // The load resolved the filter (Pending -> Resolved); push the now-visible vehicle set in case a
        // poll already landed while it was held back.
        publishVehicleSet()
        if (zoomToRoute) {
            host.frameRoute()
        }
    }

    /** Retain the route's stops narrowed to [currentDirectionId] as the base route presentation. */
    private fun showDirectionStops() {
        val stops = routeStops.stopsForDirection(currentDirectionId)
        baseStopPresentation = RouteStopPresentation(
            stops = stops,
            routes = routeStopRoutes,
            routeStopIds = stops.mapTo(LinkedHashSet(), ObaStop::id),
            projectedPoints = projectStopsOntoShape(stops),
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
        val shapes = route.shapeForDirection(currentDirectionId).polylines
            .filter { it.size >= 2 }
            .map { line -> Polyline(line.map(GeoPoint::toLocation)) }
        if (shapes.isEmpty()) return emptyMap()
        return stops.associate { stop ->
            val loc = stop.location
            val nearest = shapes
                .mapNotNull { it.nearestPoint(loc.latitude, loc.longitude) }
                .minByOrNull { loc.distanceTo(it) }
            stop.id to (nearest?.toGeoPoint() ?: loc.toGeoPoint())
        }
    }

    /**
     * Re-draw the route shape for [currentDirectionId]: the selected direction's own travel-ordered
     * shape (with direction arrows), or the whole-route merged shape drawn undirected when none is
     * selected. Passes the route's raw GTFS color through; the render layer picks the fallback when
     * it's absent. Drawn at the shared [ROUTE_LINE_WIDTH_DP] so the overview map's route reads the same
     * as the trip-focus map (#1752). Called on load and on every direction switch.
     */
    private fun showDirectionPolylines() {
        val route = routeShape ?: return
        // shapeForDirection pairs the drawn shape with its directionality, so arrows are stamped only
        // when the selected direction's own travel-ordered shape is used — never on the whole-route
        // merged fallback (a direction that carried no shape on the wire).
        val shape = route.shapeForDirection(currentDirectionId)
        basePolylines = shape.polylines.map { points ->
            RoutePolyline(
                route.route?.color,
                points,
                widthDp = ROUTE_LINE_WIDTH_DP,
                directional = shape.directional,
                transforms = ROUTE_VIEW_TRANSFORMS,
            )
        }
        publishMapPresentation()
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
        // A vehicle selected in the old direction is now filtered out — drop the selection, and drop any
        // still-pending vehicle+stop focus (the switch is a deliberate "show the other direction" action).
        renderState.setSelectedVehicle(null)
        pendingFocus = null
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
     * (Re)starts the real-time vehicle poll: after [initialDelayMs], reload vehicles every
     * [VEHICLE_REFRESH_PERIOD_MS] measured from each load's completion (so network time is excluded),
     * matching the legacy `postDelayed`-after-`onLoadFinished` cadence. The loop continues on a fixed
     * cadence even if a load fails.
     */
    private fun startVehiclePolling(initialDelayMs: Long) {
        vehicleJob?.cancel()
        val id = routeId ?: return
        vehicleJob = scope.launch {
            if (initialDelayMs > 0L) {
                delay(initialDelayMs)
            }
            // Keep the route's vehicles fresh while route mode is on screen: the repository polls
            // trips-for-route (backfilling each active trip's schedule + shape into the store) and
            // records each response. Each emission is a fresh poll — stash it for the motion sampler,
            // record its time so a resume mid-period waits only the remainder, and push the new vehicle
            // set so the renderer reconciles the marker set. The renderer's frame loop then pulls the
            // motion sampler to dead-reckon every vehicle between polls.
            tripObservationRepository.routeVehiclesStream(id, VEHICLE_REFRESH_PERIOD_MS).collect { response ->
                latestPoll = VehiclePoll(response, SystemClock.elapsedRealtimeNanos())
                publishVehicleSet()
            }
        }
    }

    /**
     * Builds the render [VehicleMarker] from a display-free [ExtrapolatedVehicle], carrying the
     * draw-time live-vs-scheduled flag through (the renderer picks its icon from it).
     */
    private fun ExtrapolatedVehicle.toMarker(): VehicleMarker =
        VehicleMarker(
            // Vehicles are only built for trips with a resolvable active id, so this is non-null here.
            activeTripId = status.activeTripId.orEmpty(),
            point = point,
            isRealtime = isRealtime,
            status = status,
            fixTimeMs = fixTimeMs,
            bearing = bearing,
            dataFixPoint = dataFixPoint,
        )
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
    markerPresent: Boolean,
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
internal fun isRouteContinuation(currentRouteId: String, neighborRouteId: String): Boolean =
    neighborRouteId.isNotEmpty() && neighborRouteId != currentRouteId

// How far (meters) the route-continuation line (#1691) is drawn into the next route's shape before it
// terminates in an arrowhead — a visual design choice (how much of the next route to preview), tunable
// to taste. Not a data heuristic: it doesn't affect the interlining decision itself (that's the exact
// routeId compare in [isRouteContinuation]) — only how much of an already-confirmed continuation is
// drawn, no different from the existing ROUTE_LINE_WIDTH_DP constant.
private const val CONTINUATION_LINE_LENGTH_METERS = 900.0

// Drawn narrower than the shown route's own line ([ROUTE_LINE_WIDTH_DP]) so a continuation reads as a
// preview/hint rather than as equally-weighted with the route the rider is actually looking at.
private const val CONTINUATION_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 0.7f

// Used only when the neighbor route carries no GTFS color to draw the continuation line in its own
// color with.
private const val CONTINUATION_FALLBACK_LINE_COLOR = 0xFF9E9E9E.toInt() // opaque gray

/** The latest trips-for-route [response] and the device clock ([loadNanos]) when it landed. */
private data class VehiclePoll(val response: RouteTrips, val loadNanos: Long)

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
     * (id + headsign, for the header's switch affordance), and the [currentDirectionId] shown now
     * (null = whole route).
     */
    data class Loaded(
        val route: ObaRoute,
        val agencyName: String?,
        val directions: List<RouteMapDirection>,
        val currentDirectionId: Int?,
    ) : LoadedRoute
}
