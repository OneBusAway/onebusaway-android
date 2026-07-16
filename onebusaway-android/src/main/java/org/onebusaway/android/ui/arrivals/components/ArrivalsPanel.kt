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

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalActionHandler
import org.onebusaway.android.ui.arrivals.ArrivalsList
import org.onebusaway.android.ui.arrivals.ArrivalsPolling
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsHost
import org.onebusaway.android.ui.arrivals.rememberArrivalRowCallbacks
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The arrivals content for HomeActivity's map slide-up panel. Unlike the standalone screen, the
 * drawer is laid out top-to-bottom as: the pinned stop header, then a single scrollable arrivals list.
 * The hosting BottomSheetScaffold supplies the drag handle above this content; tapping/dragging it
 * drives expand/collapse (the header no longer carries a chevron or tap-to-toggle). The collapsed peek
 * is just a fixed-fraction window onto the top of that same list — there is no separate peek layout.
 *
 * This composable reports its total content height (pinned header + the fully-laid-out list) in px via
 * [onContentHeight] so the host can fit the peek to short stops (capping tall ones at the fixed
 * fraction). Polling, callbacks, the per-arrival menu, and the list itself are shared with the
 * standalone screen.
 */
@Composable
fun ArrivalsPanel(
    viewModel: ArrivalsViewModel,
    listState: LazyListState,
    initialTitle: String,
    handler: ArrivalActionHandler,
    mapRouteColors: Map<String, Int> = emptyMap(),
    selectedRowKey: String? = null,
    selectedRouteId: String? = null,
    selectedRouteNames: List<String> = emptyList(),
    onClearRouteSelection: (() -> Unit)? = null,
    // Reports the panel's total content height in px (pinned header + the fully-laid-out list) so the
    // host can fit the peek to short stops; not reported until the whole list is laid out.
    onContentHeight: (heightPx: Int) -> Unit,
    // Tapping the pinned stop-name header invokes this (null = not tappable); the drawer host wires it
    // to an animated map recenter on the focused stop.
    onTitleClick: (() -> Unit)? = null,
    // An opaque anchor modifier a host may attach to the first row's ETA pill (e.g. for an onboarding
    // spotlight). The panel stays ignorant of what it's for.
    etaAnchor: Modifier = Modifier,
) {
    // The system navigation-bar inset (height varies by handset); see the list contentPadding below.
    val navBarInset = navigationBarBottomPadding()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArrivalsPolling(viewModel)
    StopDetailsHost(viewModel)
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    val content = state as? ArrivalsUiState.Content

    // Service alerts are collapsed by default; the header's alert icon toggles them so they no longer
    // crowd the head of the list. Resets to collapsed only when the panel leaves composition.
    var alertsExpanded by remember { mutableStateOf(false) }
    // The pinned header's height (px). Added to the list's laid-out extent to report the whole
    // panel's content height, which the host fits the collapsed peek to (capping tall stops).
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // The panel's total content height (px), or null until the whole list is laid out. Material3
    // measures the sheet content at full container height regardless of the peek, so listState's
    // layoutInfo reflects the full list: if the last item is present, its bottom is the true content
    // height; if it isn't, the content is taller than the screen (well past the peek cap) and an exact
    // number isn't needed. This reads real layout — not a magnitude heuristic.
    val contentHeightPx by remember(listState, content) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (content != null && headerHeightPx > 0 && last != null &&
                last.index == info.totalItemsCount - 1
            ) {
                headerHeightPx + last.offset + last.size
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
            // The stop header is pinned at the top of the drawer; everything below is a single
            // scrollable arrivals list. The collapsed peek is a fixed-fraction window onto the top of
            // that list (sized by the host), so there's no separate peek layout.
            ArrivalsPanelHeader(
                title = content?.header?.name?.takeIf { it.isNotEmpty() } ?: initialTitle,
                direction = content?.header?.direction,
                isFavorite = content?.header?.isFavorite == true,
                showActions = content != null,
                hasAlerts = content?.hasAlerts == true,
                alertsExpanded = alertsExpanded,
                onToggleAlerts = { alertsExpanded = !alertsExpanded },
                onToggleFavorite = viewModel::toggleFavorite,
                onTitleClick = onTitleClick,
                // Feed the pinned header's measured height into the reported content height.
                modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
            )
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
                    onClearRouteSelection = onClearRouteSelection,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    // The drawer header already shows the direction as a "(N)" tag.
                    showDirection = false,
                    // Alerts stay collapsed until the header's alert icon is tapped.
                    showAlerts = alertsExpanded,
                    // Header sits above the list now, so only inset the bottom (clearing the nav-bar
                    // chrome).
                    contentPadding = PaddingValues(bottom = navBarInset),
                    // The onboarding spotlight anchors on the first route row's ETA pill.
                    etaAnchor = etaAnchor,
                )
            }
        }
    }
}

