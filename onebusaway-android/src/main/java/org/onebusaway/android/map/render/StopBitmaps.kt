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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared builder for the small "dot" bus-stop marker shown at distant zoom (the full-icon ⇄ dot
 * collapse). Lives in src/main so both map flavors share one definition — the Google flavor wraps the
 * bitmap as a `BitmapDescriptor`, maplibre as an `Icon` — mirroring [BikeBitmaps]. Directionless and
 * route-type agnostic; the dot's [color][dot] distinguishes a normal vs focused stop.
 */
object StopBitmaps {
    /**
     * How much larger the focused (selected) dot is drawn than a normal dot, so the selected stop
     * stays visibly larger than its neighbours even in the zoomed-out dot band (#1679). Mirrors the
     * full-icon focus enlargement the flavour factories apply up close (their `FOCUS_ICON_SCALE`).
     */
    const val FOCUSED_DOT_SCALE = 1.5f

    /**
     * How much larger the starred-stop [star] is drawn relative to the base dot/full-icon size, so the
     * star reads clearly as a distinct favorite marker (#1680). Applied to both the full-band and
     * dot-band star sizes by the flavor factories, keeping the two map flavors in sync.
     */
    const val STAR_SIZE_SCALE = 1.75f

    /**
     * The starred-stop star's white outline width, in dp — used for both the full-band and dot-band
     * star. Tuned on device to read like the plain stop circle's border; the circle's *rendered* white
     * ring measures ~1.15dp, but the star's spiky edge reads a touch thinner at that width, so it's bumped
     * up. The factories multiply by display density to get pixels.
     */
    const val STAR_OUTLINE_WIDTH_DP = 1.5f

