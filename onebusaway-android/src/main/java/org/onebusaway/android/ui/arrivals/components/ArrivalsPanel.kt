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

package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.ui.arrivals.ArrivalActionHandler
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.ArrivalsList
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.rememberArrivalRowCallbacks
import org.onebusaway.android.ui.compose.navigationBarBottomPadding

/**
 * The arrivals content for HomeActivity's map slide-up panel. Unlike the standalone screen, the
 * drawer is a single scrollable arrivals list. Stop identity and actions live in Home's focus banner;
 * the hosting BottomSheetScaffold supplies the drag handle above this content.
 *
 * The list wraps its content within the host's height limit; polling and stop dialogs belong to the
 * shared arrivals session.
 */
@Composable
fun ArrivalsPanel(
    viewModel: ArrivalsViewModel,
    state: ArrivalsUiState,
    listState: LazyListState,
    handler: ArrivalActionHandler,
    mapRouteColors: Map<RouteDirectionKey, Int> = emptyMap(),
    // The selected trip's band tint (#1990), or null when no vehicle is selected.
    selectedTripBandColor: Int? = null,
    selectedRowKey: String? = null,
    selectedRouteId: String? = null,
    selectedRouteNames: List<String> = emptyList(),
    selectedTripId: String? = null,
    // Opaque anchor modifiers a host may attach to the first row's pill / badge / star (e.g. for an
    // onboarding spotlight). The panel stays ignorant of what they're for.
    anchors: ArrivalRowAnchors = ArrivalRowAnchors(),
    displayMode: ArrivalDisplayMode = ArrivalDisplayMode.ROUTE,
    onDisplayModeChange: ((ArrivalDisplayMode) -> Unit)? = null,
    modeSwitchModifier: Modifier = Modifier
) {
    // The system navigation-bar inset (height varies by handset); see the list contentPadding below.
    val navBarInset = navigationBarBottomPadding()
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    val content = state as? ArrivalsUiState.Content

    if (content == null) {
        // Reserve the available sheet space while the first response loads.
        Box(Modifier.fillMaxSize()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    } else {
        ArrivalsList(
            content = content,
            displayMode = displayMode,
            onDisplayModeChange = onDisplayModeChange,
            modeSwitchModifier = modeSwitchModifier,
            rowCallbacks = rowCallbacks,
            onShowAlert = handler::onShowAlert,
            onHideAlert = handler::onHideAlert,
            onShowHiddenAlerts = viewModel::showHiddenAlerts,
            onLoadMore = viewModel::loadMore,
            // Collected inside the list's footer item, not here — a load-more toggle should
            // only recompose that one item, not this whole panel.
            loadingMore = viewModel.loadingMore,
            mapRouteColors = mapRouteColors,
            selectedTripBandColor = selectedTripBandColor,
            selectedRowKey = selectedRowKey,
            selectedRouteId = selectedRouteId,
            selectedRouteNames = selectedRouteNames,
            selectedTripId = selectedTripId,
            listState = listState,
            // The focus banner already shows the direction as a "(N)" tag.
            showDirection = false,
            // Home presents alerts in a centered modal owned by the focus banner.
            showAlerts = false,
            contentPadding = PaddingValues(bottom = navBarInset),
            // The onboarding spotlight anchors on the first route row.
            anchors = anchors
        )
    }
}
