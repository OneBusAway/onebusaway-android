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
package org.onebusaway.android.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

/**
 * Header text for a result row's DropdownMenu (the route description or stop name).
 */
@Composable
fun MenuHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * The visual content of a stop result row: an optional favorite star, the stop name, and the
 * compass direction (when known). The caller supplies its own click handling and padding via
 * [modifier]; the menu (if any) is the caller's responsibility.
 *
 * @param direction raw compass direction code ("N", "SW", ...); blank when unknown.
 */
@Composable
fun StopRowContent(
    name: String,
    direction: String,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isFavorite) {
            Icon(
                painter = painterResource(R.drawable.ic_toggle_star),
                contentDescription = stringResource(R.string.stop_info_favorite),
                tint = colorResource(R.color.navdrawer_icon_tint)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            val directionText = stringResource(DisplayFormat.getStopDirectionText(direction))
            if (directionText.isNotEmpty()) {
                Text(
                    text = directionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The visual content of a route result row: the prominent short-name [LineBadge] on the left and
 * an optional description to its right, matching the legacy list. The caller supplies its own
 * click handling and padding via [modifier].
 */
@Composable
fun RouteRowContent(shortName: String, longName: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LineBadge(shortName)
        Spacer(Modifier.width(12.dp))
        if (longName != null) {
            Text(
                text = longName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteRowContentPreview() {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ObaTheme {
        Column {
            RouteRowContent("8", "Seattle Center - Capitol Hill - Rainier Beach", rowModifier)
            HorizontalDivider()
            RouteRowContent("12", "Interlaken Park - Capitol Hill - Downtown Seattle", rowModifier)
            HorizontalDivider()
            RouteRowContent("225", "Sheridan Park", rowModifier)
            HorizontalDivider()
            RouteRowContent("40", null, rowModifier)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopRowContentPreview() {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ObaTheme {
        Column {
            StopRowContent("Broadway & E Denny Way - Bay 4", "S", isFavorite = true, rowModifier)
            HorizontalDivider()
            StopRowContent("19th Ave E & E Republican St", "N", isFavorite = false, rowModifier)
            HorizontalDivider()
            StopRowContent("Stop with no direction", "", isFavorite = false, rowModifier)
        }
    }
}
