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

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.models.Status
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.util.comparators.AlphanumComparator

/**
 * The per-arrival menu actions (legacy `showListItemMenu`). Implemented by the host activity,
 * which has the Context needed to launch the targets. The route-filter toggle is
 * a ViewModel action; the rest are navigation/dialogs.
 */
class ArrivalRowCallbacks(
    val onRouteFavorite: (ArrivalActions) -> Unit,
    val onShowVehiclesOnMap: (ArrivalInfo) -> Unit,
    val onShowTripStatus: (ArrivalInfo) -> Unit,
    val onSetReminder: (ArrivalInfo) -> Unit,
    val onShowOnlyRoute: (String) -> Unit,
    val onShowRouteSchedule: (String) -> Unit,
    val onReportArrivalProblem: (ArrivalActions) -> Unit,
    /** Opens the service-alert dialog for the given situation id (the per-row alert indicator). */
    val onShowAlert: (String) -> Unit
)

/**
 * Groups arrivals into per-(route, headsign) lists for the card style, matching the legacy
 * ArrivalsListAdapterStyleB: sort by route then headsign (alphanumeric), then collect runs.
 */
fun groupForStyleB(arrivals: List<ArrivalInfo>): List<List<ArrivalInfo>> {
    val comparator = AlphanumComparator()
    val sorted = arrivals.sortedWith { a, b ->
        val byRoute = comparator.compare(a.routeId, b.routeId)
        if (byRoute != 0) byRoute else comparator.compare(a.headsign.orEmpty(), b.headsign.orEmpty())
    }
    val groups = mutableListOf<MutableList<ArrivalInfo>>()
    for (arrival in sorted) {
        val current = groups.lastOrNull()
        if (current != null &&
            current[0].routeId == arrival.routeId &&
            current[0].headsign.orEmpty() == arrival.headsign.orEmpty()
        ) {
            current.add(arrival)
        } else {
            groups.add(mutableListOf(arrival))
        }
    }
    return groups
}

/**
 * The shaded rounded card that wraps each Style A arrival (and the report-flow picker rows),
 * matching the legacy MaterialCardView. Pass [onClick] to make the whole card a single tap target
 * (the picker); leave it null when the card holds its own buttons (the interactive list row).
 */
@Composable
internal fun ArrivalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    if (onClick != null) {
        Surface(onClick = onClick, modifier = base, shape = shape, color = color, content = content)
    } else {
        Surface(modifier = base, shape = shape, color = color, content = content)
    }
}

/**
 * The visual content of a flat arrival row, driven by primitives so it stays previewable and
 * testable (the [ArrivalInfo] model can't be built in a @Preview). [ArrivalRowContent] adapts the
 * model onto it; the colored status pill and ETA both take the lateness [statusColor].
 */
@Composable
private fun ArrivalRowVisual(
    shortName: String,
    headsign: String,
    statusText: String,
    statusColor: Color,
    timeText: String,
    eta: Long,
    predicted: Boolean,
    canceled: Boolean,
    modifier: Modifier = Modifier,
    onAlertClick: (() -> Unit)? = null
) {
    val decoration = strikeThroughIf(canceled)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LineBadge(
            text = shortName,
            maxFontSize = 36.sp,
            textDecoration = decoration
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = headsign,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textDecoration = decoration
            )
            if (statusText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                StatusPill(statusText, statusColor)
            }
            if (timeText.isNotEmpty()) {
                // The scheduled/predicted clock time: "Arriving/Departing/Arrived/Departed at 4:27 PM"
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onAlertClick != null) {
            ArrivalAlertIndicator(onClick = onAlertClick)
        }
        Spacer(Modifier.width(8.dp))
        EtaContent(eta, statusColor, predicted, canceled)
    }
}

/** The tappable per-row service-alert indicator (issue #1687 Bug 2): the same warning glyph the
 *  header/banner uses, shown when this arrival is affected by an active alert; taps open its dialog. */
