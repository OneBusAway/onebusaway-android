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
// This panel threads named per-element anchor modifiers (etaAnchor/starAnchor) a host attaches to
// specific sub-elements for the onboarding spotlight — several per composable, none being the root
// `modifier` — so ModifierParameter's "name it modifier" rule doesn't apply here.
@file:Suppress("ModifierParameter")

package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalActionHandler
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.ArrivalsList
import org.onebusaway.android.ui.arrivals.ArrivalsPolling
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.dialogs.RouteFavoriteHost
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsHost
import org.onebusaway.android.ui.arrivals.rememberArrivalRowCallbacks
import org.onebusaway.android.ui.compose.MorphByProgress
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.ArrivalInfoUtils

/**
 * The arrivals content for HomeActivity's map slide-up panel. Unlike the standalone screen, the
 * drawer is laid out top-to-bottom as: the pinned stop header, then the arrivals (a compact 2-row peek
 * at rest, morphing into the full scrollable list as the drawer opens). The hosting BottomSheetScaffold
 * supplies the drag handle above this content; tapping/dragging it drives expand/collapse (the header
 * no longer carries a chevron or tap-to-toggle).
 *
 * The peek height is driven by the host: this composable reports the preferred-arrival count +
 * filter state via [onPreferredHeight] so the host can size the collapsed panel. Polling, callbacks,
 * the per-arrival menu, and the expanded list are shared with the standalone screen.
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
    onPreferredHeight: (previewCount: Int, filtering: Boolean) -> Unit,
    // Tapping the pinned stop-name header invokes this (null = not tappable); the drawer host wires it
    // to an animated map recenter on the focused stop.
    onTitleClick: (() -> Unit)? = null,
    // Opaque anchor modifiers a host may attach to the first peek row's ETA pill + favorite star (e.g.
    // for an onboarding spotlight). The panel stays ignorant of what they're for.
    etaAnchor: Modifier = Modifier,
    starAnchor: Modifier = Modifier,
) {
    // The system navigation-bar inset (height varies by handset); see the list contentPadding below.
    val navBarInset = navigationBarBottomPadding()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArrivalsPolling(viewModel)
    RouteFavoriteHost(viewModel)
    StopDetailsHost(viewModel)
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    val content = state as? ArrivalsUiState.Content

    // The original indexes (into content.arrivals) of the rows shown in the peek.
    // findPreferredArrivalIndexes returns *every* favorited match, which grows as the time window
    // widens ("load more arrivals"); the collapsed peek only has room for two rows, so cap here. Kept
    // as indexes (not just the arrivals) so the list below can drop these exact rows — when expanded
    // they morph into the pinned cards and listing them again would duplicate them.
    val previewIndexes = remember(content?.arrivals) {
        val arrivals = content?.arrivals ?: return@remember emptyList<Int>()
        ArrivalInfoUtils.findPreferredArrivalIndexes(ArrayList(arrivals))
            ?.take(2)
            ?.filter { it in arrivals.indices }
            .orEmpty()
    }
    val previewArrivals = remember(content?.arrivals, previewIndexes) {
        val arrivals = content?.arrivals ?: return@remember emptyList<ArrivalInfo>()
        previewIndexes.mapNotNull { arrivals.getOrNull(it) }
    }
    val filtering = (content?.filteredRouteCount ?: 0) > 0
    // The morph + peek/list de-dup only applies to the flat Style-A list (the default). Style B groups
    // arrivals into route cards, which a single peek row can't morph into, so it stays as-is.
    val styleA = content?.style == BuildFlavorUtils.ARRIVAL_INFO_STYLE_A
    // Reveal the below-peek list as soon as the drawer leaves its resting peek (progress > 0), so it
    // slides into view with the drag instead of popping in at the settle; at rest it stays hidden so
    // nothing bleeds through the collapsed fold.
    val revealList by remember(expandProgress) { derivedStateOf { expandProgress() > 0f } }
    // Only report the peek size once arrivals have actually loaded. Reporting the loading skeleton's
    // 0-count first would shrink the sheet's peek anchor and then grow it when arrivals arrive; that
    // anchor move, landing mid-open-animation, strands the BottomSheetScaffold's AnchoredDraggable so
    // the sheet sticks partway up. Holding the peek steady (at its prior/default height) until real
    // content lands keeps the anchor stable through the open.
    if (content != null) {
        LaunchedEffect(previewArrivals.size, filtering) {
            onPreferredHeight(previewArrivals.size, filtering)
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The stop header is pinned at the top of the drawer; everything below is a single
            // scrollable arrivals list (the preferred rows ride at the top of that list and morph
            // from peek to card as the drawer opens — see leadingContent below).
            ArrivalsPanelHeader(
                title = content?.header?.name?.takeIf { it.isNotEmpty() } ?: initialTitle,
                direction = content?.header?.direction,
                isFavorite = content?.header?.isFavorite == true,
                showActions = content != null,
                hasAlerts = content?.hasAlerts == true,
                filtering = filtering,
                onToggleFavorite = viewModel::toggleFavorite,
                onTitleClick = onTitleClick,
            )
            if (content == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                ArrivalsList(
                    content = content,
                    rowCallbacks = rowCallbacks,
                    handler = handler,
                    onLoadMore = viewModel::loadMore,
                    onShowAllRoutes = viewModel::showAllRoutes,
                    onShowHiddenAlerts = viewModel::showHiddenAlerts,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    // The drawer header already shows the direction as a "(N)" tag.
                    showDirection = false,
                    // Header sits above the list now, so only inset the bottom (clearing the nav-bar
                    // chrome); the collapsed peek shows the header + the leading rows below it.
                    contentPadding = PaddingValues(bottom = navBarInset),
                    // The preferred arrivals lead the list (as morphing peek rows); drop them from the
                    // main arrivals so they aren't shown twice. (Style B groups arrivals, so no de-dup.)
                    excludedArrivalIndexes = if (styleA) previewIndexes.toSet() else emptySet(),
                    // Until the drawer leaves its peek, show only the leading peek rows (no below-fold
                    // bleed-through); past that they reveal progressively with the drag.
                    showFullList = revealList,
                    leadingContent = {
                        itemsIndexed(
                            previewArrivals,
                            key = { index, arrival -> "peek:${arrival.tripId}#$index" }
                        ) { index, arrival ->
                            // The host's anchors apply to the first peek row only.
                            val etaModifier = if (index == 0) etaAnchor else Modifier
                            val starModifier = if (index == 0) starAnchor else Modifier
                            if (styleA) {
                                MorphingArrivalRow(
                                    arrival = arrival,
                                    actions = content.actions[arrival.tripId],
                                    filterActive = filtering,
                                    callbacks = rowCallbacks,
                                    progress = expandProgress,
                                    etaModifier = etaModifier,
                                    starModifier = starModifier,
                                )
                            } else {
                                PeekRow(
                                    arrival = arrival,
                                    actions = content.actions[arrival.tripId],
                                    filterActive = filtering,
                                    callbacks = rowCallbacks,
                                    etaModifier = etaModifier,
                                    starModifier = starModifier,
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


/** Adapts an [ArrivalInfo] onto [PeekRowVisual], wiring the favorite star and per-arrival menu. */
@Composable
private fun PeekRow(
    arrival: ArrivalInfo,
    actions: ArrivalActions?,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks,
    etaModifier: Modifier = Modifier,
    starModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    PeekRowVisual(
        shortName = arrival.shortName.orEmpty(),
        headsign = arrival.headsign.orEmpty(),
        eta = arrival.eta,
        etaColor = colorResource(arrival.color),
        predicted = arrival.predicted,
        isFavorite = actions?.isRouteFavorite == true,
        onFavorite = { actions?.let { callbacks.onRouteFavorite(it) } },
        onMore = { expanded = true },
        etaModifier = etaModifier,
        starModifier = starModifier,
        menu = {
            ArrivalActionsMenu(expanded, { expanded = false }, arrival, actions, filterActive, callbacks)
        }
    )
}

