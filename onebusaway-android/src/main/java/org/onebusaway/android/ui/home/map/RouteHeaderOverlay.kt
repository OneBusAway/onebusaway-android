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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.ui.compose.components.DirectionHeadsign
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.RadioOptionList
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.compose.theme.ObaTheme

// The route-header action icons (switch-direction / cancel) share one size + tint so they read as
// one control group: a larger-than-default 36dp icon in a deliberately tightened 40dp touch box
// (below Material's 48dp default — a conscious trade-off for a compact header banner).
private val HEADER_ICON_SIZE = 36.dp
private val HEADER_ICON_BUTTON_SIZE = 40.dp

/**
 * The route-mode header overlay (the Compose replacement for the legacy `route_info_head.xml` /
 * `RouteMapController.RoutePopup`). Renders the route short/long name + agency (or a spinner while the
 * route loads), the current direction's headsign, a switch-direction affordance (when the route has more
 * than one direction), and a cancel button that exits route mode. It reports its measured height via
 * [onHeight] so the host can set the map's top padding (keeping vehicle markers visible under it). The
 * route's favorite star no longer lives here — it's the arrival row's own corner toggle.
 *
 * [onSelectDirection] switches which direction of the route is shown (the id is one of
 * [RouteHeader.directions]). [onFrameRoute] reframes the map to the route's full extent — invoked by a tap
 * on the banner body (the badge + name column), scoped to leave the switch-direction / cancel icons as
 * their own separate tap targets.
 */
@Composable
fun RouteHeaderOverlay(
    header: RouteHeader,
    onCancel: () -> Unit,
    onSelectDirection: (Int) -> Unit,
    onFrameRoute: () -> Unit,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.onSizeChanged { onHeight(it.height) },
        // A floating rounded card (below the top chrome), rather than a full-width edge-to-edge bar.
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        if (header.loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CircularProgressIndicator(Modifier.size(48.dp))
            }
        } else {
            // The current direction's headsign (blank falls back to a generic label); null when the
            // route is shown whole (no direction selected), so the subtitle is hidden.
            val unnamed = stringResource(R.string.route_direction_unnamed)
            val directionLabel = header.currentDirection?.labelOr(unnamed)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The badge + name column is one tap target that reframes the map to the route's extent.
                // Scoped to this inner Row (given the outer Row's remaining width via weight) so it doesn't
                // overlap the switch-direction / cancel icons, which consume their own taps as separate
                // nodes; the click semantics announce the action for accessibility.
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = stringResource(R.string.route_header_frame_route),
                            role = Role.Button,
                            onClick = onFrameRoute,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        if (header.longName.isNotEmpty()) {
                            Text(
                                text = header.longName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
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
                        onSelectDirection = onSelectDirection,
                    )
                }
                HeaderIconButton(
                    iconRes = R.drawable.ic_navigation_close,
                    contentDescription = stringResource(android.R.string.cancel),
                    onClick = onCancel,
                )
            }
        }
    }
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
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(HEADER_ICON_BUTTON_SIZE)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = colorResource(R.color.navdrawer_icon_tint),
            modifier = Modifier.size(HEADER_ICON_SIZE),
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
    onSelectDirection: (Int) -> Unit,
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
        },
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
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/** The direction's headsign, or [unnamed] when the stop group carried no display name. */
private fun RouteMapDirection.labelOr(unnamed: String): String = label.ifBlank { unnamed }

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun RouteHeaderOverlayPreview() {
    ObaTheme {
        Column {
            // Two directions: the swap icon toggles between them; the current headsign shows as a subtitle.
            RouteHeaderOverlay(
                header = RouteHeader(
                    loading = false,
                    shortName = "40",
                    longName = "Downtown Seattle - Northgate",
                    agency = "King County Metro",
                    directions = listOf(
                        RouteMapDirection(0, "to Downtown Seattle"),
                        RouteMapDirection(1, "to Northgate"),
                    ),
                    currentDirectionId = 0,
                ),
                onCancel = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {},
            )
            Spacer(Modifier.size(12.dp))
            // Whole-route launch (no direction resolved yet): swap icon shown, no subtitle; a tap picks a direction.
            RouteHeaderOverlay(
                header = RouteHeader(
                    loading = false,
                    shortName = "40",
                    longName = "Downtown Seattle - Northgate",
                    agency = "King County Metro",
                    directions = listOf(
                        RouteMapDirection(0, "to Downtown Seattle"),
                        RouteMapDirection(1, "to Northgate"),
                    ),
                    currentDirectionId = null,
                ),
                onCancel = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {},
            )
            Spacer(Modifier.size(12.dp))
            // More than two directions: the swap icon opens a picker. A long route short name ellipsizes.
            RouteHeaderOverlay(
                header = RouteHeader(
                    loading = false,
                    shortName = "Mount Si. Trailhead",
                    longName = "North Bend - Snoqualmie Falls Express",
                    agency = "King County Metro",
                    directions = listOf(
                        RouteMapDirection(0, "to North Bend"),
                        RouteMapDirection(1, "to Snoqualmie Falls"),
                        RouteMapDirection(2, "to Issaquah"),
                    ),
                    currentDirectionId = 1,
                ),
                onCancel = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {},
            )
            Spacer(Modifier.size(12.dp))
            // A single-direction route: no swap icon, no subtitle.
            RouteHeaderOverlay(
                header = RouteHeader(
                    loading = false,
                    shortName = "8",
                    longName = "Capitol Hill - Rainier Beach",
                    agency = "King County Metro",
                ),
                onCancel = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {},
            )
            Spacer(Modifier.size(12.dp))
            // The loading state (spinner shown while the route resolves).
            RouteHeaderOverlay(
                header = RouteHeader(loading = true, shortName = "", longName = "", agency = ""),
                onCancel = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {},
            )
        }
    }
}
