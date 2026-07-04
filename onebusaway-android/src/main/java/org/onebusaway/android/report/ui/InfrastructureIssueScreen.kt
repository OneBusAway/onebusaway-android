/*
 * Copyright (C) 2014-2015 University of South Florida (sjbarbeau@gmail.com),
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
package org.onebusaway.android.report.ui

import org.onebusaway.android.api.adapters.ObaStopElement

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import edu.usf.cutr.open311client.Open311
import edu.usf.cutr.open311client.models.Service
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.map.StopsMapViewModel
import org.onebusaway.android.map.compose.ObaMap
import org.onebusaway.android.map.compose.ObaMapCallbacks
import edu.usf.cutr.open311client.constants.Open311Constants
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.ArrivalsViewModelFactoryEntryPoint
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.app.di.NetworkEntryPoint
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.report.ReportContext
import org.onebusaway.android.report.TripReportContext
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.report.infrastructure.DefaultGeocodeAddressRepository
import org.onebusaway.android.ui.report.infrastructure.DefaultIssueType
import org.onebusaway.android.ui.report.infrastructure.DefaultServiceListRepository
import org.onebusaway.android.ui.report.infrastructure.GeoPoint
import org.onebusaway.android.ui.report.infrastructure.InfrastructureControls
import org.onebusaway.android.ui.report.infrastructure.InfrastructureIssueEvent
import org.onebusaway.android.ui.report.infrastructure.InfrastructureIssueViewModel
import org.onebusaway.android.ui.report.infrastructure.IssueLocation
import org.onebusaway.android.ui.report.infrastructure.ReportTarget
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.report.open311.DefaultOpen311Repository
import org.onebusaway.android.ui.report.open311.Open311IssueContext
import org.onebusaway.android.ui.report.open311.Open311ProblemViewModel
import org.onebusaway.android.ui.report.open311.Open311Route
import org.onebusaway.android.ui.report.open311.Open311SubmitState
import org.onebusaway.android.ui.report.open311.Open311TripContext
import org.onebusaway.android.ui.report.problem.DefaultProblemReportRepository
import org.onebusaway.android.ui.report.problem.ProblemCodes
import org.onebusaway.android.ui.report.problem.ProblemKind
import org.onebusaway.android.ui.report.problem.ProblemParams
import org.onebusaway.android.ui.report.problem.ProblemReportRoute
import org.onebusaway.android.ui.report.problem.ProblemReportViewModel
import org.onebusaway.android.ui.report.problem.SubmitState
import org.onebusaway.android.util.BitmapUtils
import org.onebusaway.android.util.MyTextUtils

/**
 * The infrastructure-issue (stop/trip problem) NavHost destination (former
 * [InfrastructureIssueActivity]). It replaces the `infrastructure_issue.xml` layout with a Compose
 * [Scaffold]: the declarative [ObaMap] (entry-scoped [MapViewModel], stop mode), the
 * [InfrastructureControls], and the inline stop/trip form, arrivals picker, and Open311 dynamic form
 * (Tier 1) — the whole report flow is pure Compose now, with no fragments.
 *
 * The [InfrastructureIssueViewModel] is built once (back-stack-entry-scoped) from the nav-args
 * `selectedService` and the decoded [ReportContext] (lat/lon, stop id/name/code, the scalar trip
 * context, agency name, block id), reproducing the former Activity's hand-built factory.
 */
