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

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.onebusaway.android.util.DisplayFormat

/**
 * A duration rendered exactly like the arrivals ETA pill — a bold number with a smaller unit
 * abbreviation ("32 min", "1 hr 30 min") — reusing the ETA pill's own formatter
 * ([DisplayFormat.formatEtaParts]) so the two can't diverge. For a trip length, pass the whole-minute
 * duration.
 */
@Composable
fun EtaDurationText(
    minutes: Long,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 15.sp,
    unitSize: TextUnit = 12.sp,
) {
    val baseSpan = LocalTextStyle.current.toSpanStyle()
    val text = buildAnnotatedString {
        DisplayFormat.formatEtaParts(LocalContext.current, minutes).forEach { part ->
            val span = if (part.emphasized) {
                baseSpan.copy(fontSize = numberSize, fontWeight = FontWeight.Bold)
            } else {
                baseSpan.copy(fontSize = unitSize)
            }
            withStyle(span) { append(part.text) }
        }
    }
    Text(text = text, modifier = modifier)
}
