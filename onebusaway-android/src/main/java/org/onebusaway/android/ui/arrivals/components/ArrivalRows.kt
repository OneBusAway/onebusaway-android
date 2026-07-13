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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onebusaway.android.R
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.LoadMoreState
import org.onebusaway.android.ui.compose.components.FavoriteStarButton
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

/**
 * The per-arrival menu actions (legacy `showListItemMenu`). Implemented by the host activity,
 * which has the Context needed to launch the targets. The route-filter toggle is
 * a ViewModel action; the rest are navigation/dialogs.
 */
class ArrivalRowCallbacks(
    val onRouteFavorite: (ArrivalActions) -> Unit,
    val onShowVehiclesOnMap: (ArrivalInfo) -> Unit,
    /** The ETA pill was tapped: focus that arrival's live vehicle + its stop (whole-route tap is the row body). */
    val onEtaClick: (ArrivalInfo) -> Unit,
    val onShowTripStatus: (ArrivalInfo) -> Unit,
    val onSetReminder: (ArrivalInfo) -> Unit,
    val onShowOnlyRoute: (String) -> Unit,
    val onShowRouteSchedule: (String) -> Unit,
    val onReportArrivalProblem: (ArrivalActions) -> Unit,
    /** Opens the service-alert dialog for the given situation id (the per-row alert indicator). */
    val onShowAlert: (String) -> Unit,
    /** Fires a widen-window reload — invoked by pulling an ETA strip past its end (replaces the old
     *  "load more arrivals" footer button). Returns the request token whose lifecycle
     *  [loadMoreState] reports. */
    val onLoadMore: () -> Int,
    /** The stop's shared load-more lifecycle; the strip that fired the matching token drives its
     *  spinner/reveal from this. */
    val loadMoreState: StateFlow<LoadMoreState>
)

/**
 * The shaded rounded card that wraps each arrival row (and the report-flow picker rows),
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
        .padding(horizontal = 12.dp, vertical = 3.dp)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(onClick = onClick, modifier = base, shape = shape, color = color, content = content)
    } else {
        Surface(modifier = base, shape = shape, color = color, content = content)
    }
}

/**
 * The visual content of a flat arrival row, driven by primitives so it stays trivially previewable and
 * JVM-testable without the [ArrivalInfo] model (which needs a `Context`/wire data to build).
 * [ArrivalRowContent] adapts the
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
    badgeContainer: Color = Color.Unspecified,
    badgeContent: Color = Color.Unspecified,
    onAlertClick: (() -> Unit)? = null,
    onEtaClick: (() -> Unit)? = null,
) {
    val decoration = strikeThroughIf(canceled)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LineBadge(
            text = shortName,
            maxFontSize = 36.sp,
            color = badgeContent,
            containerColor = badgeContainer,
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
        EtaContent(eta, statusColor, predicted, canceled, onClick = onEtaClick)
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
    badgeContainer: Color = Color.Unspecified,
    badgeContent: Color = Color.Unspecified,
    onAlertClick: (() -> Unit)? = null,
    onEtaClick: (() -> Unit)? = null,
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
        badgeContainer = badgeContainer,
        badgeContent = badgeContent,
        onAlertClick = onAlertClick,
        onEtaClick = onEtaClick,
    )
}

/**
 * One arrivals row for a single (route, direction): the route badge on the left, and on the right
 * the direction name over a horizontally-scrollable strip of per-trip ETA pills (soonest first).
 * The unified row (issue #1707) — replaces the old per-trip Style A row and Style B card.
 *
 * - Tapping the row body frames the whole route/direction on the map ([ArrivalRowCallbacks.onShowVehiclesOnMap]).
 * - Tapping a pill focuses that specific trip's vehicle + stop ([ArrivalRowCallbacks.onEtaClick]).
 * - Long-pressing a pill opens that trip's menu (details / reminder / report).
 * - The top-left corner star toggles the route favorite ([ArrivalRowCallbacks.onRouteFavorite]).
 * - The top-right overflow ⋮ opens the route-level menu (show-only / schedule).
 *
 * [actionsFor] resolves each trip's [ArrivalActions] (keyed by trip id upstream); the representative
 * trip's actions drive the badge color and the route menu. [etaAnchor] is attached to the first pill
 * (the onboarding spotlight target).
 */
