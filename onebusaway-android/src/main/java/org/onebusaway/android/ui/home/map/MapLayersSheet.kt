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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.ui.compose.components.SegmentedChoice
import org.onebusaway.android.ui.compose.components.SheetDragHandle

/** Stable handles for the on-device test. */
object MapLayersTestTags {
    fun tile(id: LayerTileId): String = "mapLayersTile_${id.name}"
}

private val TILE_HEIGHT = 62.dp
private val TILE_RADIUS = 16.dp
private val ICON_WELL = 34.dp

/**
 * The map layers sheet (spec §3.9, screen A): the basemap control (google only), then a 2-column tile
 * grid per group. A tile takes its group's tint when on; the group disappears when it has no tiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersSheet(
    state: MapLayersUiState,
    onToggle: (LayerTileId) -> Unit,
    onBasemap: (Basemap) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.differsFromDefaults) TextButton(onClick = onReset) { Text(stringResource(R.string.map_layers_reset)) }
                }
                Text(stringResource(R.string.map_layers_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_layers_done)) }
                }
            }
            state.basemap?.let { selected ->
                SegmentedChoice(
                    options = Basemap.entries,
                    selected = selected,
                    onChange = onBasemap,
                    label = stringResource(R.string.map_layers_title),
                    optionLabel = { it.labelRes },
                    contentPadding = PaddingValues(0.dp)
                )
            }
            if (state.transit.isNotEmpty()) TileGroup(stringResource(R.string.map_layers_group_transit), state.transit, MaterialTheme.colorScheme.primary, onToggle)
            if (state.rentals.isNotEmpty()) TileGroup(stringResource(R.string.map_layers_group_rentals), state.rentals, colorResource(R.color.layer_bikeshare_color), onToggle)
        }
    }
}

@Composable
private fun TileGroup(title: String, tiles: List<LayerTile>, tint: Color, onToggle: (LayerTileId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { tile -> LayerTileView(tile, tint, onToggle, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LayerTileView(tile: LayerTile, tint: Color, onToggle: (LayerTileId) -> Unit, modifier: Modifier = Modifier) {
    val on = tile.enabled
    val contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .height(TILE_HEIGHT)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(tile.id) })
            .testTag(MapLayersTestTags.tile(tile.id)),
        shape = RoundedCornerShape(TILE_RADIUS),
        color = if (on) tint else MaterialTheme.colorScheme.surface,
        contentColor = contentColor
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(ICON_WELL).background(if (on) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(tile.iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(stringResource(tile.titleRes), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(if (on) R.string.map_layers_state_on else R.string.map_layers_state_off), style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}
