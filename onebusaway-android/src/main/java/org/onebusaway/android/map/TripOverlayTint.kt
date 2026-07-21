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
package org.onebusaway.android.map

import android.graphics.Color
import kotlin.math.roundToInt
import org.onebusaway.android.extrapolation.TripExtrapolation
import org.onebusaway.android.map.render.BandSegment
import org.onebusaway.android.map.render.TripOverlay

// The display tint applied to the color-free extrapolation when it's drawn as a [TripOverlay]. The
// producer ([TripExtrapolation]) knows nothing about color; choosing the band hue and baking each
// slice's model weight into its alpha is a display decision, so it lives here, applied by the route
// map's selected-vehicle overlay ([RouteMapController]).

/**
 * Produces a color that contrasts with [color] (the route line's color), so the uncertainty band reads
 * against the shape it's drawn over. Rotating the hue 180° works for a saturated line, but a
 * near-grayscale line (gray/black/white) has no meaningful hue to rotate — there we flip the value
 * (dark→white, light→black) instead so the band still stands out.
 */
internal fun contrastingColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color or 0xFF000000.toInt(), hsv)
    if (hsv[1] < 0.1f) {
        hsv[2] = if (hsv[2] < 0.5f) 1f else 0f
    } else {
        hsv[0] = (hsv[0] + 180f) % 360f
    }
    return Color.HSVToColor(hsv)
}

/**
 * Composites the display [bandColorArgb] onto this color-free [TripExtrapolation] to produce the render
 * [TripOverlay]: each band slice's model weight becomes the hue's alpha. Only the band + fast-estimate
 * marker are carried — the route map draws the live vehicle disc and the most-recent-data dot itself (#1752).
 */
internal fun TripExtrapolation.toTripOverlay(bandColorArgb: Int): TripOverlay {
    val baseRgb = bandColorArgb and 0x00FFFFFF
    return TripOverlay(
        fastEstimatePoint = fastEstimatePoint,
        band = band.map { slice ->
            val alpha = (slice.weight.coerceIn(0f, 1f) * 255f).roundToInt()
            BandSegment(slice.points, (alpha shl 24) or baseRgb)
        },
        fixTimeMs = fixTimeMs
    )
}