@Composable
fun InfrastructureIssueDestination(
    navController: NavController,
    selectedService: String?,
    reportContext: ReportContext,
) {
    val activity = LocalContext.current.findActivity()

    // Entry-scoped map view model (distinct from HomeActivity's own; this is a separate back-stack
    // entry). A StopsMapViewModel: just the nearby-stops map surface (no route/vehicle/directions
    // machinery), starting in stop mode so nearby stops load + are tappable.
    val mapViewModel = hiltViewModel<StopsMapViewModel>()

    // Build the InfrastructureIssueViewModel once, scoped to this back-stack entry (so its
    // viewModelScope is cancelled when the destination leaves). Reads selectedService and the rest of
    // the context from the decoded nav-args — the former Activity's createViewModel.
    val viewModel: InfrastructureIssueViewModel = viewModel(
        factory = viewModelFactory {
            initializer { createInfrastructureIssueViewModel(activity, selectedService, reportContext) }
        }
    )

    // The single manual marker reconciled from the ViewModel's markerLocation (an effect-held id, so
    // it survives recomposition but not the destination leaving — which is correct).
    val markerId = remember { intArrayOf(NO_MARKER) }

    // The "report submitted" dialog (Tier 1: was ReportSuccessDialog, a DialogFragment).
    var showSuccess by remember { mutableStateOf(false) }

    // The active inline form's "send" action, hoisted so it can live in the app bar (Tier 1). Set by the
    // showing form, null otherwise (so the send icon only appears while a submittable form is up).
    var formSubmit by remember { mutableStateOf<(() -> Unit)?>(null) }

    // The submit/loading progress overlay, owned locally now the forms are all inline (Tier 1, P3b: was
    // the host's reportProgressVisible / showProgress). Driven by service-loading + the Open311 submit.
    var loadingServices by remember { mutableStateOf(false) }
    var open311Submitting by remember { mutableStateOf(false) }

    // Map taps drive the report location: a stop tap reports that stop, an empty-map tap reports the
    // tapped point (manual pin). Both update the map's render focus + recenter via the map VM.
    val mapCallbacks = remember(viewModel, mapViewModel) {
        object : ObaMapCallbacks {
            override fun onStopClick(stop: ObaStop) {
                mapViewModel.onStopTapped(stop)
                val loc = stop.location
                viewModel.onMapFocusChanged(stop, loc.latitude, loc.longitude)
            }

            override fun onMapClick(point: org.onebusaway.android.map.render.GeoPoint?) {
                mapViewModel.onMapTapped()
                point?.let { viewModel.onMapFocusChanged(null, it.latitude, it.longitude) }
            }

            override fun onBikeClick(station: org.opentripplanner.routing.bike_rental.BikeRentalStation) {}

            override fun onVehicleInfoWindowClick(status: org.onebusaway.android.models.ObaTripStatus) {}

            override fun onBikeInfoWindowClick(station: org.opentripplanner.routing.bike_rental.BikeRentalStation) {}
        }
    }

    // Reconcile the single manual marker.
    LaunchedEffect(viewModel) {
        viewModel.uiState.map { it.markerLocation }.distinctUntilChanged().collect { location ->
            reconcileMarker(mapViewModel, markerId, location)
        }
    }

    // Loading-services progress drives the destination's local progress overlay.
    LaunchedEffect(viewModel) {
        viewModel.uiState.map { it.loadingServices }.distinctUntilChanged().collect { loading ->
            loadingServices = loading
        }
    }

    // One-shot events: recenter the map, toast a failed geocode, or — on a successful submission —
    // show the success dialog then pop back to the chooser/home.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InfrastructureIssueEvent.RecenterMap ->
                    mapViewModel.centerOn(event.latitude, event.longitude, animate = true)

                InfrastructureIssueEvent.AddressNotFound ->
                    android.widget.Toast.makeText(
                        activity, R.string.ri_address_not_found, android.widget.Toast.LENGTH_LONG
                    ).show()

                InfrastructureIssueEvent.ReportSent -> showSuccess = true
            }
        }
    }

    // Back: if a form/picker is showing, drop the spinner back to the hint (and clear the form);
    // otherwise pop the whole destination. Mirrors the former onBackPressed + form back-stack.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A form/picker is showing whenever the target isn't the hint (all forms are inline now bar Open311,
    // which is also driven by the target). Back drops the spinner back to the hint.
    BackHandler(enabled = state.target != ReportTarget.None) {
        viewModel.onResetToHint()
    }

    Scaffold(
        topBar = {
            ObaTopAppBar(
                title = stringResource(R.string.rt_infrastructure_problem_title),
                onBack = { navController.popBackStack() },
                actions = {
                    // The stop/trip form's "send" (Tier 1: was the form fragment's MenuProvider item).
                    formSubmit?.let { submit ->
                        IconButton(onClick = submit) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_social_send_now),
                                contentDescription = stringResource(R.string.report_problem_send),
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Replaces the XML CustomScrollView: a vertical scroll holding the fixed-height map, the
            // controls, and the form container (each wrap_content), matching the original arrangement.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ObaMap(
                    host = mapViewModel.host,
                    callbacks = mapCallbacks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MAP_HEIGHT.dp),
                    initialLatitude = state.location.latitude,
                    initialLongitude = state.location.longitude,
                    initialZoom = MapParams.DEFAULT_ZOOM.toFloat(),
                )

                InfrastructureControls(
                    state = state,
                    onAddressSearch = viewModel::onAddressSearch,
                    onServiceSelected = viewModel::onServiceSelected,
                )

                // The stop/trip problem form, the arrivals picker, and the Open311 dynamic form all
                // render inline (Tier 1); each sets/clears the app-bar send via [formSubmit].
                when (val target = state.target) {
                    is ReportTarget.StopProblem ->
                        StopTripProblemForm(target.stop, null, viewModel) { formSubmit = it }

                    is ReportTarget.TripProblem ->
                        if (target.arrival == null) {
                            ArrivalsPickerInline(target.stop, activity, viewModel)
                        } else {
                            StopTripProblemForm(target.stop, target.arrival, viewModel) { formSubmit = it }
                        }

                    is ReportTarget.Open311 ->
                        Open311FormInline(
                            target = target,
                            issueViewModel = viewModel,
                            onSubmit = { formSubmit = it },
                            onSubmittingChanged = { open311Submitting = it },
                        )

                    ReportTarget.None -> Unit
                }
            }

            if (loadingServices || open311Submitting) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(16.dp))
            }
        }
    }

    // OK (or back) leaves the whole report flow back to home/map, matching ReportSuccessDialog's
    // closeSuperActivity — popping past the chooser to HOME, not just this destination. Not
    // cancelable outside.
    if (showSuccess) {
        val leaveReportFlow = {
            showSuccess = false
            navController.popBackStack(NavRoutes.HOME, inclusive = false)
            Unit
        }
        AlertDialog(
            onDismissRequest = leaveReportFlow,
            properties = DialogProperties(dismissOnClickOutside = false),
            text = { Text(Open311Constants.M_REPORT_SUCCESS) },
            confirmButton = {
                TextButton(onClick = leaveReportFlow) { Text("OK") }
            },
        )
    }
}

