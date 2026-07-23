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
package org.onebusaway.android.ui.home.directions

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import java.util.TimeZone
import org.onebusaway.android.R
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.rememberArrivalRowCallbacks
import org.onebusaway.android.ui.compose.components.DRAG_HANDLE_HEIGHT
import org.onebusaway.android.ui.compose.components.DRAG_HANDLE_VERTICAL_PADDING
import org.onebusaway.android.ui.compose.components.DragHandleBar
import org.onebusaway.android.ui.compose.components.SwitchRow
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.arrivals.rememberArrivalsSession
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.ui.tripplan.AdvancedSettings
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModes
import org.onebusaway.android.ui.tripplan.TripPlanError
import org.onebusaway.android.ui.tripplan.TripPlanForm
import org.onebusaway.android.ui.tripplan.TripPlanFormState
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.TripPlanViewModel
import org.onebusaway.android.ui.tripresults.RouteLegRef
import org.onebusaway.android.ui.tripresults.RouteStopRef
import org.onebusaway.android.ui.tripresults.TripResultsSheet
import org.onebusaway.android.ui.tripresults.TripResultsViewModel
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PreferenceUtils

/**
 * The trip planner embedded on the home map as a directions focus. This file hosts the on-map surfaces:
 * [DirectionsFormCard] (the compact form that replaces the top-chrome search field),
 * [DirectionsResultsSheet] (the bottom directions list), and [DirectionsPickOverlay] (pick a From/To
 * point directly on the home map). The itinerary itself renders on the shared home map via the
 * [MapViewModel] directions controller — driven by [TripResultsSheet]'s selection.
 *
 * The address-book (contacts) picker was removed (#1936 tracks accepting place intents from other apps
 * instead). Map-pick is hoisted to the caller ([onPickFrom]/[onPickTo]); current-location, date/time,
 * and advanced settings are wired here.
 */

/** Which endpoint a map-pick is currently choosing. */
enum class DirectionsPickTarget { FROM, TO }

/**
 * The compact trip-plan form, shown in the top chrome (in place of the search field) while planning.
 * No header/close chrome — exiting directions is a back gesture (handled in HomeScreen).
 */
@Composable
fun DirectionsFormCard(
    viewModel: TripPlanViewModel,
    state: TripPlanFormState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var showAdvanced by remember { mutableStateOf(false) }

    // containerSize (px) reflects the actual available window; Configuration.screenHeightDp is lint-flagged
    // as unreliable across insets/multi-window. Match the HomeScreen peek-cap pattern.
    val maxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.6f).toDp()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            TripPlanForm(
                state = state,
                onFromQueryChange = viewModel::onFromQueryChange,
                onToQueryChange = viewModel::onToQueryChange,
                onSelectFrom = viewModel::setFrom,
                onSelectTo = viewModel::setTo,
                onClearFrom = viewModel::clearFrom,
                onClearTo = viewModel::clearTo,
                onFromCurrentLocation = { setCurrentLocation(context, viewModel::setFrom) },
                onToCurrentLocation = { setCurrentLocation(context, viewModel::setTo) },
                onFromPickOnMap = onPickFrom,
                onToPickOnMap = onPickTo,
                onSetArriving = viewModel::setArriving,
                onPickDate = { pickTripDate(activity, viewModel) },
                onPickTime = { pickTripTime(activity, viewModel) },
                onReverse = viewModel::reverseTrip,
                onAdvancedSettings = { showAdvanced = true }
            )
        }
    }
    if (showAdvanced) {
        DirectionsAdvancedSettingsDialog(viewModel = viewModel, onDismiss = { showAdvanced = false })
    }
}

/** Set an endpoint to the device's last-known location, or toast if none is available. */
private fun setCurrentLocation(context: Context, target: (TripEndpoint) -> Unit) {
    val location = LocationEntryPoint.get(context.applicationContext).lastKnownLocation()
    if (location == null) {
        // A null fix means "no permission" only when permission is actually denied; with permission
        // granted it just means we don't have a fix yet, which is a different (recoverable) message.
        val messageRes = if (PermissionUtils.hasGrantedAtLeastOnePermission(
                context,
                PermissionUtils.LOCATION_PERMISSIONS
            )
        ) {
            R.string.main_waiting_for_location
        } else {
            R.string.no_location_permission
        }
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        return
    }
    target(TripEndpoint.CurrentLocation(lat = location.latitude, lon = location.longitude))
}

