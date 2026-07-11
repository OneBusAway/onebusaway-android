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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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

/** How many route rows the collapsed drawer peek shows (the top of the ordered list). */
private const val PEEK_ROW_COUNT = 2

/**
 * The arrivals content for HomeActivity's map slide-up panel. Unlike the standalone screen, the
 * drawer is laid out top-to-bottom as: the pinned stop header, then the arrivals (a compact 2-row peek
 * at rest, morphing into the full scrollable list as the drawer opens). The hosting BottomSheetScaffold
 * supplies the drag handle above this content; tapping/dragging it drives expand/collapse (the header
 * no longer carries a chevron or tap-to-toggle).
 *
 * The peek height is driven by the host, sized from real layout rather than hand-tuned dimens: this
 * composable measures its own collapsed peek (the pinned header plus the leading peek rows) and reports
 * the total height in px via [onPeekContentHeight], so the host sizes the sheet's peek without knowing
 * what the peek is made of. Polling, callbacks, the per-arrival menu, and the expanded list are
 * shared with the standalone screen.
 */
@Composable
fun ArrivalsPanel(
    viewModel: ArrivalsViewModel,
    listState: LazyListState,
    // The drawer's live open fraction (0 = collapsed peek, 1 = fully expanded), read each frame to
    // morph the leading peek rows in lockstep with the drag and to reveal the rest of the list.
    expandProgress: () -> Float,
    initialTitle: String,
    handler: ArrivalActionHandler,
    onPeekContentHeight: (heightPx: Int) -> Unit,
    // Tapping the pinned stop-name header invokes this (null = not tappable); the drawer host wires it
    // to an animated map recenter on the focused stop.
    onTitleClick: (() -> Unit)? = null,
    // An opaque anchor modifier a host may attach to the first peek row's ETA pill (e.g. for an
    // onboarding spotlight). The panel stays ignorant of what it's for.
    etaAnchor: Modifier = Modifier,
) {
    // The system navigation-bar inset (height varies by handset); see the list contentPadding below.
    val navBarInset = navigationBarBottomPadding()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArrivalsPolling(viewModel)
    StopDetailsHost(viewModel)
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    val content = state as? ArrivalsUiState.Content

    // The collapsed peek shows the top rows of the already-ordered list (starred first, then by
    // departure — see orderRouteGroupsByFavorite), capped to what fits. It's always the contiguous
    // prefix, so the list below just drops that many leading rows (listing them again would dup them).
    val previewGroups = remember(content?.routeGroups) {
        content?.routeGroups?.take(PEEK_ROW_COUNT).orEmpty()
    }
    val filtering = (content?.filteredRouteCount ?: 0) > 0
    // Reveal the below-peek list as soon as the drawer leaves its resting peek (progress > 0), so it
    // slides into view with the drag instead of popping in at the settle; at rest it stays hidden so
    // nothing bleeds through the collapsed fold.
    val revealList by remember(expandProgress) { derivedStateOf { expandProgress() > 0f } }
    // The collapsed peek's measured pieces (px): the pinned header's height plus each promoted row's
    // *rest* height. The host sizes the sheet's peek from these (header + Σ rows) instead of a
    // hand-tuned dimen table, so it auto-adapts to content, padding, and font scale.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // Per-row rest heights, keyed by peek index. Reset whenever the promoted set changes so a stale
    // height from a since-removed row can't linger in the sum. Peek rows can differ in height (a
    // longer pill strip is no taller, but font scale / a wrapped headsign can), so we sum the real
    // rows rather than assume a uniform height.
    val rowHeights = remember(previewGroups) { mutableStateMapOf<Int, Int>() }

    val previewCount = previewGroups.size
    // Only report once arrivals have loaded AND every piece is actually measured — a 0 (loading
    // skeleton, or a not-yet-laid-out row) would shrink the peek anchor and then grow it when the real
    // height lands. That anchor move, mid-open-animation, strands the BottomSheetScaffold's
    // AnchoredDraggable so the sheet sticks partway up. Gating readiness on a real measurement of every
    // promoted row keeps the anchor stable through the open (the host only reveals the sheet once the
    // height arrives). The panel owns this "header + rows" formula so the host doesn't.
    val metricsMeasured = headerHeightPx > 0 &&
        (previewCount == 0 || (rowHeights.size == previewCount && rowHeights.values.all { it > 0 }))
    if (content != null && metricsMeasured) {
        val peekContentPx = headerHeightPx + rowHeights.values.sum()
        LaunchedEffect(peekContentPx) { onPeekContentHeight(peekContentPx) }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The stop header is pinned at the top of the drawer; everything below is a single
            // scrollable arrivals list (the preferred rows ride at the top of that list as the
            // collapsed peek — see leadingContent below).
            ArrivalsPanelHeader(
                title = content?.header?.name?.takeIf { it.isNotEmpty() } ?: initialTitle,
                direction = content?.header?.direction,
                isFavorite = content?.header?.isFavorite == true,
                showActions = content != null,
                hasAlerts = content?.hasAlerts == true,
                filtering = filtering,
                onToggleFavorite = viewModel::toggleFavorite,
                onTitleClick = onTitleClick,
                // Feed the pinned header's measured height into the collapsed-peek metrics.
                modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
            )
            if (content == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                ArrivalsList(
                    content = content,
                    rowCallbacks = rowCallbacks,
                    handler = handler,
                    onShowAllRoutes = viewModel::showAllRoutes,
                    onShowHiddenAlerts = viewModel::showHiddenAlerts,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    // The drawer header already shows the direction as a "(N)" tag.
                    showDirection = false,
                    // Header sits above the list now, so only inset the bottom (clearing the nav-bar
                    // chrome); the collapsed peek shows the header + the leading rows below it.
                    contentPadding = PaddingValues(bottom = navBarInset),
                    // The promoted route rows lead the list (as the peek); drop them from the main
                    // list below so they aren't shown twice.
                    excludedGroupIndexes = previewGroups.indices.toSet(),
                    // Until the drawer leaves its peek, show only the leading peek rows (no below-fold
                    // bleed-through); past that they reveal progressively with the drag.
                    showFullList = revealList,
                    leadingContent = {
                        itemsIndexed(
                            previewGroups,
                            key = { index, group -> "peek:${group.key}#$index" }
                        ) { index, group ->
                            // Peek and expanded share one route-row format now, so the peek row IS the
                            // expanded row — no peek→card morph, hence a constant height regardless of
                            // the drawer's open fraction. Record it unconditionally: a guard on
                            // expandProgress would skip a row that first lays out while the sheet is
                            // already open (e.g. restored expanded after a rotation), leaving its height
                            // unrecorded and the peek anchor stale. Since the height never changes with
                            // the drag, unconditional recording can't inflate the anchor mid-open. The
                            // host's ETA anchor applies to the first peek row only.
                            Box(Modifier.onSizeChanged { rowHeights[index] = it.height }) {
                                RouteArrivalRow(
                                    group = group,
                                    dataVersion = content.dataVersion,
                                    actionsFor = { content.actions[it.tripId] },
                                    isFavorite = group.routeId in content.favoriteRouteIds,
                                    filterActive = filtering,
                                    callbacks = rowCallbacks,
                                    etaAnchor = if (index == 0) etaAnchor else Modifier,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A row separator that's a touch thicker and inset from both edges (spans ~90% of the width). */
@Composable
private fun ColumnScope.PeekDivider() {
    HorizontalDivider(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth(0.9f),
        thickness = 2.dp
    )
}


/**
 * The stop header pinned at the top of the panel: the favorite star and stop name (with a
 * compass-direction tag appended, e.g. "Pine St & 3rd Ave (N)") as one centered unit, any
 * filter/alert indicators right-justified. Expand/collapse is driven by the sheet's drag handle now,
 * so the header no longer toggles on tap or shows a chevron. Tapping the stop name instead invokes
 * [onTitleClick] (null = not tappable), which the drawer host uses to recenter the map on the stop.
 * [starSize] is exposed so the star's sizing can be tuned in the preview.
 */
@Composable
private fun ArrivalsPanelHeader(
    title: String,
    direction: String?,
    isFavorite: Boolean,
    showActions: Boolean,
    hasAlerts: Boolean,
    filtering: Boolean,
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
                    modifier = Modifier
                        .clickable(onClick = onToggleFavorite)
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
        // Filter/alert indicators, right-justified.
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (filtering) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_filter_list),
                    contentDescription = stringResource(R.string.stop_info_option_filter),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasAlerts) {
                Icon(
                    painter = painterResource(R.drawable.baseline_warning_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — the pinned stop header. (The peek rows are RouteArrivalRow now, which needs the
// un-previewable ArrivalInfo model; the pill strip is covered by EtaPillStripPreview.)

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
                    filtering = false,
                    onToggleFavorite = {}
                )
                PeekDivider()
                // Long stop name: it should ellipsize, kept clear of the right-justified indicators.
                ArrivalsPanelHeader(
                    title = "Northgate Transit Center - Bay 3 & Light Rail Station Entrance",
                    direction = "SW",
                    isFavorite = false,
                    showActions = true,
                    hasAlerts = true,
                    filtering = true,
                    onToggleFavorite = {}
                )
            }
        }
    }
}