// --- Ported imperative glue (was InfrastructureIssueActivity) -----------------------------------

private const val NO_MARKER = -1

// The former map FrameLayout was 200dp tall (infrastructure_issue.xml).
private const val MAP_HEIGHT = 200

private fun reconcileMarker(
    mapViewModel: StopsMapViewModel,
    markerId: IntArray,
    location: IssueLocation?,
) {
    if (markerId[0] != NO_MARKER) {
        mapViewModel.removeMarker(markerId[0])
        markerId[0] = NO_MARKER
    }
    if (location != null) {
        markerId[0] = mapViewModel.addMarker(location.latitude, location.longitude, null)
    }
}

/**
 * Builds the [InfrastructureIssueViewModel] from the nav-arg [selectedService] and the decoded
 * [reportContext] (port of InfrastructureIssueActivity.createViewModel). The stop/location context,
 * the scalar trip context, and the agency/block ids all ride on the nav-arg now.
 */
private fun createInfrastructureIssueViewModel(
    activity: AppCompatActivity,
    selectedService: String?,
    reportContext: ReportContext,
): InfrastructureIssueViewModel {
    val latitude = reportContext.lat
    val longitude = reportContext.lon

    val initialStop: ObaStop? = reportContext.stopId?.let { stopId ->
        ObaStopElement(stopId, latitude, longitude, reportContext.stopName.orEmpty(), reportContext.stopCode.orEmpty())
    }

    val defaultIssueType = when (selectedService) {
        activity.getString(R.string.ri_selected_service_stop) -> DefaultIssueType.STOP
        activity.getString(R.string.ri_selected_service_trip) -> DefaultIssueType.TRIP
        else -> DefaultIssueType.NONE
    }

    return InfrastructureIssueViewModel(
        serviceListRepository = DefaultServiceListRepository(activity.applicationContext),
        geocodeRepository = DefaultGeocodeAddressRepository(activity.applicationContext),
        initialLocation = GeoPoint(latitude, longitude),
        initialStop = initialStop,
        defaultIssueType = defaultIssueType,
        arrivalInfo = reportContext.trip,
        agencyName = reportContext.agencyName,
        blockId = reportContext.blockId,
    )
}

// --- Inline report forms (Tier 1, P3a: were ProblemReportFragment / SimpleArrivalsPickerFragment) -----

/**
 * The stop/trip problem form, rendered inline. Builds its [ProblemReportViewModel] from the
 * [stop]/[arrival] (port of the fragment's createViewModel), reports a successful submission to the
 * destination's [issueViewModel] (→ the success dialog), and hoists its "send" action via [onSubmit] so
 * the app bar can trigger it (validate + analytics + submit-with-location — port of onSendClicked).
 */
