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
package org.onebusaway.android.map.maplibre.compose

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.onebusaway.android.map.MapHost
import org.onebusaway.android.map.compose.BikeInfoWindow
import org.onebusaway.android.map.compose.ObaComposeMapAdapter
import org.onebusaway.android.map.compose.ObaMapCallbacks
import org.onebusaway.android.map.compose.VehicleInfoWindow
import org.onebusaway.android.map.maplibre.MapLibreRenderer
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.ThemeUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.math.abs

private const val STYLE_URL_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_URL_DARK = "https://tiles.openfreemap.org/styles/dark"

/** The dynamic-layer redraw interval (~120Hz, the display rate). See its use in the frame loop. */
private const val FRAME_INTERVAL_NANOS = 8_333_333L

/**
 * maplibre flavor's [ObaComposeMapAdapter]: hosts the classic maplibre [MapView] inside an
 * [AndroidView] (there is no maplibre-compose), bridging the MapView's imperative lifecycle to Compose
 * via a [LifecycleEventObserver], and drives the [MapHost]: it sets the style, builds the
 * [MapLibreRenderer] and re-renders on every render-state change, wires map/marker/info-window clicks
 * to [callbacks], reports camera idles to [MapHost.onCameraIdle], applies the dispatched camera intents
 * from the render state's cameraCommands, and enables the location blue dot from the host's
 * permission-derived flag. There is no imperative host.
 *
 * Lifecycle note: `addObserver` on an already-STARTED/RESUMED lifecycle synchronously dispatches the
 * upward events, so the MapView still receives `onStart`/`onResume` when it enters composition late.
 * `onCreate` happens once at [remember] time; `onDestroy` happens once in `onDispose`.
 */
class MapLibreComposeAdapter : ObaComposeMapAdapter {

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
        val cb = requireNotNull(callbacks) { "MapLibreComposeAdapter requires ObaMapCallbacks" }
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }

        // MapLibre must be initialized before any MapView usage.
        remember(activity) { MapLibre.getInstance(activity) }

        // onCreate(null): MapView.onSaveInstanceState is never wired, so there's no saved MapView state
        // to restore — the camera/focus come from MapViewModel + persisted prefs.
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

        var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
        var renderer by remember { mutableStateOf<MapLibreRenderer?>(null) }
        var loadedStyle by remember { mutableStateOf<Style?>(null) }

        DisposableEffect(mapView) {
            mapView.getMapAsync { map ->
                mapLibreMap = map
                if (initialLatitude != 0.0 || initialLongitude != 0.0) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(initialLatitude, initialLongitude))
                        .zoom(initialZoom.toDouble())
                        .build()
                }
                map.uiSettings.isCompassEnabled = false
                val styleUrl = if (ThemeUtils.isInDarkMode(context)) STYLE_URL_DARK else STYLE_URL_LIGHT
                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    loadedStyle = style
                    val r = MapLibreRenderer(map, context, renderState)
                    renderer = r
                    val container = activity.findViewById<ViewGroup>(android.R.id.content)
                    val windows = MapLibreInfoWindows(activity, container, map)
                    map.setInfoWindowAdapter(windows)
                    wireClicks(map, r, windows, cb)
                    map.addOnCameraIdleListener {
                        map.cameraPosition.target?.let { host.onCameraIdle(snapshot(map, it)) }
                    }
                    r.renderStatic()
                }
            }
            onDispose { }
        }

        // Re-render the maplibre annotations, and enable the blue dot from the view model's permission flag.
        val activeRenderer = renderer
        if (activeRenderer != null) {
            // Static layer (stops / routes / bikes / generics / trip-stop dots): redraw only when the
            // snapshot or trip-stop dots change (viewport loads, the vehicle poll, focus) — bounded cost.
            LaunchedEffect(activeRenderer) {
                merge(
                    renderState.snapshot.map { },
                    renderState.tripStops.map { },
                ).collect { activeRenderer.renderStatic() }
            }
            // The vehicle set (which vehicles exist + their icons): reconcile the markers whenever it's
            // pushed — a poll, a direction switch, or leaving route mode (null). Discrete, so it's reactive
            // (not inferred from the per-frame motion loop below), which is what makes a direction switch
            // take effect immediately instead of waiting for the next poll.
            LaunchedEffect(activeRenderer) {
                renderState.vehicleSet.collect { activeRenderer.reconcileVehicles(it) }
            }
            // Dynamic layer (the live vehicles + the trip-focus band/markers): while either sampler is
            // installed, pull a fresh frame each display frame and update the annotations in place.
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
                            // Redraw at up to ~120Hz (display rate). Freeze entirely while a finger is
                            // down so a tapped marker is a stationary target — a marker moving every frame
                            // can outrun the tap hit-testing (unbounded as the CPU slows); the freeze, not
                            // the cap, is what guarantees tappability.
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
            val myLocationEnabled by host.myLocationEnabled.collectAsState()
            val map = mapLibreMap
            val style = loadedStyle
            LaunchedEffect(myLocationEnabled, map, style) {
                if (myLocationEnabled && map != null && style != null) {
                    enableLocationComponent(map, style, context)
                }
            }
        }

        // Declarative camera: apply the dispatched camera intents to the map once ready, and tell the host
        // the adapter is subscribed (so a deferred route re-frame can dispatch now rather than waiting).
        val map = mapLibreMap
        if (map != null) {
            DisposableEffect(map) {
                host.setMapAttached(true)
                onDispose { host.setMapAttached(false) }
            }
            LaunchedEffect(map) {
                renderState.cameraCommands.collect { command ->
                    applyCameraCommand(command, map, renderState)
                }
            }
        }

        AndroidView(factory = { mapView }, modifier = modifier)
    }
}

