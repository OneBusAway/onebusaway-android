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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.compose.components.CenteredLongPressMenu
import org.onebusaway.android.ui.compose.components.DirectionHeadsign
import org.onebusaway.android.ui.compose.components.FavoriteStarButton
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.MaterialSymbols
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

/**
 * The per-arrival menu actions (legacy `showListItemMenu`). Implemented by the host activity,
 * which has the Context needed to launch the targets (navigation/dialogs).
 */
class ArrivalRowCallbacks(
    val onRouteFavorite: (ArrivalActions) -> Unit,
    val onShowVehiclesOnMap: (ArrivalInfo) -> Unit,
    /** Badge long-press "Show route on map" — the whole-route (unscoped) reveal; cf. [onShowVehiclesOnMap]. */
    val onShowRouteOnMap: (ArrivalInfo) -> Unit,
    /** The ETA pill was tapped: focus that arrival's live vehicle + its stop (whole-route tap is the row body). */
    val onEtaClick: (ArrivalInfo) -> Unit,
    val onShowTripStatus: (ArrivalInfo) -> Unit,
    val onSetReminder: (ArrivalInfo) -> Unit,
    val onShowRouteSchedule: (String) -> Unit,
    val onReportArrivalProblem: (ArrivalActions) -> Unit,
    /** Opens the service-alert dialog for the given situation id (the per-row alert indicator). */
    val onShowAlert: (String) -> Unit
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
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 3.dp)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = base,
            shape = shape,
            color = color,
            border = border,
            content = content
        )
    } else {
        Surface(modifier = base, shape = shape, color = color, border = border, content = content)
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
    onEtaClick: (() -> Unit)? = null
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
internal fun ArrivalAlertIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.baseline_warning_24),
            contentDescription = stringResource(R.string.stop_info_arrival_service_alert),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(iconSize)
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
    onEtaClick: (() -> Unit)? = null
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
        onEtaClick = onEtaClick
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
 * - The badge section's top-right corner (by the divider) shows a service-alert warning glyph when
 *   any trip in the group is affected by an active alert; tapping it opens that alert ([ArrivalRowCallbacks.onShowAlert]).
 * - Long-pressing the row body (badge included) opens the route-level menu: "Show route on map"
 *   (always, framing the whole route as if searched — [ArrivalRowCallbacks.onShowRouteOnMap]) plus
 *   "Show route schedule" when the route has a schedule URL.
 *
 * [actionsFor] resolves each trip's [ArrivalActions] (keyed by trip id upstream); the representative
 * trip's actions drive the badge color and the route menu. [etaAnchor] is attached to the first pill
 * (the onboarding spotlight target).
 */
