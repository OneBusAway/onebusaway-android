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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.ScriptedTutorial
import org.onebusaway.android.ui.tutorial.tutorialAnchor

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
    /** Enabled layer tiles, shown as the button's badge (0 hides it). */
    layersBadge: Int,
    mapLoading: Boolean,
    fabBottomInsetTarget: Dp,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenLayers: () -> Unit,
    onHideLayersButton: () -> Unit
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
                badgeCount = layersBadge,
                onClick = onOpenLayers,
                onHide = onHideLayersButton,
                modifier = Modifier
                    .align(sideAlign)
                    .padding(horizontal = marginHorizontal)
                    // Clear the my-location FAB below: it occupies marginBottom..marginBottom+FAB_SIZE.
                    .padding(bottom = marginBottom + FAB_SIZE + LAYERS_FAB_GAP + fabBottomInset)
                    // The scripted tour's micromobility step spotlights this control (#2164).
                    .tutorialAnchor(LocalTutorialState.current, ScriptedTutorial.KEY_RENTALS)
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
 * The map layers button (spec §3.9), replacing the rentals speed-dial: one white FAB with the layers
 * glyph and a badge counting the enabled tiles; long-press offers to hide it, as before.
 */
@Composable
private fun LayersFab(badgeCount: Int, onClick: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val accent = colorResource(R.color.theme_accent)
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier
                .size(FAB_SIZE)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = stringResource(R.string.map_layers_hide_button)
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            contentColor = accent,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_layers), contentDescription = stringResource(R.string.map_layers_open), modifier = Modifier.size(26.dp))
            }
        }
        if (badgeCount > 0) {
            Text(
                text = badgeCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(accent, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_layers_hide_button)) },
                onClick = {
                    menuOpen = false
                    onHide()
                }
            )
        }
    }
}

/** M3's standard FAB diameter, which the my-location button is. Not exposed as a token by Material 3. */
private val FAB_SIZE = 56.dp

/** Clear air between the layers button and the my-location FAB beneath it. */
private val LAYERS_FAB_GAP = 16.dp
