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
package org.onebusaway.android.ui.home.map

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.map.render.DataAgeUnit
import org.onebusaway.android.map.render.OccupancyBucket
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.text
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.WheelchairBoarding
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.rememberLiveServerTime
import org.onebusaway.android.ui.compose.components.DirectionBothWays
import org.onebusaway.android.ui.compose.components.DirectionHeadsign
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.MaterialSymbols
import org.onebusaway.android.ui.compose.components.MenuRow
import org.onebusaway.android.ui.compose.components.StatusPill
import org.onebusaway.android.ui.compose.components.StopNameText
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.compose.components.stopNamePlaceholders
import org.onebusaway.android.ui.compose.components.stopNameText
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.DisplayFormat

// The banner's route action icons (direction menu / cancel) share one size + tint so they read as
// one control group: a larger-than-default 36dp icon in a deliberately tightened 40dp touch box
// (below Material's 48dp default — a conscious trade-off for a compact header banner).
private val HEADER_ICON_SIZE = 36.dp
private val HEADER_ICON_BUTTON_SIZE = 40.dp

// An overflow (⋮) button's width. Its glyph is a narrow column of dots, so a full-width box only cost
// every row's text a third of a button and left the dots floating mid-gap (#2471).
private val OVERFLOW_BUTTON_WIDTH = 28.dp

// The favorite star and the rail it sits in, on the banner's leading edge. The rail insets the star
// from the card edge and takes its width from that inset plus the star — it deliberately has no
// trailing gutter of its own, because the banner content already pads its own leading edge and the
// two used to stack into a right-hand gap about twice the left inset (#2216). The inset also clears
// the level stripe on that edge (#2471).
private val FAVORITE_ICON_SIZE = 26.4.dp
private val FAVORITE_RAIL_LEADING_INSET = 14.8.dp
private val BANNER_MIN_HEIGHT = 64.dp

// The gap between the rail's star and whatever the banner body leads with — the stop name, or the
// route roundel. Shared so the two focus kinds can't drift apart; the rail contributes nothing to
// it (see FavoriteRail), so this is the whole gap.
private val BANNER_CONTENT_START_PADDING = 8.dp

// The route roundel's gap to the name/direction column beside it. Trailing-only: the roundel's
// leading gap is BANNER_CONTENT_START_PADDING's job, and when this was `horizontal` the two stacked
// into a 14dp leading gap against the stop's 8dp (#2216).
private val ROUTE_BADGE_TEXT_GAP = 10.dp

// The route roundel's square tile. Internal so FocusBannerTest can locate the tile's leading edge
// from its centered label — the roundel carries no semantics of its own to measure.
internal val ROUTE_BADGE_WIDTH = 64.dp

// Sized to sit on the stop's subtitle line without outgrowing its bodySmall text.
private val SUBTITLE_ICON_SIZE = 16.dp
private val SUBTITLE_ICON_OPTICAL_LIFT = 1.dp

// A level above the deepest one, shrunk to a line (#2471): tall enough to stay a comfortable tap target.
private val COMPACT_ROW_MIN_HEIGHT = 44.dp

// Every path row's leading glyph box (see PathLevelRow). Wider than the star so a three-character route
// badge fits on one line, and inset so its center lands on the star's: 12 + 32/2 = 14.8 + 26.4/2 = 28dp.
// The path reads as one column. The inset also leaves the level stripe as much air as its own width,
// so a badge sits beside it rather than against it.
private val COMPACT_GLYPH_WIDTH = 32.dp
private val COMPACT_GLYPH_START_INSET = 12.dp

// A level's icon (the stop's pin, the bus) in that glyph box.
private val PATH_ICON_SIZE = 22.dp

// Each level's map colour, as a bar on the card's leading edge (#2471): this wide, and this far short
// of the row's top and bottom so stacked levels read as separate marks rather than one striped bar —
// and so the first and last keep clear of the card's rounded corners, which would shave them.
private val LEVEL_STRIPE_WIDTH = 6.dp
private val LEVEL_STRIPE_INSET = 8.dp

// The bus row's occupancy heads: sized to its bodySmall line, the unfilled ones faint enough to read as
// empty seats but present enough to show the scale is out of three.
private val OCCUPANCY_PIP_SIZE = 12.dp
private const val EMPTY_PIP_ALPHA = 0.3f

// A route's badge as a path glyph: one line, short enough to sit in a compact row.
private val PATH_BADGE_MAX_HEIGHT = 26.dp
private val PATH_BADGE_MAX_FONT_SIZE = 16.sp

// The stop name shrinks to fit within this many lines before ellipsizing; see ShrinkToFitStopTitle.
private const val MAX_TITLE_LINES = 2

/**
 * The focused stop's overflow actions. They act on the stop's arrivals session — which HOME owns, not
 * the banner — so they arrive as one bundle. Null hides the menu: there is no session to act on until
 * a stop's arrivals are up.
 *
 * These outlived the standalone arrivals screen whose top bar used to hold them (#1898); the banner is
 * where a focused stop's actions live now. Its "show stop details" item did not: the banner itself
 * already shows the stop's name, code and direction, and the drawer below it the routes that serve it,
 * so the dialog only restated what was on screen.
 */