@Composable
internal fun ArrivalAlertIndicator(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.baseline_warning_24),
            contentDescription = stringResource(R.string.stop_info_arrival_service_alert),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/** Adapts the [ArrivalInfo] display model onto [ArrivalRowVisual]. Shared by the interactive Style
 *  A row and the report-flow picker (which wrap it with their own click + card). [onAlertClick] adds
 *  the tappable alert indicator when the arrival is affected by an active alert (null hides it). */
@Composable
internal fun ArrivalRowContent(
    arrival: ArrivalInfo,
    modifier: Modifier = Modifier,
    onAlertClick: (() -> Unit)? = null
) {
    ArrivalRowVisual(
        shortName = arrival.shortName.orEmpty(),
        headsign = arrival.headsign.orEmpty(),
        statusText = arrival.statusText.orEmpty(),
        statusColor = colorResource(arrival.color),
        timeText = arrival.timeText.orEmpty(),
        eta = arrival.eta,
        predicted = arrival.predicted,
        canceled = arrival.status == Status.CANCELED,
        modifier = modifier,
        onAlertClick = onAlertClick
    )
}

/**
 * A single flat arrival row (Style A): a shaded card holding the tappable favorite star, the row
 * content, and a horizontal-overflow menu — matching the legacy `arrivals_list_item` card.
 */
@Composable
fun ArrivalRowStyleA(
    arrival: ArrivalInfo,
    actions: ArrivalActions?,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    StyleACard(
        modifier = modifier,
        isFavorite = actions?.isRouteFavorite,
        onFavorite = { actions?.let { callbacks.onRouteFavorite(it) } },
        onMore = { expanded = true },
        onContentClick = { expanded = true },
        overflow = {
            ArrivalActionsMenu(expanded, { expanded = false }, arrival, actions, filterActive, callbacks)
        }
    ) {
        ArrivalRowContent(arrival, Modifier.fillMaxWidth(), alertClick(actions, callbacks))
    }
}

/** The alert-indicator tap for a row: opens the arrival's active alert, or null when it has none. */
internal fun alertClick(actions: ArrivalActions?, callbacks: ArrivalRowCallbacks): (() -> Unit)? =
    actions?.alertSituationId?.let { id -> { callbacks.onShowAlert(id) } }

/**
 * The Style A card scaffold: the row content with a small favorite star tucked into the top-left
 * corner and a horizontal-overflow into the top-right (legacy `route_favorite` / `more_horizontal`).
 * Shared by the live row and its @Preview so the corner layout and padding can't drift apart.
 *
 * @param isFavorite the star's state, or null to omit the star (e.g. when there are no actions)
 * @param onContentClick tap handler for the row body, or null for a non-interactive body (preview)
 * @param overflow the dropdown menu anchored to the top-right overflow icon
 */
@Composable
private fun StyleACard(
    isFavorite: Boolean?,
    onFavorite: () -> Unit,
    onMore: () -> Unit,
    onContentClick: (() -> Unit)?,
    overflow: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ArrivalCard(modifier) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (onContentClick != null) Modifier.clickable(onClick = onContentClick) else Modifier)
                    // A little top room so the 36sp route/ETA clear the overlaid corner icons
                    .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 6.dp)
            ) {
                content()
            }
            if (isFavorite != null) {
                CornerIcon(
                    iconRes = if (isFavorite) R.drawable.ic_toggle_star else R.drawable.ic_toggle_star_outline,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.bus_options_menu_remove_star
                        else R.string.bus_options_menu_add_star
                    ),
                    tint = colorResource(R.color.navdrawer_icon_tint),
                    onClick = onFavorite,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                CornerIcon(
                    iconRes = R.drawable.ic_navigation_more_horiz,
                    contentDescription = stringResource(R.string.stop_info_item_options_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onMore
                )
                overflow()
            }
        }
    }
}

/** A small tap target tucked into a card corner — the legacy overlaid star / overflow icons. */
@Composable
private fun CornerIcon(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .size(18.dp)
    )
}