@Composable
fun RouteArrivalRow(
    group: RouteRowGroup,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    isFavorite: Boolean,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    mapRouteColor: Int? = null,
    selected: Boolean = false,
    selectedRouteNames: List<String> = emptyList(),
    etaAnchor: Modifier = Modifier
) {
    val representative = group.representative
    val routeActions = actionsFor(representative)
    var menuExpanded by remember { mutableStateOf(false) }
    val (badgeContainer, badgeContent) = rememberRouteBadgeColors(routeActions?.routeColor)
    // Fall back to the route's long name when the feed gives no headsign for this direction.
    val direction = group.headsign?.takeIf { it.isNotBlank() } ?: routeActions?.routeLongName.orEmpty()
    val onAlertClick = alertClick(group, actionsFor, callbacks)
    val selectionColor = mapRouteColor ?: routeActions?.routeColor
    val scheduleUrl = routeActions?.scheduleUrl?.takeIf { it.isNotBlank() }
    // The menu's always-present item ("Show route on map") labels the long-press for accessibility, and
    // disambiguates the row's long press from the ETA pill's own trip-actions long press.
    val routeMenuLabel = stringResource(R.string.bus_options_menu_show_route_on_map)
    val displayedRouteNames = selectedRouteNames.takeIf { selected }.orEmpty()
    val compoundBadge = displayedRouteNames.size > 1
    val selectionBorder = selectionColor
        ?.takeIf { selected }
        ?.let { BorderStroke(2.dp, Color(it)) }
    ArrivalCard(modifier, border = selectionBorder) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Give the row a definite height (its tallest child) so the divider can fill it.
                    .height(IntrinsicSize.Min)
                    // Tap frames the route scoped to this stop; long press opens the route menu (show on
                    // map / schedule). The ETA pills remain independent children with their own
                    // trip-specific tap/long-press actions.
                    .combinedClickable(
                        onClick = { callbacks.onShowVehiclesOnMap(representative) },
                        onLongClickLabel = routeMenuLabel,
                        onLongClick = { menuExpanded = true }
                    )
                    .padding(
                        start = 10.dp,
                        top = ROW_VERTICAL_PADDING,
                        end = 10.dp,
                        bottom = ROW_VERTICAL_PADDING
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The badge "section": the route chip plus its trailing gap, spanning the row's start
                // to the divider and the full row height. The service-alert glyph is overlaid on this
                // section's top-right corner (flush against the divider), on its own layer — like the
                // corner star and overflow icons — so it never reflows the row. It may overlap the
                // badge, which is acceptable for the rare alert case; same tight 28dp touch box / 20dp
                // glyph footprint as the corner star.
                Box(Modifier.fillMaxHeight()) {
                    LineBadge(
                        text = continuationBadgeText(
                            displayedRouteNames,
                            representative.shortName.orEmpty()
                        ),
                        // The trailing padding is the gap to the divider — part of the badge section,
                        // so the TopEnd-aligned alert glyph sits flush against the divider.
                        modifier = Modifier.align(Alignment.Center).padding(end = 10.dp),
                        maxFontSize = 32.sp,
                        width = if (compoundBadge) 96.dp else 64.dp,
                        maxLines = if (compoundBadge) 1 else 2,
                        color = badgeContent,
                        containerColor = badgeContainer,
                        endContainerColor = mapRouteColor?.let(::Color) ?: Color.Unspecified
                    )
                    if (onAlertClick != null) {
                        ArrivalAlertIndicator(
                            onClick = onAlertClick,
                            iconSize = 20.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                // Cancel the row's vertical padding so the triangle's top lines up with
                                // the corner star, which floats at the card's very top (above this
                                // padding) rather than inside the row's content box.
                                .offset(y = -ROW_VERTICAL_PADDING)
                        )
                    }
                }
                // A full-height thin divider sets the route chip apart from the ETA pills, so the two
                // similar-looking rounded colored chips don't read as the same kind of thing.
                VerticalDivider()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (direction.isNotBlank()) {
                        DirectionHeadsign(direction)
                        Spacer(Modifier.height(6.dp))
                    }
                    EtaStrip(
                        trips = group.trips,
                        actionsFor = actionsFor,
                        callbacks = callbacks,
                        firstPillModifier = etaAnchor
                    )
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            RouteActionsMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onShowRouteOnMap = { callbacks.onShowRouteOnMap(representative) },
                onShowSchedule = scheduleUrl?.let { url -> { callbacks.onShowRouteSchedule(url) } }
            )
        }
    }
}

internal fun continuationBadgeText(routeNames: List<String>, fallback: String): String = when {
    routeNames.size <= 1 -> routeNames.firstOrNull().orEmpty().ifBlank { fallback }
    routeNames.size == 2 -> routeNames.joinToString(" › ")
    else -> "${routeNames.first()} » ${routeNames.last()}"
}

/** The alert-indicator tap for a row: opens the first active alert affecting any trip in the group
 *  ([RouteRowGroup.activeAlertSituationId]), or null when none is affected. */
internal fun alertClick(
    group: RouteRowGroup,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    callbacks: ArrivalRowCallbacks
): (() -> Unit)? = group.activeAlertSituationId(actionsFor)?.let { id -> { callbacks.onShowAlert(id) } }

