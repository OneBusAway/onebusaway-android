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
package org.onebusaway.android.ui.tripplan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.compose.components.SwitchRow
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.map.DirectionsMapViewModel
import org.onebusaway.android.ui.tripresults.TripResultsMap
import org.onebusaway.android.ui.tripresults.TripResultsSheet
import org.onebusaway.android.ui.tripresults.TripResultsViewModel
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.PreferenceUtils
import org.opentripplanner.api.model.Itinerary


/**
 * The trip-plan NavHost destination. Ports the Android glue that the former
 * [org.onebusaway.android.ui.TripPlanActivity] owned — the date/time pickers, the contacts picker,
 * current-location reads, the advanced-options dialog, the error dialog + analytics, and rehydrating
 * from a RealtimeService trip-update notification — into Compose, wiring it to [TripPlanRoute].
 * State lives in the Hilt [TripPlanViewModel].
 */
@Composable
fun TripPlanDestination(navController: NavHostController, onBack: () -> Unit) {
    val viewModel = hiltViewModel<TripPlanViewModel>()
    val activity = LocalContext.current.findActivity()

    // Built once for the lifetime of this destination (analytics + region email for "report problem").
    // HomeActivity doesn't inject RegionRepository; reach the shared singleton via the EntryPoint.
    val regionRepository = remember { RegionEntryPoint.get(activity) }

    // -- Contacts pick: a launcher + the endpoint a pending pick should populate. A contacts pick
    // doesn't dispose this composable, so a plain remember (not rememberSaveable) suffices.
    var contactsTarget by remember { mutableStateOf<((TripEndpoint) -> Unit)?>(null) }
    val contactsLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            formattedAddress(activity, uri)?.let { address ->
                contactsTarget?.invoke(TripEndpoint.AddressBook(address, lat = null, lon = null))
            }
        }
        contactsTarget = null
    }
    val launchContacts: ((TripEndpoint) -> Unit) -> Unit = { target ->
        contactsTarget = target
        contactsLauncher.launch(
            Intent(Intent.ACTION_PICK)
                .setType(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_TYPE)
        )
    }

    // -- Map pick: instead of an ActivityResult launcher, navigate to the picker destination and read
    // the chosen point back from this entry's SavedStateHandle. The pending endpoint ("from"/"to")
    // MUST be rememberSaveable: navigating to the picker disposes this composable, and a lambda can't
    // be saved across that — so we save which endpoint to populate, not the setter.
    var mapPickTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val launchMapPicker: (String, TripEndpoint?) -> Unit = { endpoint, initial ->
        val center = if (initial?.hasCoordinates == true) {
            LocationUtils.makeLocation(initial.lat!!, initial.lon!!)
        } else {
            LocationUtils.getSearchCenter(activity.applicationContext)
        }
        mapPickTarget = endpoint
        navController.navigate(
            NavRoutes.tripPlanPickLocation(center?.latitude, center?.longitude)
        )
    }
    // When the picker hands a result back to this entry's SavedStateHandle, build the endpoint and
    // dispatch it to the saved endpoint, then clear the keys + the target.
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle, mapPickTarget) {
        val handle = savedStateHandle ?: return@LaunchedEffect
        val lat = handle.get<Double>(NavRoutes.RESULT_PICK_LAT)
        val lon = handle.get<Double>(NavRoutes.RESULT_PICK_LON)
        if (lat != null && lon != null) {
            val place = TripEndpoint.MapPoint(lat = lat, lon = lon)
            when (mapPickTarget) {
                "from" -> viewModel.setFrom(place)
                "to" -> viewModel.setTo(place)
            }
            handle.remove<Double>(NavRoutes.RESULT_PICK_LAT)
            handle.remove<Double>(NavRoutes.RESULT_PICK_LON)
            mapPickTarget = null
        }
    }

    // -- Plan errors → dialog; plan submit → analytics. Collected while this destination is composed.
    LaunchedEffect(viewModel) {
        viewModel.planState.collect { state ->
            when (state) {
                is PlanResult.Loading -> reportPlanAnalytics(activity)
                is PlanResult.Error -> {
                    showFeedbackDialog(activity, regionRepository, state.message)
                    viewModel.clearPlanResult()
                }
                else -> {}
            }
        }
    }

    // -- Notification re-entry: when the app is reopened (cold/from background) from a RealtimeService
    // trip-update notification, onNewIntent stages the TRIP_PLAN route and this destination composes
    // fresh; read the restore extras off the host intent once. (A notification arriving while already
    // on this destination — singleTop, no recomposition — won't re-restore; acceptable rare edge.)
    LaunchedEffect(Unit) {
        maybeRestoreFromIntent(viewModel, activity, activity.intent)?.let { activity.setIntent(it) }
    }

    var showAdvanced by remember { mutableStateOf(false) }

    TripPlanRoute(
        viewModel = viewModel,
        onBack = onBack,
        onPickDate = { pickDate(activity, viewModel) },
        onPickTime = { pickTime(activity, viewModel) },
        onFromCurrentLocation = { setCurrentLocation(activity, viewModel::setFrom) },
        onToCurrentLocation = { setCurrentLocation(activity, viewModel::setTo) },
        onFromContacts = { launchContacts(viewModel::setFrom) },
        onToContacts = { launchContacts(viewModel::setTo) },
        onFromPickOnMap = { launchMapPicker("from", viewModel.formState.value.from) },
        onToPickOnMap = { launchMapPicker("to", viewModel.formState.value.to) },
        onAdvancedSettings = { showAdvanced = true },
        onReportProblem = { reportProblem(activity, regionRepository) }
    )

    if (showAdvanced) {
        AdvancedSettingsDialog(activity, viewModel) { showAdvanced = false }
    }
}

