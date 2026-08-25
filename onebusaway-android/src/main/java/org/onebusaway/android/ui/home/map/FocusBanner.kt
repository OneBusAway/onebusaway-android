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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.models.WheelchairBoarding
import org.onebusaway.android.ui.compose.components.CenteredLongPressMenu
import org.onebusaway.android.ui.compose.components.DirectionBothWays
import org.onebusaway.android.ui.compose.components.DirectionHeadsign
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.MaterialSymbols
import org.onebusaway.android.ui.compose.components.MenuRow
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.util.DisplayFormat

// The banner's route action icons (direction menu / cancel) share one size + tint so they read as
// one control group: a larger-than-default 36dp icon in a deliberately tightened 40dp touch box
// (below Material's 48dp default — a conscious trade-off for a compact header banner).
private val HEADER_ICON_SIZE = 36.dp
private val HEADER_ICON_BUTTON_SIZE = 40.dp

// The favorite star and the rail it sits in, on the banner's leading edge. The rail insets the star
// from the card edge and takes its width from that inset plus the star — it deliberately has no
// trailing gutter of its own, because the banner content already pads its own leading edge and the
// two used to stack into a right-hand gap about twice the left inset (#2216).
private val FAVORITE_ICON_SIZE = 26.4.dp
private val FAVORITE_RAIL_LEADING_INSET = 10.8.dp
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

// The stop name shrinks to fit within this many lines before ellipsizing; see ShrinkToFitStopTitle.
private const val MAX_TITLE_LINES = 2

/**
 * The focused stop's overflow actions. They act on the stop's arrivals session — which HOME owns, not
 * the banner — so they arrive as one bundle rather than as two more lambdas on a banner that already
 * carries eight. Null hides the menu: there is no session to act on until a stop's arrivals are up.
 *
 * These outlived the standalone arrivals screen whose top bar used to hold them (#1898); the banner is
 * where a focused stop's actions live now. Its "show stop details" item did not: the banner itself
 * already shows the stop's name, code and direction, and the drawer below it the routes that serve it,
 * so the dialog only restated what was on screen.
 */
data class StopFocusMenu(
    val onReportStopProblem: () -> Unit,
    val onNightLight: () -> Unit,
    /**
     * Plan a trip to this stop — the map long press's "navigate here" offer, asked of the stop the rider
     * already has focused instead of a point they have to press for (#2272). Null while the stop's own
     * location is still unknown (a stop revealed by id alone has none until its arrivals land), since
     * there is nothing to route to yet; that is the same beat the banner's star waits on, and it has
     * passed by the time the menu itself is up in every entry point but that one.
     */
    val onNavigateHere: (() -> Unit)?
)

/**
 * Presentation state for the map's shared focus banner.
 */
sealed interface FocusBannerState {
    val isFavorite: Boolean
    val favoriteEnabled: Boolean

    data class Stop(
        val title: String,
        val direction: String?,
        val stopCode: String?,
        override val isFavorite: Boolean,
        override val favoriteEnabled: Boolean,
        val hasAlerts: Boolean,
        val wheelchairBoarding: WheelchairBoarding = WheelchairBoarding.UNKNOWN
    ) : FocusBannerState

    data class Route(
        val header: RouteHeader,
        override val isFavorite: Boolean
    ) : FocusBannerState {
        override val favoriteEnabled: Boolean get() = header.routeId != null
    }
}

/**
 * Floating information and actions for the current stop or standalone route focus. It reports its
 * measured height so map framing stays clear of the banner.
 */