/** The arrival row's top/bottom padding. The corner alert glyph offsets up by this amount to cancel
 *  it, so its top lines up with the favorite star (which floats above this padding at the card top);
 *  keep the two in sync via this single value rather than a bare literal on each side. */
private val ROW_VERTICAL_PADDING = 8.dp

/** The route-level long-press menu: show the whole route on the map (always), and — when [onShowSchedule]
 *  is non-null (the route has a schedule) — open its schedule. A dumb view: both actions arrive pre-bound.
 *  The route's star lives as the row's own corner toggle ([FavoriteStarButton]); per-trip actions live on
 *  each pill's long-press menu ([TripActionsMenu]). */
@Composable
internal fun RouteActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShowRouteOnMap: () -> Unit,
    onShowSchedule: (() -> Unit)?
) {
    CenteredLongPressMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuRow(
            R.string.bus_options_menu_show_route_on_map,
            ImageVector.vectorResource(R.drawable.ic_action_location_map)
        ) {
            onDismiss()
            onShowRouteOnMap()
        }
        if (onShowSchedule != null) {
            MenuRow(R.string.bus_options_menu_show_route_schedule, MaterialSymbols.Schedule) {
                onDismiss()
                onShowSchedule()
            }
        }
    }
}

/** A labelled menu item with an optional decorative leading [icon]. */
@Composable
internal fun MenuRow(textRes: Int, icon: ImageVector? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        onClick = onClick,
        leadingIcon = icon?.let { { Icon(imageVector = it, contentDescription = null) } }
    )
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
    onClick: (() -> Unit)? = null
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
            // Numbers emphasized, unit letters not (see formatEtaParts for the part shape;
            // etaAnnotatedString for why they render as a single AnnotatedString).
            val etaParts = DisplayFormat.formatEtaParts(LocalContext.current, eta)
            Text(
                text = etaAnnotatedString(
                    etaParts,
                    emphasizedSpan = SpanStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color),
                    unemphasizedSpan = MaterialTheme.typography.bodyMedium.toSpanStyle().copy(color = color)
                ),
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
        tint = color
    )
}

/**
 * The "on the map right now" indicator: a filled map pin that replaces the [RealtimeIndicator] rss glyph
 * on an ETA pill whose live vehicle is currently drawn on the map (#1992). Both mean the arrival is
 * real-time; the pin additionally says "tapping this pill reframes the map to that vehicle", where the
 * rss glyph marks an AVL-tracked trip whose vehicle isn't drawn (so a tap does nothing). Shown only on
 * the interactive ETA-strip pills, since only those drive a map reframe.
 */
@Composable
internal fun OnMapIndicator(color: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_map_pin),
        contentDescription = null,
        modifier = modifier,
        tint = color
    )
}

private fun strikeThroughIf(canceled: Boolean): TextDecoration = if (canceled) TextDecoration.LineThrough else TextDecoration.None

/**
 * Renders [DisplayFormat.formatEtaParts] output as a single [AnnotatedString] — not separate Text
 * composables — so the text shaper kerns across the number/unit boundary instead of gluing together
 * independently-measured boxes; splitting them produced inconsistent gaps (e.g. "1hr" vs "4hr") since
 * cross-Text kerning isn't a thing (#1777).
 *
 * Numbers render with [emphasizedSpan], the trailing unit letters with [unemphasizedSpan]. Callers
 * pass both spans because the two ETA surfaces style them differently — the drawer's [EtaPill] (white,
 * its own sizes) vs. the arrival row's ETA (route color, Material type).
 */
