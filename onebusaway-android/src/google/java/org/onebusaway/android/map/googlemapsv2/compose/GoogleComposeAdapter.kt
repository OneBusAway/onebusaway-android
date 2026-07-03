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
package org.onebusaway.android.map.googlemapsv2.compose

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.onebusaway.android.R
import org.onebusaway.android.map.MapHost
import org.onebusaway.android.map.compose.BikeInfoWindow
import org.onebusaway.android.map.compose.ObaComposeMapAdapter
import org.onebusaway.android.map.compose.ObaMapCallbacks
import org.onebusaway.android.map.compose.VehicleInfoWindow
import org.onebusaway.android.map.googlemapsv2.GoogleMapRenderer
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.ThemeUtils

/**
 * The dynamic-layer redraw interval: ~20Hz, the proven upstream cadence. Downsampling from the display
 * rate is deliberate — moving a native marker every frame (~120Hz) outruns the Maps SDK's tap
 * hit-testing and the marker stops registering taps (issue #551), whereas at 20Hz the SDK keeps the
 * clickable region in sync and native marker taps stay reliable. 20Hz still reads as a smooth glide.
 */
private const val FRAME_INTERVAL_NANOS = 50_000_000L

/**
 * Google flavor's [ObaComposeMapAdapter]: hosts the classic [MapView] inside an [AndroidView] (no more
 * android-maps-compose), bridging the MapView's imperative lifecycle to Compose via a
 * [LifecycleEventObserver], and drives the shared [MapHost]. It sets the style, builds the
 * [GoogleMapRenderer] and re-renders on every render-state change, wires map/marker/info-window clicks
 * to [callbacks], reports camera idles to [MapHost.onCameraIdle], applies the dispatched camera intents,
 * and enables the location blue dot from the host's permission-derived flag.
 *
 * The live route vehicles + the trip-focus estimate markers are native [Marker]s moved in place at
 * ~20Hz (the [GoogleMapRenderer.renderDynamic] loop below), so they glide with the map — no Compose
 * recomposition or projection-overlay lag — while staying reliably tappable (see [FRAME_INTERVAL_NANOS]).
 *
 * Lifecycle note: `addObserver` on an already-STARTED/RESUMED lifecycle synchronously dispatches the
 * upward events, so the MapView still receives `onStart`/`onResume` when it enters composition late.
 * `onCreate` happens once at [remember] time; `onDestroy` happens once in `onDispose`.
 */
class GoogleComposeAdapter : ObaComposeMapAdapter {

    @Composable
    override fun Content(
        host: MapHost,
        callbacks: ObaMapCallbacks?,
        modifier: Modifier,
        initialLatitude: Double,
        initialLongitude: Double,
        initialZoom: Float,
    ) {
        val renderState = host.renderState
        val cb = requireNotNull(callbacks) { "GoogleComposeAdapter requires ObaMapCallbacks" }
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }

