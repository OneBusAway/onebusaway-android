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
package org.onebusaway.android.ui.home.map

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.map.MapEffect
import org.onebusaway.android.map.MapNavigation
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.StopsBanner
import org.onebusaway.android.map.bike.BikeStation
import org.onebusaway.android.map.compose.ObaMap
import org.onebusaway.android.map.compose.ObaMapCallbacks
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.routeLineWidthScale
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.ui.home.CurrentFocus
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.HomeViewModel
import org.onebusaway.android.ui.home.MapDirective
import org.onebusaway.android.ui.home.StopFocusTransition
import org.onebusaway.android.ui.home.chrome.mapTopChromeInsetPx
import org.onebusaway.android.ui.home.chrome.mapTopChromeOverlayInset
import org.onebusaway.android.ui.home.focusedBikeStationId
import org.onebusaway.android.ui.home.focusedStop
import org.onebusaway.android.ui.tutorial.MapStopSpotlight
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.LayerUtils
import org.onebusaway.android.util.ObaRequestErrors
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PreferenceUtils

// Temporary calibration aid; retain the implementation so it can be restored with a one-line toggle.
private const val SHOW_DEBUG_ZOOM_INDICATOR = false

/**
 * The self-wiring map feature module: renders [ObaMap] and owns everything that used to be map glue
 * in HomeActivity — the tap callbacks (focus -> the map view model + the home focused stop +
 * analytics; info-window taps -> navigation), the one-shot effects (the out-of-range / no-location /
 * permission-rationale dialogs + the my-location toast + the permission request, now Compose-native),
 * the eager first-launch permission prompt, and the resume/pause lifecycle. The visibility gates are a
 * self-wired [MapChromeViewModel]; the loading bar reads [MapViewModel.progress] directly. Mirrors the
 * survey / donation / weather / help feature modules; the host just places it.
 *
 * It drives [homeViewModel] for focus (the home screen's arrivals sheet reacts to map focus) — that
 * map→home coupling is inherent to the screen.
 */
