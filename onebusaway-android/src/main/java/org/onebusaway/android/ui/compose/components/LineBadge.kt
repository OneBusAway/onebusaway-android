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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Line height as a multiple of the font size, so multi-line badges shrink with the font. */
private const val LINE_HEIGHT_RATIO = 1.1f

/** Each shrink step multiplies the font size by this until the text fits. */
private const val SHRINK_STEP = 0.9f

/**
 * A transit "line" identifier badge (a route short name) shown in a fixed-width rectangle: centered,
 * bold, wrapping to at most [maxLines] lines, and auto-shrunk just enough to fit. So a multi-word
 * name like "G Line" stays two centered lines only slightly smaller, instead of overflowing the slot
 * or collapsing to a tiny single line — while short names like "8" keep the full [maxFontSize].
 *
 * The subtlety worth reusing: the font starts at [maxFontSize] and steps down 10% at a time until the
 * text no longer visually overflows the [width] × [maxHeight] / [maxLines] box (or it reaches
 * [minFontSize]). The fit is measured synchronously during composition, so it is correct on the first
 * frame (no flash) and resolves even in a static @Preview — an onTextLayout shrink loop would not.
 *
 * Drop it anywhere a route short name sits in a constrained slot (list rows, headers, map callouts).
 * [color] defaults to [Color.Unspecified] so the badge inherits the ambient content color.
 *
 * @param width the fixed width of the badge rectangle
 * @param maxHeight the height cap for the rectangle; the text also shrinks to fit it (in addition to
 *   [maxLines]). Defaults to roughly two lines at [maxFontSize], so a single big number isn't capped
 *   but a tall multi-line name can't blow up the row. Pass [Dp.Unspecified] to leave it unbounded.
 * @param maxFontSize the starting (largest) font size, used when the text already fits
 * @param minFontSize the floor the auto-shrink won't go below (it may then clip)
 * @param maxLines the most lines the text may wrap to within the rectangle
 * @param textDecoration drawn on the text (e.g. strike-through for canceled trips); doesn't affect fit
 */
@Composable
fun LineBadge(
    text: String,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    maxHeight: Dp = 60.dp,
    maxFontSize: TextUnit = 32.sp,
    minFontSize: TextUnit = 12.sp,
    maxLines: Int = 2,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration = TextDecoration.None
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Bold and centered; line height tracks the font (a fixed line height would keep two lines the
    // same height as the font shrinks, so a multi-line block could never fit maxHeight).
    val titleLarge = MaterialTheme.typography.titleLarge
    val baseStyle = remember(titleLarge) {
        titleLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    fun styleAt(size: TextUnit) =
        baseStyle.copy(fontSize = size, lineHeight = (size.value * LINE_HEIGHT_RATIO).sp)

    // Largest font in [minFontSize, maxFontSize] whose measured text fits the width × maxHeight /
    // maxLines box; measured here (not via onTextLayout) so it's right on the first frame and in @Preview.
    val fontSize = remember(text, width, maxHeight, maxFontSize, minFontSize, maxLines, density, baseStyle) {
        with(density) {
            val constraints = if (maxHeight.isSpecified) {
                Constraints(maxWidth = width.roundToPx(), maxHeight = maxHeight.roundToPx())
            } else {
                Constraints(maxWidth = width.roundToPx())
            }
            var size = maxFontSize
            while (size.value > minFontSize.value &&
                measurer.measure(text, styleAt(size), maxLines = maxLines, constraints = constraints)
                    .hasVisualOverflow
            ) {
                size = (size.value * SHRINK_STEP).sp
            }
            size
        }
    }
    val boxModifier = modifier
        .width(width)
        .let { if (maxHeight.isSpecified) it.heightIn(max = maxHeight) else it }
    Box(boxModifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color,
            style = styleAt(fontSize),
            maxLines = maxLines,
            textDecoration = textDecoration
        )
    }
}

@Preview(showBackground = true, name = "LineBadge — shrinks to fit its rectangle")
@Composable
private fun LineBadgePreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp)) {
                // Each badge is outlined so the fixed 64dp-wide rectangle is visible; short names use
                // the full size while longer ones shrink (and wrap) to stay inside it.
                listOf("8", "44", "550", "G Line", "Swift", "RapidRide A", "Chattanooga Choo Choo Very Much Far Too Long").forEach { label ->
                    Row(
                        Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LineBadge(label, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline))
                        Spacer(Modifier.width(16.dp))
                        Text("\"$label\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                // The same name forced into shorter rectangles shrinks further to fit the height.
                Text("Same name, maxHeight 72 / 50 / 32 dp:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(72.dp, 50.dp, 32.dp).forEach { cap ->
                        LineBadge(
                            "G Line",
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline),
                            maxHeight = cap
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
        }
    }
}
