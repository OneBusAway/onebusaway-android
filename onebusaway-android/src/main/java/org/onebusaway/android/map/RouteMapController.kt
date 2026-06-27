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
import java.net.HttpURLConnection
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.models.ObaRoute
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
     * the shape once it loads (consumed once).
     */
    fun start(routeId: String, zoomToRoute: Boolean) {
        this.routeId = routeId
        _loadedRoute.value = LoadedRoute.Loading
        host.setProgress(true)
        // The live vehicle layer: the renderer pulls a frame from this sampler each display frame,
        // dead-reckoning every vehicle in the latest poll forward to the frame's clock (the icon
        // decision lives here, not in the producer). Yields nothing until the first poll lands. The
        // route-id filter set is built once here, not per frame, since it's constant for this route.
        val routeIds = setOf(routeId)
        renderState.setVehiclesSampler { nowMs ->
            latestPoll?.let { poll ->
                MapVehicles(
                    markers = extrapolatedVehicles(
                        poll.response, routeIds, nowMs, tripObservationRepository::lookupTripState
                    ).map { it.toMarker() },
                    response = poll.response,
                )
            }
        }
        loadRoute(zoomToRoute)
        startVehiclePolling(0L)
    }

    /** Leave route mode: cancel the loads + poll and clear the route's shape, vehicle layer, and header. */
    fun stop() {
        routeJob?.cancel()
        vehicleJob?.cancel()
        routeJob = null
        vehicleJob = null
        routeId = null
        latestPoll = null
        renderState.clearRoutePolylines()
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
            val result = routeRepository.getRoute(id)
            // Clear the spinner once the load resolves — before dispatching, so it's cleared on the
            // error path too (mirrors StopsMapController). A cancelled load never reaches here; the
            // view transition that cancelled it clears progress via leaveCurrentView().
            host.setProgress(false)
            onRouteLoaded(result, zoomToRoute)
        }
    }

    private fun onRouteLoaded(result: Result<RouteMap?>, zoomToRoute: Boolean) {
        val routeMap = result.getOrElse {
            MapUtils.showMapError((it as? ObaApiException)?.code ?: ObaApi.OBA_IO_EXCEPTION)
            return
        }
        // A null result (no endpoint) or an unresolved route reads as an error, like the legacy
        // null-response path.
        val route = routeMap?.route ?: run {
            MapUtils.showMapError(HttpURLConnection.HTTP_INTERNAL_ERROR)
            return
        }
        _loadedRoute.value = LoadedRoute.Loaded(route, routeMap.agencyName)
        // Pass the route's raw GTFS color through; the render layer picks the fallback when it's absent.
        renderState.setRoutePolylines(
            routeMap.polylines.map { points -> RoutePolyline(route.color, points) }
        )
        stopsController.showStops(routeMap.stops, routeMap.routes)
        if (zoomToRoute) {
            host.frameRoute()
        }
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
            // records each response. Each emission is a fresh poll — stash it for the sampler and record
            // its time so a resume mid-period waits only the remainder. The renderer's frame loop pulls
            // the sampler to dead-reckon every vehicle between polls.
            tripObservationRepository.routeVehiclesStream(id, VEHICLE_REFRESH_PERIOD_MS).collect { response ->
                latestPoll = VehiclePoll(response, SystemClock.elapsedRealtimeNanos())
            }
        }
    }

    /**
     * Builds the render [VehicleMarker] from a display-free [ExtrapolatedVehicle], deriving the
     * live-vs-scheduled flag from the source status (the renderer picks its icon from it).
     */
    private fun ExtrapolatedVehicle.toMarker(): VehicleMarker =
        VehicleMarker(
            // Vehicles are only built for trips with a resolvable active id, so this is non-null here.
            activeTripId = status.activeTripId.orEmpty(),
            point = point,
            isRealtime = status.isLocationRealtime,
            status = status,
            fixTimeMs = fixTimeMs,
            bearing = bearing,
        )
}

/** The latest trips-for-route [response] and the device clock ([loadNanos]) when it landed. */
private data class VehiclePoll(val response: RouteTrips, val loadNanos: Long)

/**
 * The raw route-load state [RouteMapController] publishes (null when not in route mode); [MapViewModel]
 * formats it into the display [RouteHeader].
 */
sealed interface LoadedRoute {
    /** The route is still loading. */
    data object Loading : LoadedRoute

    /** The route resolved: its [route] and serving [agencyName]. */
    data class Loaded(val route: ObaRoute, val agencyName: String?) : LoadedRoute
}
