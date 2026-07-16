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

import android.annotation.SuppressLint
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
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
import com.google.android.material.color.utilities.Hct
import kotlin.math.min
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Line height as a multiple of the font size, so multi-line badges shrink with the font. */
private const val LINE_HEIGHT_RATIO = 1.1f

/** Each shrink step multiplies the font size by this until the text fits. */
private const val SHRINK_STEP = 0.9f

/** The chip's corner radius + inner padding when the badge is drawn on a colored surface. */
private val CHIP_SHAPE = RoundedCornerShape(8.dp)
private val CHIP_H_PADDING = 8.dp
private val CHIP_V_PADDING = 2.dp
private const val CHIP_END_COLOR_FRACTION = 0.2f

// Route-badge color tokens. We take only the *hue* of the agency's GTFS color and re-derive the chip
// in HCT (a perceptual space) at a fixed tone + capped chroma, so the agency can't hand us an
// over-saturated or too-dark/too-light color — every chip lands at a consistent, legible brightness,
// and the tone flips lighter for dark theme. Fidget with these to taste.
private const val CHIP_TONE_LIGHT = 80.0        // container tone in light theme (0=black … 100=white)
private const val CHIP_TONE_DARK = 78.0         // container tone for dark theme
private const val CHIP_ON_TONE_LIGHT = 30.0     // text tone on the light-theme (pastel) chip (→ dark)
private const val CHIP_ON_TONE_DARK = 20.0      // text tone on the dark-theme chip (→ near-black)
private const val CHIP_MAX_CHROMA_LIGHT = 30.0  // saturation cap in light theme (soft pastel)
private const val CHIP_MAX_CHROMA_DARK = 60.0   // saturation cap in dark theme
                                                // (each hue still clamps to its own sRGB gamut limit;
                                                //  low caps mute vivid hues — e.g. orange → brown)
private const val ACHROMATIC_CHROMA = 5.0       // below this the source is grey/black/white (no hue)

/**
 * Resolves the (container, content) colors for a route-badge chip from the route's GTFS color. We keep
 * only its hue and regenerate the chip at a fixed HCT tone + capped chroma (see the tokens above), so
 * the result is a consistent brightness in the active theme regardless of what the agency picked; the
 * text tone is paired to the container for guaranteed contrast. An achromatic source (grey/black/white)
 * or a route with no color falls back to a neutral theme chip. Callers pass the pair straight to
 * [LineBadge]'s `containerColor` / `color`.
 */
@SuppressLint("RestrictedApi") // Hct is Material Components' vendored color-science util (LIBRARY_GROUP)
@Composable
fun rememberRouteBadgeColors(routeColor: Int?): Pair<Color, Color> {
    val neutralContainer = MaterialTheme.colorScheme.surfaceVariant
    val neutralContent = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(routeColor, dark, neutralContainer, neutralContent) {
        val source = routeColor?.let { Hct.fromInt(it or 0xFF000000.toInt()) }
        if (source == null || source.chroma < ACHROMATIC_CHROMA) {
            neutralContainer to neutralContent
        } else {
            val chroma = min(source.chroma, if (dark) CHIP_MAX_CHROMA_DARK else CHIP_MAX_CHROMA_LIGHT)
            val containerTone = if (dark) CHIP_TONE_DARK else CHIP_TONE_LIGHT
            val contentTone = if (dark) CHIP_ON_TONE_DARK else CHIP_ON_TONE_LIGHT
            Color(Hct.from(source.hue, chroma, containerTone).toInt()) to
                Color(Hct.from(source.hue, chroma, contentTone).toInt())
        }
    }
}

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
 * [color] defaults to [Color.Unspecified] so the badge inherits the ambient content color. Pass a
 * [containerColor] (e.g. from [rememberRouteBadgeColors]) to draw the name on a rounded colored chip
 * filling the fixed-width slot; leave it unspecified for the bare-text badge. [endContainerColor]
 * optionally replaces the rightmost fifth of that background (the arrivals drawer uses this to key
 * the GTFS-colored badge to its stop-focus map color). Set [square] to make the chip a [width]×[width]
 * square tile (a route roundel) with the text shrunk to fit inside it.
 *
 * @param width the fixed width of the badge rectangle (also its height when [square])
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
    containerColor: Color = Color.Unspecified,
    endContainerColor: Color = Color.Unspecified,
    square: Boolean = false,
    textDecoration: TextDecoration = TextDecoration.None
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // On a chip the text sits inside horizontal padding, so shrink-fit against the reduced width so the
    // chip never grows past the fixed slot (which would collide with the neighboring column).
    val textWidth = if (containerColor.isSpecified) width - CHIP_H_PADDING * 2 else width
    // A square tile is [width]×[width]; the text must also fit that height (minus vertical padding),
    // so it shrinks to sit inside the square rather than being capped only by [maxHeight].
    val fitHeight = if (square) width - CHIP_V_PADDING * 2 else maxHeight
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
    val fontSize = remember(text, textWidth, fitHeight, maxFontSize, minFontSize, maxLines, density, baseStyle) {
        with(density) {
            val constraints = if (fitHeight.isSpecified) {
                Constraints(maxWidth = textWidth.roundToPx(), maxHeight = fitHeight.roundToPx())
            } else {
                Constraints(maxWidth = textWidth.roundToPx())
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
        .let {
            when {
                square -> it.height(width)
                maxHeight.isSpecified -> it.heightIn(max = maxHeight)
                else -> it
            }
        }
    Box(boxModifier, contentAlignment = Alignment.Center) {
        val label = @Composable {
            Text(
                text = text,
                color = color,
                style = styleAt(fontSize),
                maxLines = maxLines,
                textDecoration = textDecoration
            )
        }
        if (containerColor.isSpecified) {
            // A rounded chip filling the badge's fixed slot (a standard size across rows, not hugging
            // each name) — the full square when [square], otherwise the width with wrapped height — with
            // the text centered inside.
            val chipModifier = if (square) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
            Surface(color = containerColor, shape = CHIP_SHAPE, modifier = chipModifier) {
                Box(
                    Modifier
                        .drawWithContent {
                            if (endContainerColor.isSpecified) {
                                val endWidth = size.width * CHIP_END_COLOR_FRACTION
                                drawRect(
                                    color = endContainerColor,
                                    topLeft = Offset(size.width - endWidth, 0f),
                                    size = Size(endWidth, size.height),
                                )
                            }
                            drawContent()
                        }
                        .padding(horizontal = CHIP_H_PADDING, vertical = CHIP_V_PADDING),
                    contentAlignment = Alignment.Center,
                ) { label() }
            }
        } else {
            label()
        }
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
                // On a colored chip: the name sits on a rounded surface filling the fixed-width slot,
                // centered. First a GTFS-colored route, then the neutral no-color fallback.
                Text("On a route-color chip:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (green, onGreen) = rememberRouteBadgeColors(0x00A651)
                    LineBadge("40", containerColor = green, color = onGreen)
                    Spacer(Modifier.width(12.dp))
                    val (neutral, onNeutral) = rememberRouteBadgeColors(null)
                    LineBadge("RapidRide A", containerColor = neutral, color = onNeutral)
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