private fun pickTripDate(activity: AppCompatActivity, viewModel: TripPlanViewModel) {
    val current = viewModel.formState.value.dateTimeMillis
    val picker = MaterialDatePicker.Builder.datePicker()
        .setTitleText(R.string.trip_plan_date)
        .setSelection(current)
        .build()
    picker.addOnPositiveButtonClickListener { selection ->
        // Read the selection in UTC, exactly as the user saw it (matches the legacy form).
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = current
            set(Calendar.YEAR, utc.get(Calendar.YEAR))
            set(Calendar.MONTH, utc.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        }
        viewModel.setDateTime(calendar.timeInMillis)
    }
    picker.show(activity.supportFragmentManager, "DATE_PICKER")
}

private fun pickTripTime(activity: AppCompatActivity, viewModel: TripPlanViewModel) {
    val current = viewModel.formState.value.dateTimeMillis
    val calendar = Calendar.getInstance().apply { timeInMillis = current }
    val timeFormat =
        if (DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
    val picker = MaterialTimePicker.Builder()
        .setTimeFormat(timeFormat)
        .setHour(calendar.get(Calendar.HOUR_OF_DAY))
        .setMinute(calendar.get(Calendar.MINUTE))
        .setTitleText(R.string.trip_plan_time)
        .setTheme(R.style.ThemeOverlay_App_TimePicker)
        .build()
    picker.addOnPositiveButtonClickListener {
        calendar.set(Calendar.HOUR_OF_DAY, picker.hour)
        calendar.set(Calendar.MINUTE, picker.minute)
        viewModel.setDateTime(calendar.timeInMillis)
    }
    picker.show(activity.supportFragmentManager, "TIME_PICKER")
}

/** Collapsed height of the directions sheet content — just the drag handle peeking above the map. */
private val DIRECTIONS_SHEET_PEEK = DRAG_HANDLE_HEIGHT

/** Net drag (px per gesture event) on the handle past which the sheet snaps collapsed/expanded. */
private const val DIRECTIONS_SHEET_DRAG_THRESHOLD = 8f

/**
 * The bottom directions sheet: option cards + the step-by-step list, over the map. Collapsible via the
 * drag handle at its top — tap or drag it down to collapse to just the handle (revealing the map), up to
 * restore the full ~40%-height sheet.
 */
@Composable
fun DirectionsResultsSheet(
    resultsViewModel: TripResultsViewModel,
    itineraries: List<TripItinerary>,
    params: TripPlanParams?,
    showItinerary: (TripItinerary) -> Unit,
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit,
    onFocusLeg: (List<GeoPoint>) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit,
    modifier: Modifier = Modifier
) {
    // The system nav-bar inset: the surface reaches the bottom edge (continuous background), but its
    // content is padded above the nav chrome so the collapsed handle (and the last list row) aren't
    // stranded under the gesture pill / 3-button bar. Collapsed height includes it so the handle fits.
    val navBottom = navigationBarBottomPadding()
    // containerSize (px), not Configuration.screenHeightDp (lint-flagged as unreliable across insets).
    val fullHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.4f).toDp()
    }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val sheetHeight by animateDpAsState(
        targetValue = if (collapsed) DIRECTIONS_SHEET_PEEK + navBottom else fullHeight,
        label = "directionsSheetHeight"
    )
    Surface(
        modifier = modifier.fillMaxWidth().height(sheetHeight),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            DirectionsSheetHandle(collapsed = collapsed, onSetCollapsed = { collapsed = it })
            TripResultsSheet(
                itineraries = itineraries,
                params = params,
                resultsViewModel = resultsViewModel,
                showItinerary = showItinerary,
                onFocusRouteLeg = onFocusRouteLeg,
                onFocusLeg = onFocusLeg,
                onFocusPoint = onFocusPoint,
                stopEtaStrip = stopEtaStrip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The drag handle atop [DirectionsResultsSheet]: tap toggles collapse; drag up/down sets it. */
@Composable
private fun DirectionsSheetHandle(collapsed: Boolean, onSetCollapsed: (Boolean) -> Unit) {
    // draggable() reports per-frame deltas, so accumulate them — a slow drag would never cross the
    // threshold on any single frame. Reset once a threshold crossing snaps the sheet, and at drag end.
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSetCollapsed(!collapsed) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    dragAccumulated += delta
                    if (dragAccumulated > DIRECTIONS_SHEET_DRAG_THRESHOLD) {
                        onSetCollapsed(true)
                        dragAccumulated = 0f
                    } else if (dragAccumulated < -DIRECTIONS_SHEET_DRAG_THRESHOLD) {
                        onSetCollapsed(false)
                        dragAccumulated = 0f
                    }
                },
                onDragStopped = { dragAccumulated = 0f }
            )
            .padding(vertical = DRAG_HANDLE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        DragHandleBar()
    }
}