/** A card grouping all upcoming arrivals for one route + headsign (Style B). */
@Composable
fun ArrivalCardStyleB(
    group: List<ArrivalInfo>,
    actions: ArrivalActions?,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier
) {
    val first = group.first()
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = first.shortName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = first.headsign.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = canceledDecoration(first)
                    )
                }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_navigation_more_vert),
                            contentDescription = stringResource(R.string.stop_info_item_options_title)
                        )
                    }
                    ArrivalActionsMenu(expanded, { expanded = false }, first, actions, filterActive, callbacks)
                }
            }
            group.forEachIndexed { index, arrival ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 8.dp else 0.dp)
                        // Subsequent arrivals are dimmed, matching the legacy card
                        .alpha(if (index == 0) 1f else 0.55f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusText(arrival)
                    EtaBlock(arrival)
                }
            }
        }
    }
}

/** The dropdown of per-arrival actions, gated by the same route/trip availability rules (minus occupancy). */
@Composable
internal fun ArrivalActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    arrival: ArrivalInfo,
    actions: ArrivalActions?,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (actions != null) {
            val favLabel = if (actions.isRouteFavorite) {
                R.string.bus_options_menu_remove_star
            } else {
                R.string.bus_options_menu_add_star
            }
            MenuRow(favLabel) { onDismiss(); callbacks.onRouteFavorite(actions) }
        }
        MenuRow(R.string.bus_options_menu_show_vehicles_on_map) {
            onDismiss(); callbacks.onShowVehiclesOnMap(arrival)
        }
        MenuRow(R.string.bus_options_menu_show_trip_details) {
            onDismiss(); callbacks.onShowTripStatus(arrival)
        }
        MenuRow(R.string.bus_options_menu_set_reminder) {
            onDismiss(); callbacks.onSetReminder(arrival)
        }
        val filterLabel = if (filterActive) {
            R.string.bus_options_menu_show_all_routes
        } else {
            R.string.bus_options_menu_show_only_this_route
        }
        MenuRow(filterLabel) { onDismiss(); callbacks.onShowOnlyRoute(arrival.routeId) }
        val url = actions?.scheduleUrl
        if (!url.isNullOrBlank()) {
            MenuRow(R.string.bus_options_menu_show_route_schedule) {
                onDismiss(); callbacks.onShowRouteSchedule(url)
            }
        }
        if (actions != null) {
            MenuRow(R.string.bus_options_menu_report_trip_problem) {
                onDismiss(); callbacks.onReportArrivalProblem(actions)
            }
        }
    }
}

/** A dropdown item that just shows a string resource; shared by the per-arrival and overflow menus. */
@Composable
internal fun MenuRow(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}

private val PillShape = RoundedCornerShape(6.dp)

/** The lateness-colored status pill (white text on the deviation color), the legacy status badge. */
@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = PillShape, color = color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** The prominent ETA, color-coded by lateness, with the real-time indicator as a superscript on
 *  the "min" label (matching the drawer pill); the legacy `eta`/`eta_min`. */
