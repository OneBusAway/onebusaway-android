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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
    unitSize: TextUnit = 12.sp
) {
    EtaPartsText(DisplayFormat.formatEtaParts(LocalContext.current, minutes), modifier, numberSize, unitSize)
}

/**
 * Renders value/unit display [parts] as a bold value with a smaller unit abbreviation — the shared ETA
 * styling, factored out of [EtaDurationText] so other value+unit cards (e.g. a trip's walk distance via
 * [org.onebusaway.android.directions.util.ConversionUtils.getFormattedDistanceParts]) render identically
 * without duplicating the span logic or the sizes.
 */
@Composable
fun EtaPartsText(
    parts: List<DisplayFormat.EtaPart>,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 15.sp,
    unitSize: TextUnit = 12.sp
) {
    // [tightLineStyle] trims the default font padding so a leading icon centers on the text and stacked
    // rows sit tight; the spans layer the per-part sizes on top of that same base.
    val base = LocalTextStyle.current
    val style = remember(base) { tightLineStyle(base) }
    val baseSpan = style.toSpanStyle()
    val text = buildAnnotatedString {
        parts.forEach { part ->
            val span = if (part.emphasized) {
                baseSpan.copy(fontSize = numberSize, fontWeight = FontWeight.Bold)
            } else {
                baseSpan.copy(fontSize = unitSize)
            }
            withStyle(span) { append(part.text) }
        }
    }
    Text(text = text, modifier = modifier, style = style)
}

/**
 * [base] with Android's default font-metrics padding (extra ascent/descent space reserved beyond a
 * glyph's visible ink) trimmed, so a leading icon centers on the text and stacked rows sit tight instead
 * of being pushed apart by that padding. Pass [size] to also pin the line height to a small pill/badge's
 * dominant text size; omit it to keep [base]'s own line height. Shared by the arrivals ETA pill
 * ([org.onebusaway.android.ui.arrivals.components] EtaStrip) and [EtaPartsText].
 */
internal fun tightLineStyle(base: TextStyle, size: TextUnit = TextUnit.Unspecified): TextStyle = base.copy(
    lineHeight = if (size == TextUnit.Unspecified) base.lineHeight else size,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)