@Composable
fun RouteArrivalRow(
    group: RouteRowGroup,
    // The Content.dataVersion the group came from — MUST be read off the same Content object, so the
    // ETA strip's load-more reveal can pair its pills with the data generation they render.
    dataVersion: Long,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    isFavorite: Boolean,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    etaAnchor: Modifier = Modifier,
) {
    val representative = group.representative
    val routeActions = actionsFor(representative)
    var menuExpanded by remember { mutableStateOf(false) }
    val (badgeContainer, badgeContent) = rememberRouteBadgeColors(routeActions?.routeColor)
    // Fall back to the route's long name when the feed gives no headsign for this direction.
    val direction = group.headsign?.takeIf { it.isNotBlank() } ?: routeActions?.routeLongName.orEmpty()
    val onAlertClick = alertClick(routeActions, callbacks)
    ArrivalCard(modifier) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Give the row a definite height (its tallest child) so the divider can fill it.
                    .height(IntrinsicSize.Min)
                    // Tapping the row body frames the whole route on the map (the pills below focus
                    // individual trips instead).
                    .clickable { callbacks.onShowVehiclesOnMap(representative) }
                    // A little top/end room so the badge and pills clear the overlaid overflow icon.
                    .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineBadge(
                    text = representative.shortName.orEmpty(),
                    maxFontSize = 32.sp,
                    color = badgeContent,
                    containerColor = badgeContainer,
                )
                // A full-height thin divider sets the route chip apart from the ETA pills, so the two
                // similar-looking rounded colored chips don't read as the same kind of thing.
                Spacer(Modifier.width(10.dp))
                VerticalDivider()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (direction.isNotBlank()) {
                        // Only the header needs its own clearance from the overlaid overflow icon
                        // (it sits right under it); the ETA strip below reaches the row's true end —
                        // its own trailing chevron gutter already reserves that room, and the pills
                        // sit low enough in the row to clear the icon vertically.
                        DirectionHeader(direction, modifier = Modifier.padding(end = OVERFLOW_ICON_CLEARANCE))
                        Spacer(Modifier.height(6.dp))
                    }
                    EtaStrip(
                        trips = group.trips,
                        dataVersion = dataVersion,
                        actionsFor = actionsFor,
                        callbacks = callbacks,
                        // Justify the soonest upcoming pill to the leading edge (the ETA the row is
                        // sorted by), so recent-past pills overflow left.
                        start = group.firstUpcomingIndex,
                        firstPillModifier = etaAnchor,
                    )
                }
                if (onAlertClick != null) {
                    ArrivalAlertIndicator(onClick = onAlertClick)
                }
            }
            if (routeActions != null) {
                // Own layer, overlaid on top of the row rather than laid out inline, so the star can
                // sit in the corner without shifting the badge/pills (mirrors the overflow icon below).
                Box(Modifier.align(Alignment.TopStart)) {
                    FavoriteStarButton(
                        isFavorite = isFavorite,
                        onClick = { callbacks.onRouteFavorite(routeActions) },
                        tint = colorResource(R.color.navdrawer_icon_tint),
                        iconSize = 20.dp,
                        // Tighten the button's touch box to the icon + a small margin, like the corner
                        // overflow icon below, instead of Material's 48dp default — keeps the star flush
                        // in the corner with no compensating offset.
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                CornerIcon(
                    iconRes = R.drawable.more_vert,
                    contentDescription = stringResource(R.string.stop_info_item_options_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { menuExpanded = true }
                )
                RouteActionsMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    routeId = group.routeId,
                    actions = routeActions,
                    filterActive = filterActive,
                    callbacks = callbacks,
                )
            }
        }
    }
}

/**
 * The "heading toward X" line above a route row's pill strip: a muted forward-arrow (reads as
 * "toward" without asserting a destination — a headsign can be a direction or loop name) followed by
 * the destination in a slightly-tightened monospace so it reads like the sign on the bus.
 */
