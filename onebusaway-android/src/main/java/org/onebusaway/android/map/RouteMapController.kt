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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.onebusaway.android.extrapolation.ExtrapolatedVehicle
import org.onebusaway.android.extrapolation.extrapolatedVehicles
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.time.WallTime
import java.net.HttpURLConnection
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.RouteMapStop
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.MapVehicles
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

    /**
     * Show route [routeId]: load the route + header and start the vehicle poll. [zoomToRoute] frames
     * the shape once it loads (consumed once). [directionStopId], when non-null, narrows the overlay to
     * the single direction that serves that stop (the arrivals "show vehicles on map" launch); null
     * shows the whole route. [initialDirectionId] is a restore override (the user-selected direction
     * persisted across process death) that wins over the anchor stop when it's still a valid direction.
     */
    fun start(
        routeId: String,
        zoomToRoute: Boolean,
        directionStopId: String? = null,
        initialDirectionId: Int? = null,
    ) {
        this.routeId = routeId
        this.directionStopId = directionStopId
        this.initialDirectionOverride = initialDirectionId
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
        loadRoute(zoomToRoute)
        startVehiclePolling(0L)
    }

    // Builds the vehicle markers for the current poll + direction filter at [nowMs]. Returns null while
    // the direction is still [DirectionState.Pending] (a direction launch holds the layer back until the
    // route load resolves the filter, so it never flashes the opposite-direction vehicles) or before the
    // first poll lands. Shared by the per-frame motion sampler and the discrete [currentVehicleLayer] push.
    private fun sampleVehicles(now: WallTime): MapVehicles? {
        val id = routeId ?: return null
        val resolved = directionState as? DirectionState.Resolved ?: return null
        val poll = latestPoll ?: return null
        return MapVehicles(
            markers = extrapolatedVehicles(
                poll.response, setOf(id), now, resolved.directionId,
                tripObservationRepository::lookupTripState,
            ).map { it.toMarker() },
            response = poll.response,
        )
    }

    // The vehicle set to push whenever it changes (a poll, a direction switch, the load resolving the
    // filter). Seeds marker positions at the device wall clock the per-frame sampler extrapolates
    // against (matching TripState.anchorLocalTimeMs); the next frame supersedes the seed either way.
    private fun currentVehicleLayer(): MapVehicles? = sampleVehicles(WallTime.now())

    /** Recompute + push the vehicle set (the renderer reconciles markers from it). */
    private fun publishVehicleSet() {
        renderState.setVehicleSet(currentVehicleLayer())
    }

    /** Leave route mode: cancel the loads + poll and clear the route's shape, vehicle layer, and header. */
    fun stop() {
        routeJob?.cancel()
        vehicleJob?.cancel()
        routeJob = null
        vehicleJob = null
        routeId = null
        directionStopId = null
        initialDirectionOverride = null
        routeStops = emptyList()
        routeStopRoutes = emptyList()
        directions = emptyList()
        directionState = DirectionState.Resolved(null)
        latestPoll = null
        renderState.clearRoutePolylines()
        // setVehicleSet(null) is what clears the vehicle markers (the renderer reconciles the empty set);
        // it must be nulled together with the motion sampler, which only moves already-reconciled markers.
        renderState.setVehicleSet(null)
        renderState.setVehiclesSampler(null)
        renderState.setSelectedVehicle(null)
        _loadedRoute.value = null
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
            MapUtils.showMapError((it as? ObaApiException)?.code ?: ObaApi.OBA_IO_EXCEPTION)
            publishVehicleSet()
            return
        }
        // A null result (no endpoint) or an unresolved route reads as an error, like the legacy
        // null-response path.
        val route = routeMap?.route ?: run {
            MapUtils.showMapError(HttpURLConnection.HTTP_INTERNAL_ERROR)
            publishVehicleSet()
            return
        }
        // Retain the full route so a later direction switch re-filters locally (no reload). The shown
        // direction prefers a valid restore override (a persisted user switch), else the anchor stop's.
        routeStops = routeMap.stops
        routeStopRoutes = routeMap.routes
        directions = routeMap.directions
        val override = initialDirectionOverride
        val resolved =
            if (override != null && directions.any { it.directionId == override }) override
            else routeMap.initialDirectionId
        directionState = DirectionState.Resolved(resolved)
        _loadedRoute.value = LoadedRoute.Loaded(route, routeMap.agencyName, directions, resolved)
        // Pass the route's raw GTFS color through; the render layer picks the fallback when it's absent.
        // The shape is whole-route (never narrowed by direction), so a switch leaves it untouched.
        renderState.setRoutePolylines(
            routeMap.polylines.map { points -> RoutePolyline(route.color, points) }
        )
        showDirectionStops()
        // The load resolved the filter (Pending -> Resolved); push the now-visible vehicle set in case a
        // poll already landed while it was held back.
        publishVehicleSet()
        if (zoomToRoute) {
            host.frameRoute()
        }
    }

    /** Re-render the route's stops narrowed to [currentDirectionId], replacing the prior direction's. */
    private fun showDirectionStops() {
        // showStops accumulates while a route is active, so drop the prior direction's route stops
        // first — otherwise the map would show the union of both directions. (Keeps a focused stop.)
        stopsController.clearStops(false)
        stopsController.showStops(routeStops.stopsForDirection(currentDirectionId), routeStopRoutes)
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
        // A vehicle selected in the old direction is now filtered out — drop the selection.
        renderState.setSelectedVehicle(null)
        showDirectionStops()
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
        )
}

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
