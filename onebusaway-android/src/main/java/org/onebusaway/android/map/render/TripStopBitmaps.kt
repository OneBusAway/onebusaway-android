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

/**
 * The trip-focus map's scheduled-stop dot bitmaps: a white disc with a gray stroke, plus a
 * filled-center variant for the selected stop (ported from the feature's TripRouteOverlay). Resource-
 * free and flavor-neutral, so the Google adapter wraps it in a `BitmapDescriptor` and maplibre in an
 * `Icon`; the two variants are cached.
 */
object TripStopBitmaps {

    private const val SIZE_PX = 40
    private const val STROKE_PX = 6f
    private val STROKE_COLOR = TripMarkerBitmaps.STROKE_COLOR

    private val cache = HashMap<Boolean, Bitmap>()

    /** The dot bitmap for a stop; [selected] adds a filled inner dot. */
    fun dot(selected: Boolean): Bitmap = cache.getOrPut(selected) { draw(selected) }

    private fun draw(selected: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = SIZE_PX / 2f
        val radius = center - STROKE_PX / 2f

        canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        })
        canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = STROKE_PX
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