@Composable
private fun StopTripProblemForm(
    stop: ObaStop,
    arrival: TripReportContext?,
    issueViewModel: InfrastructureIssueViewModel,
    onSubmit: ((() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val vm: ProblemReportViewModel = viewModel(
        key = "problem:${arrival?.tripId ?: stop.id}",
        factory = viewModelFactory {
            initializer { createProblemReportViewModel(context, stop, arrival) }
        },
    )

    LaunchedEffect(vm) {
        vm.submitState.collect { state ->
            when (state) {
                SubmitState.Sent -> {
                    issueViewModel.onReportSent()
                    vm.onSubmitResultHandled()
                }

                SubmitState.Error -> {
                    Toast.makeText(context, R.string.report_problem_error, Toast.LENGTH_LONG).show()
                    vm.onSubmitResultHandled()
                }

                else -> Unit
            }
        }
    }

    // Publish the send action to the app bar while shown; clear it on leave.
    DisposableEffect(vm) {
        onSubmit {
            val form = vm.formState.value
            if (!form.canSubmit) {
                Toast.makeText(
                    context, R.string.report_problem_invalid_argument, Toast.LENGTH_LONG
                ).show()
            } else {
                reportProblemAnalytics(context, form.kind)
                vm.submit(LocationEntryPoint.get(context).lastKnownLocation())
            }
        }
        onDispose { onSubmit(null) }
    }

    ProblemReportRoute(vm)
}

/** The arrivals picker, rendered inline; a tap re-drives the VM target (→ trip form or Open311). */
@Composable
private fun ArrivalsPickerInline(
    stop: ObaStop,
    activity: AppCompatActivity,
    issueViewModel: InfrastructureIssueViewModel,
) {
    val arrivalsViewModel: ArrivalsViewModel = viewModel(
        key = "picker:${stop.id}",
        factory = viewModelFactory {
            initializer {
                ArrivalsViewModelFactoryEntryPoint.get(activity).create(stop.id, ignorePersistedFilter = true)
            }
        },
    )
    SimpleArrivalsPicker(arrivalsViewModel) { arrival ->
        issueViewModel.onArrivalSelected(arrival.toTripReportContext())
    }
}

/** Port of ProblemReportFragment.createViewModel — stop or trip params + codes + repository. */
private fun createProblemReportViewModel(
    context: Context,
    stop: ObaStop,
    arrival: TripReportContext?,
): ProblemReportViewModel {
    val repository =
        DefaultProblemReportRepository(NetworkEntryPoint.getProblemReport(context.applicationContext))
    return if (arrival != null) {
        ProblemReportViewModel(
            params = ProblemParams.Trip(
                tripId = arrival.tripId,
                stopId = arrival.stopId,
                vehicleId = arrival.vehicleId,
                serviceDate = arrival.serviceDate,
            ),
            codes = ProblemCodes.trip(
                context.resources.getStringArray(R.array.report_trip_problem_code_bus).toList()
            ),
            headsign = MyTextUtils.formatDisplayText(arrival.headsign),
            repository = repository,
        )
    } else {
        ProblemReportViewModel(
            params = ProblemParams.Stop(stop.id),
            codes = ProblemCodes.stop(
                context.resources.getStringArray(R.array.report_stop_problem_code).toList()
            ),
            headsign = null,
            repository = repository,
        )
    }
}

/** Port of ProblemReportFragment.reportAnalytics — the stop/trip problem Plausible event. */
private fun reportProblemAnalytics(context: Context, kind: ProblemKind) {
    val isTrip = kind == ProblemKind.TRIP
    AnalyticsEntryPoint.get(context).reportUiEvent(
        if (isTrip) {
            PlausibleAnalytics.REPORT_VEHICLE_PROBLEM_EVENT_URL
        } else {
            PlausibleAnalytics.REPORT_STOP_PROBLEM_EVENT_URL
        },
        context.getString(R.string.analytics_problem),
        context.getString(
            if (isTrip) {
                R.string.analytics_label_report_trip_problem
            } else {
                R.string.analytics_label_report_stop_problem
            }
        ),
    )
}

// --- Inline Open311 dynamic form (Tier 1, P3b: was Open311ProblemFragment) -----------------------

private const val OPEN311_LOG_TAG = "Open311FormInline"

/**
 * The Open311 dynamic issue form, rendered inline. Builds its [Open311ProblemViewModel] from the
 * chosen category's opaque library `Service` + the host's `Open311` endpoint (port of the fragment's
 * createViewModel), owns the camera/gallery image pickers (FileProvider + PickVisualMedia /
 * TakePicture), reports a successful submission to the destination's [issueViewModel] (→ the success
 * dialog), surfaces the submit progress via [onSubmittingChanged], and hoists its "send" action via
 * [onSubmit] so the app bar can trigger it (port of onSend).
 */
@Composable
private fun Open311FormInline(
    target: ReportTarget.Open311,
    issueViewModel: InfrastructureIssueViewModel,
    onSubmit: ((() -> Unit)?) -> Unit,
    onSubmittingChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: Open311ProblemViewModel = viewModel(
        key = "open311:${target.category.code ?: target.category.name}",
        factory = viewModelFactory {
            initializer { createOpen311ViewModel(context, issueViewModel, target) }
        },
    )

    // Absolute path of the file handed to the camera, promoted to the form only on success (survives
    // the launch round-trip + process death, matching the fragment's pendingCameraPath).
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) vm.setImagePath(pendingCameraPath) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
                if (path != null) {
                    vm.setImagePath(path)
                } else {
                    Toast.makeText(context, R.string.ri_resize_image_problem, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Submit results: drive the progress spinner; on success tell the destination (→ success dialog),
    // otherwise toast the validation/server message. Port of the fragment's onSubmitState.
    LaunchedEffect(vm) {
        vm.submitState.collect { state ->
            onSubmittingChanged(state == Open311SubmitState.Submitting)
            when (state) {
                Open311SubmitState.Sent -> {
                    issueViewModel.onReportSent()
                    vm.onSubmitStateHandled()
                }

                is Open311SubmitState.ValidationError -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    vm.onSubmitStateHandled()
                }

                is Open311SubmitState.ServerError -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    vm.onSubmitStateHandled()
                }

                else -> Unit
            }
        }
    }

    // Publish the send action to the app bar while shown; clear it (and the spinner) on leave. Port of
    // onSend: submit + the Open311 server analytics event.
    DisposableEffect(vm) {
        onSubmit {
            vm.submit()
            (target.category.raw as? Service)?.let { service ->
                AnalyticsEntryPoint.get(context).reportUiEvent(
                    PlausibleAnalytics.REPORT_OPEN311_SERVER_EVENT_URL,
                    context.getString(R.string.analytics_problem),
                    service.service_name,
                )
            }
        }
        onDispose {
            onSubmit(null)
            onSubmittingChanged(false)
        }
    }

    Open311Route(
        viewModel = vm,
        onTakePhoto = {
            val file = createImageFileOrNull(context)
            if (file == null) {
                Toast.makeText(context, R.string.ri_open_camera_problem, Toast.LENGTH_LONG).show()
            } else {
                pendingCameraPath = file.absolutePath
                takePicture.launch(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                )
            }
        },
        onPickFromGallery = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
}

/** Port of Open311ProblemFragment.createViewModel — opaque service/endpoint + issue/trip context. */
private fun createOpen311ViewModel(
    context: Context,
    issueViewModel: InfrastructureIssueViewModel,
    target: ReportTarget.Open311,
): Open311ProblemViewModel {
    val (_, agencyName, blockId) = issueViewModel.tripContext()
    val tripContext = target.arrival?.let { Open311TripContext(it, agencyName, blockId) }
    val repository = DefaultOpen311Repository(
        context = context.applicationContext,
        service = target.category.raw as Service,
        open311 = issueViewModel.open311 as Open311,
        tripContext = tripContext,
        // Read the location/address/stop fresh from the issue VM (the form can't outlive a map focus
        // change — that clears the target — so this is effectively the snapshot the fragment held).
        issueProvider = {
            val snapshot = issueViewModel.issueContext()
            Open311IssueContext(snapshot.latitude, snapshot.longitude, snapshot.address, snapshot.stop)
        },
    )
    return Open311ProblemViewModel(repository)
}

/** Creates the camera output file, or null if it can't be opened (was the fragment's try/catch). */
private fun createImageFileOrNull(context: Context): File? = try {
    BitmapUtils.createImageFile(context, null)
} catch (e: IOException) {
    Log.e(OPEN311_LOG_TAG, "Couldn't open camera", e)
    null
}

/** Copies a picked content image into the cache so the repository can downsample from a file. */
private fun copyUriToCache(context: Context, uri: Uri): String? = try {
    val file = File.createTempFile("gallery_", ".jpg", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    file.absolutePath
} catch (e: IOException) {
    Log.e(OPEN311_LOG_TAG, "Couldn't copy picked image", e)
    null
}