/**
 * The inline ETA strip shown under a transit leg's Board / Alight row: the live arrivals for that leg's
 * route at [stop], rendered as the same horizontally-scrollable pill strip the arrivals drawer uses (tap
 * a pill to focus its vehicle, long-press for the trip menu — wired through [rememberArrivalRowCallbacks]).
 * Spins up a per-stop arrivals session keyed to [stop] — so it polls only while shown — and picks the
 * route group matching the leg (by route id, then headsign). Ids on [routeLeg]/[stop] are OBA-format.
 */
@Composable
fun DirectionStopEtaStrip(
    routeLeg: RouteLegRef,
    stop: RouteStopRef,
    arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    onShowTrip: (tripId: String, stopId: String) -> Unit,
    onEditReminder: (ReminderEditorArgs) -> Unit,
    onFocusVehicle: (ShowRouteRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val stopId = stop.stopId
    val point = stop.point
    val rowPadding = Modifier.fillMaxWidth().padding(start = 36.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)
    if (stopId == null || point == null) {
        NoEtasText(rowPadding)
        return
    }
    val session = rememberArrivalsSession(
        focusedStop = FocusedStop(id = stopId, name = stop.name, code = stop.stopCode, point = point),
        sheetVisible = true,
        arrivalsViewModelFactory = arrivalsViewModelFactory,
        tutorialState = null,
        onArrivalsLoaded = {},
        // A pill tap (via the shared handler's onFocusVehicleOnMap) lands here with a focusTripId
        // request — hand it to the host to focus/animate/ping that vehicle, reusing the arrivals path.
        revealRoute = { _, request -> onFocusVehicle(request) },
        onShowTrip = onShowTrip,
        onEditReminder = onEditReminder,
        showUndoSnackbar = { _, _, _ -> }
    ) ?: return
    val state by session.viewModel.state.collectAsStateWithLifecycle()
    val callbacks = rememberArrivalRowCallbacks(session.handler, session.viewModel)

    val content = state as? ArrivalsUiState.Content ?: return // nothing until the first load lands
    val group = content.routeGroups.pickForLeg(routeLeg)
    if (group == null) {
        NoEtasText(rowPadding)
        return
    }
    EtaStrip(
        trips = group.trips,
        actionsFor = { content.actions[it.tripId] },
        callbacks = callbacks,
        modifier = modifier.then(rowPadding)
    )
}

/** The leg's route group at this stop: matched by OBA route id, preferring the leg's headsign. */
private fun List<RouteRowGroup>.pickForLeg(routeLeg: RouteLegRef): RouteRowGroup? {
    val forRoute = filter { it.routeId == routeLeg.routeId }
    val headsign = routeLeg.headsign
    return forRoute.firstOrNull { headsign != null && it.headsign.equals(headsign, ignoreCase = true) }
        ?: forRoute.firstOrNull()
}

@Composable
private fun NoEtasText(modifier: Modifier) {
    Text(
        text = stringResource(R.string.directions_stop_no_arrivals),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * The modal menu shown when the user long-presses the map: "directions from here" / "directions to
 * here", each of which enters directions focus and fills that endpoint with the pressed point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsLongPressMenu(
    onFromHere: () -> Unit,
    onToHere: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.directions_from_here)) },
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onFromHere)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.directions_to_here)) },
                leadingContent = { Icon(AppIcons.Place, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onToHere)
            )
        }
    }
}

/**
 * The trip-plan error surface: a Material 3 snackbar (the `inverseSurface` container, seated at the
 * bottom of the map where the results sheet would be). It shows a stable, severity-coloured header
 * naming the category (every no-route failure reads "Cannot find route") over a single reason line —
 * the exact wire message — with an explicit dismiss. Unlike a toast or an auto-dismissing snackbar it
 * persists until the user dismisses it (or the plan state changes), so a deterministic failure —
 * outside coverage, no route, no service at this time — isn't lost if the user glances away. Header =
 * the "richer ontology" ([TripPlanError.category]); reason = the specifics ([TripPlanError.detailRes]).
 */
