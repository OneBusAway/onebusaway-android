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
// This panel threads a named per-element anchor modifier (etaAnchor) a host attaches to a specific
// sub-element for the onboarding spotlight — not the root `modifier` — so ModifierParameter's "name it
// modifier" rule doesn't apply here.
@file:Suppress("ModifierParameter")

package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.ui.arrivals.ArrivalActionHandler
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
 * This composable reports the fully-laid-out list height in px via [onContentHeight] so the host can
 * fit the peek to short stops. Polling and stop dialogs are owned by the shared arrivals session.
 */
@Composable
fun ArrivalsPanel(
    viewModel: ArrivalsViewModel,
    state: ArrivalsUiState,
    listState: LazyListState,
    handler: ArrivalActionHandler,
    mapRouteColors: Map<RouteDirectionKey, Int> = emptyMap(),
    selectedRowKey: String? = null,
    selectedRouteId: String? = null,
    selectedRouteNames: List<String> = emptyList(),
    // Reports the list's total content height in px so the host can fit the peek to short stops; not
    // reported until the whole list is laid out.
    onContentHeight: (heightPx: Int) -> Unit,
    // An opaque anchor modifier a host may attach to the first row's ETA pill (e.g. for an onboarding
    // spotlight). The panel stays ignorant of what it's for.
    etaAnchor: Modifier = Modifier
) {
    // The system navigation-bar inset (height varies by handset); see the list contentPadding below.
    val navBarInset = navigationBarBottomPadding()
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    val content = state as? ArrivalsUiState.Content

    // The panel's total content height (px), or null until the whole list is laid out. Material3
    // measures the sheet content at full container height regardless of the peek, so listState's
    // layoutInfo reflects the full list: if the last item is present, its bottom is the true content
    // height; if it isn't, the content is taller than the screen (well past the peek cap) and an exact
    // number isn't needed. This reads real layout — not a magnitude heuristic.
    val contentHeightPx by remember(listState, content) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (content != null && last != null && last.index == info.totalItemsCount - 1) {
                last.offset + last.size
            } else {
                null
            }
        }
    }
    contentHeightPx?.let { px ->
        LaunchedEffect(px) { onContentHeight(px) }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (content == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                ArrivalsList(
                    content = content,
                    rowCallbacks = rowCallbacks,
                    handler = handler,
                    onShowHiddenAlerts = viewModel::showHiddenAlerts,
                    onLoadMore = viewModel::loadMore,
                    // Collected inside the list's footer item, not here — a load-more toggle should
                    // only recompose that one item, not this whole panel.
                    loadingMore = viewModel.loadingMore,
                    mapRouteColors = mapRouteColors,
                    selectedRowKey = selectedRowKey,
                    selectedRouteId = selectedRouteId,
                    selectedRouteNames = selectedRouteNames,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    // The focus banner already shows the direction as a "(N)" tag.
                    showDirection = false,
                    // Home presents alerts in a centered modal owned by the focus banner.
                    showAlerts = false,
                    contentPadding = PaddingValues(bottom = navBarInset),
                    // The onboarding spotlight anchors on the first route row's ETA pill.
                    etaAnchor = etaAnchor
                )
            }
        }
    }
}