        // onCreate(null): MapView.onSaveInstanceState is never wired, so there's no saved MapView state
        // to restore — the camera/focus come from the view model + persisted prefs.
        val mapView = remember { MapView(activity).apply { onCreate(null) } }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, mapView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> { /* ON_CREATE handled at remember; ON_DESTROY handled in onDispose */ }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDestroy()
            }
        }

        var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
        var renderer by remember { mutableStateOf<GoogleMapRenderer?>(null) }
        var infoWindows by remember { mutableStateOf<GoogleInfoWindows?>(null) }
        // The map's top-left in root coordinates, so the published projector can report marker positions
        // in the composition's root space (not just map-view-local pixels).
        var mapRootOffset by remember { mutableStateOf(Offset.Zero) }

        DisposableEffect(mapView) {
            // Captured so onDispose can tear the renderer down (cancel in-flight marker eases + remove
            // annotations) before the lifecycle effect's mapView.onDestroy(). That effect is registered
            // earlier, so Compose runs this onDispose first — the order we want.
            var createdRenderer: GoogleMapRenderer? = null
            mapView.getMapAsync { map ->
                if (initialLatitude != 0.0 || initialLongitude != 0.0) {
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(initialLatitude, initialLongitude), initialZoom
                        )
                    )
                }
                map.uiSettings.apply {
                    isMyLocationButtonEnabled = false
                    isZoomControlsEnabled = false
                    isMapToolbarEnabled = false
                }
                map.setMapStyle(resolveMapStyle(context))

                val r = GoogleMapRenderer(map, context, renderState)

                val container = activity.findViewById<ViewGroup>(android.R.id.content)
                val windows = GoogleInfoWindows(activity, container)
                map.setInfoWindowAdapter(windows)
                wireClicks(map, r, windows, cb)

                map.setOnCameraIdleListener { host.onCameraIdle(snapshot(map)) }

                r.renderStatic()
                createdRenderer = r
                renderer = r
                infoWindows = windows
                googleMap = map
            }
            onDispose { createdRenderer?.dispose() }
        }

        val activeRenderer = renderer
        val activeInfoWindows = infoWindows
        if (activeRenderer != null && activeInfoWindows != null) {
            // Static layer (stops / routes / bikes / generics / trip-stop dots): redraw only when the
            // snapshot or trip-stop dots change (viewport loads, the vehicle poll, focus) — bounded cost.
            LaunchedEffect(activeRenderer) {
                merge(
                    renderState.snapshot.map { },
                    renderState.tripStops.map { },
                ).collect { activeRenderer.renderStatic() }
            }
            // Each fresh vehicle poll re-renders an open info window from the now-current live state, so a
            // shown bubble reflects the latest data instead of a tap-time snapshot.
            LaunchedEffect(activeRenderer, activeInfoWindows) {
                activeRenderer.vehicleResponse.collect { activeInfoWindows.refresh() }
            }
            // The persisted vehicle selection owns the bubble: re-open it whenever the selected vehicle's
            // marker exists but its bubble isn't already showing — e.g. on return from the trip list, where
            // the selection (and its data dot) survive the marker rebuild but the native info-window state
            // doesn't (issue #32). The vehicleResponse signal re-checks after each poll's marker reconcile,
            // so the bubble re-opens as soon as the marker is (re)created.
            LaunchedEffect(activeRenderer, activeInfoWindows) {
                combine(renderState.selectedVehicleTripId, activeRenderer.vehicleResponse) { id, _ -> id }
                    .collect { tripId ->
                        val marker = tripId?.let { activeRenderer.vehicleMarkerForTripId(it) } ?: return@collect
                        if (!activeInfoWindows.isShowing(marker)) {
                            activeInfoWindows.openVehicleWindow(activeRenderer, marker)
                        }
                    }
            }
            // Dynamic layer (the live vehicles + the trip-focus band/markers): while either sampler is
            // installed, pull a fresh frame each display frame and move the native markers in place.
            // withFrameNanos paces us to the display refresh and idles when nothing's drawing.
            LaunchedEffect(activeRenderer) {
                combine(renderState.tripOverlaySampler, renderState.vehiclesSampler) { t, v -> t to v }
                    .collectLatest { (tripSampler, vehiclesSampler) ->
                        if (tripSampler == null && vehiclesSampler == null) {
                            activeRenderer.renderDynamic(null, null, 0L)
                            return@collectLatest
                        }
                        var lastNanos = 0L
                        while (true) {
                            val frameNanos = withFrameNanos { it }
                            if (frameNanos - lastNanos < FRAME_INTERVAL_NANOS) {
                                continue
                            }
                            lastNanos = frameNanos
                            val now = System.currentTimeMillis()
                            activeRenderer.renderDynamic(tripSampler?.invoke(now), vehiclesSampler?.invoke(now), now)
                        }
                    }
            }
        }

        val map = googleMap
        if (map != null) {
            // Declarative styling + my-location: the blue dot tracks the host's permission-derived flag.
            val myLocationEnabled by host.myLocationEnabled.collectAsState()
            LaunchedEffect(map, myLocationEnabled) {
                applyMyLocation(map, context, myLocationEnabled)
            }
            // Declarative map padding (route-header top + arrivals-sheet bottom).
            LaunchedEffect(map) {
                renderState.padding.collect { map.setPadding(0, it.topPx, 0, it.bottomPx) }
            }
            // Declarative camera: apply the host-dispatched camera intents to the map once ready, and
            // tell the host the adapter is subscribed (so a deferred route re-frame can dispatch now).
            DisposableEffect(map) {
                host.setMapAttached(true)
                onDispose { host.setMapAttached(false) }
            }
            LaunchedEffect(map) {
                renderState.cameraCommands.collect { applyCameraCommand(it, map, renderState, context) }
            }
            // Publish a flavor-neutral lat/lng -> root-space projector so map-SDK-agnostic callers (the
            // onboarding spotlight) can locate a marker on screen without touching the Google SDK.
            DisposableEffect(map, renderState) {
                renderState.setProjector(
                    MapProjector { point ->
                        val screen = map.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                        ScreenOffset(screen.x + mapRootOffset.x, screen.y + mapRootOffset.y)
                    }
                )
                onDispose { renderState.setProjector(null) }
            }
        }

        AndroidView(
            factory = { mapView },
            modifier = modifier.onGloballyPositioned { mapRootOffset = it.positionInRoot() },
        )
    }
}

/**
 * Wires map/marker/info-window taps to [callbacks]: a tap on empty map clears focus; a marker tap
 * routes through [routeMarkerTap] (reliable for every marker, static and dynamic, because the dynamic
 * markers move at only ~20Hz — see [FRAME_INTERVAL_NANOS]); an info-window tap deep links.
 */
