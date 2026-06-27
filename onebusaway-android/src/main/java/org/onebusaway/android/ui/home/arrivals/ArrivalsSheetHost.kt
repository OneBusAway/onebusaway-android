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

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.ui.arrivals.ArrivalsLoaded
import org.onebusaway.android.ui.arrivals.components.ArrivalsPanel
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.ui.compose.rememberClearedViewModelStoreOwner
import org.onebusaway.android.ui.arrivals.createArrivalActionHandler
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.tutorial.ArrivalTutorial
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.TutorialState
import org.onebusaway.android.ui.tutorial.tutorialAnchor

/**
 * Hosts the [ArrivalsPanel] for the currently focused stop directly in the home bottom sheet,
 * replacing the old `ArrivalsPanelFragment`. Each focused stop gets its own [ArrivalsViewModel] in a
 * per-stop `ViewModelStore` that is **cleared when the stop changes or the sheet leaves composition**
 * (via [rememberClearedViewModelStoreOwner]) — so the VM's `viewModelScope`, and the refresh loop
 * `ArrivalsPanel` drives through it, are cancelled rather than accumulating in the activity's store.
 *
 * Polling lifecycle is owned by `ArrivalsPanel` itself (`ArrivalsPolling` → `repeatOnLifecycle`), so
 * it also pauses with the screen. Loaded responses are forwarded to the host via [onArrivalsLoaded]
 * (the map recenter / focus marker / tutorials), the chevron toggles the sheet via [onToggleSheet],
 * and the preview size drives the peek height via [onPreferredHeight].
 */
@Composable
internal fun ArrivalsSheetHost(
    focusedStop: FocusedStop?,
    collapsed: Boolean,
    // The sheet is actually on screen (not hidden) — gates the onboarding spotlight so it can't fire
    // over a hidden panel.
    sheetVisible: Boolean,
    // The drawer's live open fraction (0 = collapsed peek, 1 = fully expanded), read each frame to
    // drive the peek→card row morph in lockstep with the drag.
    expandProgress: () -> Float,
    arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    onArrivalsLoaded: (ArrivalsLoaded) -> Unit,
    onShowRouteOnMap: (String) -> Unit,
    onShowTrip: (tripId: String, stopId: String) -> Unit,
    onEditReminder: (args: ReminderEditorArgs) -> Unit,
    onToggleSheet: () -> Unit,
    onPreferredHeight: (previewCount: Int, filtering: Boolean) -> Unit,
    showUndoSnackbar: (messageRes: Int, actionRes: Int?, onAction: (() -> Unit)?) -> Unit,
) {
    val stop = focusedStop ?: return
    key(stop.id) {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides rememberClearedViewModelStoreOwner(stop.id)
        ) {
            val context = LocalContext.current
            val viewModel: ArrivalsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        arrivalsViewModelFactory.create(stop.id, ignorePersistedFilter = false)
                    }
                }
            )
            val activity = context.findActivity()
            // The enclosing key(stop.id) gives this whole block a fresh identity per stop, so these
            // remembers are already scoped to the stop — no per-element key needed.
            val handler = remember {
                createArrivalActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    currentContent = { viewModel.state.value as? ArrivalsUiState.Content },
                    onShowRouteOnMap = onShowRouteOnMap,
                    showUndoSnackbar = showUndoSnackbar,
                    // The home host navigates the NavHost in-app (the handler's default launcher path is for
                    // the standalone/external callers); keeps the shared handler ignorant of the concrete host.
                    onShowTrip = onShowTrip,
                    onEditReminder = onEditReminder,
                )
            }
            val listState = remember { LazyListState() }

            // This host owns the arrivals-tutorial wiring so the reusable panel stays tutorial-ignorant:
            // it maps the panel's opaque anchor slots to the spotlight targets and triggers the sequence.
            val tutorialState = LocalTutorialState.current

            // The BottomSheetScaffold sheet container is transparent; keep the legacy panel background.
            Surface(color = colorResource(R.color.trip_details_background)) {
                ArrivalsPanel(
                    viewModel = viewModel,
                    listState = listState,
                    collapsed = collapsed,
                    expandProgress = expandProgress,
                    initialTitle = stop.name.orEmpty(),
                    handler = handler,
                    onToggleExpand = onToggleSheet,
                    onPreferredHeight = onPreferredHeight,
                    etaAnchor = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_ETA),
                    starAnchor = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_STAR),
                    chevronAnchor = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_PANEL),
                )
            }

            // Forward each completed load to the host (replaces the fragment's onViewCreated collector),
            // and — the first time arrivals show — kick off the arrivals-panel onboarding spotlight.
            val sheetVisibleState = rememberUpdatedState(sheetVisible)
            LaunchedEffect(viewModel) {
                viewModel.arrivalsLoaded.collect { loaded ->
                    onArrivalsLoaded(loaded)
                    if (tutorialState != null) {
                        maybeStartArrivalTutorial(context, tutorialState, loaded.hasArrivals) {
                            // Suspend until the sheet is actually on screen (see the helper KDoc).
                            snapshotFlow { sheetVisibleState.value }.first { it }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Starts the [ArrivalTutorial] spotlight sequence the first time a focused stop's arrivals load, gated
 * so it shows at most once: nothing if a tutorial is already up, the stop has no arrivals (the ETA/star
 * spotlights need a peek row), or every step is already shown / tutorials are off ([ArrivalTutorial.pendingSteps]).
 *
 * Before starting, it [awaitSheetVisible]s — the bottom sheet opens with an animation, so its "visible"
 * state lags the focus by a few hundred ms and the (often cached) arrivals response beats it. The old
 * code checked sheet visibility *instantaneously* and dropped the start when it lost that race, which
 * skipped the tour almost every time (the next retry was a 60s-away poll). Waiting instead anchors the
 * spotlight to the panel once it's actually on screen. The pending steps are marked shown only once we
 * commit to starting, so a lost-then-retried response can't double-show.
 */
private suspend fun maybeStartArrivalTutorial(
    context: Context,
    tutorialState: TutorialState,
    hasArrivals: Boolean,
    awaitSheetVisible: suspend () -> Unit,
) {
    if (tutorialState.active) return
    if (!hasArrivals) return
    val prefs = PreferencesEntryPoint.get(context)
    val pending = ArrivalTutorial.pendingSteps(prefs)
    if (pending.isEmpty()) return
    awaitSheetVisible()
    // Re-check after the wait: a stop change or another tutorial may have intervened.
    if (tutorialState.active) return
    ArrivalTutorial.markShown(prefs, pending)
    tutorialState.start(pending)
}
