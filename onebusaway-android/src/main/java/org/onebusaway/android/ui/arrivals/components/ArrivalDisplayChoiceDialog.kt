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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import org.onebusaway.android.R
import org.onebusaway.android.time.LocalIllustrationTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.arrivals.chronologicalArrivals

/** Illustrations are fixed sample departures, so this choice also works offline or out of service. */
@Composable
internal fun ArrivalDisplayChoiceDialog(
    initialMode: ArrivalDisplayMode,
    onSave: (ArrivalDisplayMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(initialMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arrival_display_choose_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.arrival_display_choose_body))
                ArrivalDisplayMode.entries.forEach { mode ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, if (selected == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.selectable(selected == mode, role = Role.RadioButton, onClick = { selected = mode }).padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(selected = selected == mode, onClick = null)
                                Text(
                                    stringResource(if (mode == ArrivalDisplayMode.TIME) R.string.arrival_display_time_migration_title else R.string.arrival_display_route_migration_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(stringResource(if (mode == ArrivalDisplayMode.TIME) R.string.arrival_display_time_description else R.string.arrival_display_route_description))
                            ArrivalModePreview(mode, onSelect = { selected = mode })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.arrival_display_save_default)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

/** Same departures rendered by the actual drawer rows, at a scaled phone width. */
@Composable
private fun ArrivalModePreview(mode: ArrivalDisplayMode, onSelect: () -> Unit) {
    val context = LocalContext.current
    val downtown = stringResource(R.string.arrival_display_sample_downtown)
    val northgate = stringResource(R.string.arrival_display_sample_northgate)
    val now = remember {
        ServerTime(LocalDate.of(2026, 1, 1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    val arrivals = remember(context, downtown, northgate, now) {
        listOf(
            previewArrival("8", downtown, 3, tripId = "sample-eight-first", context = context, now = now),
            previewArrival("40", northgate, 5, predicted = false, tripId = "sample-forty", context = context, now = now),
            previewArrival("8", downtown, 12, scheduleDeviationMinutes = 2, tripId = "sample-eight-second", context = context, now = now)
        )
    }
    val groups = remember(arrivals, mode) {
        if (mode == ArrivalDisplayMode.TIME) {
            chronologicalArrivals(arrivals).map { RouteRowGroup(listOf(it)) }
        } else {
            arrivals.groupBy { it.routeId }.values.map(::RouteRowGroup)
        }
    }
    val actions = remember(arrivals) {
        arrivals.associate { arrival ->
            arrival.tripId to ArrivalActions(
                tripId = arrival.tripId,
                routeId = arrival.routeId,
                routeShortName = arrival.shortName,
                routeLongName = arrival.headsign,
                routeColor = if (arrival.shortName == "8") 0xFF9C27B0.toInt() else 0xFF0A5B3E.toInt(),
                scheduleUrl = null,
                agencyName = null,
                blockId = null
            )
        }
    }
    val callbacks = remember { previewRowCallbacks() }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics { }) {
        CompositionLocalProvider(LocalIllustrationTime provides now) {
            Column(Modifier.phonePreviewScale()) {
                groups.forEach { group ->
                    RouteArrivalRow(
                        group = group,
                        actionsFor = { actions[it.tripId] },
                        isFavorite = group.routeId == "route_8",
                        callbacks = callbacks,
                        chronological = mode == ArrivalDisplayMode.TIME
                    )
                }
            }
        }
        // Cover the illustration's route/ETA/star touch targets. Every tap selects this option;
        // long presses and swipes must never open sample-trip menus or scroll a sample ETA strip.
        Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null, onClick = onSelect))
    }
}

/** Preserve the drawer's real proportions instead of squeezing its columns into a dialog card. */
private fun Modifier.phonePreviewScale(): Modifier = layout { measurable, constraints ->
    val phoneWidth = 360.dp.roundToPx()
    val width = minOf(phoneWidth, constraints.maxWidth)
    val scale = width.toFloat() / phoneWidth
    val placeable = measurable.measure(Constraints.fixedWidth(phoneWidth))
    layout(width, (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
