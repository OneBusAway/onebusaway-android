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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.map.MapEffect
import org.onebusaway.android.map.MapNavigation
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.compose.ObaMap
import org.onebusaway.android.map.compose.ObaMapCallbacks
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.HomeViewModel
import org.onebusaway.android.ui.home.MapDirective
import org.onebusaway.android.ui.tutorial.MapStopSpotlight
import org.onebusaway.android.util.LayerUtils
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PreferenceUtils
import org.opentripplanner.routing.bike_rental.BikeRentalStation

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
) {
    val context = LocalContext.current
    val resources = LocalResources.current

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
            override fun onStopClick(stop: ObaStop) {
                mapViewModel.onStopTapped(stop)
                // Already focused on this stop? Then don't re-fire the home focus + analytics.
                val focusedId = homeViewModel.uiState.value.focusedStop?.id
                if (focusedId != null && focusedId.equals(stop.id, ignoreCase = true)) {
                    return
                }
                homeViewModel.onStopFocused(
                    FocusedStop(stop.id, stop.name, stop.stopCode, stop.latitude, stop.longitude)
                )
                AnalyticsEntryPoint.get(context).reportUiEvent(
                    PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                    resources.getString(R.string.analytics_label_button_press_map_icon),
                    null,
                )
            }

            override fun onMapClick(point: GeoPoint?) {
                mapViewModel.onMapTapped()
                homeViewModel.onStopFocused(null)
                homeViewModel.onBikeStationFocused(null)
            }

            override fun onBikeClick(station: BikeRentalStation) {
                val bikeId = homeViewModel.uiState.value.focusedBikeStationId
                if (bikeId == null || !bikeId.equals(station.id, ignoreCase = true)) {
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
                    null,
                )
            }

            override fun onVehicleClick(status: ObaTripStatus) {
                mapViewModel.onVehicleTapped(status)
            }

            override fun onVehicleInfoWindowClick(status: ObaTripStatus) {
                MapNavigation.openVehicleTripDetails(
                    context, status, homeViewModel.uiState.value.focusedStop?.id
                )
            }

            override fun onBikeInfoWindowClick(station: BikeRentalStation) {
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
        homeViewModel.mapDirectives.collect { directive ->
            when (directive) {
                is MapDirective.RecenterOnFocusedStop ->
                    mapViewModel.recenterOnFocusedStop(directive.lat, directive.lon)
                is MapDirective.ShowRoute -> mapViewModel.toRoute(directive.request)
                MapDirective.ClearFocus -> mapViewModel.clearFocus()
                is MapDirective.FocusStop ->
                    mapViewModel.focusStop(directive.stop, directive.routes, directive.overlayExpanded)
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
                    context, R.string.main_waiting_for_location, Toast.LENGTH_SHORT
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
            onConfirm = { mapViewModel.zoomToRegion(); dialog = null },
            onDismiss = { dialog = null },
        )
        MapEffect.NoLocation -> NoLocationDialog(
            onEnable = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        MapEffect.ShowPermissionRationale -> PermissionRationaleDialog(
            onOk = {
                PreferenceUtils.setUserDeniedLocationPermissions(false)
                permissionLauncher.launch(PermissionUtils.LOCATION_PERMISSIONS)
                dialog = null
            },
            onNoThanks = {
                PreferenceUtils.setUserDeniedLocationPermissions(true)
                mapViewModel.onLocationPermissionResult(false)
                homeViewModel.onLocationPermissionResult()
                dialog = null
            },
        )
        else -> {}
    }

    // The map itself. MapFeature composes only when HOME is the current destination, so the SDK init is
    // already deferred to the first HOME view by composition — no separate "mapComposed" latch needed.
    // The cold-launch seed (flash avoidance) comes from the view model, which owns its own mode/camera
    // persistence. remember()ed so cameraSeed's Bundle alloc doesn't re-run; a config change recreates
    // this composition, so MapLibre still re-reads the live camera.
    val seed = remember(mapViewModel) { mapViewModel.cameraSeed }
    ObaMap(
        host = mapViewModel.host,
        callbacks = callbacks,
        modifier = modifier,
        initialLatitude = seed.lat,
        initialLongitude = seed.lon,
        initialZoom = seed.zoom,
    )

    // "Zoom in to see more stops" — shown when the viewport stop load was truncated (the API's
    // limitExceeded), i.e. more stops match the viewport than were returned. Driven purely by map state.
    val moreStops by mapViewModel.moreStopsAvailable.collectAsStateWithLifecycle()
    // The map sits below HomeTopBar (which already consumes the status-bar inset), so the banner is
    // flush at the map's top edge — no extra inset. clipToBounds clips the upward slide at that edge so
    // the bar tucks up behind the title bar instead of drawing over it.
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        MoreStopsBanner(
            visible = moreStops,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        )
    }

    // The welcome tutorial's map-stop spotlight, wired from the flavor-neutral map seam (the published
    // projector + the shared stop list) so this host knows nothing of the underlying map SDK. A tap
    // focuses the chosen stop exactly like a marker tap, continuing into the arrivals tutorial.
    val mapStopProjector by mapViewModel.renderState.projector.collectAsStateWithLifecycle()
    MapStopSpotlight(
        projector = mapStopProjector,
        currentStops = { mapViewModel.renderState.snapshot.value.stops },
        onFocusStop = { callbacks.onStopClick(it) },
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
                resources.getString(R.string.preference_key_never_show_location_dialog), false
            )
            PreferenceUtils.setUserDeniedLocationPermissions(false)
            mapViewModel.requestMyLocation(useDefaultZoom = true, animate = true)
            AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                resources.getString(R.string.analytics_label_button_press_location),
                null,
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
                ),
            )
        },
    )
}

/**
 * The "zoom in to see more stops" hint: a full-width, text-height bar at the top edge of the map that
 * slides down to appear and up (tucking behind the home top bar) to disappear. Shown while the last
 * viewport stop load was truncated by the API; purely state-driven, no dismiss button. The status-bar
 * inset is already consumed by HomeTopBar above the map, so the caller's slot adds no inset — only the
 * slide clipping (clipToBounds).
 */
@Composable
private fun MoreStopsBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
    ) {
        // A plain Box (not a Surface): a non-clickable Surface still consumes pointer events across its
        // bounds, so a pan/tap/pinch that starts on the banner strip would be swallowed instead of
        // reaching the map underneath. A Box with just background/shadow is not hit-testable, so gestures
        // fall through to the map.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp)
                // A neutral, informational tint (not a warning), alpha'd so the map shows through slightly.
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
        ) {
            Text(
                text = stringResource(R.string.map_zoom_in_for_more_stops),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        },
    )
}

/** Location services are off (ported from GoogleMapHost.showNoLocationDialog + its never-ask opt-out). */
@Composable
private fun NoLocationDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    var neverAskAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_nolocation_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.main_nolocation, stringResource(R.string.app_name)))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = neverAskAgain,
                        onCheckedChange = {
                            neverAskAgain = it
                            PreferenceUtils.saveBoolean(
                                Application.get()
                                    .getString(R.string.preference_key_never_show_location_dialog),
                                it,
                            )
                        },
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
        },
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
        },
    )
}
