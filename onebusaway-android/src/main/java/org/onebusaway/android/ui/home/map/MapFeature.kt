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
import android.content.Context
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.flow.map
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.demo.DemoModeController
import org.onebusaway.android.map.MapEffect
import org.onebusaway.android.map.MapNavigation
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.StopsBanner
import org.onebusaway.android.map.compose.ObaMap
import org.onebusaway.android.map.compose.ObaMapCallbacks
import org.onebusaway.android.map.mapBanner
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RouteBadgeTap
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.routeLineWidthScale
import org.onebusaway.android.map.render.stopZoomBand
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.RentalPlace
import org.onebusaway.android.map.settledCamera
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
import org.onebusaway.android.ui.home.nearby.NearbyArrivalsViewModel
import org.onebusaway.android.ui.tripplan.tripPlanningUnavailableMessage
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.MapPointSpotlight
import org.onebusaway.android.ui.tutorial.MapStopSpotlight
import org.onebusaway.android.ui.tutorial.ScriptedTutorial
import org.onebusaway.android.ui.tutorial.tutorialAnchor
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.ObaRequestErrors
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PreferenceUtils

// Temporary calibration aid; retain the implementation so it can be restored with a one-line toggle.
private const val SHOW_DEBUG_ZOOM_INDICATOR = false

