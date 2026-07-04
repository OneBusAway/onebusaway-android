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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.SavedStateHandle
import org.onebusaway.android.R
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.FramingIntent
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PreferenceUtils

/**
 * The flavor-neutral **map surface**: everything a map screen needs regardless of *what* it shows.
 * It owns the shared [MapRenderState], the live camera read-back + seed + cross-process persistence,
 * the content padding, the generic-marker + camera-command write paths, and the
 * my-location / permission / region-framing machinery — the concerns the legacy flavor "host" carried.
 *
 * The use-case [MapViewModel] loaders (stops / route / directions / trip-focus / bike) drive *what*
 * the map shows by observing [camera] and writing [renderState]; this host is the single thing the
 * flavor adapter binds to (it reads [renderState] + [myLocationEnabled] and feeds the camera back via
 * [onCameraIdle]). Constructed by the owning view model on its `viewModelScope`, so the host's
 * collectors (the region re-zoom) share the view model's lifetime; deps are passed in (the same
 * repositories Hilt supplies the view model), so it stays JVM-constructible with fakes.
 */
class MapHost(
    private val scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val regionRepo: RegionRepository,
    private val locationRepository: LocationRepository,
    private val prefsRepository: PreferencesRepository,
    private val context: Context,
) {

    val renderState = MapRenderState()

    init {
        // Re-center when the region changes from the one present at startup (replaces the host's
        // onRegionChanged push). We compare each emission's id against the last-seen id, seeded with the
        // region at construction — rather than dropping the first emission — so it's race-free: even if
        // the auto-select resolves before this collector starts (making the first value the new region),
        // id != seed still frames it. The startup region of a cached restart matches the seed, so the
        // persisted camera isn't yanked. (The StateFlow already dedups same-id republishes via Region's
        // id-based equals, so the unchanged-refresh doesn't emit.)
        var lastRegionId = regionRepo.region.value?.id
        scope.launch {
            regionRepo.region.collect { region ->
                val id = region?.id
                if (region != null && id != lastRegionId) rezoomForRegion()
                lastRegionId = id
            }
        }
    }

    // ----- Camera read-back (the hot path) -----

    // The live camera, published by the flavor adapter on every camera-idle. The reactive loaders
    // debounce + dedup off this, replacing the controllers' MapWatcher/onMapChanged callbacks.
    private val _camera = MutableStateFlow<CameraSnapshot?>(null)

    val camera: StateFlow<CameraSnapshot?> = _camera.asStateFlow()

    fun onCameraIdle(snapshot: CameraSnapshot) {
        _camera.value = snapshot
        // Persist the live viewport so a process-death restore (and MapLibre, which has no rememberSaveable
        // backstop) re-seeds here via [cameraSeed]. Same keys as the intent extras, so they interoperate.
        savedStateHandle[MapParams.CENTER_LAT] = snapshot.center.latitude
        savedStateHandle[MapParams.CENTER_LON] = snapshot.center.longitude
        savedStateHandle[MapParams.ZOOM] = snapshot.zoom.toFloat()
    }

    /**
     * The map's initial camera, read live from [savedStateHandle] each time the adapter (re)composes
     * (replacing the host's readMapSeed). A getter, not a cached val: on a config change the VM survives
     * but the MapLibre MapView is recreated and re-reads this, so it must reflect the *current* camera
     * (kept fresh by [onCameraIdle]) rather than the stale cold-launch seed. The Google adapter restores
     * its own camera via rememberSaveable and ignores this after the first composition.
     */
    val cameraSeed: MapCameraSeed get() = resolveCameraSeed(savedStateHandle)

    // ----- Loading / region status + one-shot effects -----

    private val _progress = MutableStateFlow(false)

    /** Whether a viewport/route load is in flight (the old `Callback.showProgress`). */
    val progress: StateFlow<Boolean> = _progress.asStateFlow()

    /** Set by the use-case loaders while a load is in flight. */
    fun setProgress(inProgress: Boolean) {
        _progress.value = inProgress
    }

    private val _moreStopsAvailable = MutableStateFlow(false)

    /**
     * Whether the last viewport stop load was truncated (the API's `limitExceeded`): more stops match
     * the viewport than were returned, so the UI can prompt the user to zoom in. Set by the stop
     * loader on each load; cleared when leaving the nearby-stops view.
     */
    val moreStopsAvailable: StateFlow<Boolean> = _moreStopsAvailable.asStateFlow()

    /** Set by the stop loader to the last response's `limitExceeded`; cleared on view changes. */
    fun setMoreStopsAvailable(available: Boolean) {
        _moreStopsAvailable.value = available
    }

    private val _effects = MutableSharedFlow<MapEffect>(extraBufferCapacity = 8)

    /** One-shot events that need an Activity (e.g. the out-of-range prompt). */
    val effects: SharedFlow<MapEffect> = _effects.asSharedFlow()

    /** Raise a one-shot [MapEffect] for the Activity to carry out. */
    fun emitEffect(effect: MapEffect) {
        _effects.tryEmit(effect)
    }

    /** The current region's display name (for the out-of-range prompt), or null if none is selected. */
    val currentRegionName: String? get() = regionRepo.region.value?.name

    // ----- Padding + camera commands -----

    // Map content padding: the route-mode header sets the top, the arrivals sheet sets the bottom.
    // Declarative state the renderer applies (Google: GoogleMap contentPadding), replacing the old
    // imperative mapView.setPadding(...) relay through HomeActivity.
    fun setTopPadding(px: Int) = renderState.setTopPadding(px)

    fun setBottomPadding(px: Int) = renderState.setBottomPadding(px)

    /** Dispatch a transient camera gesture (zoom step / recenter / my-location move / stop-tap center). */
    fun dispatchGesture(command: CameraCommand) = renderState.dispatchGesture(command)

    /** Set the map's retained framing intent (fit route / itinerary / region / a fixed point). */
    fun frame(intent: FramingIntent) = renderState.frame(intent)

    /** Clear the retained framing so a stale fit isn't re-applied when the map leaves a framed view. */
    fun clearFraming() = renderState.clearFraming()

    /**
     * Frame the active route's bounding box (the "zoom to route" camera move). Sets the retained
     * [FramingIntent.Route], so a late or re-created adapter replays it — the frame isn't dropped when
     * re-selecting the already-shown route from a list that navigates back to a freshly re-created map
     * (the recent-routes case), nor swallowed when re-tapping the same route to snap back to its extent.
     * The route's shape is already in the render state, so the framing has bounds to fit.
     */
    fun frameRoute() = frame(FramingIntent.Route)

    /**
     * Frame the current directions itinerary's bounding box. Retained, so it isn't lost when the
     * directions map is composed behind the results sheet the instant a plan completes, before the
     * adapter subscribes (#1640) — the replay catches the late subscriber up.
     */
    fun frameItinerary() = frame(FramingIntent.Itinerary)

    /**
     * Frame a degenerate directions itinerary (no route shape — start == end): center on [lat],[lon] at
     * the default zoom. Retained like [frameItinerary] since it's requested at the same moment (a plan
     * completing behind the results sheet).
     */
    fun frameStart(lat: Double, lon: Double) =
        frame(FramingIntent.Point(lat, lon, MapParams.DEFAULT_ZOOM.toFloat()))

    /** Animate/move the camera to a point with no route-header bias (a general recenter for any screen). */
    fun centerOn(lat: Double, lon: Double, animate: Boolean) {
        dispatchGesture(CameraCommand.Recenter(lat, lon, animate, applyRouteBias = false))
    }

    fun zoomIn() = dispatchGesture(CameraCommand.ZoomIn)

    fun zoomOut() = dispatchGesture(CameraCommand.ZoomOut)

    /** Frame the current region's bounds (the out-of-range dialog's "take me there"). */
    fun zoomToRegion() = frame(FramingIntent.Region)

    // ----- Generic markers -----

    fun addMarker(latitude: Double, longitude: Double, hue: Float?): Int =
        renderState.addMarker(GeoPoint(latitude, longitude), hue)

    fun removeMarker(id: Int) = renderState.removeMarker(id)

    // ----- My-location / styling / permission (replaces the host's location machinery) -----

    private val _myLocationEnabled = MutableStateFlow(false)

    /** Whether the blue-dot my-location layer is enabled (granted permission). Applied by the adapter. */
    val myLocationEnabled: StateFlow<Boolean> = _myLocationEnabled.asStateFlow()

    /** Re-reads the location permission and reflects it in [myLocationEnabled] (call on resume/grant). */
    fun refreshMyLocationEnabled() {
        _myLocationEnabled.value =
            PermissionUtils.hasGrantedAtLeastOnePermission(context, PermissionUtils.LOCATION_PERMISSIONS)
    }

    /**
     * Called once when the map first shows: enable the blue dot if we already have permission, else —
     * unless the user already declined — raise the rationale so the first-launch flow can ask (this is
     * the host's old initMap → requestPermissionAndInit eager prompt, which also drives the deferred
     * first-launch region check via the permission result).
     */
    fun requestLocationPermissionIfNeeded() {
        if (PermissionUtils.hasGrantedAtLeastOnePermission(context, PermissionUtils.LOCATION_PERMISSIONS)) {
            _myLocationEnabled.value = true
        } else if (!PreferenceUtils.userDeniedLocationPermission(context)) {
            emitEffect(MapEffect.ShowPermissionRationale)
        }
    }

    /** The Activity delivered a location-permission result; reflect it (blue dot on grant). */
    fun onLocationPermissionResult(granted: Boolean) {
        PreferenceUtils.setUserDeniedLocationPermissions(context, !granted)
        if (granted) {
            _myLocationEnabled.value = true
            // onResume already ran startUpdates() with no permission (a no-op); now that it's granted,
            // begin the live feed immediately rather than waiting for the next resume.
            locationRepository.startUpdates()
        }
    }

    /**
     * The my-location FAB / a programmatic recenter: center on the user's location, or raise the
     * appropriate effect (services off / permission needed / no fix yet). Ported from
     * GoogleMapHost.setMyLocation; the dialogs + the permission launcher are Activity effects.
     */
    fun requestMyLocation(useDefaultZoom: Boolean, animate: Boolean) {
        val app = context
        // lastKnownLocation() (not the repo's .value): the FAB must trigger the lazy provider poll, like
        // the legacy Application.getLastKnownLocation. Reading .value would show the "waiting" toast
        // whenever nothing has seeded the flow yet (e.g. the cold-start poll ran before permission was
        // granted, or the region never changed so frameCurrentRegion never polled).
        val last = locationRepository.lastKnownLocation()
        val action = myLocationAction(
            locationEnabled = LocationUtils.isLocationEnabled(app),
            neverShowLocationDialog =
                prefsRepository.getBoolean(R.string.preference_key_never_show_location_dialog, false),
            hasLastKnownLocation = last != null,
            hasPermission = PermissionUtils.hasGrantedAtLeastOnePermission(
                app, PermissionUtils.LOCATION_PERMISSIONS
            ),
            userDeniedPermission = PreferenceUtils.userDeniedLocationPermission(context),
        )
        when (action) {
            MyLocationAction.MoveToLocation -> last?.let {
                dispatchGesture(
                    CameraCommand.MoveToLocation(it.latitude, it.longitude, useDefaultZoom, animate)
                )
            }
            MyLocationAction.ShowNoLocationDialog -> emitEffect(MapEffect.NoLocation)
            MyLocationAction.ShowPermissionRationale -> emitEffect(MapEffect.ShowPermissionRationale)
            MyLocationAction.ShowWaitingToast -> emitEffect(MapEffect.WaitingForLocation)
            MyLocationAction.None -> {}
        }
    }

    // ----- Region re-zoom (the old ObaRegionsTask.Callback.onRegionTaskFinished) -----

    /**
     * The current region changed (driven by the region collector, so this only fires on a real change to
     * a present region). Frames it once the map has published a camera — awaiting the first non-null
     * [camera] reactively rather than deferring through a boolean flushed in [onCameraIdle]. Waiting for
     * the camera lets the `cameraAtSeed` decision read the real restored/seed viewport (not a premature
     * null). Both outcomes are retained framings ([FramingIntent]), so the frame survives even if the
     * first camera idle beats the adapter's subscription (#1640) — the exact seed-window this decision
     * runs in — rather than being dropped into the no-replay gesture flow.
     */
    private fun rezoomForRegion() {
        scope.launch {
            val cam = camera.filterNotNull().first()
            frameCurrentRegion(cam)
        }
    }

    /**
     * Frame the current region: if we have no location, or the camera is still at the (0,0) seed, frame
     * the user's location if we have one, else the region — but don't yank a camera the user already moved.
     * Both frames go through the retained [FramingIntent] flow (not the FAB's [requestMyLocation] gesture
     * path), so neither can be lost in the first-idle-before-subscribe window this cold-start decision
     * runs in. The my-location leg has already established a fix here (regionRezoom returns FrameMyLocation
     * only when one exists), so it centers on it directly rather than re-deriving the FAB's dialog logic.
     */
    private fun frameCurrentRegion(cam: CameraSnapshot) {
        // lastKnownLocation() (not the repo's .value): this runs at cold-start framing and must trigger
        // the lazy provider poll so the first frame can target the user's location.
        val location = locationRepository.lastKnownLocation()
        val center = cam.center
        val atSeed = center.latitude == 0.0 && center.longitude == 0.0
        when (regionRezoom(changed = true, hasLocation = location != null, cameraAtSeed = atSeed)) {
            RegionRezoom.FrameMyLocation ->
                location?.let { frame(FramingIntent.Point(it.latitude, it.longitude, SEED_DEFAULT_ZOOM)) }
            RegionRezoom.FrameRegion -> frame(FramingIntent.Region)
            RegionRezoom.None -> {}
        }
    }

    // ----- Live location feed lifecycle (the owner forwards onPause/onResume) -----

    /** Begin the live location feed for as long as the map is shown (permission-gated; a no-op until granted). */
    fun startLocationFeed() = locationRepository.startUpdates()

    /** Stop the live location feed so the providers don't run in the background (battery). */
    fun stopLocationFeed() = locationRepository.stopUpdates()

    /** Persist the live viewport for the next launch's seed (the host's onPause). */
    fun persistCamera() {
        _camera.value?.let {
            PreferenceUtils.saveMapViewToPreferences(it.center.latitude, it.center.longitude, it.zoom.toFloat())
        }
    }
}