@Composable
fun DirectionsErrorSnackbar(
    error: TripPlanError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        // A polite live region so a screen reader announces a newly surfaced planning failure.
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(error.category.headerRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorResource(error.category.severity.colorRes)
                )
                Text(
                    text = stringResource(error.detailRes),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

/**
 * Pick a From/To point directly on the home map: a fixed center crosshair the map pans under, a top
 * hint for which endpoint is being set, and a bottom confirm. [onConfirm] captures the map's current
 * center; the caller resolves it to a [org.onebusaway.android.ui.tripplan.TripEndpoint.MapPoint].
 */
@Composable
fun BoxScope.DirectionsPickOverlay(
    target: DirectionsPickTarget,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (target == DirectionsPickTarget.FROM) {
                        R.string.trip_plan_from
                    } else {
                        R.string.trip_plan_to
                    }
                ),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onCancel) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.close))
            }
        }
    }
    // Fixed, non-interactive center crosshair: the map pans under it and its center is the chosen point.
    Icon(
        painter = painterResource(R.drawable.ic_my_location),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.Center).size(48.dp)
    )
    Button(
        onClick = onConfirm,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Text(stringResource(R.string.trip_plan_use_this_location))
    }
}

/** Advanced trip options (travel-by mode, max walk, minimize transfers, wheelchair). */
@Composable
private fun DirectionsAdvancedSettingsDialog(
    viewModel: TripPlanViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val imperial = remember { !PreferenceUtils.getUnitsAreMetricFromPreferences(context) }
    // (display label, trip-mode code) for each option, dropping bikeshare modes when unavailable.
    val options = remember(resources) {
        val typed = resources.obtainTypedArray(R.array.transit_mode_array)
        val labels = resources.getStringArray(R.array.transit_mode_array)
        val all = (0 until typed.length()).map { i ->
            labels[i] to TripModes.getTripModeCodeFromSelection(typed.getResourceId(i, 0))
        }
        typed.recycle()
        if (BikeshareAvailability.isEnabled(context)) {
            all
        } else {
            all.filter { it.second != TripModes.BIKESHARE && it.second != TripModes.TRANSIT_AND_BIKE }
        }
    }
    val current = remember { viewModel.formState.value }
    var selectedMode by remember {
        mutableIntStateOf(
            options.firstOrNull { it.second == current.modeId }?.second ?: options.first().second
        )
    }
    var minimizeTransfers by remember { mutableStateOf(current.optimizeTransfers) }
    var wheelchair by remember { mutableStateOf(current.wheelchair) }
    var maxWalk by remember {
        mutableStateOf(
            current.maxWalkMeters?.let {
                (if (imperial) ConversionUtils.metersToFeet(it) else it).toLong().toString()
            }.orEmpty()
        )
    }
    var expanded by remember { mutableStateOf(false) }

    // Preference keys resolved in composition (stringResource) so the confirm handler doesn't read
    // resource values off LocalContext.current (lint: LocalContextGetResourceValueCall).
    val prefKeyTravelBy = stringResource(R.string.preference_key_trip_plan_travel_by)
    val prefKeyMaxWalk = stringResource(R.string.preference_key_trip_plan_maximum_walking_distance)
    val prefKeyMinimizeTransfers = stringResource(R.string.preference_key_trip_plan_minimize_transfers)
    val prefKeyAvoidStairs = stringResource(R.string.preference_key_trip_plan_avoid_stairs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_plan_advanced_settings)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.travel_by_label))
                    Spacer(Modifier.width(8.dp))
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(options.firstOrNull { it.second == selectedMode }?.first.orEmpty())
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { (label, code) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedMode = code
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.maximum_walk_distance), modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = maxWalk,
                        onValueChange = { new -> maxWalk = new.filter { it.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (imperial) R.string.feet_abbreviation else R.string.meters_abbreviation
                        )
                    )
                }
                SwitchRow(
                    label = stringResource(R.string.minimize_transfers),
                    checked = minimizeTransfers,
                    onCheckedChange = { minimizeTransfers = it }
                )
                SwitchRow(
                    label = stringResource(R.string.wheelchair_accessible),
                    checked = wheelchair,
                    onCheckedChange = { wheelchair = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val maxWalkMeters: Double? = maxWalk.takeIf { it.isNotEmpty() }?.toDouble()?.let {
                    if (imperial) ConversionUtils.feetToMeters(it) else it
                }
                viewModel.applyAdvancedSettings(
                    AdvancedSettings(selectedMode, maxWalkMeters, minimizeTransfers, wheelchair)
                )
                PreferenceUtils.saveInt(prefKeyTravelBy, selectedMode)
                PreferenceUtils.saveDouble(prefKeyMaxWalk, maxWalkMeters ?: Double.MAX_VALUE)
                PreferenceUtils.saveBoolean(prefKeyMinimizeTransfers, minimizeTransfers)
                PreferenceUtils.saveBoolean(prefKeyAvoidStairs, wheelchair)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        }
    )
}
