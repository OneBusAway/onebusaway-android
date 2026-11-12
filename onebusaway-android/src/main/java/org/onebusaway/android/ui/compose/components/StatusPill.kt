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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.onebusaway.android.ui.arrivals.components.RealtimeIndicator

/**
 * The lateness-colored status pill: white text on the deviation [color]. When the status is
 * real-time, the real-time indicator sits inside the pill, right of the text, in the same white as the
 * lettering. Shared by trip details' header and the map focus banner's bus row (#2471), so one bus's
 * status reads the same on both.
 */
@Composable
internal fun StatusPill(text: String, color: Color, isRealtime: Boolean, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(6.dp), color = color, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1
            )
            if (isRealtime) {
                Spacer(Modifier.width(6.dp))
                RealtimeIndicator(color = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}
