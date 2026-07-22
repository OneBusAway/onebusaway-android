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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import org.onebusaway.android.ui.compose.components.DirectionHeadsign
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.RadioOptionList
import org.onebusaway.android.ui.compose.components.RouteBadgeChip
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

// The banner's route action icons (switch-direction / cancel) share one size + tint so they read as
// one control group: a larger-than-default 36dp icon in a deliberately tightened 40dp touch box
// (below Material's 48dp default — a conscious trade-off for a compact header banner).
private val HEADER_ICON_SIZE = 36.dp
private val HEADER_ICON_BUTTON_SIZE = 40.dp
private val FOCUS_RAIL_ICON_SIZE = 26.4.dp

// The stop name shrinks to fit within this many lines before ellipsizing; see ShrinkToFitStopTitle.
private const val MAX_TITLE_LINES = 2

/**
 * Presentation state for the map's shared focus banner.
 */
sealed interface FocusBannerState {
    val isFavorite: Boolean
    val favoriteEnabled: Boolean

    @get:DrawableRes val focusIconRes: Int

    @get:StringRes val focusDescriptionRes: Int

    data class SubordinateRoute(
        val shortName: String,
        val color: Int? = null
    )

    data class Stop(
        val title: String,
        val direction: String?,
        val stopCode: String?,
        override val isFavorite: Boolean,
        override val favoriteEnabled: Boolean,
        val hasAlerts: Boolean,
        val subordinateRoutes: List<SubordinateRoute> = emptyList(),
        val subordinateHeadsign: String? = null
    ) : FocusBannerState {
        override val focusIconRes = R.drawable.stop_flag
        override val focusDescriptionRes = R.string.stop_shortcut
    }

    data class Route(
        val header: RouteHeader,
        override val isFavorite: Boolean
    ) : FocusBannerState {
        override val favoriteEnabled: Boolean get() = header.routeId != null
        override val focusIconRes = R.drawable.ic_route
        override val focusDescriptionRes = R.string.route_shortcut
    }
}

/**
 * Floating information and actions for the current stop or standalone route focus. It reports its
 * measured height so map framing stays clear of the complete banner, including a subordinate route
 * status line beneath a focused stop.
 */
@Composable
fun FocusBanner(
    state: FocusBannerState,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowAlerts: () -> Unit,
    onClearSubordinateRoute: () -> Unit,
    onRecenterStop: () -> Unit,
    onSelectDirection: (Int) -> Unit,
    onFrameRoute: () -> Unit,
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
            FocusIdentityRail(
                iconRes = state.focusIconRes,
                iconDescription = stringResource(state.focusDescriptionRes),
                isFavorite = state.isFavorite,
                favoriteEnabled = state.favoriteEnabled,
                onToggleFavorite = onToggleFavorite
            )
            VerticalDivider(Modifier.fillMaxHeight())
            Box(Modifier.weight(1f)) {
                when (state) {
                    is FocusBannerState.Stop -> StopFocusBanner(
                        state = state,
                        onShowAlerts = onShowAlerts,
                        onClearSubordinateRoute = onClearSubordinateRoute,
                        onRecenter = onRecenterStop,
                        onClose = onClose
                    )
                    is FocusBannerState.Route -> RouteFocusBanner(
                        state = state,
                        onSelectDirection = onSelectDirection,
                        onFrameRoute = onFrameRoute,
                        onClose = onClose
                    )
                }
            }
        }
    }
}

/** Shared left-side orientation chrome: focus type above favorite, separated from content by a rule. */
@Composable
private fun FocusIdentityRail(
    @DrawableRes iconRes: Int,
    iconDescription: String,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight().width(48.dp).heightIn(min = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = iconDescription,
            tint = colorResource(R.color.navdrawer_icon_tint),
            modifier = Modifier.size(FOCUS_RAIL_ICON_SIZE)
        )
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
    onClearSubordinateRoute: () -> Unit,
    onRecenter: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (state.subordinateRoutes.isEmpty()) {
                        Modifier.weight(1f)
                    } else {
                        Modifier
                    }
                )
                .padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
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
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (state.hasAlerts) {
                BannerAlertAction(onClick = onShowAlerts)
            }
            HeaderIconButton(
                iconRes = R.drawable.ic_navigation_close,
                contentDescription = stringResource(android.R.string.cancel),
                onClick = onClose
            )
        }
        if (state.subordinateRoutes.isNotEmpty()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.subordinateRoutes.forEachIndexed { index, route ->
                    if (index > 0) {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    }
                    CompactRouteBadge(route)
                }
                state.subordinateHeadsign?.takeIf { it.isNotBlank() }?.let { headsign ->
                    Text(
                        text = headsign,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp).weight(1f)
                    )
                } ?: Spacer(Modifier.weight(1f))
                CompactRouteDismissAction(onClick = onClearSubordinateRoute)
            }
        }
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