/**
 * The trip-plan container: the directions map ([TripResultsMap]) is the constant scaffold *body*, with
 * the form hovering over it as a top sheet ([TripPlanFormSheet], slides down from the top) and — once a
 * plan completes — the results header + directions list in a Material3 bottom sheet over it
 * ([TripResultsSheet]). Both sheets keep their gestures off the map, which owns its whole area and pans
 * with vertical drags (#1640). When results arrive the top form collapses to a compact `From → To` bar
 * (tap to re-edit); the bottom sheet expands. Back walks: directions-expanded → peek → form-collapsed →
 * results-cleared → exit. Date/time/contacts/current-location/advanced/report are platform interactions
 * delegated to the host Activity.
 */
@Composable
fun TripPlanRoute(
    viewModel: TripPlanViewModel,
    onBack: () -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onFromCurrentLocation: () -> Unit,
    onToCurrentLocation: () -> Unit,
    onFromContacts: () -> Unit,
    onToContacts: () -> Unit,
    onFromPickOnMap: () -> Unit,
    onToPickOnMap: () -> Unit,
    onAdvancedSettings: () -> Unit,
    onReportProblem: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val planState by viewModel.planState.collectAsStateWithLifecycle()

    // The results VMs are hoisted here (not created inside the sheet content) so the map renders as the
    // constant backdrop behind both sheets, while the results header + directions list live in the bottom
    // sheet. Both are scoped to this destination's back-stack entry, so hoisting doesn't change their
    // identity/lifetime.
    val resultsViewModel: TripResultsViewModel = hiltViewModel()
    val mapViewModel: DirectionsMapViewModel = hiltViewModel(key = "tripResultsMap")

    val hasResults = planState is PlanResult.Success
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // The directions sheet is a fixed half-screen tall; its two positions (fully open vs. handle-only)
    // are driven by an AnchoredDraggable state whose drag gesture is attached to the *handle only* (see
    // [DirectionsSheet]) — so the handle opens/closes the sheet while the directions list inside scrolls
    // freely, never hijacked by the sheet drag.
    // The sheet's usable content is 40% of the screen; the sheet then extends *past* that down to the
    // very bottom edge by the system nav-bar inset, so there's no gap below it. That inset is reserved as
    // bottom padding inside the sheet (see [DirectionsSheet]) — the top edge is raised to make room while
    // the bottom stays flush, keeping the content (and collapsed handle) above the gesture/nav chrome.
    val navInset = navigationBarBottomPadding()
    val contentHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    val sheetHeight = contentHeight + navInset
    // Collapsed slides the sheet down until just the handle remains above the nav chrome.
    val collapsedOffsetPx = with(density) { (contentHeight - DIRECTIONS_HANDLE_PEEK).toPx() }
    val dragState = remember { AnchoredDraggableState(initialValue = DirectionsAnchor.Expanded) }
    val anchors = remember(collapsedOffsetPx) {
        DraggableAnchors {
            DirectionsAnchor.Expanded at 0f
            DirectionsAnchor.Collapsed at collapsedOffsetPx
        }
    }
    LaunchedEffect(anchors) { dragState.updateAnchors(anchors) }
    val directionsExpanded = dragState.targetValue == DirectionsAnchor.Expanded

    val toggleDirections: () -> Unit = {
        scope.launch {
            runCatching {
                dragState.animateTo(
                    if (directionsExpanded) DirectionsAnchor.Collapsed else DirectionsAnchor.Expanded
                )
            }
        }
    }

    // The top form sheet drags like the directions sheet (finger-following height): expanded = full form,
    // collapsed = the From/To summary. Its anchors are set inside the sheet once the form is measured.
    val formDragState = remember { AnchoredDraggableState(initialValue = FormAnchor.Expanded) }
    val formExpanded = formDragState.targetValue == FormAnchor.Expanded

    // Back walks the flow back to front: an open directions sheet collapses to its handle, then an
    // expanded form collapses to its From/To summary, then the results are dropped (returning to the
    // form), and only then does back exit the screen.
    val collapseOrBack: () -> Unit = {
        when {
            hasResults && directionsExpanded -> toggleDirections()
            hasResults && formExpanded ->
                scope.launch { runCatching { formDragState.animateTo(FormAnchor.Collapsed) } }
            hasResults -> viewModel.clearPlanResult()
            else -> onBack()
        }
    }
    BackHandler(enabled = hasResults) { collapseOrBack() }

    // React to plan state: fresh results collapse the form to its summary and open the directions sheet;
    // resetting to Idle re-expands the form (the directions sheet unmounts with the results).
    LaunchedEffect(planState) {
        when (planState) {
            is PlanResult.Success -> {
                runCatching { formDragState.animateTo(FormAnchor.Collapsed) }
                runCatching { dragState.animateTo(DirectionsAnchor.Expanded) }
            }
            PlanResult.Idle -> runCatching { formDragState.animateTo(FormAnchor.Expanded) }
            else -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The directions map is the constant backdrop both sheets hover over. It owns its whole gesture
        // area (#1640); before a plan completes it shows the base map centered on the user's location,
        // and draws the selected itinerary once results arrive.
        TripResultsMap(
            mapViewModel = mapViewModel,
            modifier = Modifier.fillMaxSize()
        )
        if (planState is PlanResult.Loading) {
            LinearProgressIndicator(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
        // The bottom directions sheet — half-screen when open, handle-only when closed. Mounts with the
        // results; the handle drags/toggles it while the list inside scrolls.
        val result = planState
        if (result is PlanResult.Success) {
            DirectionsSheet(
                dragState = dragState,
                sheetHeight = sheetHeight,
                onToggle = toggleDirections,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                TripResultsSheet(
                    itineraries = result.itineraries,
                    resultsViewModel = resultsViewModel,
                    mapViewModel = mapViewModel,
                    // The sheet's surface reaches the bottom edge; keep the last directions row scrollable
                    // clear of the nav chrome via list content padding instead of an empty inset strip.
                    listBottomInset = navInset,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // The top form sheet, slid down from the top edge over the map.
        TripPlanFormSheet(
            dragState = formDragState,
            state = formState,
            onFromQueryChange = viewModel::onFromQueryChange,
            onToQueryChange = viewModel::onToQueryChange,
            onSelectFrom = viewModel::setFrom,
            onSelectTo = viewModel::setTo,
            onClearFrom = viewModel::clearFrom,
            onClearTo = viewModel::clearTo,
            onFromCurrentLocation = onFromCurrentLocation,
            onToCurrentLocation = onToCurrentLocation,
            onFromContacts = onFromContacts,
            onToContacts = onToContacts,
            onFromPickOnMap = onFromPickOnMap,
            onToPickOnMap = onToPickOnMap,
            onSetArriving = viewModel::setArriving,
            onPickDate = onPickDate,
            onPickTime = onPickTime,
            onReverse = viewModel::reverseTrip,
            onAdvancedSettings = onAdvancedSettings,
            onBack = collapseOrBack,
            onReportProblem = onReportProblem,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

// The directions sheet's drag handle geometry. The peek height equals the whole handle strip so that,
// collapsed, exactly the handle shows; the bar + vertical padding also drive what the strip draws.
private val DIRECTIONS_HANDLE_BAR_HEIGHT = 4.dp
private val DIRECTIONS_HANDLE_VERTICAL_PADDING = 14.dp
private val DIRECTIONS_HANDLE_PEEK = DIRECTIONS_HANDLE_BAR_HEIGHT + DIRECTIONS_HANDLE_VERTICAL_PADDING * 2

/** The two rest positions of the directions sheet: fully open (half-screen) or handle-only. */
private enum class DirectionsAnchor { Collapsed, Expanded }

/**
 * The bottom directions sheet: a fixed [sheetHeight] tall, pinned to the bottom edge and translated by
 * [dragState] between fully open (offset 0) and handle-only (slid down). The drag gesture lives on the
 * handle **only**, so the directions list ([content]) inside scrolls freely — a body drag never moves
 * the sheet. Tapping the handle also toggles it via [onToggle].
 *
 * The sheet extends past its content down to the very bottom edge (no gap); [content] fills all the way
 * down. Nav-bar clearance for the list is handled as scroll content padding by the caller, so the last
 * directions row can still be scrolled clear of the chrome without leaving an empty strip here.
 */
@Composable
private fun DirectionsSheet(
    dragState: AnchoredDraggableState<DirectionsAnchor>,
    sheetHeight: Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .offset {
                // `offset` is NaN until the anchors are applied; rest at the open position (0) until then.
                IntOffset(0, dragState.offset.let { if (it.isNaN()) 0 else it.roundToInt() })
            },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column {
            DirectionsDragHandle(dragState = dragState, onToggle = onToggle)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/**
 * The directions sheet's grab handle — the only open/close control. It carries the sheet's drag gesture
 * (so the body/list can scroll independently), and a tap toggles open/closed via [onToggle]. Mirrors the
 * home arrivals sheet's [org.onebusaway.android.ui.home] handle styling.
 */
@Composable
private fun DirectionsDragHandle(
    dragState: AnchoredDraggableState<DirectionsAnchor>,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .anchoredDraggable(dragState, Orientation.Vertical)
            .clickable(onClick = onToggle)
            .padding(vertical = DIRECTIONS_HANDLE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colorResource(R.color.navdrawer_icon_tint),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Box(Modifier.size(width = 32.dp, height = DIRECTIONS_HANDLE_BAR_HEIGHT))
        }
    }
}

// -- Trip-plan platform glue, ported verbatim from the former TripPlanActivity ---------------------
//
// These were the Activity's private helpers; as a NavHost destination there is no per-screen Activity,
// so they live here as file-private functions taking the host AppCompatActivity (its supportFragment-
// Manager / contentResolver / window). Behavior is preserved exactly.

// -- Date / time pickers (platform), feeding the ViewModel ------------------------------------

private fun pickDate(activity: AppCompatActivity, viewModel: TripPlanViewModel) {
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

private fun pickTime(activity: AppCompatActivity, viewModel: TripPlanViewModel) {
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

// -- Current location + contacts (platform) --------------------------------------------------

private fun setCurrentLocation(
    activity: AppCompatActivity,
    target: (TripEndpoint) -> Unit
) {
    val location = LocationEntryPoint.get(activity.applicationContext).lastKnownLocation()
    if (location == null) {
        Toast.makeText(activity, activity.getString(R.string.no_location_permission), Toast.LENGTH_SHORT)
            .show()
        // Without a location this would be a coordinate-less, non-submittable pill that also drops any
        // existing result — bail after the toast instead.
        return
    }
    target(TripEndpoint.CurrentLocation(lat = location.latitude, lon = location.longitude))
}

private fun formattedAddress(
    activity: AppCompatActivity,
    uri: Uri
): String? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
    activity.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
            )
            return cursor.getString(index)?.replace("\n", ", ")
        }
    }
    return null
}

// -- Advanced options dialog (Compose port of the legacy form) --------------------------------

@Composable
private fun AdvancedSettingsDialog(
    activity: AppCompatActivity,
    viewModel: TripPlanViewModel,
    onDismiss: () -> Unit,
) {
    val imperial = remember { !PreferenceUtils.getUnitsAreMetricFromPreferences(activity) }
    // (display label, trip-mode code) for each option, dropping bikeshare modes when unavailable.
    val options = remember {
        val typed = activity.resources.obtainTypedArray(R.array.transit_mode_array)
        val labels = activity.resources.getStringArray(R.array.transit_mode_array)
        val all = (0 until typed.length()).map { i ->
            labels[i] to TripModes.getTripModeCodeFromSelection(typed.getResourceId(i, 0))
        }
        typed.recycle()
        if (BikeshareAvailability.isEnabled(activity)) {
            all
        } else {
            all.filter { it.second != TripModes.BIKESHARE && it.second != TripModes.TRANSIT_AND_BIKE }
        }
    }
    val current = remember { viewModel.formState.value }
    var selectedMode by remember {
        mutableStateOf(options.firstOrNull { it.second == current.modeId }?.second ?: options.first().second)
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
                                    onClick = { selectedMode = code; expanded = false },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.maximum_walk_distance), modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = maxWalk,
                        onValueChange = { new -> maxWalk = new.filter { it.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
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
                    onCheckedChange = { minimizeTransfers = it },
                )
                SwitchRow(
                    label = stringResource(R.string.wheelchair_accessible),
                    checked = wheelchair,
                    onCheckedChange = { wheelchair = it },
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
                PreferenceUtils.saveInt(
                    activity.getString(R.string.preference_key_trip_plan_travel_by), selectedMode
                )
                PreferenceUtils.saveDouble(
                    activity.getString(R.string.preference_key_trip_plan_maximum_walking_distance),
                    maxWalkMeters ?: Double.MAX_VALUE
                )
                PreferenceUtils.saveBoolean(
                    activity.getString(R.string.preference_key_trip_plan_minimize_transfers), minimizeTransfers
                )
                PreferenceUtils.saveBoolean(
                    activity.getString(R.string.preference_key_trip_plan_avoid_stairs), wheelchair
                )
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
    )
}

// -- Errors, reporting, analytics, notification re-entry --------------------------------------

private fun showFeedbackDialog(
    activity: AppCompatActivity,
    regionRepository: RegionRepository,
    message: String
) {
    MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.tripplanner_error_dialog_title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(R.string.report_problem_report) { _, _ ->
            reportProblem(activity, regionRepository)
        }
        .show()
}

private fun reportProblem(
    activity: AppCompatActivity,
    regionRepository: RegionRepository
) {
    val email = regionRepository.region.value?.otpContactEmail
    if (email.isNullOrEmpty()) {
        Toast.makeText(activity, activity.getString(R.string.tripplanner_no_contact), Toast.LENGTH_SHORT)
            .show()
        return
    }
    val location = LocationEntryPoint.get(activity.applicationContext).lastKnownLocation()
    val locationString = location?.let { LocationUtils.printLocationDetails(it) }
    ExternalIntents.sendEmail(activity, email, locationString, null, true)
    AnalyticsEntryPoint.get(activity).reportUiEvent(
        PlausibleAnalytics.REPORT_TRIP_PLANNER_EVENT_URL,
        activity.getString(R.string.analytics_label_app_feedback_otp), null
    )
}

private fun reportPlanAnalytics(
    activity: AppCompatActivity
) {
    AnalyticsEntryPoint.get(activity).reportUiEvent(
        PlausibleAnalytics.REPORT_TRIP_PLANNER_EVENT_URL,
        activity.getString(R.string.analytics_label_trip_plan), null
    )
}

/**
 * Rehydrates the form + results when reopened from a RealtimeService notification. Returns a cleared
 * [Intent] for the caller to set on the host (so a config change doesn't re-restore), or null when
 * there was nothing to restore.
 */
@Suppress("UNCHECKED_CAST", "DEPRECATION")
private fun maybeRestoreFromIntent(
    viewModel: TripPlanViewModel,
    context: Context,
    intent: Intent?,
): Intent? {
    val extras = intent?.extras ?: return null
    if (extras.getSerializable(OTPConstants.INTENT_SOURCE) == null) return null
    val itineraries =
        (extras.getSerializable(OTPConstants.ITINERARIES) as? ArrayList<Itinerary>).orEmpty()
    if (itineraries.isEmpty()) return null

    val builder = TripRequestBuilder.initFromBundleSimple(context, extras)
    viewModel.restoreFrom(
        from = builder.from?.toGeocoded(),
        to = builder.to?.toGeocoded(),
        dateTimeMillis = builder.dateTime?.time ?: System.currentTimeMillis(),
        arriving = builder.arriveBy,
        itineraries = itineraries
    )
    return Intent()
}
