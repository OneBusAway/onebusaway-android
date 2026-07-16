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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.google.android.material.color.utilities.Hct

/**
 * Bitmaps for the route-continuation overlay (#1691): the tappable pill badge showing a route short
 * name, and the arrowhead terminating the continuation line. Resource-free and flavor-neutral (like
 * [BikeBitmaps], so a flavor renderer just wraps the [Bitmap] as a marker icon.
 *
 * A prototype-quality first pass: legible + tappable, not a final visual design.
 */
object ContinuationBadgeBitmaps {

    private const val TEXT_SIZE_PX = 38f
    private const val HORIZONTAL_PADDING_PX = 20f
    private const val VERTICAL_PADDING_PX = 12f
    private const val CORNER_RADIUS_PX = 22f
    private const val OUTLINE_WIDTH_DP = 2f
    private const val OUTLINE_TONE_LIGHT = 35.0
    private const val OUTLINE_TONE_DARK = 85.0

    private const val ARROW_WIDTH_PX = 56f
    private const val ARROW_HEIGHT_PX = 60f
    private const val ARROW_STROKE_PX = 4f

    /**
     * The badge bitmap for [routeShortName] — sized to fit the text, a rounded-rect pill filled with
     * [color] (the continuation line's own color). Text is black or white, whichever contrasts better
     * with [color], so the badge stays legible across the full range of GTFS route colors.
     */
    fun badge(routeShortName: String, color: Int, density: Float, darkMode: Boolean): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = legibleTextColor(color)
            textSize = TEXT_SIZE_PX
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val metrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(routeShortName)
        val width = (textWidth + HORIZONTAL_PADDING_PX * 2).coerceAtLeast(CORNER_RADIUS_PX * 2)
        val height = (metrics.descent - metrics.ascent) + VERTICAL_PADDING_PX * 2

        val bitmap = createBitmap(width.toInt(), height.toInt())
        val canvas = Canvas(bitmap)
        val outlineWidth = OUTLINE_WIDTH_DP * density
        val badgeBounds = RectF(
            outlineWidth / 2f,
            outlineWidth / 2f,
            width - outlineWidth / 2f,
            height - outlineWidth / 2f,
        )
        canvas.drawRoundRect(
            badgeBounds,
            CORNER_RADIUS_PX,
            CORNER_RADIUS_PX,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
                style = Paint.Style.FILL
            },
        )
        canvas.drawRoundRect(
            badgeBounds,
            CORNER_RADIUS_PX,
            CORNER_RADIUS_PX,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = routeBadgeOutlineColor(color, darkMode)
                style = Paint.Style.STROKE
                strokeWidth = outlineWidth
            },
        )
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(routeShortName, width / 2f, baseline, textPaint)
        return bitmap
    }

    /** Black or white, whichever contrasts better against [background] by relative luminance. */
    private fun legibleTextColor(background: Int): Int {
        val luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }

    /**
     * The arrowhead bitmap terminating a continuation line, filled with [color] (the line's own color)
     * and outlined in white for contrast against any basemap. Drawn tip-up (bearing 0°); the renderer
     * anchors it bottom-center at the line's end point and rotates it to the line's travel bearing, so
     * the tip points onward from that point.
     */
    fun arrow(color: Int): Bitmap {
        val bitmap = createBitmap(ARROW_WIDTH_PX.toInt(), ARROW_HEIGHT_PX.toInt())
        val canvas = Canvas(bitmap)
        val path = Path().apply {
            moveTo(ARROW_WIDTH_PX / 2f, 0f)
            lineTo(ARROW_WIDTH_PX, ARROW_HEIGHT_PX)
            lineTo(0f, ARROW_HEIGHT_PX)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = ARROW_STROKE_PX
                strokeJoin = Paint.Join.ROUND
            },
        )
        return bitmap
    }

    /** Theme-aware casing that retains the badge color's HCT hue/chroma and shifts only its tone. */
    @SuppressLint("RestrictedApi") // Material Components' vendored color-science utility.
    internal fun routeBadgeOutlineColor(color: Int, darkMode: Boolean): Int {
        val source = Hct.fromInt(color or 0xFF000000.toInt())
        val tone = if (darkMode) OUTLINE_TONE_DARK else OUTLINE_TONE_LIGHT
        return Hct.from(source.hue, source.chroma, tone).toInt()
    }
}
