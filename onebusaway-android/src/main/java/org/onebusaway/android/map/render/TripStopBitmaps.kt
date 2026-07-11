/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * The route/trip map's scheduled-stop dot bitmaps: a white disc with a gray stroke, plus a
 * filled-center variant for the selected stop (ported from the feature's TripRouteOverlay). The
 * selected (focused) stop is also drawn [FOCUSED_SCALE]× larger — circle, stroke, and center dot
 * scale together — so a selection stands out while a route is shown, matching the focus enlargement
 * nearby stops get ([StopBitmaps.FOCUSED_DOT_SCALE]). Resource-free and flavor-neutral, so the Google
 * adapter wraps it in a `BitmapDescriptor` and maplibre in an `Icon`; the two variants are cached.
 */
object TripStopBitmaps {

    private const val SIZE_PX = 40
    private const val STROKE_PX = 6f
    private val STROKE_COLOR = TripMarkerBitmaps.STROKE_COLOR

    /** The selected (focused) route stop's size multiplier — the app-wide focus enlargement. */
    private const val FOCUSED_SCALE = StopBitmaps.FOCUSED_DOT_SCALE

    private val cache = HashMap<Boolean, Bitmap>()

    /** The dot bitmap for a stop; [selected] adds a filled inner dot and enlarges it [FOCUSED_SCALE]×. */
    fun dot(selected: Boolean): Bitmap = cache.getOrPut(selected) { draw(selected) }

    private fun draw(selected: Boolean): Bitmap {
        // Scale the whole marker (circle + stroke + center dot) uniformly when selected, so the
        // enlargement is a clean 1.5× of the base rather than a bigger circle with the same-width ring.
        val scale = if (selected) FOCUSED_SCALE else 1f
        val sizePx = (SIZE_PX * scale).roundToInt()
        val strokePx = STROKE_PX * scale
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val radius = center - strokePx / 2f

        canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        })
        canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        })
        if (selected) {
            canvas.drawCircle(center, center, radius * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = STROKE_COLOR
                style = Paint.Style.FILL
            })
        }
        return bitmap
    }
}
