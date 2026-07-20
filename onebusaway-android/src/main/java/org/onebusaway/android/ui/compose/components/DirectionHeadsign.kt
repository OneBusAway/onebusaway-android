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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.ui.icons.AppIcons

/**
 * The "heading toward X" line: a muted forward-arrow (reads as "toward" without asserting a
 * destination — a headsign can be a direction or loop name) followed by the destination in a
 * slightly-tightened monospace so it reads like the sign on the bus.
 *
 * Shared by the arrivals row's pill-strip header and the map's route-header overlay so the same
 * headsign reads identically on both surfaces (#1823). The treatment is background-agnostic: the
 * arrow tints with `onSurfaceVariant` and the text takes the ambient content color, both legible on
 * the row's and header's `surfaceContainer` backgrounds.
 */
@Composable
internal fun DirectionHeadsign(direction: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = AppIcons.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = direction,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
