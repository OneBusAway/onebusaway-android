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
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
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
    fun scale(bitmap: Bitmap, factor: Float): Bitmap = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * factor).toInt(),
        (bitmap.height * factor).toInt(),
        true
    )

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
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        topColor: Int,
        bottomColor: Int,
        outlineWidthPx: Float
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
                cx,
                cy - outerRadius,
                cx,
                cy + outerRadius,
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP
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
        starOutlineWidthPx: Float
    ): Bitmap {
        val circleRadius = basePx / 2f
        val starRadius = starDiameterPx / 2f

        // Square canvas big enough for whichever reaches further from center — the inflated star or the
        // arrow tip — so the arrow fits at any rotation. +2 leaves room for the anti-aliased outline.
        val reach = max(starRadius, if (hasArrow) directionArrowReach(circleRadius) else 0f)
        val size = ceil(2 * reach).toInt() + 2
        val bm = createBitmap(size, size)
        val canvas = Canvas(bm)
        val center = size / 2f

        drawStar(
            canvas,
            center,
            center,
            starDiameterPx.toFloat(),
            starTopColor,
            starBottomColor,
            starOutlineWidthPx
        )

        if (hasArrow) {
            drawDirectionArrow(canvas, center, center, circleRadius, arrowTipColor, arrowBaseColor, directionAngleDeg)
        }
        return bm
    }

    /**
     * How far the tip of a [drawDirectionArrow] reaches from the circle center, for a circle of
     * [circleRadiusPx] with the arrow scaled by [arrowScale] and pushed out by [radialOffsetPx]. Callers
     * use it to size a bitmap that fits the arrow at any rotation (it overhangs the circle on one side).
     */
    @JvmStatic
    @JvmOverloads
    fun directionArrowReach(circleRadiusPx: Float, arrowScale: Float = 1f, radialOffsetPx: Float = 0f): Float {
        val baseDistance = circleRadiusPx - circleRadiusPx / 5f + radialOffsetPx
        val arrowHeight = circleRadiusPx * 2f / 3f * arrowScale
        return baseDistance + arrowHeight
    }

    /**
     * Draws the shared stop direction arrow — a north-pointing arrowhead tucked at the edge of a circle
     * of [circleRadiusPx] centered at ([cx], [cy]), rotated [angleDeg] clockwise, filled with the
     * [arrowTipColor]→[arrowBaseColor] gradient (darkest at the tip; pass the same colour twice for a
     * solid fill). It matches the arrowhead the plain directional stop markers ([directionalStopMarker])
     * draw, and is the shared builder the starred marker ([favoriteMarker]) and the focused route-stop
     * (#1985) render through. [arrowScale] enlarges the arrowhead and [radialOffsetPx] pushes it farther
     * from center — the focused route-stop passes both so its arrow reads bigger and clears the enlarged
     * selected circle. Its outer stroke is [outlineColor] at [outlineWidthPx] (default a thin white edge;
     * the focused route-stop passes the marker's own ring colour and width so the arrow matches it). The
     * caller centers the circle and sizes the canvas from [directionArrowReach] so the arrow fits at any
     * [angleDeg].
     */
    @JvmStatic
    @JvmOverloads
    fun drawDirectionArrow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        circleRadiusPx: Float,
        arrowTipColor: Int,
        arrowBaseColor: Int,
        angleDeg: Float,
        arrowScale: Float = 1f,
        radialOffsetPx: Float = 0f,
        outlineColor: Int = Color.WHITE,
        outlineWidthPx: Float = 1f
    ) {
        val arrowWidth = circleRadiusPx * arrowScale
        val arrowHeight = circleRadiusPx * 2f / 3f * arrowScale
        val cutout = circleRadiusPx / 6f * arrowScale
        // Radial distances to the arrow's base and tip; the base tucks under the circle edge (a fifth of
        // the radius), then [radialOffsetPx] pushes the whole arrow outward. The tip distance is the
        // reach the caller sizes the canvas from — single-sourced so drawing and sizing can't diverge.
        val baseDistance = circleRadiusPx - circleRadiusPx / 5f + radialOffsetPx
        val tipDistance = directionArrowReach(circleRadiusPx, arrowScale, radialOffsetPx)
        // A north-pointing arrow about the center; rotating the whole canvas turns both the path and its
        // gradient to the compass direction in one step.
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(cx, cy - tipDistance) // tip
            lineTo(cx - arrowWidth / 2, cy - baseDistance) // lower left
            lineTo(cx, cy - baseDistance - cutout) // cutout notch
            lineTo(cx + arrowWidth / 2, cy - baseDistance) // lower right
            lineTo(cx, cy - tipDistance)
            close()
        }
        canvas.withRotation(angleDeg, cx, cy) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                // Darkest at the tip, matching the plain markers' arrow shading.
                shader = LinearGradient(
                    cx,
                    cy - tipDistance,
                    cx,
                    cy - tipDistance + arrowHeight,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
            }
            drawPath(path, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = outlineWidthPx
                // Rounded joins so a thicker stroke doesn't spike out at the arrow's sharp tip.
                strokeJoin = Paint.Join.ROUND
                color = outlineColor
            }
            drawPath(path, stroke)
        }
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

    /**
     * The fraction to shift a directional stop marker's anchor off-center (anchor = `0.5 ± this`) so the
     * pin tip lands on the circle center, for a base icon size of [baseIconPx]. Derived from the same
     * arrow overhang [directionalStopMarker] draws — the arrow's `/3` height minus its `/10` tuck — so a
     * flavor's anchors track any change to that geometry instead of re-deriving the ratios themselves.
     */
    @JvmStatic
    fun anchorPercentOffset(baseIconPx: Int): Float {
        val buffer = baseIconPx / 3f - baseIconPx / 10f
        return buffer / (baseIconPx + buffer) * 0.5f
    }

    /**
     * Draws a directional bus-stop marker: the [shape] circle drawable with a gradient direction arrow
     * ([arrowTipColor] at the tip → [arrowBaseColor] at the base) pointing [direction], onto a bitmap
     * sized [circlePx] plus the arrow's overhang. [StopDirection.NONE] draws the bare circle with no
     * arrow. Shared by both map flavors' stop-icon factories — the Google flavor passes a glyph-scaled
     * [circlePx] and stamps a route glyph via [onCircle]; maplibre uses it as-is.
     *
     * [onCircle], if given, is invoked once the circle + arrow are drawn, with the same [Canvas] and the
     * circle's bounds within the bitmap — so a caller can draw on top (e.g. Google's centered route
     * glyph) without relying on reading back the mutated [shape]'s bounds after this returns.
     */
    @JvmStatic
    fun directionalStopMarker(
        shape: Drawable,
        direction: StopDirection,
        circlePx: Int,
        arrowTipColor: Int,
        arrowBaseColor: Int,
        onCircle: ((Canvas, Rect) -> Unit)? = null
    ): Bitmap {
        val arrowWidthPx = circlePx / 2f
        val arrowHeightPx = circlePx / 3f
        val arrowSpacingReductionPx = circlePx / 10f
        val buffer = arrowHeightPx - arrowSpacingReductionPx

        val arrowPaintFill = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val bm: Bitmap
        val c: Canvas
        val directionAngle: Float // 0-360 degrees
        val rotationX: Float // Point around which to rotate the arrow
        val rotationY: Float

        when (direction) {
            StopDirection.NONE -> {
                // Don't draw the arrow
                bm = createBitmap(circlePx, circlePx)
                c = Canvas(bm)
                shape.setBounds(0, 0, bm.width, bm.height)
                shape.draw(c)
                onCircle?.invoke(c, shape.bounds)
                return bm
            }

            StopDirection.NORTH -> {
                directionAngle = 0f
                bm = createBitmap(circlePx, (circlePx + buffer).toInt())
                c = Canvas(bm)
                shape.setBounds(0, buffer.toInt(), circlePx, bm.height)
                // Shade with darkest color at tip of arrow
                arrowPaintFill.shader = LinearGradient(
                    (bm.width / 2).toFloat(),
                    0f,
                    (bm.width / 2).toFloat(),
                    arrowHeightPx,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                // For NORTH, no rotation occurs - use center of image anyway so we have some value
                rotationX = bm.width / 2f
                rotationY = bm.height / 2f
            }

            StopDirection.NORTH_WEST -> {
                directionAngle = 315f // Arrow is drawn N, rotate 315 degrees
                bm = createBitmap((circlePx + buffer).toInt(), (circlePx + buffer).toInt())
                c = Canvas(bm)
                shape.setBounds(buffer.toInt(), buffer.toInt(), bm.width, bm.height)
                // Shade with darkest color at tip of arrow
                arrowPaintFill.shader = LinearGradient(
                    0f,
                    0f,
                    buffer,
                    buffer,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                // Rotate around below coordinates (trial and error)
                rotationX = circlePx / 2f + buffer / 2f
                rotationY = bm.height / 2f - buffer / 2f
            }

            StopDirection.WEST -> {
                directionAngle = 0f // Arrow is drawn pointing West, so no rotation
                bm = createBitmap((circlePx + buffer).toInt(), circlePx)
                c = Canvas(bm)
                shape.setBounds(buffer.toInt(), 0, bm.width, bm.height)
                arrowPaintFill.shader = LinearGradient(
                    0f,
                    (bm.height / 2).toFloat(),
                    arrowHeightPx,
                    (bm.height / 2).toFloat(),
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                // For WEST
                rotationX = bm.height / 2f
                rotationY = bm.height / 2f
            }

            StopDirection.SOUTH_WEST -> {
                directionAngle = 225f // Arrow is drawn N, rotate 225 degrees
                bm = createBitmap((circlePx + buffer).toInt(), (circlePx + buffer).toInt())
                c = Canvas(bm)
                shape.setBounds(buffer.toInt(), 0, bm.width, circlePx)
                arrowPaintFill.shader = LinearGradient(
                    0f,
                    bm.height.toFloat(),
                    buffer,
                    bm.height - buffer,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                // Rotate around below coordinates (trial and error)
                rotationX = bm.width / 2f - buffer / 4f
                rotationY = circlePx / 2f + buffer / 4f
            }

            StopDirection.SOUTH -> {
                directionAngle = 180f // Arrow is drawn N, rotate 180 degrees
                bm = createBitmap(circlePx, (circlePx + buffer).toInt())
                c = Canvas(bm)
                shape.setBounds(0, 0, bm.width, (bm.height - buffer).toInt())
                arrowPaintFill.shader = LinearGradient(
                    (bm.width / 2).toFloat(),
                    bm.height.toFloat(),
                    (bm.width / 2).toFloat(),
                    bm.height - arrowHeightPx,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                rotationX = bm.width / 2f
                rotationY = bm.height / 2f
            }

            StopDirection.SOUTH_EAST -> {
                directionAngle = 135f // Arrow is drawn N, rotate 135 degrees
                bm = createBitmap((circlePx + buffer).toInt(), (circlePx + buffer).toInt())
                c = Canvas(bm)
                shape.setBounds(0, 0, circlePx, circlePx)
                arrowPaintFill.shader = LinearGradient(
                    bm.width.toFloat(),
                    bm.height.toFloat(),
                    bm.width - buffer,
                    bm.height - buffer,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                // Rotate around below coordinates (trial and error)
                rotationX = (circlePx + buffer / 2) / 2f
                rotationY = bm.height / 2f
            }

            StopDirection.EAST -> {
                directionAngle = 180f // Arrow is drawn pointing West, so rotate 180
                bm = createBitmap((circlePx + buffer).toInt(), circlePx)
                c = Canvas(bm)
                shape.setBounds(0, 0, circlePx, bm.height)
                arrowPaintFill.shader = LinearGradient(
                    bm.width.toFloat(),
                    (bm.height / 2).toFloat(),
                    bm.width - arrowHeightPx,
                    (bm.height / 2).toFloat(),
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                rotationX = bm.width / 2f
                rotationY = bm.height / 2f
            }

            StopDirection.NORTH_EAST -> {
                directionAngle = 45f // Arrow is drawn pointing N, so rotate 45 degrees
                bm = createBitmap((circlePx + buffer).toInt(), (circlePx + buffer).toInt())
                c = Canvas(bm)
                shape.setBounds(0, buffer.toInt(), circlePx, bm.height)
                // Shade with darkest color at tip of arrow
                arrowPaintFill.shader = LinearGradient(
                    bm.width.toFloat(),
                    0f,
                    bm.width - buffer,
                    buffer,
                    arrowTipColor,
                    arrowBaseColor,
                    Shader.TileMode.MIRROR
                )
                // Rotate around middle of circle
                rotationX = circlePx / 2f
                rotationY = bm.height - circlePx / 2f
            }
        }

        shape.draw(c)

        /*
         * Draw the arrow - all dimensions should be relative to circlePx so the arrow is drawn the same
         * size for all orientations
         */
        // Height of the cutout in the bottom of the triangle that makes it an arrow (0=triangle)
        val cutoutHeight = circlePx / 12f
        var x1 = 0f // Tip of arrow
        var y1 = 0f
        var x2 = 0f // lower left
        var y2 = 0f
        var x3 = 0f // cutout in arrow bottom
        var y3 = 0f
        var x4 = 0f // lower right
        var y4 = 0f

        when (direction) {
            StopDirection.NORTH, StopDirection.SOUTH, StopDirection.NORTH_EAST,
            StopDirection.SOUTH_EAST, StopDirection.NORTH_WEST, StopDirection.SOUTH_WEST -> {
                // Arrow is drawn pointing NORTH
                // Tip of arrow
                x1 = circlePx / 2f
                y1 = 0f
                // lower left
                x2 = (circlePx / 2f) - (arrowWidthPx / 2)
                y2 = arrowHeightPx
                // cutout in arrow bottom
                x3 = circlePx / 2f
                y3 = arrowHeightPx - cutoutHeight
                // lower right
                x4 = (circlePx / 2f) + (arrowWidthPx / 2)
                y4 = arrowHeightPx
            }

            StopDirection.EAST, StopDirection.WEST -> {
                // Arrow is drawn pointing WEST
                // Tip of arrow
                x1 = 0f
                y1 = circlePx / 2f
                // lower left
                x2 = arrowHeightPx
                y2 = (circlePx / 2f) - (arrowWidthPx / 2)
                // cutout in arrow bottom
                x3 = arrowHeightPx - cutoutHeight
                y3 = circlePx / 2f
                // lower right
                x4 = arrowHeightPx
                y4 = (circlePx / 2f) + (arrowWidthPx / 2)
            }

            StopDirection.NONE -> Unit // handled above (returns early); unreachable here
        }

        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x3, y3)
            lineTo(x4, y4)
            lineTo(x1, y1)
            close()
        }

        // Rotate arrow around (rotationX, rotationY) point
        val matrix = Matrix()
        matrix.postRotate(directionAngle, rotationX, rotationY)
        path.transform(matrix)

        c.drawPath(path, arrowPaintFill)
        c.drawPath(path, arrowStrokePaint)

        onCircle?.invoke(c, shape.bounds)
        return bm
    }

    /** White outline stroked around the direction arrow for contrast against the map. */
    private val arrowStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        isAntiAlias = true
    }
}