/**
 * Wires map/marker/info-window taps to [callbacks] (the home-screen tap policy the host used to
 * install): a stop tap focuses + recenters via [callbacks]; a tap on empty map clears focus; a
 * vehicle/bike tap pre-renders its shared info window (via [infoWindows]) and opens it; a tap on the
 * open window deep links. The bike content is wrapped in a white bubble ([VehicleInfoWindow] draws its
 * own card, [BikeInfoWindow] is background-free), matching the Google flavor.
 */
private fun wireClicks(
    map: MapLibreMap,
    renderer: MapLibreRenderer,
    infoWindows: MapLibreInfoWindows,
    callbacks: ObaMapCallbacks,
) {
    map.addOnMapClickListener {
        infoWindows.clear()
        callbacks.onMapClick(null)
        false
    }
    map.setOnMarkerClickListener { marker ->
        val stop = renderer.stopForMarker(marker)
        if (stop != null) {
            infoWindows.clear()
            callbacks.onStopClick(stop.stop)
            return@setOnMarkerClickListener true
        }
        val vehicle = renderer.vehicleForMarker(marker)
        val response = renderer.vehicleResponse()
        if (vehicle != null && response != null) {
            callbacks.onVehicleClick(vehicle.status) // selects it -> renderer shows the most-recent-data dot
            infoWindows.open(marker) { VehicleInfoWindow(vehicle.status, vehicle.isRealtime, response) }
            return@setOnMarkerClickListener true
        }
        val bike = renderer.bikeForMarker(marker)
        if (bike != null) {
            infoWindows.open(marker) {
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
                    BikeInfoWindow(bike.station)
                }
            }
            return@setOnMarkerClickListener true
        }
        // Titled markers (the trip-focus estimate markers + the most-recent-data dot) fall through to
        // the SDK's default title window. Untitled decorations (trip-stop dots, generic start/end
        // markers) have nothing to show, so consume the tap (return true) — a no-op, not an empty bubble.
        marker.title.isNullOrEmpty()
    }
    map.setOnInfoWindowClickListener { marker ->
        val vehicle = renderer.vehicleForMarker(marker)
        if (vehicle != null) {
            callbacks.onVehicleInfoWindowClick(vehicle.status)
            return@setOnInfoWindowClickListener true
        }
        val bike = renderer.bikeForMarker(marker)
        if (bike != null) {
            callbacks.onBikeInfoWindowClick(bike.station)
            return@setOnInfoWindowClickListener true
        }
        false
    }
}

/** Builds a [CameraSnapshot] from the maplibre map's current camera + visible region. */
private fun snapshot(map: MapLibreMap, target: LatLng): CameraSnapshot {
    val bounds = map.projection.visibleRegion.latLngBounds
    val north = bounds.getLatNorth()
    val south = bounds.getLatSouth()
    val east = bounds.getLonEast()
    val west = bounds.getLonWest()
    return CameraSnapshot(
        center = GeoPoint(target.latitude, target.longitude),
        zoom = map.cameraPosition.zoom,
        latSpan = abs(north - south),
        lonSpan = abs(east - west),
        southWest = GeoPoint(south, west),
        northEast = GeoPoint(north, east),
    )
}

@SuppressLint("MissingPermission")
private fun enableLocationComponent(map: MapLibreMap, style: Style, context: Context) {
    if (!PermissionUtils.hasGrantedAtLeastOnePermission(context, PermissionUtils.LOCATION_PERMISSIONS)) {
        return
    }
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style).build()
        )
        component.cameraMode = CameraMode.NONE
        component.renderMode = RenderMode.COMPASS
    }
    component.isLocationComponentEnabled = true
}

private fun Context.findActivity(): Activity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("MapLibreComposeAdapter must be hosted in an Activity context")
}
