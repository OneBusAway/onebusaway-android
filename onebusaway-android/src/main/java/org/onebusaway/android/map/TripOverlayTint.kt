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
// slice's model weight into its alpha is a display decision, so it lives here, shared by the trip-focus
// map ([TripFocusController]) and the route map's selected-vehicle overlay ([RouteMapController]).

/**
 * Shifts hue by 180° to produce a color that contrasts with [color] (the route line's color), so the
 * uncertainty band reads against the shape it's drawn over.
 */
internal fun contrastingColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color or 0xFF000000.toInt(), hsv)
    hsv[0] = (hsv[0] + 180f) % 360f
    return Color.HSVToColor(hsv)
}

/**
 * Composites the display [bandColorArgb] onto this color-free [TripExtrapolation] to produce the render
 * [TripOverlay]: each band slice's model weight becomes the hue's alpha.
 *
 * [includeMarkers] false drops the best-estimate marker and the data-age dot, which the route map
 * already draws itself (the live vehicle disc, and the most-recent-data dot it shows on selection), so a
 * selected vehicle there gains only the band + fast-estimate marker (#1752). The trip-focus map keeps
 * them (the default).
 */
internal fun TripExtrapolation.toTripOverlay(
    bandColorArgb: Int,
    includeMarkers: Boolean = true,
): TripOverlay {
    val baseRgb = bandColorArgb and 0x00FFFFFF
    return TripOverlay(
        vehiclePoint = if (includeMarkers) vehiclePoint else null,
        fastEstimatePoint = fastEstimatePoint,
        band = band.map { slice ->
            val alpha = (slice.weight.coerceIn(0f, 1f) * 255f).roundToInt()
            BandSegment(slice.points, (alpha shl 24) or baseRgb)
        },
        dataAge = if (includeMarkers) dataAge else null,
        fixTimeMs = fixTimeMs,
    )
}