@Composable
private fun DirectionHeader(direction: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = direction,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The alert-indicator tap for a row: opens the arrival's active alert, or null when it has none. */
internal fun alertClick(actions: ArrivalActions?, callbacks: ArrivalRowCallbacks): (() -> Unit)? =
    actions?.alertSituationId?.let { id -> { callbacks.onShowAlert(id) } }

/** Horizontal clearance [RouteArrivalRow] gives the direction header so it doesn't run under the
 *  overlaid corner icon below ([CornerIcon]'s own footprint is 18dp + 4dp padding on each side —
 *  this is a bit tighter, tuned by eye against a device screenshot rather than derived from it). */
private val OVERFLOW_ICON_CLEARANCE = 20.dp

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

/** The route-level overflow menu (the row's ⋮): narrow the stop to this route and open its schedule.
 *  The route's star now lives only as the row's own corner toggle ([FavoriteStarButton]) — no longer
 *  duplicated here. Per-trip actions live on each pill's long-press menu ([TripActionsMenu]). */
@Composable
internal fun RouteActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    routeId: String,
    actions: ArrivalActions?,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val filterLabel = if (filterActive) {
            R.string.bus_options_menu_show_all_routes
        } else {
            R.string.bus_options_menu_show_only_this_route
        }
        MenuRow(filterLabel) { onDismiss(); callbacks.onShowOnlyRoute(routeId) }
        val url = actions?.scheduleUrl
        if (!url.isNullOrBlank()) {
            MenuRow(R.string.bus_options_menu_show_route_schedule) {
                onDismiss(); callbacks.onShowRouteSchedule(url)
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
 *  the "min" label (matching the drawer pill); the legacy `eta`/`eta_min`. [onClick], when non-null,
 *  makes the ETA its own tap target (focus this trip's vehicle + stop, distinct from the row-body tap). */
@Composable
private fun EtaContent(
    eta: Long,
    color: Color,
    predicted: Boolean,
    canceled: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val decoration = strikeThroughIf(canceled)
    // When clickable, clip to the pill shape so the tap ripple stays inside the ETA. No padding/size
    // change, so the ETA looks identical at rest; the clickable is a solid target on the number itself.
    val rowModifier = if (onClick != null) {
        Modifier.clip(PillShape).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(modifier = rowModifier) {
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
            // One consistent "Xhr Ymin" shape: ["23", "min"] under an hour (the "0hr" is omitted), or
            // ["1", "hr", " 30", "min"] past it. All parts render as a single AnnotatedString (not
            // separate Text composables) so the text shaper kerns across the number/unit boundary
            // instead of gluing together independently-measured boxes — splitting them produced
            // inconsistent gaps (e.g. "1hr" vs "4hr") since cross-Text kerning isn't a thing.
            val etaParts = DisplayFormat.formatEtaParts(LocalContext.current, eta)
            val emphasizedSpan = SpanStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
            val unemphasizedSpan = MaterialTheme.typography.bodyMedium.toSpanStyle().copy(color = color)
            Text(
                text = buildAnnotatedString {
                    etaParts.forEach { part ->
                        withStyle(if (part.emphasized) emphasizedSpan else unemphasizedSpan) {
                            append(part.text)
                        }
                    }
                },
                textDecoration = decoration
            )
            EtaRealtimeIndicator(predicted, color)
        }
    }
}

/** The real-time indicator's reserved column (so ETAs right-justify alike); shown when live. */
@Composable
private fun EtaRealtimeIndicator(predicted: Boolean, color: Color) {
    Box(Modifier.padding(start = 2.dp).size(8.dp)) {
        if (predicted) RealtimeIndicator(color = color, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The real-time ("connectedness") indicator: a static Material `rss_feed` glyph marking an
 * AVL-tracked arrival. Shared by the standalone/list ETA and the map drawer's ETA pill.
 *
 * Deliberately static: the previous concentric-rings version ran an infinite animation per
 * indicator, and a stop with many live pills kept the whole arrivals screen re-rendering every
 * vsync at 120Hz (main thread pegged ~30%, ~35% of frames missing the 8.3ms deadline). A glyph
 * conveys the same "live" cue at zero steady-state cost.
 */
@Composable
internal fun RealtimeIndicator(color: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_rss_feed),
        contentDescription = null,
        modifier = modifier,
        tint = color,
    )
}

private fun strikeThroughIf(canceled: Boolean): TextDecoration =
    if (canceled) TextDecoration.LineThrough else TextDecoration.None

// ---------------------------------------------------------------------------------------------
// Previews.

/**
 * A minimal [ArrivalData] for previews — [ArrivalInfo] computes its display model from this interface,
 * so implementing it (rather than the wire type) is enough to build a real row. Only the fields a row
 * reads are meaningful; the rest default. Times are server-clock offsets from a zero "now" (see
 * [previewArrival]), and `stopSequence` is non-zero so the arrival (not departure) times are used.
 */
private data class PreviewArrivalData(
    override val routeId: String,
    override val shortName: String?,
    override val headsign: String?,
    override val scheduledArrivalTime: ServerTime,
    override val predictedArrivalTime: ServerTime?,
    override val predicted: Boolean,
    override val status: Status? = Status.DEFAULT,
    override val tripId: String = "trip",
    override val stopId: String = "stop",
    override val routeLongName: String? = null,
    override val stopSequence: Int = 1,
    override val serviceDate: Long = 0L,
    override val vehicleId: String? = null,
    override val situationIds: List<String> = emptyList(),
    override val frequency: FrequencyWindow? = null,
    override val historicalOccupancy: Occupancy? = null,
    override val predictedOccupancy: Occupancy? = null,
    override val hasTripStatus: Boolean = false,
    override val scheduleDeviation: Long = 0L,
    override val lastKnownLat: Double? = null,
    override val lastKnownLon: Double? = null,
) : ArrivalData {
    override val scheduledDepartureTime: ServerTime get() = scheduledArrivalTime
    override val predictedDepartureTime: ServerTime? get() = predictedArrivalTime
}

private const val PREVIEW_MIN_MS = 60_000L

/**
 * Builds a real [ArrivalInfo] for a preview: [etaMinutes] from "now" (predicted when [predicted]),
 * with the schedule offset by [scheduleDeviationMinutes] so the pill takes its on-time/late/early
 * color. A null context is passed deliberately — the row shows only the badge, headsign, and pills, so
 * the (context-dependent) status/time labels stay empty and no resources/app singletons are touched.
 */
internal fun previewArrival(
    shortName: String,
    headsign: String,
    etaMinutes: Long,
    predicted: Boolean = true,
    scheduleDeviationMinutes: Long = 0L,
    status: Status = Status.DEFAULT,
): ArrivalInfo {
    val predictedMs = etaMinutes * PREVIEW_MIN_MS
    val scheduledMs = (etaMinutes - scheduleDeviationMinutes) * PREVIEW_MIN_MS
    return ArrivalInfo(
        context = null,
        data = PreviewArrivalData(
            routeId = "route_$shortName",
            shortName = shortName,
            headsign = headsign,
            scheduledArrivalTime = ServerTime(scheduledMs),
            predictedArrivalTime = if (predicted) ServerTime(predictedMs) else null,
            predicted = predicted,
            status = status,
        ),
        now = ServerTime(0L),
        includeArrivalDepartureInStatusLabel = false,
    )
}

/** No-op [ArrivalRowCallbacks] for previews (nothing is interactive in a static preview). */
internal fun previewRowCallbacks() = ArrivalRowCallbacks(
    onRouteFavorite = {},
    onShowVehiclesOnMap = {},
    onEtaClick = {},
    onShowTripStatus = {},
    onSetReminder = {},
    onShowOnlyRoute = {},
    onShowRouteSchedule = {},
    onReportArrivalProblem = {},
    onShowAlert = {},
    onLoadMore = { NO_LOAD_REQUEST },
    loadMoreState = MutableStateFlow(LoadMoreState.Idle),
)

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun RouteArrivalRowPreview() {
    val callbacks = previewRowCallbacks()
    // Badge colors come from the representative trip's ArrivalActions (keyed by trip id).
    val actions = remember {
        mapOf(
            "trip" to ArrivalActions(
                tripId = "trip",
                routeId = "route_40",
                routeShortName = "40",
                routeLongName = "Downtown Seattle",
                routeColor = 0xFF0A5B3E.toInt(),
                scheduleUrl = null,
                agencyName = null,
                blockId = null,
            )
        )
    }
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 8.dp)) {
                // A busy route: an on-time next arrival, a couple of predicted-late trips, then scheduled.
                RouteArrivalRow(
                    group = RouteRowGroup(
                        listOf(
                            previewArrival("40", "Northgate", etaMinutes = 3),
                            previewArrival("40", "Northgate", etaMinutes = 11, scheduleDeviationMinutes = 2),
                            previewArrival("40", "Northgate", etaMinutes = 24, predicted = false),
                        )
                    ),
                    dataVersion = 1L,
                    actionsFor = { actions[it.tripId] },
                    isFavorite = true,
                    filterActive = false,
                    callbacks = callbacks,
                )
                // A single-arrival route with a just-departed (recent-past) pill leading.
                RouteArrivalRow(
                    group = RouteRowGroup(
                        listOf(
                            previewArrival("8", "Rainier Beach", etaMinutes = -2),
                            previewArrival("8", "Rainier Beach", etaMinutes = 9, scheduleDeviationMinutes = -3),
                        )
                    ),
                    dataVersion = 1L,
                    actionsFor = {
                        ArrivalActions(
                            tripId = it.tripId,
                            routeId = "route_8",
                            routeShortName = "8",
                            routeLongName = "MLK Jr Way",
                            routeColor = 0xFF9C27B0.toInt(),
                            scheduleUrl = null,
                            agencyName = null,
                            blockId = null,
                        )
                    },
                    isFavorite = false,
                    filterActive = false,
                    callbacks = callbacks,
                )
            }
        }
    }
}
