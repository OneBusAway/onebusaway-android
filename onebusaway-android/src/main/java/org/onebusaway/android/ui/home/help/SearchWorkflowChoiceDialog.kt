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
package org.onebusaway.android.ui.home.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.searchresults.SearchResultMode

/** Offline illustrations show the two search destinations without requesting tiles or transit data. */
@Composable
internal fun SearchWorkflowChoiceDialog(onSave: (SearchResultMode) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(SearchResultMode.MAP) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_result_mode_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.search_result_mode_migration_body))
                listOf(SearchResultMode.LISTS, SearchResultMode.MAP).forEach { mode ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, if (selected == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            Modifier.selectable(selected == mode, role = Role.RadioButton, onClick = { selected = mode }).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(selected = selected == mode, onClick = null)
                                Column {
                                    Text(
                                        stringResource(if (mode == SearchResultMode.MAP) R.string.search_result_mode_map else R.string.search_result_mode_lists),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        stringResource(if (mode == SearchResultMode.LISTS) R.string.migration_previous_layout else R.string.migration_new_layout),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            Text(stringResource(if (mode == SearchResultMode.MAP) R.string.search_result_mode_map_description else R.string.search_result_mode_lists_description))
                            SearchWorkflowPreview(mode)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.migration_continue)) } }
    )
}

@Composable
private fun SearchWorkflowPreview(mode: SearchResultMode) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (mode == SearchResultMode.MAP) {
            val street = MaterialTheme.colorScheme.outlineVariant
            val route = MaterialTheme.colorScheme.primary
            val stop = MaterialTheme.colorScheme.surface
            Canvas(Modifier.fillMaxWidth().height(86.dp)) {
                for (i in 1..4) {
                    val x = size.width * i / 5
                    drawLine(street, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
                }
                for (i in 1..2) {
                    val y = size.height * i / 3
                    drawLine(street, Offset(0f, y), Offset(size.width, y), 5.dp.toPx())
                }
                val path = Path().apply {
                    moveTo(size.width * .2f, size.height)
                    lineTo(size.width * .2f, size.height / 3)
                    lineTo(size.width * .8f, size.height / 3)
                    lineTo(size.width * .8f, 0f)
                }
                drawPath(path, route, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                for (x in listOf(.2f, .5f, .8f)) {
                    drawCircle(route, 6.dp.toPx(), Offset(size.width * x, size.height / 3))
                    drawCircle(stop, 3.dp.toPx(), Offset(size.width * x, size.height / 3))
                }
            }
        } else {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineBadge("8")
                Text(stringResource(R.string.search_result_mode_sample_direction), style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()
            Text(stringResource(R.string.search_result_mode_sample_stop), Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineBadge("8")
            Text(stringResource(R.string.arrival_display_sample_downtown), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.search_result_mode_sample_eta), Modifier.padding(6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
