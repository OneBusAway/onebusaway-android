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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

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
    bikeshareActive: Boolean,
    mapLoading: Boolean,
    fabBottomInsetTarget: Dp,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleBikeshare: () -> Unit,
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
                bikeshareActive = bikeshareActive,
                leftHandMode = leftHandMode,
                onToggleBikeshare = onToggleBikeshare,
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
 * The layers FAB: tapping it expands a single labeled "Bikeshare" item (the only layer), replacing
 * the android-fab speed-dial. The item is tinted by its active state; tapping it toggles the layer.
 */
@Composable
private fun LayersFab(
    bikeshareActive: Boolean,
    leftHandMode: Boolean,
    onToggleBikeshare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val iconRotation by animateFloatAsState(if (expanded) 45f else 0f, label = "layersFabIcon")
    val alignment = if (leftHandMode) Alignment.Start else Alignment.End

    Column(modifier, horizontalAlignment = alignment) {
        AnimatedVisibility(visible = expanded) {
            BikeshareItem(
                active = bikeshareActive,
                leftHandMode = leftHandMode,
                onClick = {
                    expanded = false
                    onToggleBikeshare()
                }
            )
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

/** The single bikeshare layer row: a labeled chip beside a small FAB, tinted by [active]. */
@Composable
private fun BikeshareItem(
    active: Boolean,
    leftHandMode: Boolean,
    onClick: () -> Unit
) {
    val itemColor = colorResource(if (active) R.color.layer_bikeshare_color else R.color.layer_disabled)
    val label: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(4.dp), color = itemColor) {
            Text(
                stringResource(R.string.layers_speedial_bikeshare_label),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    val fab: @Composable () -> Unit = {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = itemColor,
            contentColor = Color.White
        ) {
            Icon(
                painterResource(R.drawable.ic_directions_bike_white),
                contentDescription = stringResource(R.string.layers_speedial_bikeshare_label),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Keep the label on the inner side, away from the screen edge the FAB hugs.
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