/**
 * A peek arrival that morphs between its two formats in lockstep with the drawer's open fraction
 * [progress]: the compact [PeekRow] at 0 and the full [ArrivalRowStyleA] card at 1. The seekable
 * crossfade/size morph lives in [MorphByProgress]; this just binds the two arrival layouts to it.
 */
@Composable
private fun MorphingArrivalRow(
    arrival: ArrivalInfo,
    actions: ArrivalActions?,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks,
    progress: () -> Float,
    etaModifier: Modifier = Modifier,
    starModifier: Modifier = Modifier,
) {
    MorphByProgress(
        progress = progress,
        start = { PeekRow(arrival, actions, filterActive, callbacks, etaModifier, starModifier) },
        end = { ArrivalRowStyleA(arrival, actions, filterActive, callbacks) },
    )
}

/**
 * A single drawer peek row, driven by primitives so it's previewable: a full-height favorite star,
 * the route short name and destination in line, a white-on-lateness ETA pill, and a full-size
 * overflow menu — matching the legacy ArrivalsListHeader eta rows.
 */
@Composable
private fun PeekRowVisual(
    shortName: String,
    headsign: String,
    eta: Long,
    etaColor: Color,
    predicted: Boolean,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    etaModifier: Modifier = Modifier,
    starModifier: Modifier = Modifier,
    menu: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFavorite, modifier = starModifier) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_toggle_star else R.drawable.ic_toggle_star_outline
                ),
                contentDescription = stringResource(
                    if (isFavorite) R.string.bus_options_menu_remove_star
                    else R.string.bus_options_menu_add_star
                ),
                tint = colorResource(R.color.navdrawer_icon_tint),
                modifier = Modifier.size(28.dp)
            )
        }
        // The shared auto-shrinking route badge (sized down for the condensed peek), so a long short
        // name fits its slot instead of crowding out the headsign.
        LineBadge(text = shortName, maxFontSize = 28.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = headsign,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        EtaPill(eta, etaColor, predicted, modifier = etaModifier)
        Box {
            IconButton(onClick = onMore) {
                Icon(
                    painter = painterResource(R.drawable.ic_navigation_more_vert),
                    contentDescription = stringResource(R.string.stop_info_item_options_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            menu()
        }
    }
}

/**
 * The prominent white-on-lateness ETA pill shown in each drawer peek row (the "above-the-peek" ETA
 * style — white text on the deviation color). Also reused by the Home legend dialog so its samples match
 * the real peek; [canceled] strikes the text through for the legend's canceled row.
 */
@Composable
internal fun EtaPill(
    eta: Long,
    color: Color,
    predicted: Boolean,
    modifier: Modifier = Modifier,
    canceled: Boolean = false,
) {
    val decoration = if (canceled) TextDecoration.LineThrough else null
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = color) {
        // Fixed height + centered content so "NOW" and "21 min" pills render the same height.
        Box(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (eta != 0L) {
                    Text(
                        text = eta.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textDecoration = decoration
                    )
                }
                // The trailing label ("min" / "Now") with the radiating real-time indicator at its
                // upper-right: top-aligning this inner row floats the small indicator to the label's
                // top. The Box is always present so the pill width is stable whether or not it's live.
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (eta == 0L) {
                            stringResource(R.string.stop_info_eta_now)
                        } else {
                            " " + stringResource(R.string.minutes_abbreviation)
                        },
                        fontSize = if (eta == 0L) 22.sp else 14.sp,
                        fontWeight = if (eta == 0L) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                        textDecoration = decoration
                    )
                    Box(Modifier.padding(start = 2.dp).size(8.dp)) {
                        if (predicted) {
                            RealtimeIndicator(color = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
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
) {
    val name = if (!direction.isNullOrBlank()) "$title (${direction.trim()})" else title
    Box(
        Modifier
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
                        if (isFavorite) R.drawable.ic_toggle_star else R.drawable.ic_toggle_star_outline
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
// Previews — the collapsed drawer and its peek row, rendered from primitives.

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun DrawerCollapsedPreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                PeekRowVisual(
                    shortName = "12",
                    headsign = "Interlaken Park Via 19th Ave E",
                    eta = 19,
                    etaColor = colorResource(R.color.stop_info_delayed),
                    predicted = true,
                    isFavorite = false,
                    onFavorite = {},
                    onMore = {}
                )
                PeekDivider()
                PeekRowVisual(
                    shortName = "12",
                    headsign = "Interlaken Park Via 19th Ave E",
                    eta = 21,
                    etaColor = colorResource(R.color.stop_info_delayed),
                    predicted = true,
                    isFavorite = false,
                    onFavorite = {},
                    onMore = {}
                )
                ArrivalsPanelHeader(
                    title = "19th Ave E & E Republican St",
                    direction = "N",
                    isFavorite = false,
                    showActions = true,
                    hasAlerts = false,
                    filtering = false,
                    onToggleFavorite = {},
                    // Tune this in the preview to dial in the star sizing
                    starSize = 20.dp,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun DrawerPeekRowPreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                PeekRowVisual("8", "Mount Baker Transit Center", 1, colorResource(R.color.stop_info_delayed), true, true, {}, {})
                PeekDivider()
                PeekRowVisual("40", "Downtown Seattle", 0, colorResource(R.color.stop_info_ontime), true, false, {}, {})
                PeekDivider()
                PeekRowVisual("550", "Bellevue Transit Center", 28, colorResource(R.color.stop_info_scheduled_time), false, false, {}, {})
                PeekDivider()
                // A long route short name: it should ellipsize at the capped width, not crowd the headsign.
                PeekRowVisual("Mount Si. Trailhead", "North Bend", 12, colorResource(R.color.stop_info_ontime), true, false, {}, {})
            }
        }
    }
}

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