data class StopFocusMenu(
    val onReportStopProblem: () -> Unit,
    val onNightLight: () -> Unit,
    val onCreateShortcut: () -> Unit,
    val onShowArrivals: () -> Unit,
    /**
     * Plan a trip to this stop — the map long press's "navigate here" offer, asked of the stop the rider
     * already has focused instead of a point they have to press for (#2272). Null while the stop's own
     * location is still unknown (a stop revealed by id alone has none until its arrivals land), since
     * there is nothing to route to yet; that is the same beat the banner's star waits on, and it has
     * passed by the time the menu itself is up in every entry point but that one.
     */
    val onNavigateHere: (() -> Unit)?,
    /**
     * Give the stop a name of the rider's own (#2414). Null on the same condition as [onNavigateHere]:
     * naming a stop records it in the stops table, which needs its location.
     */
    val onRename: (() -> Unit)? = null
)

/**
 * Presentation state for the map's shared focus banner: the whole focus path, one row per level (#2471).
 *
 * The root is a stop or a standalone route. Below a stop can sit the route drilled into over it, and
 * below either route the bus drilled into on it — stop › route › bus, or route › bus. The deepest level
 * gets the full row; the levels above it shrink to one line each, so the path stays readable without the banner growing a full row per level.
 *
 * [isFavorite]/[favoriteEnabled] are the root's star; a route over a stop carries none.
 */
sealed interface FocusBannerState {
    val isFavorite: Boolean
    val favoriteEnabled: Boolean

    /** The trip drilled into at the end of the path, or null when the path ends above it. */
    val trip: FocusBannerTrip?

    data class Stop(
        val title: String,
        /** [title] is the rider's own name for the stop rather than its official one (#2414). */
        val isCustomName: Boolean = false,
        val direction: String?,
        val stopCode: String?,
        override val isFavorite: Boolean,
        override val favoriteEnabled: Boolean,
        val hasAlerts: Boolean,
        val wheelchairBoarding: WheelchairBoarding = WheelchairBoarding.UNKNOWN,
        /** The route drilled into over this stop, or null for the plain stop. */
        val route: FocusBannerRoute? = null,
        override val trip: FocusBannerTrip? = null
    ) : FocusBannerState

    data class Route(
        val header: RouteHeader,
        override val isFavorite: Boolean,
        override val trip: FocusBannerTrip? = null,
        /** The route's line colour as the map draws it, which marks its level; null draws no mark. */
        @param:ColorInt val lineColor: Int? = null
    ) : FocusBannerState {
        override val favoriteEnabled: Boolean get() = header.routeId != null
    }
}

/**
 * A route drilled into over a focused stop. It came back to the banner after #2207 took it out as a
 * restatement of the drawer's outlined row: as a level of the path it carries what the drawer doesn't
 * — the line's name and agency, its stop list and schedule — and it is what the bus below it hangs off.
 */
data class FocusBannerRoute(
    val header: RouteHeader,
    /** The route's line colour as the map draws it around this stop; null draws no mark. */
    @param:ColorInt val lineColor: Int?
)

/**
 * Floating information and actions for the current focus path — a stop or standalone route, and what has
 * been drilled into below it (see [FocusBannerState]). It reports its measured height so map framing
 * stays clear of the banner.
 *
 * Every row behaves alike: tapping it brings its level into view on the map ([onRecenterStop],
 * [onFrameRoute], [onFrameBus]) without changing the focus, and its own overflow (⋮) holds the level's
 * context actions and ways to drill in.
 *
 * There is one close, on the top row, and it clears the whole focus ([onClose]) however deep the path
 * goes — leaving is one tap, not one per level. Dropping a single level is Back's job, and a tap on
 * empty map's.
 */
@Composable
fun FocusBanner(
    state: FocusBannerState,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowAlerts: () -> Unit,
    onRecenterStop: () -> Unit,
    onSelectDirection: (Int?) -> Unit,
    onFrameRoute: () -> Unit,
    onShowSchedule: (String) -> Unit,
    onShowStopList: (String) -> Unit,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stopMenu: StopFocusMenu? = null,
    onFrameBus: () -> Unit = {},
    onOpenBus: () -> Unit = {}
) {
    val routeOverStop = (state as? FocusBannerState.Stop)?.route
    val trip = state.trip
    Surface(
        modifier = modifier.onSizeChanged { onHeight(it.height) },
        // A floating rounded card (below the top chrome), rather than a full-width edge-to-edge bar.
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        // Each level carries the colour the map draws it in — the focused stop's orange, the route's line,
        // the bus's band — so a row and the thing it names on the map read as one object.
        val stopColor = colorResource(R.color.map_stop_focus)
        Column(Modifier.fillMaxWidth()) {
            when (state) {
                is FocusBannerState.Stop -> if (routeOverStop != null) {
                    CompactStopRow(state, onRecenter = onRecenterStop, stripe = stopColor, stopMenu = stopMenu, onClose = onClose)
                } else {
                    ExpandedLevelRow(state.isFavorite, state.favoriteEnabled, onToggleFavorite, stripe = stopColor) {
                        StopFocusBanner(
                            state = state,
                            onShowAlerts = onShowAlerts,
                            onRecenter = onRecenterStop,
                            stopMenu = stopMenu,
                            onClose = onClose
                        )
                    }
                }
                is FocusBannerState.Route -> if (trip != null) {
                    CompactRouteRow(
                        state.header,
                        onFrameRoute,
                        onShowSchedule,
                        onShowStopList,
                        stripe = state.lineColor?.let(::Color),
                        onClose = onClose
                    )
                } else {
                    ExpandedLevelRow(
                        state.isFavorite,
                        state.favoriteEnabled,
                        onToggleFavorite,
                        stripe = state.lineColor?.let(::Color)
                    ) {
                        RouteFocusBanner(
                            header = state.header,
                            onSelectDirection = onSelectDirection,
                            onFrameRoute = onFrameRoute,
                            onShowSchedule = onShowSchedule,
                            onShowStopList = onShowStopList,
                            onClose = onClose
                        )
                    }
                }
            }
            if (routeOverStop != null) {
                if (trip != null) {
                    CompactRouteRow(
                        routeOverStop.header,
                        onFrameRoute,
                        onShowSchedule,
                        onShowStopList,
                        stripe = routeOverStop.lineColor?.let(::Color)
                    )
                } else {
                    RouteOverStopRow(routeOverStop, onFrameRoute, onShowSchedule, onShowStopList)
                }
            }
            trip?.let { BusRow(it, onFrame = onFrameBus, onOpenDetails = onOpenBus) }
        }
    }
}

