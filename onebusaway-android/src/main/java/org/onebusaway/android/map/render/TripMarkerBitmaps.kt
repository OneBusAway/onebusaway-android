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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat

/**
 * Circular trip-marker bitmaps: a dark-stroked, opaque-white-filled disc with a drawable centered
 * inside (ported from the feature's Google-only MapIconUtils.createCircleIcon, lifted to `src/main` so
 * both flavors share it — the Google adapter wraps the Bitmap in a `BitmapDescriptor`, maplibre in an
 * `Icon`). Used for the trip map's extrapolated-vehicle / fast-estimate / last-fix markers. The fill is
 * fully opaque: these markers overlap and move every frame, so a translucent fill makes the blended
 * overlap shimmer. Cached per (drawable, tint).
 */
object TripMarkerBitmaps {

    /** The stroke + tint color (gray); callers tint a light glyph (e.g. the signal indicator) with it. */
    const val STROKE_COLOR = 0xFF616161.toInt()

    private const val ICON_SIZE_DP = 28
    private const val ICON_PADDING_DP = 4
    private const val STROKE_WIDTH_DP = 2f
    // Fully opaque: overlapping, per-frame-moving estimate markers blend visibly with a translucent fill.
    private const val FILL_COLOR = 0xFFFFFFFF.toInt()

    private val cache = HashMap<Long, Bitmap>()

    /**
     * A circular marker for [drawableRes] centered on the disc. [tintColor] tints the glyph when
     * non-zero (use it for light/white glyphs like the signal indicator); 0 keeps its intrinsic color.
     */
    fun circle(context: Context, drawableRes: Int, tintColor: Int = 0): Bitmap {
        val key = (drawableRes.toLong() shl 32) or (tintColor.toLong() and 0xFFFFFFFFL)
        return cache.getOrPut(key) { draw(context, drawableRes, tintColor) }
    }

    private fun draw(context: Context, drawableRes: Int, tintColor: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (ICON_SIZE_DP * density).toInt()
        val padding = (ICON_PADDING_DP * density).toInt()
        val strokeWidth = STROKE_WIDTH_DP * density
        val center = sizePx / 2f

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawCircle(center, center, center - strokeWidth / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        })
        canvas.drawCircle(center, center, center - strokeWidth, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FILL_COLOR
            style = Paint.Style.FILL
        })

        ContextCompat.getDrawable(context, drawableRes)?.apply {
            if (tintColor != 0) setTint(tintColor)
            setBounds(padding, padding, sizePx - padding, sizePx - padding)
            draw(canvas)
        }
        return bitmap
    }
}