internal fun etaAnnotatedString(
    etaParts: List<DisplayFormat.EtaPart>,
    emphasizedSpan: SpanStyle,
    unemphasizedSpan: SpanStyle
): AnnotatedString = buildAnnotatedString {
    etaParts.forEach { part ->
        withStyle(if (part.emphasized) emphasizedSpan else unemphasizedSpan) {
            append(part.text)
        }
    }
}

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
    override val directionId: Int?,
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
    override val hasPlottableVehicle: Boolean = false
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
    routeId: String = "route_$shortName",
    routeLongName: String? = null,
    directionId: Int? = null,
    // When true (and [predicted]), the arrival's live vehicle is drawn on the map for this trip, so its
    // pill shows the "on the map" pin instead of the rss glyph (#1992).
    hasPlottableVehicle: Boolean = false,
    // Trip-instance identity for the strip's LazyRow key (see EtaStrip). Callers that build a
    // multi-pill strip must pass a distinct id per pill — the default alone collides, and a duplicate
    // key throws. Not derived from etaMinutes on purpose: two pills legitimately share an ETA (a loop
    // route's two visits, a pre-dedup phantom), so identity must stay independent of it.
    tripId: String = "trip"
): ArrivalInfo {
    val predictedMs = etaMinutes * PREVIEW_MIN_MS
    val scheduledMs = (etaMinutes - scheduleDeviationMinutes) * PREVIEW_MIN_MS
    return ArrivalInfo(
        context = null,
        data = PreviewArrivalData(
            routeId = routeId,
            directionId = directionId,
            shortName = shortName,
            headsign = headsign,
            scheduledArrivalTime = ServerTime(scheduledMs),
            predictedArrivalTime = if (predicted) ServerTime(predictedMs) else null,
            predicted = predicted,
            status = status,
            routeLongName = routeLongName,
            tripId = tripId,
            hasPlottableVehicle = hasPlottableVehicle
        ),
        now = ServerTime(0L),
        includeArrivalDepartureInStatusLabel = false
    )
}

/** All-no-op [ArrivalRowCallbacks] for previews (nothing is interactive in a static preview) and for
 *  tests, which override just the slots they observe — the one place to grow when a slot is added. */
internal fun previewRowCallbacks(
    onShowRouteOnMap: (ArrivalInfo) -> Unit = {},
    onShowRouteSchedule: (String) -> Unit = {}
) = ArrivalRowCallbacks(
    onRouteFavorite = {},
    onShowVehiclesOnMap = {},
    onShowRouteOnMap = onShowRouteOnMap,
    onEtaClick = {},
    onShowTripStatus = {},
    onSetReminder = {},
    onShowRouteSchedule = onShowRouteSchedule,
    onReportArrivalProblem = {},
    onShowAlert = {}
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
                blockId = null
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
                            // Representative keeps the default "trip" id so the actions map (keyed
                            // there) resolves the badge; the rest get distinct ids for the strip key.
                            previewArrival("40", "Northgate", etaMinutes = 3),
                            previewArrival("40", "Northgate", etaMinutes = 11, scheduleDeviationMinutes = 2, tripId = "trip_2"),
                            previewArrival("40", "Northgate", etaMinutes = 24, predicted = false, tripId = "trip_3")
                        )
                    ),
                    actionsFor = { actions[it.tripId] },
                    isFavorite = true,
                    callbacks = callbacks
                )
                // A single-arrival route with a just-departed (recent-past) pill leading.
                RouteArrivalRow(
                    group = RouteRowGroup(
                        listOf(
                            previewArrival("8", "Rainier Beach", etaMinutes = -2),
                            previewArrival("8", "Rainier Beach", etaMinutes = 9, scheduleDeviationMinutes = -3, tripId = "trip_2")
                        )
                    ),
                    actionsFor = {
                        ArrivalActions(
                            tripId = it.tripId,
                            routeId = "route_8",
                            routeShortName = "8",
                            routeLongName = "MLK Jr Way",
                            routeColor = 0xFF9C27B0.toInt(),
                            scheduleUrl = null,
                            agencyName = null,
                            blockId = null
                        )
                    },
                    isFavorite = false,
                    callbacks = callbacks
                )
            }
        }
    }
}
