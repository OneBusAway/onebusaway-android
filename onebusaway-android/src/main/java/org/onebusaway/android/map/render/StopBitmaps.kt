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
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared builder for the small "dot" bus-stop marker shown at distant zoom (the full-icon ⇄ dot
 * collapse). Lives in src/main so both map flavors share one definition — the Google flavor wraps the
 * bitmap as a `BitmapDescriptor`, maplibre as an `Icon` — mirroring [BikeBitmaps]. Directionless and
 * route-type agnostic; the dot's [color][dot] distinguishes a normal vs focused stop.
 */
object StopBitmaps {
    /**
     * A filled circle in [fillColor] with a thin white outline for contrast against the map, sized
     * from [baseIconPx] (the full stop icon size) so a dense viewport reads as points.
     */
    @JvmStatic
    fun dot(baseIconPx: Int, fillColor: Int): Bitmap {
        val sizePx = max(6, (baseIconPx * 0.5f).roundToInt())
        val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
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
}