/**
 * The self-wiring map feature module: renders [ObaMap] and owns everything that used to be map glue
 * in HomeActivity — the tap callbacks (focus -> the map view model + the home focused stop +
 * analytics; info-window taps -> navigation), the one-shot effects (the no-location /
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
    // The transit-centre drawer's query (#2107). Created by the host (the sheet is its), fed from here,
    // which is where the map view model lives — the same bridge role this module already plays for
    // padding, insets, and directives.
    nearbyArrivalsViewModel: NearbyArrivalsViewModel,
    // The sheet-driven FAB lift, computed by HomeScreen from its live SheetState (the map composes only
    // when HOME is the destination, so this lives with the sheet rather than round-tripping the VM).
    fabBottomInset: Dp,
    modifier: Modifier = Modifier,
    // How tall the stops notice currently is, or 0 with none showing (#2229). Reported because the
    // parked-trip button sits in the *host's* overlay layer and has to clear this one, which lives here —
    // the same padding/inset bridging this module already does.
    onStopsBannerHeight: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Whether the soft keyboard is up. Read through derivedStateOf rather than in composition: the
    // inset updates on every frame of the keyboard animation, and reading it here directly would
    // re-run this whole composable each time for a boolean that flips twice. Same reasoning as the
    // snapshotFlow the chrome insets below go through. Only ever read from onMapClick, outside any
    // snapshot observer, so nothing subscribes to it.
    val imeInsets = WindowInsets.ime
    val imeDensity = LocalDensity.current
    val imeVisible by remember(imeInsets, imeDensity) {
        derivedStateOf { imeInsets.getBottom(imeDensity) > 0 }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

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
                dismissNavigateHere()
                val stop = marker.stop
                val transition = homeViewModel.onStopFocused(
                    FocusedStop(stop.id, stop.name, stop.stopCode, marker.point, stop.wheelchairBoarding),
                    continuingRoutes = marker.presentedRoutes
                )
                // Refused (directions owns the map): leave before the map marks the stop selected,
                // which is the render state that used to outlive the focus and confuse the trip.
                if (transition == StopFocusTransition.Refused) return
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
                // A tap made while the keyboard is up is aimed at the keyboard: half the map is behind
                // it, and the rider is reaching for the part they can see again. So that tap does only
                // that. Without this it also unfocused the map a level, which from the directions form
                // — an editor being typed into, with nothing focused beneath it — meant one stray tap
                // left directions altogether and discarded the trip being entered.
                if (imeVisible) {
                    // Clearing focus is what closes the editor and its suggestion list; hide() covers
                    // a keyboard raised by something that isn't a focused Compose field. A standing
                    // "navigate here" offer deliberately survives this tap for the same reason the
                    // focus does — the tap was aimed at the keyboard, and answering the offer with it
                    // would be the second unasked-for effect this branch exists to prevent. The next
                    // tap, aimed at the map the rider can now see, retires it below.
                    focusManager.clearFocus()
                    keyboard?.hide()
                    return
                }
                dismissNavigateHere()
                homeViewModel.unfocusMapOneLevel()
            }

            override fun onMapLongClick(point: GeoPoint) {
                // The offer's only destination is the trip planner, so it is made only where there is
                // one to reach. In a region that publishes no OTP server the bubble could only ever end
                // in a failed plan, so the gesture is answered with the reason instead — naming the
                // region, because "no trip planning" arriving as "No region selected" is exactly the
                // confusion this replaces (#2264).
                tripPlanningUnavailableMessage(context)?.let { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    return
                }
                // The one gesture that *raises* the offer: drop the pin, which is what the home screen
                // hangs its "navigate here" bubble off (#2243).
                mapViewModel.setNavigateHerePin(point)
            }

            /**
             * Any other tap on the map answers a standing offer by moving on from it: the pin and its
             * bubble go, and the tap does whatever it was for.
             *
             * Called from each tap rather than inferred from a focus change, because the commonest
             * dismissal — a tap on empty map with nothing focused — changes no focus at all. The offer
             * deliberately survives panning and zooming: it names a place, and the rider is allowed to
             * look around it before deciding.
             */
            private fun dismissNavigateHere() = mapViewModel.setNavigateHerePin(null)

            override fun onRentalClick(place: RentalPlace) {
                dismissNavigateHere()
                val focusedId = homeViewModel.currentFocus.value.focusedBikeStationId
                if (focusedId == null || !focusedId.equals(place.id, ignoreCase = true)) {
                    // Refused (directions owns the map): leave before the map tears the trip down —
                    // the same order the stop tap above uses, home focus first, map render after.
                    if (!homeViewModel.onBikeStationFocused(place.id)) return
                    mapViewModel.clearAllFocus()
                }
                AnalyticsEntryPoint.get(context).reportUiEvent(
                    PlausibleAnalytics.REPORT_BIKE_EVENT_URL,
                    resources.getString(
                        if (place.kind == RentalKind.STATION) {
                            R.string.analytics_label_bike_station_marker_clicked
                        } else {
                            R.string.analytics_label_floating_bike_marker_clicked
                        }
                    ),
                    null
                )
            }

            override fun onVehicleClick(status: ObaTripStatus) {
                dismissNavigateHere()
                val tripId = status.activeTripId
                // Tap to select (the trip overlay + most-recent-data dot), tap the selected one again to
                // read it. The bubble this replaces (#2194) put the same navigation behind a chevron the
                // rider had to aim at, and took over the map to offer it. Asked before the selection is
                // driven, since driving it would make every tap look like a re-tap.
                if (mapViewModel.isVehicleReTap(tripId)) {
                    MapNavigation.openVehicleTripDetails(
                        context,
                        status,
                        homeViewModel.currentFocus.value.focusedStop?.id
                    )
                    return
                }
                // The tapped vehicle is a focus level of its own wherever a route is drawn — over a
                // focused stop, a standalone route, or a directions leg (#2205, #2224) — so HOME owns the
                // transition and the selection arrives as its directive; a background tap then unwinds
                // it. Same ask-HOME-then-render shape as the stop and bike taps above.
                homeViewModel.selectFocusedRouteTrip(tripId)
            }

            override fun onRouteContinuationClick(
                routeId: String,
                routeShortName: String,
                directionId: Int?
            ) {
                dismissNavigateHere()
                homeViewModel.advanceRouteContinuation(
                    routeId,
                    routeShortName,
                    directionId,
                    undoViewport = mapViewModel.viewport
                )
            }

            override fun onRouteBadgeClick(badge: RouteBadge) {
                dismissNavigateHere()
                when (val tap = badge.tap) {
                    // An adjacency label (#1827) names a route the rider hasn't opened: open it.
                    is RouteBadgeTap.ShowRoute -> homeViewModel.requestShowFocusedStopRouteOnMap(
                        tap.route.routeId,
                        tap.route.directionId,
                        badge.tappedRouteShortName,
                        undoViewport = mapViewModel.viewport
                    )
                    // A directions label (#2101) names a ride of the trip already being read: focus it
                    // where it is, without trading the itinerary for one route's map.
                    is RouteBadgeTap.FocusItineraryRide ->
                        homeViewModel.onItineraryRideBadgeTapped(tap.legIndices)
                    // A label a producer left inert is never registered as a tap target, so this can't
                    // arrive — named rather than swallowed by an else, so a third kind of tap is a
                    // compile error here (the one place that reads them) instead of a silent no-op.
                    null -> Unit
                }
            }

            override fun onRentalInfoWindowClick(place: RentalPlace) {
                MapNavigation.openRentalLink(context, place)
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
    // Feed the transit-centre drawer's query (#2107) the settled viewport, on the same "settled" the
    // stop and rental loaders use — so it re-queries once at drag-end rather than per intermediate idle.
    // Collected straight into the VM, never into Compose state, so a pan doesn't recompose the map.
    LaunchedEffect(mapViewModel, nearbyArrivalsViewModel) {
        mapViewModel.host.settledCamera()
            .distinctUntilChanged()
            .collect { nearbyArrivalsViewModel.onViewportSettled(it) }
    }
    LaunchedEffect(mapViewModel, nearbyArrivalsViewModel) {
        mapViewModel.renderState.snapshot
            .map { it.stopBand }
            .distinctUntilChanged()
            .collect { nearbyArrivalsViewModel.onStopBand(it) }
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
                is MapDirective.ShowRoute -> {
                    mapViewModel.toRoute(
                        directive.request,
                        directive.stopScoped,
                        directive.frameRoute,
                        directive.withinDirections
                    )
                    // After the route, always, and null included: entering a route tears the previous
                    // session's selection down, so this is what restores the focus's own — and what
                    // stops a route change from leaving a previous trip selected (#2224).
                    mapViewModel.selectVehicleTrip(directive.selectedTripId)
                }
                is MapDirective.RestoreViewport ->
                    mapViewModel.restoreViewport(directive.viewport)
                MapDirective.FrameRoute -> mapViewModel.frameRoute()
                is MapDirective.ShowStopRoutes ->
                    mapViewModel.showStopRoutes(
                        directive.stopId,
                        directive.routes,
                        directive.trips
                    )
                is MapDirective.SetRideArrivals ->
                    mapViewModel.setRideArrivals(directive.stopId, directive.groups)
                MapDirective.ClearStopRoutes -> mapViewModel.clearStopRoutes()
                MapDirective.ClearSelectedRoute -> mapViewModel.clearSelectedRoute()
                is MapDirective.SelectVehicle -> mapViewModel.selectVehicleTrip(directive.tripId)
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
                    mapViewModel.showItinerary(directive.itinerary, directive.pins)
                is MapDirective.FocusItineraryPoint ->
                    mapViewModel.focusItineraryPoint(directive.point)
                is MapDirective.FocusItineraryLeg ->
                    mapViewModel.focusItineraryLeg(directive.points, directive.legIndices)
                MapDirective.ClearItineraryLegFocus -> mapViewModel.clearItineraryLegFocus()
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
        // The scripted tour's opening step rings the whole map ("this is the map"), and its
        // long-press step draws its gesture hint at this surface's centre (#2164).
        modifier = modifier.tutorialAnchor(LocalTutorialState.current, ScriptedTutorial.KEY_MAP),
        initialLatitude = seed.point.latitude,
        initialLongitude = seed.point.longitude,
        initialZoom = seed.zoom
    )

    // The nearby-stops info notice: "zoom in to see more stops" when the load was truncated (the API's
    // limitExceeded), or "showing saved stops" when a load failed with cached stops on screen (offline,
    // #1754). Driven purely by map state.
    val stopsBanner by mapViewModel.stopsBanner.collectAsStateWithLifecycle()
    // The rental layer's own refusal to draw this viewport (#2168) — shown in the same pill, behind
    // whatever the stops loader has to say (see [mapBanner]).
    val rentalsNeedCloserZoom by mapViewModel.rentalsNeedCloserZoom.collectAsStateWithLifecycle()
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
        // Measured through a wrapper that is always here rather than on the notice itself: the notice
        // emits *nothing* when there is nothing to say, and a modifier on something that isn't composed
        // never reports the shrink — it would just leave its last height standing.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { onStopsBannerHeight(it.height) }
        ) {
            StopsInfoBanner(
                banner = mapBanner(stopsBanner.forFocus(currentFocus), rentalsNeedCloserZoom),
                regionName = mapViewModel.currentRegionName.orEmpty(),
                onViewServiceArea = mapViewModel::zoomToRegion
            )
        }
        if (BuildConfig.DEBUG && SHOW_DEBUG_ZOOM_INDICATOR) {
            val zoom = camera?.zoom?.toFloat() ?: seed.zoom
            Text(
                text = String.format(
                    Locale.US,
                    "Zoom %.2f · route %.2f× · %s",
                    zoom,
                    routeLineWidthScale(zoom),
                    // The stop band this zoom falls in, so the dot/full/routes thresholds can be tuned
                    // against the map rather than by arithmetic (#2107).
                    stopZoomBand(zoom)
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

    // The scripted tour's map-stop spotlight, wired from the flavor-neutral map seam (the published
    // projector + the shared stop list) so this host knows nothing of the underlying map SDK.
    val mapStopProjector by mapViewModel.renderState.projector.collectAsStateWithLifecycle()
    MapStopSpotlight(
        projector = mapStopProjector,
        currentStops = { mapViewModel.renderState.snapshot.value.stops },
        targetStopId = DemoModeController.ANCHOR_STOP_ID
    )
    // The tour's long-press step rings the place the rider would press — the demo trip's destination —
    // which has no marker of its own until the trip is planned.
    MapPointSpotlight(
        projector = mapStopProjector,
        point = DemoModeController.TRIP_PLAN_DESTINATION,
        anchorId = ScriptedTutorial.KEY_TRIP_DESTINATION
    )
    // ...and for the length of that step the map also *answers* the mimed press, so the caption's "then
    // tap Navigate here" points at something that is really there (#2243).
    TourNavigateHerePin(mapViewModel)

    // The map chrome FABs (my-location / zoom / layers), over the map. The visibility gates are a
    // self-wired feature module ([MapChromeViewModel]); the map-loading bar reads the map VM's progress
    // directly. Their actions drive the map view model.
    val chrome by hiltViewModel<MapChromeViewModel>().state.collectAsStateWithLifecycle()
    val mapLoading by mapViewModel.progress.collectAsStateWithLifecycle()
    val rentalsLoading by mapViewModel.rentalsLoading.collectAsStateWithLifecycle()
    MapChrome(
        zoomVisible = chrome.zoomControls,
        leftHandMode = chrome.leftHand,
        // Hidden while directions own the map (#2168): the layer draws nothing there, so a button
        // offering to toggle it would be inert — and directions already crowd this corner.
        layersVisible = chrome.layersFab && currentFocus !is CurrentFocus.Directions,
        rentalsActive = chrome.rentalsActive,
        bikesActive = chrome.bikesActive,
        scootersActive = chrome.scootersActive,
        rentalsLoading = rentalsLoading,
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
        onToggleRentals = { toggleRentals(context, mapViewModel, chrome.rentalsActive) },
        onToggleBikes = { mapViewModel.setRentalLayerVisible(RentalLayer.BIKES, !chrome.bikesActive) },
        onToggleScooters = { mapViewModel.setRentalLayerVisible(RentalLayer.SCOOTERS, !chrome.scootersActive) },
        onHideRentalButton = {
            PreferenceUtils.saveBoolean(resources.getString(R.string.preference_key_show_rental_button), false)
            // The button is the only signpost to itself, so hiding it without saying where it went
            // would look like a bug. The toast names Settings, which is the one way back.
            Toast.makeText(context, R.string.layers_rentals_hidden_toast, Toast.LENGTH_LONG).show()
        }
    )
}

/**
 * Shows or hides the rental layers: persist the new value (DataStore) and drive the loader.
 * [MapChromeViewModel] observes the visibility preference reactively, so the button's tint updates
 * without a host push.
 *
 * Reports to the long-standing bikeshare analytics event rather than a new one, so the series that
 * has been counting "the rider changed the rental overlay" keeps counting the same thing.
 */
private fun toggleRentals(
    context: Context,
    mapViewModel: MapViewModel,
    active: Boolean
) {
    mapViewModel.setRentalsVisible(!active)
    AnalyticsEntryPoint.get(context).reportUiEvent(
        PlausibleAnalytics.REPORT_MAP_EVENT_URL,
        context.getString(R.string.analytics_layer_bikeshare),
        context.getString(
            if (active) R.string.analytics_label_bikeshare_deactivated else R.string.analytics_label_bikeshare_activated
        )
    )
}

/** A truncated nearby-stop load is irrelevant while the user is already focused on one stop. */
internal fun StopsBanner.forFocus(focus: CurrentFocus): StopsBanner = if (this == StopsBanner.MoreStopsAvailable && focus is CurrentFocus.Stop) StopsBanner.None else this

/**
 * The nearby-stops notice: a pill at the top-center of the map. Shows "zoom in to see more stops" (a
 * truncated load), "showing saved stops" (a failed load with cached stops on screen, #1754), or an
 * out-of-region message with an action that frames the service area. Hidden on [StopsBanner.None].
 */
@Composable
private fun StopsInfoBanner(
    banner: StopsBanner,
    regionName: String,
    onViewServiceArea: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Retain the last shown banner so its label stays put during the slide-out (when banner -> None),
    // instead of blanking mid-animation. Seeded with the more-stops case; only ever set to a real one.
    var lastShown by remember { mutableStateOf<StopsBanner>(StopsBanner.MoreStopsAvailable) }
    if (banner != StopsBanner.None) lastShown = banner
    val iconRes = when (lastShown) {
        StopsBanner.None,
        StopsBanner.MoreStopsAvailable -> R.drawable.ic_zoom_in
        StopsBanner.ZoomInForRentals -> R.drawable.ic_zoom_in
        StopsBanner.ShowingSavedStops -> R.drawable.history_24
        StopsBanner.OutsideRegion -> R.drawable.ic_action_location_map
    }
    AnimatedVisibility(
        visible = banner != StopsBanner.None,
        // The actionable banner is wider than the informational pills, so place it below the
        // top-right weather chip instead of letting that sibling obscure its action.
        modifier = modifier.padding(top = if (lastShown == StopsBanner.OutsideRegion) 56.dp else 0.dp),
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
            if (lastShown == StopsBanner.OutsideRegion) {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(
                        text = stringResource(R.string.map_outside_service_area, regionName),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onViewServiceArea,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(stringResource(R.string.map_view_service_area))
                    }
                }
            } else {
                Text(
                    text = when (lastShown) {
                        StopsBanner.ShowingSavedStops -> stringResource(R.string.map_showing_cached_stops)
                        StopsBanner.ZoomInForRentals -> stringResource(R.string.map_zoom_in_for_rentals)
                        else -> stringResource(R.string.map_zoom_in_for_more_stops)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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

/**
 * The pin the scripted tour's long-press step would have left, dropped for the length of that step and
 * taken away again (#2243).
 *
 * Here beside [MapPointSpotlight] — the sibling that rings the same place — rather than in the host:
 * the tour's press is *map* content, this is where the map view model and the demo system's coordinates
 * already are, and reading the tutorial state in a composable this small keeps a step change from
 * recomposing the whole home screen.
 *
 * The tour never presses the map itself (the spotlight overlay swallows every touch), so this is the one
 * writer of that pin which isn't a gesture — and it is a [DisposableEffect] because the teardown is the
 * point: a tour abandoned on this step must not leave a pin behind.
 */
@Composable
private fun TourNavigateHerePin(mapViewModel: MapViewModel) {
    val onPressStep = LocalTutorialState.current?.current?.id == ScriptedTutorial.STEP_PLAN_PRESS
    DisposableEffect(onPressStep) {
        if (onPressStep) mapViewModel.setNavigateHerePin(DemoModeController.TRIP_PLAN_DESTINATION)
        onDispose { if (onPressStep) mapViewModel.setNavigateHerePin(null) }
    }
}