/** A preview-only separator (a touch thicker, inset from both edges to span ~90% of the width)
 *  between the two stacked header previews below. */
@Composable
private fun ColumnScope.PreviewDivider() {
    HorizontalDivider(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth(0.9f),
        thickness = 2.dp
    )
}


/**
 * The stop header pinned at the top of the panel: the favorite star and stop name (with a
 * compass-direction tag appended, e.g. "Pine St & 3rd Ave (N)") as one centered unit, an
 * alert indicator right-justified. Expand/collapse is driven by the sheet's drag handle now,
 * so the header no longer toggles on tap or shows a chevron. Tapping the stop name instead invokes
 * [onTitleClick] (null = not tappable), which the drawer host uses to recenter the map on the stop.
 * [starSize] is exposed so the star's sizing can be tuned in the preview.
 */
@VisibleForTesting
@Composable
internal fun ArrivalsPanelHeader(
    title: String,
    direction: String?,
    isFavorite: Boolean,
    showActions: Boolean,
    hasAlerts: Boolean,
    alertsExpanded: Boolean,
    onToggleAlerts: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    starSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val name = if (!direction.isNullOrBlank()) "$title (${direction.trim()})" else title
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        // Star + name as one centered unit (kept clear of the right-justified indicators).
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showActions) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.star else R.drawable.star_outline
                    ),
                    contentDescription = stringResource(R.string.stop_info_favorite),
                    tint = colorResource(R.color.navdrawer_icon_tint),
                    // Expand the star's tappable area to the 48dp accessibility minimum (the glyph
                    // itself stays starSize). As with the alert toggle, clickable is the outer node so
                    // its pointer region measures the reserved 48dp.
                    modifier = Modifier
                        .clickable(onClick = onToggleFavorite)
                        .minimumInteractiveComponentSize()
                        .padding(end = 6.dp)
                        .size(starSize)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = onTitleClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
            )
        }
        // Alert indicator, right-justified. Tapping it toggles the service-alert list.
        if (hasAlerts) {
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = stringResource(
                    if (alertsExpanded) {
                        R.string.stop_info_hide_alerts_toggle
                    } else {
                        R.string.stop_info_show_alerts
                    }
                ),
                tint = MaterialTheme.colorScheme.error,
                // The warning glyph is 24dp; expand its tappable area to the 48dp
                // accessibility minimum without enlarging the icon. clickable must stay
                // the outer node so its pointer region measures the reserved 48dp.
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onToggleAlerts)
                    .minimumInteractiveComponentSize()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — the pinned stop header. (The route rows are RouteArrivalRow, previewed in ArrivalRows.kt
// via RouteArrivalRowPreview.)

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ArrivalsPanelHeaderPreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                // Short stop name: the star + name unit stays centered.
                ArrivalsPanelHeader(
                    title = "Pine St & 3rd Ave",
                    direction = "N",
                    isFavorite = true,
                    showActions = true,
                    hasAlerts = false,
                    alertsExpanded = false,
                    onToggleAlerts = {},
                    onToggleFavorite = {}
                )
                PreviewDivider()
                // Long stop name: it should ellipsize, kept clear of the right-justified indicators.
                ArrivalsPanelHeader(
                    title = "Northgate Transit Center - Bay 3 & Light Rail Station Entrance",
                    direction = "SW",
                    isFavorite = false,
                    showActions = true,
                    hasAlerts = true,
                    alertsExpanded = true,
                    onToggleAlerts = {},
                    onToggleFavorite = {}
                )
            }
        }
    }
}