@Composable
private fun EtaContent(eta: Long, color: Color, predicted: Boolean, canceled: Boolean) {
    val decoration = strikeThroughIf(canceled)
    Row {
        if (eta == 0L) {
            Text(
                text = stringResource(R.string.stop_info_eta_now),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textDecoration = decoration
            )
            EtaRealtimeIndicator(predicted, color)
        } else {
            Text(
                text = eta.toString(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textDecoration = decoration,
                modifier = Modifier.alignByBaseline()
            )
            // "min" shares the number's baseline; the indicator rides at its upper-right.
            Row(modifier = Modifier.alignByBaseline(), verticalAlignment = Alignment.Top) {
                Text(
                    text = " " + stringResource(R.string.minutes_abbreviation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    textDecoration = decoration,
                    modifier = Modifier.alignByBaseline()
                )
                EtaRealtimeIndicator(predicted, color)
            }
        }
    }
}

/** The real-time indicator's reserved column (so ETAs right-justify alike); animates when live. */
@Composable
private fun EtaRealtimeIndicator(predicted: Boolean, color: Color) {
    Box(Modifier.padding(start = 2.dp).size(8.dp)) {
        if (predicted) RealtimeIndicator(color = color, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The pulsing real-time "connectedness" indicator: concentric stroked circles expanding and
 * contracting at staggered durations (transparent fill, stroked outline, FastOutLinearIn, REVERSE
 * repeat). Shared by the standalone/list ETA and the map drawer's ETA pill.
 */
@Composable
internal fun RealtimeIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "realtime")
    // Staggered durations make the rings radiate out of phase, matching the legacy 1500/1800/2000.
    val rings = listOf(1500, 1800, 2000).map { duration ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "realtime-$duration"
        )
    }
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2f
        val stroke = Stroke(width = 1.2.dp.toPx())
        rings.forEach { ring ->
            drawCircle(color = color, radius = maxRadius * ring.value, style = stroke)
        }
    }
}

/** [ArrivalInfo]-driven status pill, for the Style B card and the map-panel preview. */
@Composable
internal fun StatusText(arrival: ArrivalInfo) {
    val text = arrival.statusText.orEmpty()
    if (text.isNotEmpty()) StatusPill(text, colorResource(arrival.color))
}

/** [ArrivalInfo]-driven ETA, for the Style B card and the map-panel preview. */
@Composable
internal fun EtaBlock(arrival: ArrivalInfo) {
    EtaContent(arrival.eta, colorResource(arrival.color), arrival.predicted, arrival.status == Status.CANCELED)
}

private fun strikeThroughIf(canceled: Boolean): TextDecoration =
    if (canceled) TextDecoration.LineThrough else TextDecoration.None

private fun canceledDecoration(arrival: ArrivalInfo): TextDecoration =
    strikeThroughIf(arrival.status == Status.CANCELED)

// ---------------------------------------------------------------------------------------------
// Previews — exercise the Style A card states without the (un-previewable) ArrivalInfo model.

@Preview(showBackground = true)
@Composable
private fun ArrivalRowStyleAPreview() {
    ObaTheme {
        Column(Modifier.padding(vertical = 8.dp)) {
            PreviewStyleACard("8", "Capitol Hill", "2 min late", R.color.stop_info_delayed, "Departing at 4:32 PM", 5, predicted = true, favorite = true)
            PreviewStyleACard("12", "Downtown Seattle", "On time", R.color.stop_info_ontime, "Departing at 4:27 PM", 0, predicted = true, favorite = false)
            PreviewStyleACard("40", "Northgate", "3 min early", R.color.stop_info_early, "Arriving at 4:39 PM", 12, predicted = true, favorite = false)
            PreviewStyleACard("550", "Bellevue Transit Center", "Scheduled", R.color.stop_info_scheduled_time, "Departing at 4:49 PM", 22, predicted = false, favorite = false)
            PreviewStyleACard("7", "Rainier Beach", "Departed 1 min late", R.color.stop_info_delayed, "Departed at 4:25 PM", -2, predicted = true, favorite = false)
            PreviewStyleACard("49", "University District", "Canceled", R.color.stop_info_scheduled_time, "", 8, predicted = false, favorite = false, canceled = true)
        }
    }
}

/** Mirrors [ArrivalRowStyleA]'s layout with static icons so the @Preview can render every state. */
@Composable
private fun PreviewStyleACard(
    shortName: String,
    headsign: String,
    status: String,
    statusColorRes: Int,
    timeText: String,
    eta: Long,
    predicted: Boolean,
    favorite: Boolean,
    canceled: Boolean = false
) {
    StyleACard(
        isFavorite = favorite,
        onFavorite = {},
        onMore = {},
        onContentClick = null,
        overflow = {}
    ) {
        ArrivalRowVisual(
            shortName = shortName,
            headsign = headsign,
            statusText = status,
            statusColor = colorResource(statusColorRes),
            timeText = timeText,
            eta = eta,
            predicted = predicted,
            canceled = canceled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
