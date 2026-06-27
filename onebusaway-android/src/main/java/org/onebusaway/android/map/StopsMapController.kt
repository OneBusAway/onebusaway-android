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

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.models.NearbyStops
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.primaryRouteType
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.RegionUtils

/**
 * The nearby-stops use case (the legacy `StopMapController`): loads + accumulates the bus stops in the
 * current viewport as the camera pans/zooms, and owns the stop **render focus** (the 1.5× icon + the
 * center-on-tap camera move). A cold driver over a [MapHost]: it reacts to [MapHost.camera] and writes
 * [MapHost.renderState], so it carries no map-SDK dependency. [start] launches the loader on [scope];
 * [cancel] stops it.
 *
 * Shared by every map that shows nearby stops — the home map, the trip-results / report / location-picker
 * screens — and reused by the route map (which feeds the route's own stops in via [showStops]). The
 * route-header camera bias on programmatic focus is supplied by [routeActive] (the home map passes a
 * route-mode predicate; standalone stop maps leave it false).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StopsMapController(
    private val host: MapHost,
    private val mapDataSource: MapDataSource,
    private val regionRepo: RegionRepository,
    private val locationRepository: LocationRepository,
    private val scope: CoroutineScope,
    private val routeActive: () -> Boolean = { false },
) {

    private val renderState get() = host.renderState

    // Stop accumulation across pans (capped, keeping the focused stop) + the routes cache used to
    // resolve a stop's icon route type and to report a stop's routes to focus listeners.
    private val stopAccum = LinkedHashMap<String, StopMarker>()

    private val cachedRoutes = HashMap<String, ObaRoute>()

    // routeId -> ObaRoute.TYPE_*, maintained alongside cachedRoutes so toStopMarker doesn't rebuild
    // the lookup on every pan.
    private val routeTypeById = HashMap<String, Int>()

    private var loadJob: Job? = null

    /** (Re)start the viewport stop loader (the old StopMapController's camera watch). */
    fun start() {
        loadJob?.cancel()
        loadJob = launchLoader()
    }

    /** Stop the viewport loader (its accumulated stops + focus are left intact for the next [start]). */
    fun stop() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun launchLoader(): Job = scope.launch {
        // The "is the last load still good for this viewport?" state (the old StopsResponse): the
        // camera the last completed load was made at + whether it had a response + its limit flag.
        var lastLoad: CameraSnapshot? = null
        var lastHadResponse = false
        var lastLimitExceeded = false

        host.camera
            .filterNotNull()
            .debounce(STOP_LOAD_DEBOUNCE_MS)
            .filterNot { next -> stopRequestFulfilled(lastLoad, lastHadResponse, lastLimitExceeded, next) }
            .flatMapLatest { snapshot ->
                // flatMapLatest cancels an in-flight load when a newer viewport arrives, matching the
                // controller's `loadJob?.cancel()`; a cancelled load leaves lastLoad untouched.
                flow {
                    host.setProgress(true)
                    val result = mapDataSource
                        .nearbyStops(snapshot.center.latitude, snapshot.center.longitude, snapshot.latSpan, snapshot.lonSpan)
                    // Only a usable load updates the fulfillment gate: a success — OK stops, or a null
                    // no-op (e.g. no stops endpoint, which intentionally fulfills future same-center
                    // viewports). A failure (error code / transport) showed no stops, so leave the gate
                    // untouched — like a cancelled load — otherwise stopRequestFulfilled would treat this
                    // viewport as already satisfied and short-circuit the retry.
                    result.onSuccess { nearby ->
                        lastLoad = snapshot
                        lastHadResponse = nearby != null
                        lastLimitExceeded = nearby?.limitExceeded ?: false
                    }
                    emit(result)
                }
            }
            .collect { result ->
                host.setProgress(false)
                onStopsLoaded(result)
            }
    }

    private fun onStopsLoaded(result: Result<NearbyStops?>) {
        val nearby = result.getOrElse {
            MapUtils.showMapError((it as? ObaApiException)?.code ?: ObaApi.OBA_IO_EXCEPTION)
            return
        }
        if (nearby == null) {
            // No endpoint yet (or a null no-op); do nothing (#615).
            return
        }
        if (nearby.outOfRange) {
            notifyOutOfRange()
            return
        }

        // Workaround for https://github.com/OneBusAway/onebusaway-application-modules/issues/59 where
        // the outOfRange element is false even if the location was out of range. We also make sure the
        // list of stops is empty, otherwise we'd screen out valid responses.
        val myLocation = locationRepository.location.value
        val region = regionRepo.region.value
        if (myLocation != null && region != null) {
            var inRegion = true // Assume user is in region unless we detect otherwise.
            try {
                inRegion = RegionUtils.isLocationWithinRegion(myLocation, region)
            } catch (e: IllegalArgumentException) {
                // Issue #69 - some devices are providing invalid lat/long coordinates.
                Log.e(
                    TAG, "Invalid latitude or longitude - lat = " + myLocation.latitude +
                            ", long = " + myLocation.longitude
                )
            }
            if (!inRegion && nearby.stops.isEmpty()) {
                Log.d(TAG, "Device location is outside region range, notifying...")
                notifyOutOfRange()
                return
            }
        }

        showStops(nearby.stops, nearby.routes)
    }

    private fun notifyOutOfRange() {
        host.emitEffect(MapEffect.OutOfRange)
    }

    // ----- Focus -----
    // This owns only the *render* focus (the 1.5x icon, via renderState.focusedStopId) and the camera
    // move. The home-side focus (the arrivals sheet + analytics) is driven directly by the owner's tap
    // callback, so a re-tap of the same stop isn't swallowed by state-dedup.

    /** A stop marker was tapped: render-focus it + center on it (the old GoogleMapHost.onStopClick). */
    fun onStopTapped(stop: ObaStop) {
        setFocusedStopId(stop.id)
        val loc = stop.location
        host.dispatchCamera(CameraCommand.CenterOnStopTap(loc.latitude, loc.longitude))
    }

    /** A tap away from any marker clears the stop render focus (the old onMapClick). */
    fun clearStopFocus() {
        setFocusedStopId(null)
    }

    /**
     * Programmatic focus for a restored/deep-linked stop once its arrivals load (driven by
     * `MapDirective.FocusStop`): ensure the stop is on the map + render-focused, and center on it
     * (route-header bias only when [routeActive] and the sheet settled expanded).
     */
    fun focusStop(stop: ObaStop, routes: List<ObaRoute>?, overlayExpanded: Boolean) {
        val loc = stop.location
        host.dispatchCamera(
            CameraCommand.Recenter(
                loc.latitude, loc.longitude,
                animate = false,
                applyRouteBias = routeActive() && overlayExpanded,
            )
        )
        setFocusStop(stop, routes)
    }

    /** Clear the render focus (back-press from a peeking sheet; a `MapDirective.ClearFocus`). */
    fun clearFocus() {
        setFocusStop(null, null)
    }

    // ----- Stops -----

    fun showStops(stops: List<ObaStop>, routes: List<ObaRoute>) {
        cacheRoutes(routes)
        capStopAccumulation(stopAccum, renderState.snapshot.value.focusedStopId, FUZZY_MAX_STOP_COUNT)
        for (stop in stops) {
            if (!stopAccum.containsKey(stop.id)) {
                stopAccum[stop.id] = toStopMarker(stop)
            }
        }
        renderState.setStops(ArrayList(stopAccum.values))
    }

    /** Clears accumulated stops; keeps the focused one unless [clearFocusedStop]. */
    fun clearStops(clearFocusedStop: Boolean) {
        if (clearFocusedStop) {
            stopAccum.clear()
            renderState.setFocusedStopId(null)
        } else {
            retainOnlyFocusedStop(stopAccum, renderState.snapshot.value.focusedStopId)
        }
        renderState.setStops(ArrayList(stopAccum.values))
    }

    /** Programmatic focus (intent/rotation): ensures the stop is on the map, then focuses it. */
    fun setFocusStop(stop: ObaStop?, routes: List<ObaRoute>?) {
        if (stop == null) {
            renderState.setFocusedStopId(null)
            return
        }
        if (!stopAccum.containsKey(stop.id)) {
            routes?.let { cacheRoutes(it) }
            stopAccum[stop.id] = toStopMarker(stop)
            renderState.setStops(ArrayList(stopAccum.values))
        }
        renderState.setFocusedStopId(stop.id)
    }

    fun setFocusedStopId(stopId: String?) = renderState.setFocusedStopId(stopId)

    /** A snapshot copy of the cached routes, for reporting a stop's routes to focus listeners. */
    fun cachedRoutes(): HashMap<String, ObaRoute> = HashMap(cachedRoutes)

    /** Adds routes to the caches that a stop tap reports + the icon route-type lookup. */
    private fun cacheRoutes(routes: Iterable<ObaRoute>) {
        for (route in routes) {
            cachedRoutes[route.id] = route
            routeTypeById[route.id] = route.type
        }
    }

    private fun toStopMarker(stop: ObaStop): StopMarker {
        val routeType = primaryRouteType(stop.routeIds, routeTypeById)
        // ObaStop.getDirection() is "N".."NW" or the literal "null" string for no direction.
        val direction = stop.direction ?: "null"
        return StopMarker(stop.id, stop.location.toGeoPoint(), direction, routeType, stop)
    }

    companion object {
        private const val TAG = "StopsMapController"

        private const val FUZZY_MAX_STOP_COUNT = 200
    }
}
