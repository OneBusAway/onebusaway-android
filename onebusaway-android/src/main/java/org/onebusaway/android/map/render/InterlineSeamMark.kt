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
import kotlin.math.tan

/**
 * The mark drawn across a route line where a stay-aboard interline changes route (#2127) — the cutover
 * point of [RoutePolyline.startSeam]. Resource-free and flavor-neutral, like [ContinuationBadgeBitmaps], so
 * each renderer only has to wrap the [Bitmap]: on gms as a `CustomCap` on the line's start, on maplibre as a
 * rotated symbol (whose classic polyline annotation has no configurable cap — the same gap
 * `MapLibreRouteEndpointBulbLayer` fills for the endpoint bulbs).
 *
 * It is the **casing of a mitred joint**: the hairline you would see where two route lines are mitred
 * together at an angle, drawn in the line's own case colour and reaching exactly its two edges — nothing
 * added on top of the corridor, just the seam in it made visible. That is why it is a diagonal and not a
 * blunt perpendicular bar: a perpendicular end is what the rider reads as a line *stopping*, which is the
 * one thing a stay-aboard route change must not look like. (A bulb pair already means "get off here and
 * board there"; this is the opposite instruction.)
 *
 * Sized against a **reference stroke width** ([REFERENCE_WIDTH_PX]) rather than in dp: both renderers scale
 * the bitmap by the width of the line it cuts, so the mark keeps its proportions against that line at every
 * zoom rather than needing a schedule (or a camera-settle re-stamp) of its own. Its own weight is therefore
 * proportional too — see [STROKE_DP_AT_FULL_WIDTH] for what that costs at a receded zoom.
 */
object InterlineSeamMark {

    /**
     * The stroke width, in bitmap pixels, that [bitmap] is drawn for: a renderer scales the bitmap by
     * `lineWidthPx / REFERENCE_WIDTH_PX`, which puts the corridor's two edges at ±half of this about the
     * bitmap's centre. Deliberately a large value — the bitmap is rasterized once and cached, and a mark
     * drawn at reference size would have to be scaled *up* on a dense screen at close zoom.
     */
    const val REFERENCE_WIDTH_PX = 60f

    // Square, with room for the tilted slash and its stroke to be drawn without clipping. Only
    // [REFERENCE_WIDTH_PX] decides how the bitmap maps onto a line's width, so the surplus is transparent
    // margin and costs nothing but the (cached, once-per-colour) allocation.
    private const val BITMAP_PX = REFERENCE_WIDTH_PX * 2f

    // How far the slash leans off perpendicular. The lean is what makes it read as a mitre rather than as
    // the end of a line, and it is the reason the two routes' spans look joined at an angle rather than
    // butted together.
    private const val TILT_DEGREES = 30.0

    /**
     * The mark's weight where the line it cuts is at its full width, which for every line that carries one
     * today is [ITINERARY_RIDE_WIDTH_PROFILE]'s close-zoom thickness. A ruled line, in the same family as the
     * cases around these lines ([RouteLineCase.OUTLINE] is 0.75dp per side, [RouteLineCase.SELECTION] 1.5dp)
     * — because that is what it is: the casing of the joint, not a symbol laid over the corridor.
     *
     * Expressed as a ratio of the line's width rather than in absolute dp, since the bitmap scales as a
     * whole. So the mark thins with its line as the camera pulls back — down to half this at the far end of
     * the detail ramp. A case proper deliberately does *not* thin (see [RouteLineCase]); this one does,
     * because holding a constant dp weight inside a uniformly-scaled bitmap would mean re-rasterizing the
     * cap on every camera settle. At overview zoom the ride is 7.5dp of a trip drawn whole, and a seam
     * receding with it reads correctly there.
     */
    private const val STROKE_DP_AT_FULL_WIDTH = 2f

    private val STROKE_SCALE = STROKE_DP_AT_FULL_WIDTH / ITINERARY_RIDE_WIDTH_PROFILE.thicknessDp

    /**
     * The slash bitmap in [color] — the cut line's own case colour, resolved by the renderer against the
     * current theme, so the seam is cased exactly as the corridor around it is (see `mapRouteLineCaseColor`).
     *
     * Drawn for a line running *up* the bitmap (both SDKs orient a line-anchored symbol along the travel
     * direction), and symmetric under a half turn — so it reads the same whichever end of the join a
     * renderer anchors it to.
     */
    fun bitmap(color: Int): Bitmap {
        val bitmap = createBitmap(BITMAP_PX.toInt(), BITMAP_PX.toInt())
        val centre = BITMAP_PX / 2f
        // The slash stops at the line's two edges — half a line width either side of its centre. Its length
        // follows from the tilt rather than being its own knob: what has to be exact is where it *ends*, and
        // a mitre's casing ends on the edge it mitres. Butt caps for the same reason (a round cap would
        // bulge half a stroke past it).
        val across = REFERENCE_WIDTH_PX / 2f
        val along = (across * tan(Math.toRadians(TILT_DEGREES))).toFloat()
        Canvas(bitmap).drawLine(
            centre - across,
            centre - along,
            centre + across,
            centre + along,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = REFERENCE_WIDTH_PX * STROKE_SCALE
                strokeCap = Paint.Cap.BUTT
            }
        )
        return bitmap
    }
}