private fun wireClicks(
    map: GoogleMap,
    renderer: GoogleMapRenderer,
    infoWindows: GoogleInfoWindows,
    cb: ObaMapCallbacks,
) {
    map.setOnMapClickListener { latLng ->
        infoWindows.clear()
        cb.onMapClick(GeoPoint(latLng.latitude, latLng.longitude))
    }

    map.setOnMarkerClickListener { marker -> routeMarkerTap(marker, renderer, infoWindows, cb) }

    map.setOnInfoWindowClickListener { marker ->
        val vehicle = renderer.vehicleForMarker(marker)
        if (vehicle != null) {
            cb.onVehicleInfoWindowClick(vehicle.status)
            return@setOnInfoWindowClickListener
        }
        renderer.bikeForMarker(marker)?.let { cb.onBikeInfoWindowClick(it.station) }
    }
}

/**
 * Routes a native marker tap: a stop focuses + recenters; a vehicle/bike pre-renders its shared info
 * window (via [GoogleInfoWindows]) and opens it without recentering; a trip-focus estimate marker opens
 * the SDK's default title window. Always returns true (handled, no default camera recenter).
 */
private fun routeMarkerTap(
    marker: Marker,
    renderer: GoogleMapRenderer,
    infoWindows: GoogleInfoWindows,
    cb: ObaMapCallbacks,
): Boolean {
    val stop = renderer.stopForMarker(marker)
    if (stop != null) {
        infoWindows.clear() // the open bubble (if any) is dismissed by tapping the stop
        cb.onStopClick(stop.stop)
        return true
    }
    val vehicle = renderer.vehicleForMarker(marker)
    if (vehicle != null) {
        cb.onVehicleClick(vehicle.status)
        infoWindows.openVehicleWindow(renderer, marker)
        return true
    }
    val bike = renderer.bikeForMarker(marker)
    if (bike != null) {
        cb.onBikeClick(bike.station)
        infoWindows.open(marker) {
            Surface(color = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
                BikeInfoWindow(bike.station)
            }
        }
        return true
    }
    // Trip-focus estimate markers + the most-recent-data dot (titled markers): the SDK's default
    // title/snippet info window, which dismisses any open custom bubble.
    infoWindows.clear()
    marker.showInfoWindow()
    return true
}

/**
 * Open [marker]'s vehicle info window. The content lambda re-reads the marker's live state on each
 * render, so the bubble reflects the latest poll (re-rendered via [GoogleInfoWindows.refresh]), not a
 * tap-time snapshot.
 */
private fun GoogleInfoWindows.openVehicleWindow(renderer: GoogleMapRenderer, marker: Marker) {
    open(marker) {
        val live = renderer.vehicleForMarker(marker)
        // Deliberate snapshot read, not a reactive subscription: this content is rendered to a bitmap on
        // each GoogleInfoWindows.refresh (the window is a detached bitmap, not a live composition), so we
        // want the latest poll at render time rather than a collectAsState that would never recompose here.
        @Suppress("StateFlowValueCalledInComposition")
        val response = renderer.vehicleResponse.value
        if (live != null && response != null) VehicleInfoWindow(live.status, live.isRealtime, response)
    }
}

/** Builds a [CameraSnapshot] from the Google map's current camera + visible region. */
private fun snapshot(map: GoogleMap): CameraSnapshot {
    val pos = map.cameraPosition
    val bounds = map.projection.visibleRegion.latLngBounds
    val sw = bounds.southwest
    val ne = bounds.northeast
    return CameraSnapshot(
        center = GeoPoint(pos.target.latitude, pos.target.longitude),
        zoom = pos.zoom.toDouble(),
        latSpan = ne.latitude - sw.latitude,
        lonSpan = ne.longitude - sw.longitude,
        southWest = GeoPoint(sw.latitude, sw.longitude),
        northEast = GeoPoint(ne.latitude, ne.longitude),
    )
}

@SuppressLint("MissingPermission")
private fun applyMyLocation(map: GoogleMap, context: Context, enabled: Boolean) {
    val granted = PermissionUtils.hasGrantedAtLeastOnePermission(context, PermissionUtils.LOCATION_PERMISSIONS)
    map.isMyLocationEnabled = enabled && granted
}

/** The map style for the current night-mode state: the dark theme, or POI removal in light mode. */
private fun resolveMapStyle(context: Context): MapStyleOptions =
    if (ThemeUtils.isInDarkMode(context)) {
        MapStyleOptions.loadRawResourceStyle(context, R.raw.dark_map)
    } else {
        // Light mode: just hide POIs (ported from GoogleMapHost.onMapReady).
        MapStyleOptions(
            "[{\"featureType\":\"poi\",\"elementType\":\"all\",\"stylers\":[{\"visibility\":\"off\"}]}]"
        )
    }
