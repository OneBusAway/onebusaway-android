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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import org.onebusaway.android.R

/**
 * The marker at the head of the rider's parked trip plan (#2053): the app's accent teardrop carrying the
 * push-pin glyph, so it reads as *the thing you pinned* rather than as one more transit object.
 *
 * Built the way [BikeBitmaps] builds its station pin — `pin_base` tinted, glyph centred on the head, tip
 * at the bottom — so the two sit on one map without looking like they came from different apps. Cached
 * in a single field because there is only ever one pinned trip, and so only ever one of these.
 */
object PinnedTripBitmaps {

    /** Side of the pin, matching the bikeshare station pin so the map's pins are one size. */
    private const val SIZE_DP = 40f

    /** Glyph side in `pin_base`'s 24-unit grid, sized to sit inside the teardrop's head. */
    private const val GLYPH_SIZE = 11f

    private var cached: Bitmap? = null

    fun pin(context: Context): Bitmap = cached ?: build(context).also { cached = it }

    private fun build(context: Context): Bitmap {
        val scale = context.resources.displayMetrics.density * SIZE_DP / MarkerRendering.GRID
        val sizePx = (MarkerRendering.GRID * scale).toInt()
        val bitmap = createBitmap(sizePx, sizePx)
        MarkerRendering.drawPinAndGlyph(
            Canvas(bitmap),
            context,
            sizePx,
            scale,
            pinColor = ContextCompat.getColor(context, R.color.theme_accent),
            glyphRes = R.drawable.ic_pin_filled,
            glyphColor = Color.WHITE,
            glyphSize = GLYPH_SIZE
        )
        return bitmap
    }
}
