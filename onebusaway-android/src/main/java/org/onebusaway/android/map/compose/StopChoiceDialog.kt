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
package org.onebusaway.android.map.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRouteGridOrientation
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.ROUTE_NAME_ORDER

/** A hit-test ambiguity, not a claim that the provider's stops are equivalent. */
internal fun stopChoicesAt(tapped: StopMarker, stops: List<StopMarker>): List<StopMarker> = (listOf(tapped) + stops)
    .filter { it.point == tapped.point }
    .distinctBy { it.id }
    .sortedWith(compareBy<StopMarker, String>(ROUTE_NAME_ORDER) { it.stop.stopCode.orEmpty() }.thenBy { it.id })

/** Choose an original stop; the caller forwards it through the ordinary single-stop callback. */
@Composable
internal fun StopChoiceDialog(
    stops: List<StopMarker>,
    onSelect: (StopMarker) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_choose_stop)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (marker in stops) {
                    val stop = marker.stop
                    ListItem(
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { onSelect(marker) },
                        headlineContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stop.name?.takeIf(String::isNotBlank) ?: stop.id,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                DisplayFormat.stopSubtitleText(
                                    LocalContext.current,
                                    stop.stopCode?.takeIf(String::isNotBlank) ?: stop.id,
                                    stop.direction
                                )?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            // Balance the title's visible top inset: its text line includes font space
                            // above the letters, while the route bitmap ends at its visible border.
                            if (marker.routes.isEmpty()) {
                                Text(stringResource(R.string.map_stop_routes_unavailable), modifier = Modifier.padding(top = 8.dp, bottom = 5.dp))
                            } else {
                                StopRouteGrid(marker.routes, modifier = Modifier.padding(top = 8.dp, bottom = 5.dp), orientation = StopRouteGridOrientation.Horizontal)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
