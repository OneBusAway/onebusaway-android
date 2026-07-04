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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.directions.util.CustomAddress
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.components.SwitchRow
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.tripresults.TripResults
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
    val firebaseAnalytics = remember { FirebaseAnalytics.getInstance(activity) }
    // HomeActivity doesn't inject RegionRepository; reach the shared singleton via the EntryPoint.
    val regionRepository = remember { RegionEntryPoint.get(activity) }

    // -- Contacts pick: a launcher + the endpoint a pending pick should populate. A contacts pick
    // doesn't dispose this composable, so a plain remember (not rememberSaveable) suffices.
    var contactsTarget by remember { mutableStateOf<((PlaceItem) -> Unit)?>(null) }
    val contactsLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            formattedAddress(activity, uri)?.let { address ->
                contactsTarget?.invoke(PlaceItem(displayName = address))
            }
        }
        contactsTarget = null
    }
    val launchContacts: ((PlaceItem) -> Unit) -> Unit = { target ->
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
    val launchMapPicker: (String, PlaceItem?) -> Unit = { endpoint, initial ->
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
    // When the picker hands a result back to this entry's SavedStateHandle, build the PlaceItem and
    // dispatch it to the saved endpoint, then clear the keys + the target.
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle, mapPickTarget) {
        val handle = savedStateHandle ?: return@LaunchedEffect
        val lat = handle.get<Double>(NavRoutes.RESULT_PICK_LAT)
        val lon = handle.get<Double>(NavRoutes.RESULT_PICK_LON)
        if (lat != null && lon != null) {
            val place = PlaceItem(
                displayName = activity.getString(R.string.trip_plan_map_location),
                lat = lat,
                lon = lon
            )
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
                is PlanResult.Loading -> reportPlanAnalytics(activity, firebaseAnalytics)
                is PlanResult.Error -> {
                    showFeedbackDialog(activity, firebaseAnalytics, regionRepository, state.message)
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
        onReportProblem = { reportProblem(activity, firebaseAnalytics, regionRepository) }
    )

    if (showAdvanced) {
        AdvancedSettingsDialog(activity, viewModel) { showAdvanced = false }
    }
}

/**
 * The trip-plan container: the [TripPlanForm] is the main content; when a plan completes, the
 * results appear inline in a Material3 bottom sheet via [TripResults] (Compose, owning its own
 * directions-mode map). Date/time/contacts/current-location/advanced/report are platform interactions
 * delegated to the host Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )
    val hasResults = planState is PlanResult.Success
    val scope = rememberCoroutineScope()
    val sheetExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

    // Back (system or toolbar) collapses an expanded results sheet first, then exits — mirrors
    // the legacy sliding-panel behavior (onBackPressed collapsed the panel before finishing).
    val collapseOrBack: () -> Unit = {
        if (sheetExpanded) scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        else onBack()
    }
    BackHandler(enabled = sheetExpanded) { collapseOrBack() }

    // Expand the sheet when results arrive; hide it when the form is reset to Idle.
    LaunchedEffect(planState) {
        when (planState) {
            is PlanResult.Success -> scaffoldState.bottomSheetState.expand()
            PlanResult.Idle -> scaffoldState.bottomSheetState.hide()
            else -> {}
        }
    }

    // The toolbar lives above the sheet (not in the scaffold's topBar slot) so the results sheet
    // only ever fills the area *below* the toolbar — the toolbar stays visible even when the sheet
    // is fully expanded, matching the legacy panel.
    Column(Modifier.fillMaxSize()) {
        TripPlanTopBar(onBack = collapseOrBack, onReportProblem = onReportProblem)
        BottomSheetScaffold(
            modifier = Modifier.weight(1f),
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (hasResults) 220.dp else 0.dp,
            sheetContent = {
                val result = planState
                if (result is PlanResult.Success) {
                    TripResults(
                        itineraries = result.itineraries,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                if (planState is PlanResult.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                TripPlanForm(
                    state = formState,
                    onFromQueryChange = viewModel::onFromQueryChange,
                    onToQueryChange = viewModel::onToQueryChange,
                    onSelectFrom = viewModel::setFrom,
                    onSelectTo = viewModel::setTo,
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
                    onAdvancedSettings = onAdvancedSettings
                )
            }
        }
    }
}

@Composable
private fun TripPlanTopBar(onBack: () -> Unit, onReportProblem: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    ObaTopAppBar(title = stringResource(R.string.trip_plan_title), onBack = onBack) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tripplanner_report_trip_problem)) },
                onClick = {
                    menuExpanded = false
                    onReportProblem()
                }
            )
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
    target: (PlaceItem) -> Unit
) {
    val location = LocationEntryPoint.get(activity.applicationContext).lastKnownLocation()
    if (location == null) {
        Toast.makeText(activity, activity.getString(R.string.no_location_permission), Toast.LENGTH_SHORT)
            .show()
    }
    target(
        PlaceItem(
            displayName = activity.getString(R.string.tripplanner_current_location),
            lat = location?.latitude,
            lon = location?.longitude,
            isCurrentLocation = true
        )
    )
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
    firebaseAnalytics: FirebaseAnalytics,
    regionRepository: RegionRepository,
    message: String
) {
    MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.tripplanner_error_dialog_title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(R.string.report_problem_report) { _, _ ->
            reportProblem(activity, firebaseAnalytics, regionRepository)
        }
        .show()
}

private fun reportProblem(
    activity: AppCompatActivity,
    firebaseAnalytics: FirebaseAnalytics,
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
    ObaAnalytics.reportUiEvent(
        firebaseAnalytics, AnalyticsEntryPoint.get(activity).plausible,
        PlausibleAnalytics.REPORT_TRIP_PLANNER_EVENT_URL,
        activity.getString(R.string.analytics_label_app_feedback_otp), null
    )
}

private fun reportPlanAnalytics(
    activity: AppCompatActivity,
    firebaseAnalytics: FirebaseAnalytics
) {
    ObaAnalytics.reportUiEvent(
        firebaseAnalytics, AnalyticsEntryPoint.get(activity).plausible,
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
        from = builder.from?.toPlaceItem(),
        to = builder.to?.toPlaceItem(),
        dateTimeMillis = builder.dateTime?.time ?: System.currentTimeMillis(),
        arriving = builder.arriveBy,
        itineraries = itineraries
    )
    return Intent()
}

private fun CustomAddress.toPlaceItem(): PlaceItem = PlaceItem(
    displayName = toString(),
    lat = if (isSet) latitude else null,
    lon = if (isSet) longitude else null
)
