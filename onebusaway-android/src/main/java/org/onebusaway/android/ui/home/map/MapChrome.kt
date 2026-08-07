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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.ui.compose.unitsAreMetric

/**
 * The map's Compose overlay chrome, replacing the XML my-location FAB, zoom buttons, and the
 * third-party android-fab layers speed-dial. Hosted over the map inside HomeScreen's
 * BottomSheetScaffold content; [fabBottomInsetTarget] is the sheet-driven lift target (the peek
 * height when collapsed, else 0) that the FABs animate to — replacing the legacy
 * `moveFabsLocation()` margin animation. All state + actions are supplied by [MapFeature].
 */
@Composable
fun MapChrome(
    zoomVisible: Boolean,
    leftHandMode: Boolean,
    layersVisible: Boolean,
    bikesActive: Boolean,
    scootersActive: Boolean,
    minimumRangeMeters: Int?,
    mapLoading: Boolean,
    fabBottomInsetTarget: Dp,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleBikes: () -> Unit,
    onToggleScooters: () -> Unit,
    onMinimumRangeSelected: (Int?) -> Unit
) {
    // Animate the lift here so the per-frame value only recomposes the FABs, not the hosting map
    // AndroidView / overlay cards (which are siblings in HomeScreen's Box).
    val fabBottomInset by animateDpAsState(fabBottomInsetTarget, label = "fabInset")
    val sideAlign = if (leftHandMode) Alignment.BottomStart else Alignment.BottomEnd
    val marginHorizontal = dimensionResource(R.dimen.fab_margin_horizontal)
    val marginBottom = dimensionResource(R.dimen.fab_margin_vertical)
    val accent = colorResource(R.color.theme_accent)
    Box(Modifier.fillMaxSize()) {
        // Indeterminate map-loading bar across the top (replaces the legacy XML progress_horizontal).
        if (mapLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
        if (zoomVisible) {
            ZoomControls(
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = marginBottom + fabBottomInset)
            )
        }
        if (layersVisible) {
            LayersFab(
                bikesActive = bikesActive,
                scootersActive = scootersActive,
                minimumRangeMeters = minimumRangeMeters,
                leftHandMode = leftHandMode,
                onToggleBikes = onToggleBikes,
                onToggleScooters = onToggleScooters,
                onMinimumRangeSelected = onMinimumRangeSelected,
                modifier = Modifier
                    .align(sideAlign)
                    .padding(horizontal = marginHorizontal)
                    .padding(bottom = LAYERS_MARGIN_BOTTOM + fabBottomInset)
            )
        }
        // The my-location FAB always shows on the map (this chrome only composes on HOME, the map screen).
        FloatingActionButton(
            onClick = onMyLocation,
            containerColor = accent,
            contentColor = Color.White,
            modifier = Modifier
                .align(sideAlign)
                .padding(horizontal = marginHorizontal)
                .padding(bottom = marginBottom + fabBottomInset)
        ) {
            Icon(
                painterResource(R.drawable.ic_maps_my_location),
                contentDescription = stringResource(R.string.map_option_mylocation),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** A white rounded pill of zoom-out / zoom-in glyphs, mirroring the legacy zoom_buttons_layout. */
@Composable
private fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.85f),
        shadowElevation = 4.dp
    ) {
        Row {
            IconButton(onClick = onZoomOut) {
                Icon(
                    painterResource(R.drawable.ic_zoom_out),
                    contentDescription = stringResource(R.string.map_option_zoom_out),
                    tint = Color.Unspecified
                )
            }
            IconButton(onClick = onZoomIn) {
                Icon(
                    painterResource(R.drawable.ic_zoom_in),
                    contentDescription = stringResource(R.string.map_option_zoom_in),
                    tint = Color.Unspecified
                )
            }
        }
    }
}

/**
 * The layers FAB: tapping it expands the rental layer rows — Bikes, Scooters, and the range filter
 * (#2168) — replacing the android-fab speed-dial. Each layer row is tinted by its active state and
 * toggles that layer; the range row opens a menu of presets.
 *
 * The two layers are separate rows over **one** fetch: `vehicleRentalsByBbox` returns everything in
 * the viewport regardless, so turning both on costs no extra request.
 */
@Composable
private fun LayersFab(
    bikesActive: Boolean,
    scootersActive: Boolean,
    minimumRangeMeters: Int?,
    leftHandMode: Boolean,
    onToggleBikes: () -> Unit,
    onToggleScooters: () -> Unit,
    onMinimumRangeSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val iconRotation by animateFloatAsState(if (expanded) 45f else 0f, label = "layersFabIcon")
    val alignment = if (leftHandMode) Alignment.Start else Alignment.End

    Column(modifier, horizontalAlignment = alignment) {
        AnimatedVisibility(visible = expanded) {
            Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LayerItem(
                    label = stringResource(R.string.layers_speedial_bikes_label),
                    iconRes = R.drawable.ic_directions_bike,
                    color = colorResource(if (bikesActive) R.color.layer_bikeshare_color else R.color.layer_disabled),
                    leftHandMode = leftHandMode,
                    onClick = {
                        expanded = false
                        onToggleBikes()
                    }
                )
                LayerItem(
                    label = stringResource(R.string.layers_speedial_scooters_label),
                    iconRes = R.drawable.ic_kick_scooter,
                    color = colorResource(if (scootersActive) R.color.layer_scooters_color else R.color.layer_disabled),
                    leftHandMode = leftHandMode,
                    onClick = {
                        expanded = false
                        onToggleScooters()
                    }
                )
                RangeFilterItem(
                    minimumRangeMeters = minimumRangeMeters,
                    leftHandMode = leftHandMode,
                    onSelected = {
                        expanded = false
                        onMinimumRangeSelected(it)
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = colorResource(R.color.theme_accent),
            contentColor = Color.White
        ) {
            Icon(
                painterResource(if (expanded) R.drawable.ic_add_white_24dp else R.drawable.ic_layers_white_24dp),
                contentDescription = stringResource(
                    if (expanded) R.string.map_option_layers_close else R.string.map_option_layers
                ),
                modifier = Modifier.size(24.dp).rotate(iconRotation)
            )
        }
    }
}

/**
 * The range-filter row: the current preset on a chip, and a menu of the rest.
 *
 * The filter **fails open** — a dock, a pedal bike, and any vehicle whose feed omits its range stay
 * visible at every preset (see [org.onebusaway.android.map.rental.matchesMinimumRange]) — so this
 * narrows the map rather than emptying it.
 */
@Composable
private fun RangeFilterItem(
    minimumRangeMeters: Int?,
    leftHandMode: Boolean,
    onSelected: (Int?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val active = minimumRangeMeters != null
    val itemColor = colorResource(if (active) R.color.layer_bikeshare_color else R.color.layer_disabled)
    Box {
        SpeedDialRow(
            leftHandMode = leftHandMode,
            label = {
                LayerChip(rangePresetLabel(minimumRangeMeters), itemColor)
            },
            fab = {
                SmallFloatingActionButton(
                    onClick = { menuOpen = true },
                    containerColor = itemColor,
                    contentColor = Color.White
                ) {
                    Icon(
                        painterResource(R.drawable.ic_bike_rental),
                        contentDescription = stringResource(R.string.layers_speedial_range_label),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            for (preset in RANGE_PRESETS_METERS) {
                DropdownMenuItem(
                    text = { Text(rangePresetLabel(preset)) },
                    onClick = {
                        menuOpen = false
                        onSelected(preset)
                    }
                )
            }
        }
    }
}

/**
 * The minimum-range presets, in metres, "any" first. Round numbers a rider can reason about rather
 * than anything the feeds publish — GBFS states a per-vehicle range and nothing about useful
 * thresholds, so these are chosen for the question being asked ("enough to get across town?").
 */
private val RANGE_PRESETS_METERS = listOf(null, 1_000, 3_000, 5_000, 10_000)

@Composable
private fun rangePresetLabel(meters: Int?): String = if (meters == null) {
    stringResource(R.string.rental_range_filter_any)
} else {
    stringResource(
        R.string.rental_range_filter_at_least,
        ConversionUtils.getFormattedDistance(meters.toDouble(), LocalContext.current, unitsAreMetric())
    )
}

/** One layer row: a labeled chip beside a small FAB, tinted by the layer's active state. */
@Composable
private fun LayerItem(
    label: String,
    @DrawableRes iconRes: Int,
    color: Color,
    leftHandMode: Boolean,
    onClick: () -> Unit
) {
    SpeedDialRow(
        leftHandMode = leftHandMode,
        label = { LayerChip(label, color) },
        fab = {
            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = color,
                contentColor = Color.White
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

@Composable
private fun LayerChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color) {
        Text(
            text,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Keeps every speed-dial row's label on the inner side, away from the screen edge the FAB hugs. */
@Composable
private fun SpeedDialRow(
    leftHandMode: Boolean,
    label: @Composable () -> Unit,
    fab: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leftHandMode) {
            fab()
            label()
        } else {
            label()
            fab()
        }
    }
}

// The my-location FAB uses @dimen/fab_margin_*; the layers FAB sits a fixed amount above it (the
// legacy layout hardcoded this 80dp, with no dimen).
private val LAYERS_MARGIN_BOTTOM = 80.dp