@Composable
fun MapFeature(
    mapViewModel: MapViewModel,
    homeViewModel: HomeViewModel,
    // The sheet-driven FAB lift, computed by HomeScreen from its live SheetState (the map composes only
    // when HOME is the destination, so this lives with the sheet rather than round-tripping the VM).
    fabBottomInset: Dp,
    modifier: Modifier = Modifier,
    // A long-press on the map surfaces the "directions from/to here" menu; HomeScreen owns that state.
    onMapLongPress: (GeoPoint) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Keep the remembered ObaMapCallbacks calling HomeScreen's latest long-press handler.
    val currentOnMapLongPress by rememberUpdatedState(onMapLongPress)

    // Compose-native permission launcher: deliver the result to the map view model (blue dot) + the
    // home view model (the deferred first-launch region check).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        mapViewModel.onLocationPermissionResult(granted)
        homeViewModel.onLocationPermissionResult()
    }

    val callbacks = remember(mapViewModel, homeViewModel) {
        object : ObaMapCallbacks {
            override fun onStopClick(marker: StopMarker) {
                val stop = marker.stop
                val transition = homeViewModel.onStopFocused(
                    FocusedStop(stop.id, stop.name, stop.stopCode, marker.point),
                    continuingRoutes = marker.presentedRoutes
                )
                if (transition == StopFocusTransition.ReplacePresentation) {
                    mapViewModel.clearAllFocus()
                }
                mapViewModel.onStopTapped(stop)
                // Already focused on this stop? Then don't re-fire the home focus + analytics.
                if (transition == StopFocusTransition.Unchanged) return
                AnalyticsEntryPoint.get(context).reportUiEvent(
                    PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                    resources.getString(R.string.analytics_label_button_press_map_icon),
                    null
                )
            }

            override fun onMapClick(point: GeoPoint?) {
                homeViewModel.unfocusMapOneLevel()
            }

            override fun onMapLongClick(point: GeoPoint) {
                currentOnMapLongPress(point)
            }

            override fun onBikeClick(station: BikeStation) {
                val bikeId = homeViewModel.currentFocus.value.focusedBikeStationId
                if (bikeId == null || !bikeId.equals(station.id, ignoreCase = true)) {
                    mapViewModel.clearAllFocus()
                    homeViewModel.onBikeStationFocused(station.id)
                }
                AnalyticsEntryPoint.get(context).reportUiEvent(
                    PlausibleAnalytics.REPORT_BIKE_EVENT_URL,
                    resources.getString(
                        if (station.isFloatingBike) {
                            R.string.analytics_label_bike_station_marker_clicked
                        } else {
                            R.string.analytics_label_floating_bike_marker_clicked
                        }
                    ),
                    null
                )
            }

            override fun onVehicleClick(status: ObaTripStatus) {
                mapViewModel.onVehicleTapped(status)
            }

            override fun onRouteContinuationClick(
                routeId: String,
                routeShortName: String,
                directionId: Int?
            ) {
                homeViewModel.advanceRouteContinuation(
                    routeId,
                    routeShortName,
                    directionId,
                    undoViewport = mapViewModel.viewport
                )
            }

            override fun onRouteBadgeClick(
                routeId: String,
                routeShortName: String,
                directionId: Int?
            ) {
                homeViewModel.requestShowFocusedStopRouteOnMap(
                    routeId,
                    directionId,
                    routeShortName,
                    undoViewport = mapViewModel.viewport
                )
            }

            override fun onVehicleInfoWindowClick(status: ObaTripStatus) {
                MapNavigation.openVehicleTripDetails(
                    context,
                    status,
                    homeViewModel.currentFocus.value.focusedStop?.id
                )
            }

            override fun onBikeInfoWindowClick(station: BikeStation) {
                MapNavigation.openBikeDeepLink(context, station)
            }
        }
    }

    // Bridge Home's outbound map interactions to the map view model. MapFeature is the neutral observer
    // that holds both VMs, so HomeViewModel and MapViewModel need no reference to each other (this
    // replaces the old MapInteractionBus). Both flows are collected straight into the map VM (never into
    // Compose state) so a padding change doesn't recompose this — the map — composable.
    LaunchedEffect(mapViewModel, homeViewModel) {
        homeViewModel.mapBottomPadding.collect { mapViewModel.host.setBottomPadding(it) }
    }
    LaunchedEffect(mapViewModel, homeViewModel) {
        homeViewModel.directionsBottomInset.collect { mapViewModel.host.setDirectionsBottomInset(it) }
    }
    // Publish the floating top chrome's footprint (status-bar inset + FAB-row clearance) as the map's
    // baseline top inset, so the Google compass and centered content clear the FABs instead of drawing at
    // topPx=0 behind them. The status-bar inset read is confined to a snapshotFlow (not composition), so
    // inset churn feeds the VM without recomposing the map — same discipline as the bottom-padding wiring.
    val statusBars = WindowInsets.statusBars
    val density = LocalDensity.current
    LaunchedEffect(mapViewModel, statusBars, density) {
        snapshotFlow { mapTopChromeInsetPx(statusBars.getTop(density), density) }
            .distinctUntilChanged()
            .collect { mapViewModel.host.setTopChromeInset(it) }
    }
    LaunchedEffect(mapViewModel, homeViewModel) {
        homeViewModel.mapDirectives.collect { directive ->
            when (directive) {
                is MapDirective.RecenterOnFocusedStop ->
                    mapViewModel.recenterOnFocusedStop(directive.point)
                is MapDirective.ShowRoute ->
                    mapViewModel.toRoute(
                        directive.request,
                        directive.stopScoped,
                        directive.frameRoute
                    )
                is MapDirective.RestoreViewport ->
                    mapViewModel.restoreViewport(directive.viewport)
                MapDirective.FrameRoute -> mapViewModel.frameRoute()
                is MapDirective.ShowStopRoutes ->
                    mapViewModel.showStopRoutes(
                        directive.stopId,
                        directive.routes,
                        directive.trips
                    )
                MapDirective.ClearStopRoutes -> mapViewModel.clearStopRoutes()
                MapDirective.ClearSelectedRoute -> mapViewModel.clearSelectedRoute()
                MapDirective.ClearFocus -> mapViewModel.clearAllFocus()
                is MapDirective.FocusStop ->
                    mapViewModel.focusStop(
                        directive.stop,
                        directive.routes,
                        directive.overlayExpanded,
                        directive.recenter,
                        directive.animate
                    )
                is MapDirective.ShowItinerary ->
                    mapViewModel.showItinerary(directive.itinerary)
                is MapDirective.FocusItineraryPoint ->
                    mapViewModel.focusItineraryPoint(directive.point)
                is MapDirective.FocusItineraryLeg ->
                    mapViewModel.focusItineraryLeg(directive.points)
                MapDirective.ClearItinerary -> mapViewModel.clearShownItinerary()
                is MapDirective.SetDirectionsEndpoints ->
                    mapViewModel.setDirectionsEndpoints(directive.from, directive.to)
            }
        }
    }

    // One-shot effects -> Compose dialogs / the permission launcher / a toast.
    var dialog by remember { mutableStateOf<MapEffect?>(null) }
    LaunchedEffect(mapViewModel) {
        mapViewModel.effects.collect { effect ->
            when (effect) {
                MapEffect.OutOfRange,
                MapEffect.NoLocation,
                MapEffect.ShowPermissionRationale -> dialog = effect
                MapEffect.RequestLocationPermission ->
                    permissionLauncher.launch(PermissionUtils.LOCATION_PERMISSIONS)
                MapEffect.WaitingForLocation -> Toast.makeText(
                    context,
                    R.string.main_waiting_for_location,
                    Toast.LENGTH_SHORT
                ).show()
                is MapEffect.ShowError -> Toast.makeText(
                    context,
                    ObaRequestErrors.getMapErrorString(context, effect.code),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Eager first-launch permission prompt when the map first shows (was the host's initMap prompt;
    // also drives the deferred first-launch region check via the permission result).
    LaunchedEffect(Unit) {
        mapViewModel.requestLocationPermissionIfNeeded()
    }

    // Resume/pause: the map view model restarts its vehicle poll + refreshes prefs on resume, and
    // persists the camera + stops the poll on pause.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewModel.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (dialog) {
        MapEffect.OutOfRange -> OutOfRangeDialog(
            regionName = mapViewModel.currentRegionName.orEmpty(),
            onConfirm = {
                mapViewModel.zoomToRegion()
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        MapEffect.NoLocation -> NoLocationDialog(
            onEnable = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        MapEffect.ShowPermissionRationale -> PermissionRationaleDialog(
            onOk = {
                PreferenceUtils.setUserDeniedLocationPermissions(context, false)
                permissionLauncher.launch(PermissionUtils.LOCATION_PERMISSIONS)
                dialog = null
            },
            onNoThanks = {
                PreferenceUtils.setUserDeniedLocationPermissions(context, true)
                mapViewModel.onLocationPermissionResult(false)
                homeViewModel.onLocationPermissionResult()
                dialog = null
            }
        )
        else -> {}
    }

    // The map itself. MapFeature composes only when HOME is the current destination, so the SDK init is
    // already deferred to the first HOME view by composition — no separate "mapComposed" latch needed.
    // The cold-launch seed (flash avoidance) comes from the view model, which owns its own mode/camera
    // persistence. remember()ed so cameraSeed's Bundle alloc doesn't re-run; a config change recreates
    // this composition, so MapLibre still re-reads the live camera.
    val seed = remember(mapViewModel) { mapViewModel.cameraSeed }
    val camera by mapViewModel.camera.collectAsStateWithLifecycle()
    ObaMap(
        host = mapViewModel.host,
        callbacks = callbacks,
        modifier = modifier,
        initialLatitude = seed.point.latitude,
        initialLongitude = seed.point.longitude,
        initialZoom = seed.zoom
    )

    // The nearby-stops info notice: "zoom in to see more stops" when the load was truncated (the API's
    // limitExceeded), or "showing saved stops" when a load failed with cached stops on screen (offline,
    // #1754). Driven purely by map state.
    val stopsBanner by mapViewModel.stopsBanner.collectAsStateWithLifecycle()
    val currentFocus by homeViewModel.currentFocus.collectAsStateWithLifecycle()
    // The map is now edge-to-edge (no solid top bar), so this notice floats as a pill at the top-center,
    // below the floating top chrome — the same shared inset (status bar + clearance) the HomeScreen
    // overlays use, so the pill lines up with them and can't drift from the FAB-row height.
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .mapTopChromeOverlayInset()
    ) {
        StopsInfoBanner(
            banner = stopsBanner.forFocus(currentFocus),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        if (BuildConfig.DEBUG && SHOW_DEBUG_ZOOM_INDICATOR) {
            val zoom = camera?.zoom?.toFloat() ?: seed.zoom
            Text(
                text = String.format(
                    Locale.US,
                    "Zoom %.2f · route %.2f×",
                    zoom,
                    routeLineWidthScale(zoom)
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    // The welcome tutorial's map-stop spotlight, wired from the flavor-neutral map seam (the published
    // projector + the shared stop list) so this host knows nothing of the underlying map SDK. A tap
    // focuses the chosen stop exactly like a marker tap, continuing into the arrivals tutorial.
    val mapStopProjector by mapViewModel.renderState.projector.collectAsStateWithLifecycle()
    MapStopSpotlight(
        projector = mapStopProjector,
        currentStops = { mapViewModel.renderState.snapshot.value.stops },
        onFocusStop = { callbacks.onStopClick(it) }
    )

    // The map chrome FABs (my-location / zoom / layers), over the map. The visibility gates are a
    // self-wired feature module ([MapChromeViewModel]); the map-loading bar reads the map VM's progress
    // directly. Their actions drive the map view model.
    val chrome by hiltViewModel<MapChromeViewModel>().state.collectAsStateWithLifecycle()
    val mapLoading by mapViewModel.progress.collectAsStateWithLifecycle()
    MapChrome(
        zoomVisible = chrome.zoomControls,
        leftHandMode = chrome.leftHand,
        layersVisible = chrome.layersFab,
        bikeshareActive = chrome.bikeshareActive,
        mapLoading = mapLoading,
        fabBottomInsetTarget = fabBottomInset,
        onMyLocation = {
            // Reset the prefs that suppress the enable-location / permission prompts, then recenter.
            PreferenceUtils.saveBoolean(
                resources.getString(R.string.preference_key_never_show_location_dialog),
                false
            )
            PreferenceUtils.setUserDeniedLocationPermissions(context, false)
            mapViewModel.requestMyLocation(useDefaultZoom = true, animate = true)
            AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                resources.getString(R.string.analytics_label_button_press_location),
                null
            )
        },
        onZoomIn = { mapViewModel.zoomIn() },
        onZoomOut = { mapViewModel.zoomOut() },
        onToggleBikeshare = {
            val active = LayerUtils.isBikeshareLayerVisible(context)
            // Persist the toggled state (DataStore) + drive the bike loader. MapChromeViewModel observes
            // the visibility pref reactively, so the bikeshare-active tint updates without a host push.
            mapViewModel.setBikeshareLayerVisible(!active, persist = true)
            AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                resources.getString(R.string.analytics_layer_bikeshare),
                resources.getString(
                    if (active) {
                        R.string.analytics_label_bikeshare_deactivated
                    } else {
                        R.string.analytics_label_bikeshare_activated
                    }
                )
            )
        }
    )
}

/** A truncated nearby-stop load is irrelevant while the user is already focused on one stop. */
internal fun StopsBanner.forFocus(focus: CurrentFocus): StopsBanner = if (this == StopsBanner.MoreStopsAvailable && focus is CurrentFocus.Stop) StopsBanner.None else this

/**
 * The nearby-stops info notice: an extended-FAB-style pill (leading icon + text) at the top-center of the
 * map that slides down to appear and up to disappear. Shows either "zoom in to see more stops" (a
 * truncated load) or "showing saved stops" (a failed load with cached stops on screen, #1754); hidden on
 * [StopsBanner.None]. Purely state-driven and informational — no dismiss button, and no action on tap.
 * The caller applies the status-bar inset and the slide clipping (clipToBounds).
 */
@Composable
private fun StopsInfoBanner(banner: StopsBanner, modifier: Modifier = Modifier) {
    // Retain the last shown banner so its label stays put during the slide-out (when banner -> None),
    // instead of blanking mid-animation. Seeded with the more-stops case; only ever set to a real one.
    var lastShown by remember { mutableStateOf<StopsBanner>(StopsBanner.MoreStopsAvailable) }
    if (banner != StopsBanner.None) lastShown = banner
    val (labelRes, iconRes) = when (lastShown) {
        StopsBanner.ShowingSavedStops -> R.string.map_showing_cached_stops to R.drawable.history_24
        else -> R.string.map_zoom_in_for_more_stops to R.drawable.ic_zoom_in
    }
    AnimatedVisibility(
        visible = banner != StopsBanner.None,
        modifier = modifier,
        // Pop into place (scale up from ~80% with a little spring), rather than sliding down from the edge.
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) +
            fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut()
    ) {
        // A plain Row (not a Surface): a non-clickable Surface still consumes pointer events across its
        // bounds, so a pan/tap/pinch that starts on the pill would be swallowed instead of reaching the
        // map underneath. A Row with just background/shadow is not hit-testable, so gestures fall through.
        val pillShape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .shadow(6.dp, pillShape)
                .clip(pillShape)
                // A neutral, informational tint (not a warning), alpha'd so the map shows through slightly.
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The viewport (or device) is outside the current region (ported from GoogleMapHost.showOutOfRange). */
@Composable
private fun OutOfRangeDialog(regionName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_outofrange_title)) },
        text = { Text(stringResource(R.string.main_outofrange, regionName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.main_outofrange_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_outofrange_no)) }
        }
    )
}

/** Location services are off (ported from GoogleMapHost.showNoLocationDialog + its never-ask opt-out). */
@Composable
private fun NoLocationDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    var neverAskAgain by remember { mutableStateOf(false) }
    val neverShowDialogKey = stringResource(R.string.preference_key_never_show_location_dialog)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_nolocation_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.main_nolocation, stringResource(R.string.app_name)))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = neverAskAgain,
                        onCheckedChange = {
                            neverAskAgain = it
                            PreferenceUtils.saveBoolean(neverShowDialogKey, it)
                        }
                    )
                    Text(stringResource(R.string.main_never_ask_again))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEnable) { Text(stringResource(R.string.rt_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rt_no)) }
        }
    )
}

/** Why location permission is needed (ported from GoogleMapHost.showLocationPermissionDialog). */
@Composable
private fun PermissionRationaleDialog(onOk: () -> Unit, onNoThanks: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNoThanks,
        title = { Text(stringResource(R.string.location_permissions_title)) },
        text = { Text(stringResource(R.string.location_permissions_message)) },
        confirmButton = {
            TextButton(onClick = onOk) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onNoThanks) { Text(stringResource(R.string.no_thanks)) }
        }
    )
}
