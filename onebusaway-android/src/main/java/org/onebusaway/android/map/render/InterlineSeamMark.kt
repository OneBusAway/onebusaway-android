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
package org.onebusaway.android.map.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import kotlin.math.cos
import kotlin.math.sin

/**
 * The mark drawn across a route line where a stay-aboard interline changes route (#2127) — the cutover
 * point of [RoutePolyline.startSeam]. Resource-free and flavor-neutral, like [ContinuationBadgeBitmaps], so
 * each renderer only has to wrap the [Bitmap]: on gms as a `CustomCap` on the line's start, on maplibre as a
 * rotated symbol (whose classic polyline annotation has no configurable cap — the same gap
 * `MapLibreRouteEndpointBulbLayer` fills for the endpoint bulbs).
 *
 * Drawn as a **slash**, not a bar or a bulb, and that is the whole point of the mark. The rider already
 * reads a round bulb at each end of a ride, so a bulb-to-bulb join means "get off here and board there";
 * a stay-aboard route change is the opposite instruction and must not look like a near-miss of one. A
 * stroke cut diagonally across the corridor reads as one line ruled through rather than two lines meeting
 * — the [45]  \  [75] the issue asks for.
 *
 * Sized against a **reference stroke width** ([REFERENCE_WIDTH_PX]) rather than in dp: both renderers scale
 * the bitmap by the width of the line it cuts, so the mark keeps its proportions against that line at every
 * zoom rather than needing a schedule (or a camera-settle re-stamp) of its own.
 */
object InterlineSeamMark {

    /**
     * The stroke width, in bitmap pixels, that [bitmap] is drawn for: a renderer scales the bitmap by
     * `lineWidthPx / REFERENCE_WIDTH_PX`, which puts the corridor's two edges at ±half of this about the
     * bitmap's centre. Deliberately a large value — the bitmap is rasterized once and cached, and a mark
     * drawn at reference size would have to be scaled *up* on a dense screen at close zoom.
     */
    const val REFERENCE_WIDTH_PX = 60f

    // Twice the reference width, so the slash has room to overhang the line on both sides: the line's own
    // edges then sit a quarter and three quarters of the way across the bitmap.
    private const val BITMAP_PX = REFERENCE_WIDTH_PX * 2f

    // How far the slash reaches from the line's centre, as a multiple of the line's width. Past 0.5 it
    // overhangs the corridor, which is what makes the cut legible against a line of the same weight.
    private const val HALF_LENGTH_SCALE = 0.85f

    // How far the slash leans off perpendicular. Perpendicular would read as the blunt end of a line — the
    // very thing the mark is drawn to not look like; the lean is what says "ruled through".
    private const val TILT_DEGREES = 30.0

    // The slash's own weight, as a multiple of the line's width: heavy enough to hold at an overview zoom,
    // light enough to leave the line either side of it readable as one continuous ride.
    private const val STROKE_SCALE = 0.3f

    /**
     * The slash bitmap in [color] — the cut line's own case colour, resolved by the renderer against the
     * current theme, so the mark separates itself from the corridor exactly as the ride's hairline case
     * separates that corridor from the basemap (see `mapRouteLineCaseColor`).
     *
     * Drawn for a line running *up* the bitmap (both SDKs orient a line-anchored symbol along the travel
     * direction), and symmetric under a half turn — so it reads the same whichever end of the join a
     * renderer anchors it to.
     */
    fun bitmap(color: Int): Bitmap {
        val bitmap = createBitmap(BITMAP_PX.toInt(), BITMAP_PX.toInt())
        val centre = BITMAP_PX / 2f
        val halfLength = REFERENCE_WIDTH_PX * HALF_LENGTH_SCALE
        // Across the line, and along it: the tilt splits the slash's reach between the two axes.
        val across = (halfLength * cos(Math.toRadians(TILT_DEGREES))).toFloat()
        val along = (halfLength * sin(Math.toRadians(TILT_DEGREES))).toFloat()
        Canvas(bitmap).drawLine(
            centre - across,
            centre - along,
            centre + across,
            centre + along,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = REFERENCE_WIDTH_PX * STROKE_SCALE
                strokeCap = Paint.Cap.ROUND
            }
        )
        return bitmap
    }
}