/**
 * A level's map colour as a bar on the card's leading edge, the arrivals badges' stripe turned on its
 * side; null draws nothing. Drawn half outside the row so only its trailing corners round — the card
 * clips the rest, and the bar reads as set into the edge rather than floating beside it.
 */
private fun Modifier.levelStripe(color: Color?): Modifier = if (color == null) {
    this
} else {
    drawBehind {
        val width = LEVEL_STRIPE_WIDTH.toPx()
        val inset = LEVEL_STRIPE_INSET.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-width, inset),
            size = Size(width * 2, (size.height - inset * 2).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(width / 2)
        )
    }
}

/**
 * The deepest level's full row: the level's star in the leading rail, then its content. A Row of
 * intrinsic-min height so the rail centers the star on however tall the content lays out.
 */
@Composable
private fun ExpandedLevelRow(
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onToggleFavorite: () -> Unit,
    stripe: Color?,
    content: @Composable () -> Unit
) {
    Row(Modifier.levelStripe(stripe).fillMaxWidth().height(IntrinsicSize.Min)) {
        FavoriteRail(isFavorite = isFavorite, favoriteEnabled = favoriteEnabled, onToggleFavorite = onToggleFavorite)
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * One row of the path below the expanded root: a glyph naming the level, centered on the same axis as
 * the expanded rows' star so the path reads as one column, then the level's content, its overflow
 * ([trailing]) and the card's close column. The glyph and content are the level's tap target, and
 * [onLongClick], when given, opens its overflow.
 *
 * [minHeight] and [verticalPadding] are the only ways the rows differ: a level shrunk to a line is
 * [COMPACT_ROW_MIN_HEIGHT] tall, a deepest level breathes around its two lines.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PathLevelRow(
    onClick: () -> Unit,
    onClickLabel: String,
    stripe: Color?,
    glyph: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    minHeight: Dp = COMPACT_ROW_MIN_HEIGHT,
    verticalPadding: Dp = 0.dp,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .levelStripe(stripe)
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The tap target is the glyph and content only, so a trailing control isn't nested inside it.
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = COMPACT_ROW_MIN_HEIGHT)
                .combinedClickable(
                    onClickLabel = onClickLabel,
                    role = Role.Button,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.padding(start = COMPACT_GLYPH_START_INSET).width(COMPACT_GLYPH_WIDTH),
                contentAlignment = Alignment.Center
            ) { glyph() }
            Spacer(Modifier.width(BANNER_CONTENT_START_PADDING))
            Box(Modifier.weight(1f)) { content() }
        }
        trailing?.invoke()
        CloseColumn(onClose)
    }
}

/**
 * The card's trailing column: the close on the path's top row ([onClose]), and the same width held empty
 * on every row below it, so each level's overflow stays in one column down the card.
 */
@Composable
private fun CloseColumn(onClose: (() -> Unit)?) {
    if (onClose != null) {
        HeaderIconButton(
            painter = painterResource(R.drawable.ic_navigation_close),
            contentDescription = stringResource(android.R.string.cancel),
            onClick = onClose
        )
    } else {
        Spacer(Modifier.width(HEADER_ICON_BUTTON_SIZE))
    }
}

/** A level's icon in the path's glyph column, tinted as the banner's other icons are. */
@Composable
private fun PathIconGlyph(@DrawableRes icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = colorResource(R.color.navdrawer_icon_tint),
        modifier = Modifier.size(PATH_ICON_SIZE)
    )
}

/** Secondary text on a path row: one line, in the banner's muted colour. */
@Composable
private fun SecondaryLine(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * The stop shrunk to a line above a route. It keeps its overflow: the stop's actions (plan a trip here,
 * rename, night light, …) belong to the stop, which is still focused however deep the path goes.
 */