/** A deliberately tiny route chip: only enough padding to distinguish the route from its headsign. */
@Composable
private fun CompactRouteBadge(route: FocusBannerState.SubordinateRoute) {
    RouteBadgeChip(shortName = route.shortName, routeColor = route.color)
}

/** The nested-route dismiss affordance keeps its visible 22dp size as its exact clickable bounds. */
@Composable
private fun CompactRouteDismissAction(onClick: () -> Unit) {
    Icon(
        painter = painterResource(R.drawable.ic_navigation_close),
        contentDescription = stringResource(R.string.stop_info_unselect_route),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(22.dp)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun RouteFocusBanner(
    state: FocusBannerState.Route,
    onSelectDirection: (Int) -> Unit,
    onFrameRoute: () -> Unit,
    onClose: () -> Unit
) {
    val header = state.header
    Row(
        Modifier.fillMaxWidth().padding(if (header.loading) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (header.loading) {
            CircularProgressIndicator(Modifier.size(48.dp))
            Spacer(Modifier.weight(1f))
        } else {
            // The current direction's headsign (blank falls back to a generic label); null when the
            // route is shown whole (no direction selected), so the subtitle is hidden.
            val unnamed = stringResource(R.string.route_direction_unnamed)
            val directionLabel = header.currentDirection?.labelOr(unnamed)
            Row(
                Modifier
                    .weight(1f)
                    .clickable(
                        onClickLabel = stringResource(R.string.route_header_frame_route),
                        role = Role.Button,
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
                    width = 64.dp,
                    square = true,
                    color = badgeContent,
                    containerColor = badgeContainer,
                    modifier = Modifier.padding(horizontal = 10.dp)
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
                    if (directionLabel != null) {
                        // The same arrow-glyph + tightened-monospace treatment as an arrivals row, so
                        // the headsign reads identically on both surfaces (#1823).
                        DirectionHeadsign(directionLabel)
                    }
                    if (header.agency.isNotEmpty()) {
                        // One type-scale step below the direction line so it recedes as secondary info.
                        Text(text = header.agency, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // A route with a single direction has nothing to switch to — the affordance is hidden.
            if (header.directions.size >= 2) {
                SwitchDirectionAction(
                    directions = header.directions,
                    currentDirectionId = header.currentDirectionId,
                    onSelectDirection = onSelectDirection
                )
            }
        }
        HeaderIconButton(
            iconRes = R.drawable.ic_navigation_close,
            contentDescription = stringResource(android.R.string.cancel),
            onClick = onClose
        )
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
            .size(FOCUS_RAIL_ICON_SIZE)
            .clickable(enabled = enabled, onClick = onClick)
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
 * the nav-drawer icon color, in the tightened [HEADER_ICON_BUTTON_SIZE] box. Used by the switch-direction
 * and cancel actions.
 */
@Composable
private fun HeaderIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(HEADER_ICON_BUTTON_SIZE)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = colorResource(R.color.navdrawer_icon_tint),
            modifier = Modifier.size(HEADER_ICON_SIZE)
        )
    }
}

/**
 * The swap-direction icon button. With exactly two directions a tap toggles to the other; with more it
 * opens a radio picker of the directions' headsigns. [currentDirectionId] is the shown direction (null
 * when the route is shown whole — a whole-route launch); a toggle from there picks the first direction.
 */
@Composable
private fun SwitchDirectionAction(
    directions: List<RouteMapDirection>,
    currentDirectionId: Int?,
    onSelectDirection: (Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    HeaderIconButton(
        iconRes = R.drawable.ic_swap_direction,
        contentDescription = stringResource(R.string.route_header_switch_direction),
        onClick = {
            if (directions.size == 2) {
                onSelectDirection(directions.first { it.directionId != currentDirectionId }.directionId)
            } else {
                showPicker = true
            }
        }
    )
    if (showPicker) {
        val unnamed = stringResource(R.string.route_direction_unnamed)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.route_header_switch_direction)) },
            text = {
                RadioOptionList(
                    options = directions.map { it.labelOr(unnamed) }.toTypedArray(),
                    selectedIndex = directions.indexOfFirst { it.directionId == currentDirectionId },
                    onSelect = { index ->
                        showPicker = false
                        onSelectDirection(directions[index].directionId)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
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
                    subordinateRoutes = listOf(
                        FocusBannerState.SubordinateRoute("65", 0xFF26823B.toInt()),
                        FocusBannerState.SubordinateRoute("75", 0xFF125BA8.toInt())
                    ),
                    subordinateHeadsign = "Downtown Seattle"
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onClearSubordinateRoute = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
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
                onClearSubordinateRoute = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {}
            )
        }
    }
}