@Composable
fun FocusBanner(
    state: FocusBannerState,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowAlerts: () -> Unit,
    onRecenterStop: () -> Unit,
    stopMenu: StopFocusMenu? = null,
    onSelectDirection: (Int?) -> Unit,
    onFrameRoute: () -> Unit,
    onShowSchedule: (String) -> Unit,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.onSizeChanged { onHeight(it.height) },
        // A floating rounded card (below the top chrome), rather than a full-width edge-to-edge bar.
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        ) {
            FavoriteRail(
                isFavorite = state.isFavorite,
                favoriteEnabled = state.favoriteEnabled,
                onToggleFavorite = onToggleFavorite
            )
            Box(Modifier.weight(1f)) {
                when (state) {
                    is FocusBannerState.Stop -> StopFocusBanner(
                        state = state,
                        onShowAlerts = onShowAlerts,
                        onRecenter = onRecenterStop,
                        stopMenu = stopMenu,
                        onClose = onClose
                    )
                    is FocusBannerState.Route -> RouteFocusBanner(
                        state = state,
                        onSelectDirection = onSelectDirection,
                        onFrameRoute = onFrameRoute,
                        onShowSchedule = onShowSchedule,
                        onClose = onClose
                    )
                }
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

@Composable
private fun StopFocusBanner(
    state: FocusBannerState.Stop,
    onShowAlerts: () -> Unit,
    onRecenter: () -> Unit,
    stopMenu: StopFocusMenu?,
    onClose: () -> Unit
) {
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
        val subtitle = stopSubtitleText(state.stopCode, state.direction)
        Column(
            modifier = Modifier.weight(1f).clickable(
                onClickLabel = stringResource(R.string.stop_info_recenter),
                role = Role.Button,
                onClick = onRecenter
            )
        ) {
            ShrinkToFitStopTitle(state.title)
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
            StopMenuAction(stopMenu)
        }
        HeaderIconButton(
            painter = painterResource(R.drawable.ic_navigation_close),
            contentDescription = stringResource(android.R.string.cancel),
            onClick = onClose
        )
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
private fun ShrinkToFitStopTitle(title: String) {
    val fullStyle = MaterialTheme.typography.titleLarge
    val floorSize = MaterialTheme.typography.titleMedium.fontSize
    val textMeasurer = rememberTextMeasurer()
    var maxWidthPx by remember { mutableIntStateOf(0) }

    fun fitsWithinLineCap(fontSize: TextUnit) = maxWidthPx <= 0 ||
        textMeasurer.measure(
            text = title,
            style = fullStyle.copy(fontSize = fontSize),
            constraints = Constraints(maxWidth = maxWidthPx)
        ).lineCount <= MAX_TITLE_LINES

    val resolvedSize = remember(title, maxWidthPx) {
        var candidate = fullStyle.fontSize
        while (candidate > floorSize && !fitsWithinLineCap(candidate)) {
            candidate = (candidate.value - 1).sp
        }
        candidate
    }
    Text(
        text = title,
        style = fullStyle.copy(fontSize = resolvedSize),
        maxLines = MAX_TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().onSizeChanged { maxWidthPx = it.width }
    )
}

/** The stop's identity line: passenger-facing stop number and formatted direction, joined when both are known. */
@Composable
private fun stopSubtitleText(stopCode: String?, direction: String?): String? {
    val codeText = stopCode?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.stop_details_code, it) }
    val directionText = DisplayFormat.stopDirectionText(LocalContext.current, direction)
    return listOfNotNull(codeText, directionText).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteFocusBanner(
    state: FocusBannerState.Route,
    onSelectDirection: (Int?) -> Unit,
    onFrameRoute: () -> Unit,
    onShowSchedule: (String) -> Unit,
    onClose: () -> Unit
) {
    val header = state.header
    val scheduleUrl = header.scheduleUrl
    var menuExpanded by remember { mutableStateOf(false) }
    val scheduleLabel = stringResource(R.string.bus_options_menu_show_route_schedule)
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
                    // Tap frames the route; long press opens the route menu — the same gesture
                    // pairing the arrivals drawer's route rows use. A route with no schedule page
                    // has nothing to put in the menu, so it stays tap-only.
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.route_header_frame_route),
                        role = Role.Button,
                        onLongClickLabel = if (scheduleUrl != null) scheduleLabel else null,
                        onLongClick = if (scheduleUrl != null) ({ menuExpanded = true }) else null,
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
                    // schedule, and a nested clickable would consume both on this line.
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
        }
        HeaderIconButton(
            painter = painterResource(R.drawable.ic_navigation_close),
            contentDescription = stringResource(android.R.string.cancel),
            onClick = onClose
        )
    }
    if (scheduleUrl != null) {
        CenteredLongPressMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            MenuRow(R.string.bus_options_menu_show_route_schedule, MaterialSymbols.Schedule) {
                menuExpanded = false
                onShowSchedule(scheduleUrl)
            }
        }
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
 * the nav-drawer icon color, in the tightened [HEADER_ICON_BUTTON_SIZE] box. Used by the direction-menu
 * and cancel actions.
 */
@Composable
private fun HeaderIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(HEADER_ICON_BUTTON_SIZE)) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = colorResource(R.color.navdrawer_icon_tint),
            modifier = Modifier.size(HEADER_ICON_SIZE)
        )
    }
}

/**
 * The focused stop's overflow: the stop actions that are neither frequent enough for their own icon nor
 * expressible on the map — a trip planned to the stop, a problem report against it, and the night-light
 * flasher a rider holds up to a driver. Inherited from the retired standalone arrivals screen's top bar
 * (#1898).
 *
 * "Navigate here" leads because it is the one item a rider looking at a stop is likely to want (#2272);
 * the other two answer situations, not intentions.
 */
@Composable
private fun StopMenuAction(menu: StopFocusMenu) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = stringResource(R.string.stop_info_item_options_title),
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // The same words the map long press's bubble offers, because it is the same offer.
            menu.onNavigateHere?.let { navigate ->
                MenuRow(R.string.map_navigate_here) {
                    expanded = false
                    navigate()
                }
            }
            MenuRow(R.string.stop_info_option_report_problem) {
                expanded = false
                menu.onReportStopProblem()
            }
            MenuRow(R.string.stop_info_option_night_light) {
                expanded = false
                menu.onNightLight()
            }
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
                onHeight = {}
            )
        }
    }
}