    /** Scales [bitmap] uniformly by [factor] — shared by the flavor factories' focused/star variants. */
    @JvmStatic
    fun scale(bitmap: Bitmap, factor: Float): Bitmap =
        Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true
        )

    /**
     * Clockwise degrees from north for a stop [direction] string ("N".."NW"), used to rotate the star's
     * direction arrow ([favoriteMarker]). "null"/unknown map to 0° — the caller passes `hasArrow=false`
     * for a directionless stop, so the angle is unused there. Shared so the table lives in one place.
     */
    @JvmStatic
    fun compassAngle(direction: String): Float = when (direction) {
        "NE" -> 45f
        "E" -> 90f
        "SE" -> 135f
        "S" -> 180f
        "SW" -> 225f
        "W" -> 270f
        "NW" -> 315f
        else -> 0f // N (and "null", unused since it has no arrow)
    }

    /**
     * A filled circle in [fillColor] with a thin white outline for contrast against the map, sized
     * from [baseIconPx] (the full stop icon size) so a dense viewport reads as points. [scale]
     * enlarges the dot relative to that base — the focused stop passes [FOCUSED_DOT_SCALE] so its
     * selection reads as larger than surrounding dots.
     */
    @JvmStatic
    @JvmOverloads
    fun dot(baseIconPx: Int, fillColor: Int, scale: Float = 1f): Bitmap {
        val sizePx = max(6, (baseIconPx * 0.5f * scale).roundToInt())
        val bm = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bm)
        val center = sizePx / 2f
        val stroke = max(1f, sizePx / 10f)
        val radius = center - stroke

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        canvas.drawCircle(center, center, radius, fill)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = Color.WHITE
        }
        canvas.drawCircle(center, center, radius, outline)
        return bm
    }

    /**
     * A five-pointed star filled with the same vertical [topColor]→[bottomColor] gradient and white
     * outline as the stop circle, sized to the [sizePx] bounding box, marking a starred (favorite) stop
     * (#1680) — direction- and route-type agnostic, so the caller centers it on the marker. Shared by
     * both flavors like [dot]; used for the dot-band favorite (no direction arrow), while the full-band
     * factories draw the star with an arrow via [favoriteMarker]. [outlineWidthPx] is passed the same
     * circle-matched width the full-band star uses (see [STAR_OUTLINE_WIDTH_DP]).
     */
    @JvmStatic
    fun star(sizePx: Int, topColor: Int, bottomColor: Int, outlineWidthPx: Float): Bitmap {
        val size = max(8, sizePx)
        val bm = createBitmap(size, size)
        drawStar(Canvas(bm), size / 2f, size / 2f, size.toFloat(), topColor, bottomColor, outlineWidthPx)
        return bm
    }

    /**
     * Fills a [size]-wide five-pointed star at ([cx], [cy]) with a vertical [topColor]→[bottomColor]
     * gradient, plus a white outline of [outlineWidthPx] (matched by the caller to the circle's border).
     */
    private fun drawStar(
        canvas: Canvas, cx: Float, cy: Float, size: Float,
        topColor: Int, bottomColor: Int, outlineWidthPx: Float,
    ) {
        // Inset the star tips so the outline stays inside the bounds.
        val inset = max(1f, size / 12f)
        val outerRadius = size / 2f - inset
        // 0.42 gives a classic five-point star (not too spindly, not a blob).
        val innerRadius = outerRadius * 0.42f
        val path = starPath(cx, cy, outerRadius, innerRadius)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                cx, cy - outerRadius, cx, cy + outerRadius,
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, fill)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, outlineWidthPx)
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }
        canvas.drawPath(path, outline)
    }

    /**
     * The full-band starred-stop marker (#1680): an inflated [starDiameterPx] star centered on the
     * marker, with the *normal-sized* direction arrow the other stop markers carry drawn on top,
     * pointing [directionAngleDeg] degrees clockwise from north (ignored when [hasArrow] is false, for a
     * directionless stop). Inflating only the star — not the whole marker — keeps the arrow the same
     * size as on plain stops; the bigger star simply overlaps the arrow's base, which is why the arrow
     * is drawn last (on top).
     *
     * The arrow's shape/position derive from [basePx] (the flavor's normal stop-circle diameter), so it
     * matches that flavor's plain markers — the arrow constants below (`/2`, `/3`, `/12`, `/10`) mirror
     * the plain directional-icon arrow in each flavor's Java factory. That match is by convention, not
     * enforced: if the plain arrow geometry changes, update it here too. The star is centered, so the caller anchors this at
     * the marker center (0.5, 0.5) — the star lands on the stop and the arrow points outward. Shared by
     * both flavors so the two maps draw an identical marker.
     *
     * The star is filled with a vertical [starTopColor]→[starBottomColor] gradient (its own gold hue)
     * and outlined at [starOutlineWidthPx] (the caller passes the circle's white-ring width) — the same
     * gradient-plus-white-outline styling as the stop circle, in the star's colour. The direction arrow
     * keeps the plain markers' [arrowTipColor]→[arrowBaseColor] (theme primary→accent) shading.
     */
    @JvmStatic
    fun favoriteMarker(
        basePx: Int,
        starDiameterPx: Int,
        hasArrow: Boolean,
        directionAngleDeg: Float,
        starTopColor: Int,
        starBottomColor: Int,
        arrowTipColor: Int,
        arrowBaseColor: Int,
        starOutlineWidthPx: Float,
    ): Bitmap {
        val circleRadius = basePx / 2f
        val arrowWidth = basePx / 2f
        val arrowHeight = basePx / 3f
        val cutout = basePx / 12f
        val tuck = basePx / 10f
        // Radial distances from the marker center to the arrow's tip and base, matching the plain
        // marker's arrow-vs-circle geometry (the arrow base tucks slightly under the circle edge).
        val tipDistance = circleRadius + arrowHeight - tuck
        val baseDistance = circleRadius - tuck
        val starRadius = starDiameterPx / 2f

        // Square canvas big enough for whichever reaches further from center — the inflated star or the
        // arrow tip — so the arrow fits at any rotation. +2 leaves room for the anti-aliased outline.
        val reach = max(starRadius, if (hasArrow) tipDistance else 0f)
        val size = ceil(2 * reach).toInt() + 2
        val bm = createBitmap(size, size)
        val canvas = Canvas(bm)
        val center = size / 2f

        drawStar(
            canvas, center, center, starDiameterPx.toFloat(),
            starTopColor, starBottomColor, starOutlineWidthPx
        )

        if (hasArrow) {
            // A north-pointing arrow about the center; rotating the whole canvas turns both the path and
            // its gradient to the compass direction in one step.
            val path = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                moveTo(center, center - tipDistance)                    // tip
                lineTo(center - arrowWidth / 2, center - baseDistance)  // lower left
                lineTo(center, center - baseDistance - cutout)          // cutout notch
                lineTo(center + arrowWidth / 2, center - baseDistance)  // lower right
                lineTo(center, center - tipDistance)
                close()
            }
            canvas.withRotation(directionAngleDeg, center, center) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    // Darkest at the tip, matching the plain markers' arrow shading.
                    shader = LinearGradient(
                        center, center - tipDistance, center, center - tipDistance + arrowHeight,
                        arrowTipColor, arrowBaseColor, Shader.TileMode.MIRROR
                    )
                }
                drawPath(path, fill)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = Color.WHITE
                }
                drawPath(path, stroke)
            }
        }
        return bm
    }

    /** Builds the 10-vertex path of a five-pointed star centered at ([cx], [cy]), tip up. */
    private fun starPath(cx: Float, cy: Float, outerRadius: Float, innerRadius: Float): Path {
        val path = Path()
        // Start at the top tip and alternate outer/inner vertices every 36 degrees.
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = Math.toRadians((i * 36 - 90).toDouble())
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
