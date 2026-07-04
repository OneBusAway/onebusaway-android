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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.map.bike.BikeStationsRepository
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.LayerUtils
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.getRouteDescription
import org.onebusaway.android.util.getRouteDisplayName

/**
 * The route-mode header content (the old `R.id.route_info` overlay): the route's short/long name +
 * agency, or a loading state while the route loads. Published while a route is shown and rendered
 * as a Compose overlay by the home screen. Null when not in route mode. [directions] +
 * [currentDirectionId] drive the header's switch-direction affordance (empty/size-1 = no switch).
 */
data class RouteHeader(
    val loading: Boolean,
    val shortName: String,
    val longName: String,
    val agency: String,
    val directions: List<RouteMapDirection> = emptyList(),
    val currentDirectionId: Int? = null,
)

/**
 * The **home map** view model: the coordinator that composes the shared [MapHost] (the flavor-neutral
 * map surface — render state, camera, padding, my-location/region framing) with the use-case
 * controllers the home map needs — [StopsMapController] (nearby stops), [RouteMapController] (a route +
 * its vehicles), [BikeLayerController] (the bikeshare overlay), and [TripFocusController] (the
 * speed-estimation trip map, which reuses this host). [showNearbyStops] / [showRoute] start the
 * matching controller (the route controller is the single source of truth for whether a route is
 * shown — there is no separate "mode" state); the controllers react to the live camera
 * ([MapHost.onCameraIdle]) on [viewModelScope], so there is no imperative host — a flavor adapter only
 * binds to the host.
 *
 * Other map screens have their own slim view models over the same building blocks — `StopsMapViewModel`
 * (the report + location-picker maps) and `DirectionsMapViewModel` (the trip-plan results map) — so
 * they pull in only the controllers they use rather than this whole coordinator.
 *
 * Scoped to the hosting Activity (obtained via `by viewModels()`), so the home screen + the trip-focus
 * destination share one model and the rendered state survives a configuration change. Its data
 * collaborators are constructor-injected — Hilt provides the repositories, the
 * [RegionRepository]/[LocationRepository] singletons, and the `@ApplicationContext` in production;
 * tests construct it directly with fakes (the standard pattern here). Home's outbound interactions
 * (show-route / focus / recenter / bottom-padding) arrive as direct method calls from `MapFeature`,
 * which bridges them from [org.onebusaway.android.ui.home.HomeViewModel.mapDirectives] — so neither
 * view model references the other.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    // Persists the map's mode + camera across process death (Hilt-supplied). On a fresh launch it's
    // pre-seeded with the host Activity's intent extras (the MapParams.* keys), so it subsumes both the
    // deep-link intent read and the Bundle restore the host used to do.
    private val savedStateHandle: SavedStateHandle,
    private val mapDataSource: MapDataSource,
    private val routeRepository: RouteMapRepository,
    private val bikeStationsRepository: BikeStationsRepository,
    private val regionRepo: RegionRepository,
    private val locationRepository: LocationRepository,
    private val prefsRepository: PreferencesRepository,
    private val tripObservationRepository: TripObservationRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // The flavor-neutral map surface: render state + camera + padding + generic markers +
    // my-location / region framing. The use-case loaders below drive *what* the map shows by writing
    // its [MapHost.renderState]; the flavor adapter binds to the host. Constructed on viewModelScope so
    // the host's region collector shares this view model's lifetime.
    private val mapHost = MapHost(
        scope = viewModelScope,
        savedStateHandle = savedStateHandle,
        regionRepo = regionRepo,
        locationRepository = locationRepository,
        prefsRepository = prefsRepository,
        context = context,
    )

    /** The shared map surface the flavor adapter binds to (render state + camera + my-location). */
    val host: MapHost get() = mapHost

    /** The shared, flavor-neutral render state the adapter draws and the loaders write. */
    val renderState: MapRenderState get() = mapHost.renderState

    // The nearby-stops use case (viewport stop loader + accumulation + render focus). Drives the host's
    // renderState; the route loader feeds the route's own stops in via showStops. The route-header focus
    // bias is gated on this map being in route mode.
    private val stopsController = StopsMapController(
        host = mapHost,
        mapDataSource = mapDataSource,
        regionRepo = regionRepo,
        locationRepository = locationRepository,
        scope = viewModelScope,
        routeActive = { routeController.isActive },
        cacheSize = {
            prefsRepository.getInt(
                R.string.preference_key_map_stop_cache_size,
                StopsMapController.DEFAULT_STOP_CACHE_SIZE,
            )
        },
    )

    // ----- Map-host surface (delegated) -----
    // These live on [mapHost]; the view model re-exposes them so existing callers (the flavor adapter,
    // MapFeature, the trip-results / picker screens) are unaffected by the extraction.

    /** The live camera, published by the flavor adapter on each idle; the loaders react off it. */
    val camera: StateFlow<CameraSnapshot?> get() = mapHost.camera

    fun onCameraIdle(snapshot: CameraSnapshot) = mapHost.onCameraIdle(snapshot)

    /** Whether a viewport/route load is in flight (the old `Callback.showProgress`). */
    val progress: StateFlow<Boolean> get() = mapHost.progress

    /** Whether the last nearby-stops load was truncated (drives the "zoom in to see more stops" banner). */
    val moreStopsAvailable: StateFlow<Boolean> get() = mapHost.moreStopsAvailable

    /** One-shot events that need an Activity (e.g. the out-of-range prompt). */
    val effects: SharedFlow<MapEffect> get() = mapHost.effects

    /** The current region's display name (for the out-of-range prompt), or null if none is selected. */
    val currentRegionName: String? get() = mapHost.currentRegionName

    // ----- Bikeshare layer toggle (overlays every mode) -----

    // The bikeshare overlay (loads stations for the viewport when the layer is on / directions in play).
    private val bikeController = BikeLayerController(
        host = mapHost,
        bikeStationsRepository = bikeStationsRepository,
        prefsRepository = prefsRepository,
        regionRepository = regionRepo,
        scope = viewModelScope,
    )

    /** Toggle the home-map bikeshare layer (the host syncs the pref on resume; the FAB persists it). */
    fun setBikeshareLayerVisible(visible: Boolean, persist: Boolean = false) =
        bikeController.setBikeshareLayerVisible(visible, persist)

    // The single-route use case (route shape + stops + header + the real-time vehicle poll). Feeds its
    // stops into stopsController so they accumulate + focus like nearby stops. (Explicit type so the
    // stopsController `routeActive` lambda above can resolve it without a circular inference.)
    private val routeController: RouteMapController = RouteMapController(
        host = mapHost,
        renderState = mapHost.renderState,
        routeRepository = routeRepository,
        stopsController = stopsController,
        tripObservationRepository = tripObservationRepository,
        scope = viewModelScope,
    )

    // Keeps vehicle markers from being hidden under the route-mode header (was RoutePopup's logic):
    // added to the reported header height to derive the map's top padding in [setRouteHeaderHeight].
    private val routeHeaderMarkerPaddingPx =
        context.resources.getDimensionPixelSize(R.dimen.map_route_vehicle_markers_padding)

    /**
     * The route-mode header (route name + agency), published while a route is shown; null otherwise.
     * Formats the controller's display-free [LoadedRoute] into the overlay's presentation model here, at
     * the view-model boundary (mirroring where the extrapolation display policy is applied).
     */
    val routeHeader: StateFlow<RouteHeader?> =
        routeController.loadedRoute
            // Eagerly, not WhileSubscribed: the upstream is a StateFlow (re-subscription replays the
            // latest value with no query to restart), so there's no work a stop/restart window would
            // save — and it emits only a few times per route session, so the idle collector is free.
            .map { it?.toRouteHeader() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The route header reported its measured height; derive the map's top padding (0 clears it). */
    fun setRouteHeaderHeight(heightPx: Int) {
        mapHost.setTopPadding(if (heightPx > 0) heightPx + routeHeaderMarkerPaddingPx else 0)
    }

    // Map the controller's raw load into the display header: blank-but-loading until the route resolves,
    // then the formatted short/long name + agency. This is the display policy the controller is free of.
    private fun LoadedRoute.toRouteHeader(): RouteHeader = when (this) {
        LoadedRoute.Loading -> RouteHeader(loading = true, shortName = "", longName = "", agency = "")
        is LoadedRoute.Loaded -> RouteHeader(
            loading = false,
            shortName = MyTextUtils.formatDisplayText(getRouteDisplayName(route))!!,
            longName = MyTextUtils.formatDisplayText(getRouteDescription(route))!!,
            agency = agencyName ?: "",
            directions = directions,
            currentDirectionId = currentDirectionId,
        )
    }

    // ----- What the home map shows (nearby stops, or a route) -----
    // There is no stored "mode" field: [RouteMapController.routeId] is the single source of truth
    // for whether a route is shown. Each transition tears down the prior view, persists the intended
    // restore view, and starts the new view's loaders. The home bike layer is gated purely by the user's
    // layer toggle (directions — the only thing that forces bikes on / filters them — is its own
    // DirectionsMapViewModel), so both transitions start it the same way.

    /** Show nearby stops in the current viewport (the default home view). */
    fun showNearbyStops() {
        leaveCurrentView()
        persistRoute(null)
        stopsController.start()
        bikeController.start(directions = false, selectedBikeStationIds = null)
    }

    /**
     * Enter route mode for [routeId], framing its bounding box unless [zoomToRoute] is false (a silent
     * restore). Callers ([toRoute] and the restore in `init`) only ever pass a route different from the
     * current one — re-selecting the active route is handled (re-framed) by [toRoute].
     */
    private fun enterRoute(
        routeId: String,
        zoomToRoute: Boolean,
        directionStopId: String?,
        initialDirectionId: Int? = null,
    ) {
        leaveCurrentView()
        persistRoute(routeId, directionStopId, initialDirectionId)
        routeController.start(routeId, zoomToRoute, directionStopId, initialDirectionId)
        bikeController.start(directions = false, selectedBikeStationIds = null)
    }

    // Persist which route (if any) to restore across process death — null means nearby stops. This is
    // the whole "which view" state (the route controller is the single live source of truth), so there's
    // no separate mode to save. [directionStopId] rides along so a restored route keeps its direction
    // filter; [initialDirectionId] persists a later user switch (null on a fresh entry, clearing any
    // prior route's saved direction). The transient trip-focus view deliberately doesn't touch this, so
    // a back-press restores the prior view.
    private fun persistRoute(
        routeId: String?,
        directionStopId: String? = null,
        initialDirectionId: Int? = null,
    ) {
        savedStateHandle[MapParams.ROUTE_ID] = routeId
        savedStateHandle[MapParams.ROUTE_DIRECTION_STOP_ID] = directionStopId
        savedStateHandle[MapParams.ROUTE_DIRECTION_ID] = initialDirectionId
    }

    /**
     * Tears down whatever the map is currently showing: stops the loaders (each controller clears its
     * own overlays) and drops the accumulated stops (keeping the focused one). Shared by the transitions
     * above and [enterTripFocus].
     */
    private fun leaveCurrentView() {
        stopsController.stop()
        routeController.stop()
        bikeController.stop()
        stopsController.clearStops(false)
        mapHost.setProgress(false)
        mapHost.setMoreStopsAvailable(false)
    }

    // ----- Focus + taps (delegated to [stopsController], except the vehicle selection) -----

    /** A stop marker was tapped: render-focus it + center on it (the old GoogleMapHost.onStopClick). */
    fun onStopTapped(stop: ObaStop) = stopsController.onStopTapped(stop)

    /** A tap away from any marker clears the stop focus + deselects any vehicle (the old onMapClick). */
    fun onMapTapped() {
        stopsController.clearStopFocus()
        renderState.setSelectedVehicle(null)
    }

    /** Selects the tapped vehicle, so the renderer shows its most-recent-data marker. */
    fun onVehicleTapped(status: ObaTripStatus) {
        renderState.setSelectedVehicle(status.activeTripId)
    }

    /** Animate/move the camera to a point with no route-header bias (a general recenter for any screen). */
    fun centerOn(lat: Double, lon: Double, animate: Boolean) = mapHost.centerOn(lat, lon, animate)

    /** Recenter on the focused stop after the arrivals sheet expands over it (a Home map directive). */
    fun recenterOnFocusedStop(lat: Double, lon: Double) {
        mapHost.dispatchCamera(
            CameraCommand.Recenter(lat, lon, animate = true, applyRouteBias = routeController.isActive)
        )
    }

    /**
     * Programmatically focus a restored / deep-linked [stop] once its arrivals load (a Home map
     * directive): ensure it's on the map + render-focused and recenter on it (route-header bias only
     * when [overlayExpanded] in route mode). Delegated to the stops controller.
     */
    fun focusStop(stop: ObaStop, routes: List<ObaRoute>?, overlayExpanded: Boolean) =
        stopsController.focusStop(stop, routes, overlayExpanded)

    /** Clear the map's render focus (back-press from a peeking arrivals sheet; a Home map directive). */
    fun clearFocus() = stopsController.clearFocus()

    /** The map's initial camera, read live from the host each time the adapter (re)composes. */
    val cameraSeed: MapCameraSeed get() = mapHost.cameraSeed

    // ----- "Show route on map" / leave route mode (replaces ShowRoute / ExitRouteMode commands) -----

    /**
     * Focus the map on [request]'s route — the single entry every "show route on map" caller funnels
     * through (the recent/starred lists, route search, the arrivals "show vehicles on map", RouteInfo).
     * Enters route mode (loading its shape, stops, and live vehicles) and frames the route's bounding
     * box. Re-frames even when the map is already parked on that route + direction: re-tapping it in the
     * recent-routes list snaps the camera back to the route's extent instead of no-op'ing.
     */
    fun toRoute(request: ShowRouteRequest) {
        // Same route AND same direction anchor: just reframe (the recent-routes re-tap). A different
        // route, or the same route from a different-direction stop, re-enters with the new filter.
        if (routeController.routeId == request.routeId &&
            routeController.directionStopId == request.directionStopId
        ) {
            mapHost.frameRoute()
        } else {
            enterRoute(request.routeId, zoomToRoute = true, directionStopId = request.directionStopId)
        }
    }

    /** Leave route mode back to nearby stops, preserving the current camera (the route header's cancel). */
    fun exitRouteMode() = showNearbyStops()

    /**
     * Switch the shown route to another of its directions (one of the header's [RouteHeader.directions]
     * ids) via the header's swap affordance. Re-filters the stops + vehicles in place (no reload /
     * reframe). On a real switch, persists the choice and retires the launch anchor
     * ([MapParams.ROUTE_DIRECTION_STOP_ID]) so a process-death restore honors this explicit selection
     * rather than re-resolving the anchor stop.
     */
    fun selectRouteDirection(directionId: Int) {
        if (!routeController.selectDirection(directionId)) return
        savedStateHandle[MapParams.ROUTE_DIRECTION_STOP_ID] = null
        savedStateHandle[MapParams.ROUTE_DIRECTION_ID] = directionId
    }

    // ----- Trip-focus mode (the speed-estimation trip map) -----

    // The single-trip live view. Drives the shared host (so the trip map reuses the home map surface);
    // the home VM just leaves its prior mode on entry and restores stop mode on exit.
    private val tripFocusController =
        TripFocusController(mapHost, tripObservationRepository, viewModelScope)

    /**
     * Enters the trip-focus map: a dedicated **single-trip live view**, distinct from the route view.
     * Leaves whatever the map was doing (no route-wide shape, vehicles, or stops — just the single
     * trip) and hands off to [tripFocusController]. Call [exitTripFocus] on leave.
     */
    fun enterTripFocus(tripId: String, routeColorArgb: Int) {
        // Leaving the current view stops the route controller, so isActive becomes false — no separate
        // "mode" to reset. The trip map shows only the single trip (no route/stops/bikes).
        leaveCurrentView()
        renderState.clearBikeStations()
        tripFocusController.enter(tripId, routeColorArgb)
    }

    /** Leaves trip-focus: stops the frame loop, clears the trip line + overlay, restores nearby stops. */
    fun exitTripFocus() {
        tripFocusController.exit()
        showNearbyStops()
    }

    // ----- My-location / styling / permission (delegated to [mapHost]) -----

    /** Whether the blue-dot my-location layer is enabled (granted permission). Applied by the adapter. */
    val myLocationEnabled: StateFlow<Boolean> get() = mapHost.myLocationEnabled

    /** Re-reads the location permission and reflects it in [myLocationEnabled] (call on resume/grant). */
    fun refreshMyLocationEnabled() = mapHost.refreshMyLocationEnabled()

    /** Eager first-launch permission prompt when the map first shows (blue dot if already granted). */
    fun requestLocationPermissionIfNeeded() = mapHost.requestLocationPermissionIfNeeded()

    /** The Activity delivered a location-permission result; reflect it (blue dot on grant). */
    fun onLocationPermissionResult(granted: Boolean) = mapHost.onLocationPermissionResult(granted)

    /** The my-location FAB / a programmatic recenter: center on the user's location, or raise an effect. */
    fun requestMyLocation(useDefaultZoom: Boolean, animate: Boolean) =
        mapHost.requestMyLocation(useDefaultZoom, animate)

    fun zoomIn() = mapHost.zoomIn()

    fun zoomOut() = mapHost.zoomOut()

    /** Frame the current region's bounds (the out-of-range dialog's "take me there"). */
    fun zoomToRegion() = mapHost.zoomToRegion()

    // ----- Lifecycle (the owner forwards onPause/onResume) -----

    /** Stop the vehicle poll and persist the camera + stop the location feed (the host's onPause). */
    fun onPause() {
        routeController.onPause()
        mapHost.stopLocationFeed()
        mapHost.persistCamera()
    }

    /** Refresh prefs-backed state and restart the vehicle poll if in route mode (the host's onResume). */
    fun onResume() {
        setBikeshareLayerVisible(LayerUtils.isBikeshareLayerVisible(context))
        mapHost.refreshMyLocationEnabled()
        // Begin the live location feed for as long as the map is shown (permission-gated; a no-op until
        // granted). This is what makes `location` a live stream — the legacy host's LocationHelper feed.
        mapHost.startLocationFeed()
        routeController.onResume()
    }

    // Seed the initial view once per VM creation (replacing the host's guarded initMapMode). On a fresh
    // launch SavedStateHandle carries the intent extras as default-args; on a process-death restore it
    // carries the value [persistRoute] wrote. init runs once (NOT on a config change), so this is exactly
    // the old `if (currentMapMode == null)` guard. Placed last — after every field the transitions touch
    // (the controllers) is initialized — so the loaders they launch see fully-constructed state.
    init {
        // A saved ROUTE_ID (process-death restore, or a route deep-link intent) re-enters that route;
        // otherwise nearby stops. Whether a route was shown is fully captured by ROUTE_ID's presence.
        val restoreRouteId: String? = savedStateHandle[MapParams.ROUTE_ID]
        if (restoreRouteId != null) {
            // ZOOM_TO_ROUTE is a launching-intent framing flag (consumed once); [persistRoute]
            // deliberately doesn't write it, so a process-death restore keeps the saved camera rather
            // than re-framing the route.
            enterRoute(
                restoreRouteId,
                zoomToRoute = savedStateHandle[MapParams.ZOOM_TO_ROUTE] ?: false,
                directionStopId = savedStateHandle[MapParams.ROUTE_DIRECTION_STOP_ID],
                // A user-selected direction persisted before process death wins over the anchor stop.
                initialDirectionId = savedStateHandle[MapParams.ROUTE_DIRECTION_ID],
            )
        } else {
            showNearbyStops()
        }
    }

}