@Composable
private fun CompactStopRow(
    state: FocusBannerState.Stop,
    onRecenter: () -> Unit,
    stripe: Color,
    stopMenu: StopFocusMenu?,
    onClose: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    PathLevelRow(
        onClick = onRecenter,
        onClickLabel = stringResource(R.string.stop_info_recenter),
        stripe = stripe,
        onClose = onClose,
        trailing = stopMenu?.let { menu ->
            { StopMenuAction(menu, expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) }
        },
        glyph = { PathIconGlyph(R.drawable.ic_drawer_maps_place) }
    ) {
        StopNameText(
            name = state.title,
            isCustomName = state.isCustomName,
            // The headsign's size on the row below, so the path's lines read as one list — and the
            // size at which a typical "street & cross street - bay" name fits beside the overflow.
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A route shrunk to a line above its bus: tap frames it, and it keeps its own overflow. */
@Composable
private fun CompactRouteRow(
    header: RouteHeader,
    onFrameRoute: () -> Unit,
    onShowSchedule: (String) -> Unit,
    onShowStopList: (String) -> Unit,
    stripe: Color?,
    onClose: (() -> Unit)? = null
) {
    val unnamed = stringResource(R.string.route_direction_unnamed)
    var menuExpanded by remember { mutableStateOf(false) }
    PathLevelRow(
        onClick = onFrameRoute,
        onClickLabel = stringResource(R.string.route_header_frame_route),
        stripe = stripe,
        onClose = onClose,
        trailing = { RouteMenuAction(header, onShowSchedule, onShowStopList, menuExpanded, { menuExpanded = it }) },
        glyph = { PathRouteBadge(header) }
    ) {
        val direction = header.currentDirection?.labelOr(unnamed)
        if (direction != null) {
            DirectionHeadsign(direction)
        } else {
            Text(header.longName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The route's badge sized as a path glyph — one line in [COMPACT_GLYPH_WIDTH], shrinking to fit. */
@Composable
private fun PathRouteBadge(header: RouteHeader) {
    val (badgeContainer, badgeContent) = rememberRouteBadgeColors(header.routeColor)
    LineBadge(
        text = header.shortName,
        width = COMPACT_GLYPH_WIDTH,
        maxHeight = PATH_BADGE_MAX_HEIGHT,
        maxFontSize = PATH_BADGE_MAX_FONT_SIZE,
        maxLines = 1,
        color = badgeContent,
        containerColor = badgeContainer
    )
}

/**
 * The route drilled into over a stop, as the deepest level: the badge in the path's glyph column, then
 * where it is headed — the stop has already fixed the direction, so the headsign is the route's name
 * here — over the line's long name and agency, then its overflow. Tap frames the route; long press opens its overflow.
 *
 * Deliberately not the standalone route's header: that one leads with the long name under a 64dp tile,
 * which as one level of three crowded the headsign — the line a rider at a stop reads — to an ellipsis.
 * It carries no star: the drawer's row for this route already stars it, one tap away below.
 */
@Composable
private fun RouteOverStopRow(
    route: FocusBannerRoute,
    onFrameRoute: () -> Unit,
    onShowSchedule: (String) -> Unit,
    onShowStopList: (String) -> Unit
) {
    val header = route.header
    var menuExpanded by remember { mutableStateOf(false) }
    PathLevelRow(
        onClick = onFrameRoute,
        onClickLabel = stringResource(R.string.route_header_frame_route),
        stripe = route.lineColor?.let(::Color),
        glyph = { PathRouteBadge(header) },
        trailing = { RouteMenuAction(header, onShowSchedule, onShowStopList, menuExpanded, { menuExpanded = it }) },
        minHeight = BANNER_MIN_HEIGHT,
        verticalPadding = 10.dp,
        onLongClick = { menuExpanded = true },
        onLongClickLabel = stringResource(R.string.focus_banner_route_options)
    ) {
        Column {
            val unnamed = stringResource(R.string.route_direction_unnamed)
            header.currentDirection?.let { DirectionHeadsign(it.labelOr(unnamed)) }
            val line = listOf(header.longName, header.agency).filter { it.isNotEmpty() }.joinToString(" · ")
            if (line.isNotEmpty()) SecondaryLine(line)
        }
    }
}

/**
 * The bus at the end of the path. It leads with what the rider asked the map for, whether the bus is
 * early, on time or late and how fresh that is, then names the vehicle and how full it is. Tapping the
 * row frames the bus on the map, as every level's tap does; its overflow drills into the trip details,
 * the visible twin of a second tap on the marker.
 *
 * A trip the map has no bus for yet still gets its row, saying so, so the path shown is the path held.
 */
@Composable
private fun BusRow(trip: FocusBannerTrip, onFrame: () -> Unit, onOpenDetails: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    PathLevelRow(
        onClick = onFrame,
        onClickLabel = stringResource(R.string.stop_info_option_showonmap),
        stripe = trip.bandColor?.let(::Color),
        glyph = { PathIconGlyph(R.drawable.ic_bus) },
        trailing = {
            OverflowMenuAction(
                contentDescription = stringResource(R.string.focus_banner_trip_options),
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it }
            ) {
                MenuRow(R.string.bus_options_menu_show_trip_details) {
                    menuExpanded = false
                    onOpenDetails()
                }
            }
        },
        // As tall as its two lines need and no taller: a trip with no bus is one line, and a full-height
        // row around it read as a gap.
        verticalPadding = 10.dp
    ) {
        val bus = trip.bus
        if (bus != null) {
            BusDetails(bus)
        } else {
            val context = LocalContext.current
            SecondaryLine(
                text = trip.scheduledAtStop
                    ?.let {
                        stringResource(R.string.focus_banner_timed_trip_not_on_map, DisplayFormat.formatTime(context, it.epochMs))
                    }
                    ?: stringResource(R.string.focus_banner_trip_not_on_map),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** The bus row's two lines: its status and how fresh that is, then its number and how full it is. */
@Composable
private fun BusDetails(bus: FocusBannerBus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // A flow, so on a narrow banner the freshness drops below the pill whole rather than being cut to
        // "Updated 1…" beside it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = ArrivalInfoUtils.computeStatusLabel(
                    LocalContext.current.resources,
                    bus.deviationStatus,
                    bus.deviationMinutes
                ),
                color = colorResource(bus.deviationStatus.fillColorRes),
                // No live glyph: on the ETA pills it means "status but no location", and this bus has
                // both. The freshness text beside the pill already says the status is live.
                isRealtime = false
            )
            bus.lastReport?.let { BusDataAge(it, bus.polledAt) }
        }
        if (bus.vehicleId != null || bus.occupancy != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                bus.vehicleId?.let {
                    SecondaryLine(stringResource(R.string.trip_details_vehicle, it), Modifier.weight(1f, fill = false))
                }
                bus.occupancy?.let { OccupancyPips(it) }
            }
        }
    }
}

/**
 * "Updated 1 min ago": [lastReport]'s age against the poll's server clock ([polledAt]) carried forward
 * by the live ticker, so it counts up between polls and resets on a fresh one (#1612). Its own scope, so
 * the ticker recomposes this line and not the row around it.
 */
@Composable
private fun BusDataAge(lastReport: ServerTime, polledAt: ServerTime) {
    val age = DataAgeUnit.of(rememberLiveServerTime(polledAt) - lastReport) ?: return
    SecondaryLine(age.text(LocalContext.current.resources))
}

/**
 * How full the bus is, as its marker's tab draws it: three heads, [OccupancyBucket.pips] of them inked
 * and the rest faint, so the row's length states the scale. The same glyph as the tab
 * ([VehicleBitmaps]), so the banner and the marker say it in one vocabulary; a screen reader hears the
 * bucket's words.
 */
@Composable
private fun OccupancyPips(occupancy: OccupancyBucket) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(occupancy.labelRes)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(VehicleBitmaps.MAX_PIPS) { i ->
            Icon(
                painter = painterResource(R.drawable.ic_occupancy),
                contentDescription = null,
                tint = if (i < occupancy.pips) ink else ink.copy(alpha = EMPTY_PIP_ALPHA),
                modifier = Modifier.size(OCCUPANCY_PIP_SIZE)
            )
        }
    }
}

/**
 * A level's overflow (⋮): its context actions and the ways to drill into it, in a menu anchored on the
 * button. Every row of the path carries one, so what a level offers is always found the same way.
 * [contentDescription] names the level ("Route options") and is the open menu's heading too.
 */
@Composable
private fun OverflowMenuAction(
    contentDescription: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: @Composable ColumnScope.() -> Unit
) {
    Box {
        HeaderIconButton(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = contentDescription,
            width = OVERFLOW_BUTTON_WIDTH,
            onClick = { onExpandedChange(true) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            MenuHeading(contentDescription)
            items()
        }
    }
}

/**
 * What a level's menu is the menu of. Three rows each carry the same ⋮, so the open menu names its own
 * level rather than leaving the rider to trace which dots it dropped from.
 */
@Composable
private fun MenuHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
    )
    HorizontalDivider(Modifier.padding(bottom = 4.dp))
}

/**
 * A route's overflow: its stop list and, when the agency publishes one, its schedule. Nothing is drawn
 * when the route offers neither.
 */
@Composable
private fun RouteMenuAction(
    header: RouteHeader,
    onShowSchedule: (String) -> Unit,
    onShowStopList: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val routeId = header.routeId
    val scheduleUrl = header.scheduleUrl
    if (routeId == null && scheduleUrl == null) return
    OverflowMenuAction(stringResource(R.string.focus_banner_route_options), expanded, onExpandedChange) {
        routeId?.let {
            MenuRow(R.string.bus_options_menu_show_stop_list, MaterialSymbols.TripStatus) {
                onExpandedChange(false)
                onShowStopList(it)
            }
        }
        scheduleUrl?.let {
            MenuRow(R.string.bus_options_menu_show_route_schedule, MaterialSymbols.Schedule) {
                onExpandedChange(false)
                onShowSchedule(it)
            }
        }
    }
}

/**
 * The banner's leading rail: just the favorite star, vertically centered. It carried a focus-type
 * glyph (stop flag / route icon) above the star and a divider beside it until #2216 — orientation
 * the banner's own content already gives, since a stop shows a stop name and a route shows a route
 * roundel.
 *
 * The rail wraps the star rather than centering it in a fixed-width column: it pads only its
 * leading edge, so the gap on the star's right is exactly the banner content's own start padding.
 * Centering in a fixed width added a trailing gutter *on top of* that padding, which is why the
 * right-hand gap used to read as roughly double the left inset. Its width is still identical for
 * both focus kinds (the star is a fixed size), so stop and route content start at the same x.
 *
 * It also sets the banner's minimum height so a still-loading stop doesn't collapse to a sliver.
 */
@Composable
private fun FavoriteRail(
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onToggleFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .heightIn(min = BANNER_MIN_HEIGHT)
            .padding(start = FAVORITE_RAIL_LEADING_INSET),
        contentAlignment = Alignment.Center
    ) {
        BannerFavoriteAction(
            isFavorite = isFavorite,
            enabled = favoriteEnabled,
            onClick = onToggleFavorite
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopFocusBanner(
    state: FocusBannerState.Stop,
    onShowAlerts: () -> Unit,
    onRecenter: () -> Unit,
    stopMenu: StopFocusMenu?,
    onClose: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .padding(
                start = BANNER_CONTENT_START_PADDING,
                top = 4.dp,
                end = 4.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val subtitle = DisplayFormat.stopSubtitleText(LocalContext.current, state.stopCode, state.direction)
        Column(
            modifier = Modifier.weight(1f).combinedClickable(
                onClickLabel = stringResource(R.string.stop_info_recenter),
                role = Role.Button,
                onLongClickLabel = if (stopMenu != null) stringResource(R.string.focus_banner_stop_options) else null,
                onLongClick = if (stopMenu != null) ({ menuExpanded = true }) else null,
                onClick = onRecenter
            )
        ) {
            ShrinkToFitStopTitle(state.title, isCustomName = state.isCustomName)
            val wheelchairGlyph = wheelchairGlyph(state.wheelchairBoarding)
            if (subtitle != null || wheelchairGlyph != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (wheelchairGlyph != null) {
                        WheelchairBoardingIndicator(wheelchairGlyph)
                    }
                }
            }
        }
        if (state.hasAlerts) {
            BannerAlertAction(onClick = onShowAlerts)
        }
        if (stopMenu != null) {
            StopMenuAction(stopMenu, expanded = menuExpanded, onExpandedChange = { menuExpanded = it })
        }
        CloseColumn(onClose)
    }
}

/**
 * The stop name at [MaterialTheme.typography.titleLarge], shrinking down to [MaterialTheme.typography.titleMedium]'s
 * size (in 1sp steps) so the name fits within [MAX_TITLE_LINES] lines before it has to ellipsize, and capped at
 * [MAX_TITLE_LINES] lines.
 *
 * Measures via [TextMeasurer] rather than `BoxWithConstraints`: this sits inside [FocusBanner]'s
 * `Modifier.height(IntrinsicSize.Min)` row, and `BoxWithConstraints` is a `SubcomposeLayout`, which throws when
 * asked for intrinsic measurements. `fillMaxWidth` + `onSizeChanged` reports the available width without one.
 *
 * [LineBadge] hand-rolls the same shrink-to-fit idea, but against a fixed known width so it can measure
 * synchronously during composition; this variant fits an unknown fill-available width, hence the `onSizeChanged`
 * round-trip below. Kept file-private for its single call site — if a second fill-width shrink consumer appears,
 * that's the trigger to promote a shared `ShrinkToFitText` into the components package.
 *
 * `maxWidthPx` is reported through `onSizeChanged`, so it lags the actual available width by a layout→recompose
 * round-trip: when the width shrinks (e.g. the alert icon arrives asynchronously after the arrivals load and
 * narrows this column) the font resolved for the previous, wider width is momentarily a touch too large for the new
 * width. The two-line budget absorbs that — the transient is at worst a brief extra line or ellipsis that the next
 * recomposition resolves as the font steps down — rather than the hard one-line edge-truncation an earlier revision
 * showed.
 */
@Composable
private fun ShrinkToFitStopTitle(title: String, isCustomName: Boolean) {
    val fullStyle = MaterialTheme.typography.titleLarge
    val floorSize = MaterialTheme.typography.titleMedium.fontSize
    val textMeasurer = rememberTextMeasurer()
    var maxWidthPx by remember { mutableIntStateOf(0) }
    // The custom-name glyph (#2414) is measured as the placeholder it renders as, so the fit counts its width.
    val text = remember(title, isCustomName) { stopNameText(title, isCustomName) }
    val placeholders = stopNamePlaceholders(isCustomName)

    fun fitsWithinLineCap(fontSize: TextUnit) = maxWidthPx <= 0 ||
        textMeasurer.measure(
            text = text,
            style = fullStyle.copy(fontSize = fontSize),
            placeholders = placeholders,
            constraints = Constraints(maxWidth = maxWidthPx)
        ).lineCount <= MAX_TITLE_LINES

    val resolvedSize = remember(text, maxWidthPx) {
        var candidate = fullStyle.fontSize
        while (candidate > floorSize && !fitsWithinLineCap(candidate)) {
            candidate = (candidate.value - 1).sp
        }
        candidate
    }
    StopNameText(
        name = title,
        isCustomName = isCustomName,
        style = fullStyle.copy(fontSize = resolvedSize),
        maxLines = MAX_TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { maxWidthPx = it.width }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteFocusBanner(
    header: RouteHeader,
    onSelectDirection: (Int?) -> Unit,
    onFrameRoute: () -> Unit,
    onShowSchedule: (String) -> Unit,
    onShowStopList: (String) -> Unit,
    onClose: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // The loading spinner needs more breathing room from the card edges than the laid-out header
    // does, but the leading gap is the star's and stays fixed either way.
    val edgePadding = if (header.loading) 8.dp else 4.dp
    Row(
        Modifier.fillMaxWidth().padding(
            start = BANNER_CONTENT_START_PADDING,
            top = edgePadding,
            end = edgePadding,
            bottom = edgePadding
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (header.loading) {
            CircularProgressIndicator(Modifier.size(48.dp))
            Spacer(Modifier.weight(1f))
        } else {
            // The current direction's headsign (blank falls back to a generic label); null when the
            // route is shown whole (no direction selected).
            val unnamed = stringResource(R.string.route_direction_unnamed)
            val directionLabel = header.currentDirection?.labelOr(unnamed)
            // A route with a single direction has nothing to choose between — no menu, and no line
            // stating a choice the user can't make.
            val hasDirectionMenu = header.directions.size >= 2
            Row(
                Modifier
                    .weight(1f)
                    // Tap frames the route; long press opens its overflow — the same gesture pairing
                    // the arrivals drawer's route rows use.
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.route_header_frame_route),
                        role = Role.Button,
                        onLongClickLabel = stringResource(R.string.focus_banner_route_options),
                        onLongClick = { menuExpanded = true },
                        onClick = onFrameRoute
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The badge + name column is one tap target that reframes the map to the route's extent.
                // A square route roundel: the short name shrinks to fit inside the tile, on the same
                // HCT-normalized GTFS-color chip as the arrival rows.
                val (badgeContainer, badgeContent) = rememberRouteBadgeColors(header.routeColor)
                LineBadge(
                    text = header.shortName,
                    maxFontSize = 45.sp,
                    width = ROUTE_BADGE_WIDTH,
                    square = true,
                    color = badgeContent,
                    containerColor = badgeContainer,
                    modifier = Modifier.padding(end = ROUTE_BADGE_TEXT_GAP)
                )
                Column(Modifier.weight(1f)) {
                    if (header.longName.isNotEmpty()) {
                        Text(
                            text = header.longName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // The direction line states the menu's current value; the chevron beside it is the
                    // control that changes it. Deliberately not a trigger itself — it sits inside the
                    // banner body, whose tap reframes the route and whose long press opens the
                    // route menu, and a nested clickable would consume both on this line.
                    if (directionLabel != null) {
                        // The same arrow-glyph + tightened-monospace treatment as an arrivals row, so
                        // the headsign reads identically on both surfaces (#1823).
                        DirectionHeadsign(directionLabel)
                    } else if (hasDirectionMenu) {
                        // Set like a headsign, but with the two-way arrow: the whole route runs both
                        // ways, so the one-way "toward" glyph would misdescribe it.
                        DirectionBothWays(stringResource(R.string.route_header_all_directions))
                    }
                    if (header.agency.isNotEmpty()) {
                        // One type-scale step below the direction line so it recedes as secondary info.
                        Text(text = header.agency, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (hasDirectionMenu) {
                DirectionMenuAction(
                    directions = header.directions,
                    currentDirectionId = header.currentDirectionId,
                    onSelectDirection = onSelectDirection
                )
            }
            RouteMenuAction(header, onShowSchedule, onShowStopList, menuExpanded, { menuExpanded = it })
        }
        CloseColumn(onClose)
    }
}

/**
 * The rail lays out only the visible star. Foundation's clickable node expands touch hit-testing
 * to the platform minimum target without making that invisible target consume spacing in the rail.
 */
@Composable
private fun BannerFavoriteAction(
    isFavorite: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Icon(
        painter = painterResource(if (isFavorite) R.drawable.star else R.drawable.star_outline),
        contentDescription = stringResource(
            if (isFavorite) {
                R.string.bus_options_menu_remove_star
            } else {
                R.string.bus_options_menu_add_star
            }
        ),
        tint = colorResource(R.color.navdrawer_icon_tint),
        modifier = Modifier
            .size(FAVORITE_ICON_SIZE)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

/**
 * The icon and its description for a stop's GTFS wheelchair boarding (#1029), or null when there is
 * nothing to show: [WheelchairBoarding.UNKNOWN] draws no glyph, since most feeds leave the field unset
 * and a stream of "unknown" glyphs would be noise. Resolving it once — rather than at both the layout
 * guard and the glyph — keeps the "what's worth showing" rule in a single exhaustive `when`.
 */
private fun wheelchairGlyph(boarding: WheelchairBoarding): Pair<Int, Int>? = when (boarding) {
    WheelchairBoarding.ACCESSIBLE ->
        R.drawable.ic_wheelchair_accessible to R.string.stop_wheelchair_accessible
    WheelchairBoarding.NOT_ACCESSIBLE ->
        R.drawable.not_accessible_24 to R.string.stop_wheelchair_not_accessible
    WheelchairBoarding.UNKNOWN -> null
}

/**
 * A non-interactive [wheelchairGlyph] on the stop's subtitle line, sized and tinted to read as part of
 * the subtitle rather than as an action.
 *
 * Nudged up by [SUBTITLE_ICON_OPTICAL_LIFT]: the row centers the icon against the subtitle's *layout*
 * box, which reserves descender space the stop code and direction never use, so a box-centered glyph
 * reads slightly low against the text beside it.
 */
@Composable
private fun WheelchairBoardingIndicator(glyph: Pair<Int, Int>) {
    val (iconRes, descriptionRes) = glyph
    Icon(
        painter = painterResource(iconRes),
        contentDescription = stringResource(descriptionRes),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .offset(y = -SUBTITLE_ICON_OPTICAL_LIFT)
            .size(SUBTITLE_ICON_SIZE)
    )
}

@Composable
private fun BannerAlertAction(onClick: () -> Unit) {
    Icon(
        painter = painterResource(R.drawable.baseline_warning_24),
        contentDescription = stringResource(R.string.stop_info_show_alerts),
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(12.dp)
            .size(24.dp)
    )
}

/**
 * A route-header action icon button in the shared header style: the [HEADER_ICON_SIZE] icon tinted with
 * the nav-drawer icon color, in the tightened [HEADER_ICON_BUTTON_SIZE] box — or a narrower [width], for
 * the overflow (⋮), whose glyph is a column of dots. Used by the direction menu, overflows and close.
 */
@Composable
private fun HeaderIconButton(
    painter: Painter,
    contentDescription: String,
    width: Dp = HEADER_ICON_BUTTON_SIZE,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(width = width, height = HEADER_ICON_BUTTON_SIZE)) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = colorResource(R.color.navdrawer_icon_tint),
            // Required, so a box narrower than the icon centers it at full size rather than shrinking it.
            modifier = Modifier.requiredSize(HEADER_ICON_SIZE)
        )
    }
}

/**
 * The focused stop's overflow: the stop actions that are neither frequent enough for their own icon nor
 * expressible on the map: a trip planned to the stop, the mapless board, a name of the rider's own, a
 * home-screen shortcut, a problem report against the stop, and the night-light flasher a rider holds up
 * to a driver.
 *
 * "Navigate here" leads because it is the one item a rider looking at a stop is likely to want (#2272);
 * the rest answer situations, not intentions.
 */
@Composable
private fun StopMenuAction(menu: StopFocusMenu, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    OverflowMenuAction(stringResource(R.string.focus_banner_stop_options), expanded, onExpandedChange) {
        // The same words the map long press's bubble offers, because it is the same offer.
        menu.onNavigateHere?.let { navigate ->
            MenuRow(R.string.map_navigate_here) {
                onExpandedChange(false)
                navigate()
            }
        }
        MenuRow(R.string.view_arrivals_only) {
            onExpandedChange(false)
            menu.onShowArrivals()
        }
        menu.onRename?.let { rename ->
            MenuRow(R.string.stop_info_option_editname) {
                onExpandedChange(false)
                rename()
            }
        }
        MenuRow(R.string.my_context_create_shortcut) {
            onExpandedChange(false)
            menu.onCreateShortcut()
        }
        MenuRow(R.string.stop_info_option_report_problem) {
            onExpandedChange(false)
            menu.onReportStopProblem()
        }
        MenuRow(R.string.stop_info_option_night_light) {
            onExpandedChange(false)
            menu.onNightLight()
        }
    }
}

/**
 * The direction picker: a caret button opening a menu whose first entry is "All directions" (the whole
 * route), followed by each of the route's named directions. [currentDirectionId] is the shown direction,
 * null when the route is shown whole.
 *
 * This replaced a swap button that *cycled* directions (#2033). Cycling could only ever land on a
 * direction, so a route entered whole — or switched once — had no way back to both directions; the menu
 * makes the whole route a first-class, always-reachable choice rather than only an entry state.
 */
@Composable
private fun DirectionMenuAction(
    directions: List<RouteMapDirection>,
    currentDirectionId: Int?,
    onSelectDirection: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(
            painter = rememberVectorPainter(AppIcons.KeyboardArrowDown),
            contentDescription = stringResource(R.string.route_header_select_direction),
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val unnamed = stringResource(R.string.route_direction_unnamed)
            DirectionMenuItem(
                label = stringResource(R.string.route_header_all_directions),
                selected = currentDirectionId == null
            ) {
                expanded = false
                onSelectDirection(null)
            }
            directions.forEach { direction ->
                DirectionMenuItem(
                    label = direction.labelOr(unnamed),
                    selected = direction.directionId == currentDirectionId
                ) {
                    expanded = false
                    onSelectDirection(direction.directionId)
                }
            }
        }
    }
}

/**
 * One direction choice. The check glyph is decorative — the row carries its state as `selected`
 * semantics, so a screen reader announces the current direction instead of relying on the icon.
 */
@Composable
private fun DirectionMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.semantics { this.selected = selected },
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (selected) {
                Icon(imageVector = AppIcons.Check, contentDescription = null)
            }
        }
    )
}

/** The direction's headsign, or [unnamed] when the stop group carried no display name. */
private fun RouteMapDirection.labelOr(unnamed: String): String = label.ifBlank { unnamed }

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun FocusBannerPreview() {
    ObaTheme {
        Column {
            FocusBanner(
                state = FocusBannerState.Stop(
                    title = "Pine St & 3rd Ave",
                    direction = "N",
                    stopCode = "12345",
                    isFavorite = true,
                    favoriteEnabled = true,
                    hasAlerts = true,
                    wheelchairBoarding = WheelchairBoarding.ACCESSIBLE
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onShowSchedule = {},
                onShowStopList = {},
                onHeight = {}
            )
            Spacer(Modifier.size(12.dp))
            FocusBanner(
                state = FocusBannerState.Route(
                    RouteHeader(
                        loading = false,
                        shortName = "40",
                        longName = "Downtown Seattle - Northgate",
                        agency = "King County Metro",
                        scheduleUrl = "https://example.org/route/40/schedule",
                        directions = listOf(
                            RouteMapDirection(0, "to Downtown Seattle"),
                            RouteMapDirection(1, "to Northgate")
                        ),
                        currentDirectionId = 0
                    ),
                    isFavorite = false
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onShowSchedule = {},
                onShowStopList = {},
                onHeight = {}
            )
            Spacer(Modifier.size(12.dp))
            // The same route shown whole — the direction menu's default, which the subtitle states.
            FocusBanner(
                state = FocusBannerState.Route(
                    RouteHeader(
                        loading = false,
                        shortName = "40",
                        longName = "Downtown Seattle - Northgate",
                        agency = "King County Metro",
                        directions = listOf(
                            RouteMapDirection(0, "to Downtown Seattle"),
                            RouteMapDirection(1, "to Northgate")
                        ),
                        currentDirectionId = null
                    ),
                    isFavorite = false
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onShowSchedule = {},
                onShowStopList = {},
                onHeight = {}
            )
        }
    }
}
