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
package org.onebusaway.android.ui.home.arrivals

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.ui.arrivals.ArrivalActionHandler
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.ArrivalsLoaded
import org.onebusaway.android.ui.arrivals.ArrivalsPolling
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.components.ArrivalsPanel
import org.onebusaway.android.ui.arrivals.createArrivalActionHandler
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsHost
import org.onebusaway.android.ui.arrivals.routeRowKey
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.rememberClearedViewModelStoreOwner
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.StopRouteSelection
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.ui.tutorial.ArrivalTutorial
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.TutorialState
import org.onebusaway.android.ui.tutorial.tutorialAnchor

/**
 * The shared per-stop arrivals session used by both the home focus banner and bottom sheet.
 */
internal data class ArrivalsSession(
    val viewModel: ArrivalsViewModel,
    val handler: ArrivalActionHandler,
    val listState: LazyListState
)

/**
 * Creates the arrivals session for the currently focused stop. Each stop gets its own
 * [ArrivalsViewModel] in a per-stop `ViewModelStore` that is **cleared when the stop changes or the
 * session leaves composition**
 * (via [rememberClearedViewModelStoreOwner]) — so the VM's `viewModelScope`, and the refresh loop
 * [ArrivalsPolling] drives through it, are cancelled rather than accumulating in the activity's store.
 *
 * Polling and stop-detail dialogs live here so the banner and drawer share one lifecycle and one
 * state source. Loaded responses are forwarded to the host for map focus and tutorials.
 */
@Composable
internal fun rememberArrivalsSession(
    focusedStop: FocusedStop?,
    // The sheet is actually on screen (not hidden) — gates the onboarding spotlight so it can't fire
    // over a hidden panel.
    sheetVisible: Boolean,
    arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    tutorialState: TutorialState?,
    onArrivalsLoaded: (ArrivalsLoaded) -> Unit,
    // The transport for every "show a route on the map" variant (row tap, ETA pill, menu item) — the
    // request's own fields carry the stop/direction scoping, or its absence.
    revealRoute: (ArrivalInfo, ShowRouteRequest) -> Unit,
    onShowTrip: (tripId: String, stopId: String) -> Unit,
    onEditReminder: (args: ReminderEditorArgs) -> Unit,
    showUndoSnackbar: (messageRes: Int, actionRes: Int?, onAction: (() -> Unit)?) -> Unit
): ArrivalsSession? {
    val stop = focusedStop ?: return null
    return key(stop.id) {
        val viewModelStoreOwner = rememberClearedViewModelStoreOwner(stop.id)
        val context = LocalContext.current
        // Resolve the Hilt entry point once per stop rather than on each recomposition / each
        // arrivals load (matches the remember { RegionEntryPoint.get(...) } pattern elsewhere).
        val prefs = remember { PreferencesEntryPoint.get(context) }
        val viewModel: ArrivalsViewModel = viewModel(
            viewModelStoreOwner = viewModelStoreOwner,
            factory = viewModelFactory {
                initializer {
                    arrivalsViewModelFactory.create(stop.id)
                }
            }
        )
        val activity = context.findActivity()
        val showRouteState = rememberUpdatedState(revealRoute)
        val showTripState = rememberUpdatedState(onShowTrip)
        val editReminderState = rememberUpdatedState(onEditReminder)
        val undoSnackbarState = rememberUpdatedState(showUndoSnackbar)
        val handler = remember(viewModel, activity) {
            createArrivalActionHandler(
                activity = activity,
                viewModel = viewModel,
                currentContent = { viewModel.state.value as? ArrivalsUiState.Content },
                revealRoute = { arrival, request -> showRouteState.value(arrival, request) },
                showUndoSnackbar = { messageRes, actionRes, onAction ->
                    undoSnackbarState.value(messageRes, actionRes, onAction)
                },
                onShowTrip = { tripId, stopId -> showTripState.value(tripId, stopId) },
                onEditReminder = { args -> editReminderState.value(args) }
            )
        }
        val listState = remember { LazyListState() }

        ArrivalsPolling(viewModel)
        StopDetailsHost(viewModel)

        // Forward each completed load to the host and start onboarding after the sheet is visible.
        val sheetVisibleState = rememberUpdatedState(sheetVisible)
        LaunchedEffect(viewModel) {
            viewModel.arrivalsLoaded.collect { loaded ->
                onArrivalsLoaded(loaded)
                if (tutorialState != null) {
                    maybeStartArrivalTutorial(prefs, tutorialState, loaded.hasArrivals) {
                        snapshotFlow { sheetVisibleState.value }.first { it }
                    }
                }
            }
        }

        remember(viewModel, handler, listState) {
            ArrivalsSession(viewModel, handler, listState)
        }
    }
}

/** Renders only the drawer body; session lifecycle and state are owned above the scaffold. */
@Composable
internal fun ArrivalsSheetHost(
    session: ArrivalsSession?,
    state: ArrivalsUiState,
    selectedRoute: StopRouteSelection?,
    mapRouteColors: Map<RouteDirectionKey, Int>,
    onContentHeight: (heightPx: Int) -> Unit
) {
    session ?: return
    val tutorialState = LocalTutorialState.current
    Surface(color = colorResource(R.color.trip_details_background)) {
        ArrivalsPanel(
            viewModel = session.viewModel,
            state = state,
            listState = session.listState,
            handler = session.handler,
            mapRouteColors = mapRouteColors,
            selectedRowKey = selectedRoute?.selectedArrivalRowKey(),
            selectedRouteId = selectedRoute?.originLeg?.routeId,
            selectedRouteNames = selectedRoute?.legs?.map { it.shortName }.orEmpty(),
            onContentHeight = onContentHeight,
            etaAnchor = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_ETA)
        )
    }
}

/** Keep a continuation selected on the drawer row that originally launched it. */
internal fun StopRouteSelection.selectedArrivalRowKey(): String = originLeg.let { first ->
    routeRowKey(first.routeId, first.directionId, originHeadsign)
}

/**
 * Starts the [ArrivalTutorial] spotlight sequence the first time a focused stop's arrivals load, gated
 * so it shows at most once: nothing if a tutorial is already up, the stop has no arrivals (the ETA/star
 * spotlights need a route row to anchor on), or every step is already shown / tutorials are off ([ArrivalTutorial.pendingSteps]).
 *
 * Before starting, it [awaitSheetVisible]s — the bottom sheet opens with an animation, so its "visible"
 * state lags the focus by a few hundred ms and the (often cached) arrivals response beats it. The old
 * code checked sheet visibility *instantaneously* and dropped the start when it lost that race, which
 * skipped the tour almost every time (the next retry was a 60s-away poll). Waiting instead anchors the
 * spotlight to the panel once it's actually on screen. The pending steps are marked shown only once we
 * commit to starting, so a lost-then-retried response can't double-show.
 */
private suspend fun maybeStartArrivalTutorial(
    prefs: PreferencesRepository,
    tutorialState: TutorialState,
    hasArrivals: Boolean,
    awaitSheetVisible: suspend () -> Unit
) {
    if (tutorialState.active) return
    if (!hasArrivals) return
    val pending = ArrivalTutorial.pendingSteps(prefs)
    if (pending.isEmpty()) return
    awaitSheetVisible()
    // Re-check after the wait: a stop change or another tutorial may have intervened.
    if (tutorialState.active) return
    ArrivalTutorial.markShown(prefs, pending)
    tutorialState.start(pending)
}
