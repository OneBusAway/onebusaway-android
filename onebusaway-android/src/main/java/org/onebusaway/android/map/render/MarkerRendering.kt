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
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.onebusaway.android.R

/**
 * Shared low-level map-marker drawing, used by the flavor-neutral bitmap factories ([VehicleBitmaps],
 * [BikeBitmaps]) and the Google-flavor [StopIconFactory][org.onebusaway.android.map.googlemapsv2.StopIconFactory]
 * / renderer, so the "rasterize a drawable" and "pin_base + centered glyph" operations live in one place.
 */
object MarkerRendering {

    /** pin_base is authored on a 24-unit grid; marker geometry is in those units. */
    const val GRID = 24f

    /** The pin_base head center (grid units) — where the mode/vehicle glyph is centered. */
    const val HEAD_CX = 12f
    const val HEAD_CY = 8f

    /** 8-way unit offsets used to stamp a black outline around an element (a cheap dilate). */
    private val OUTLINE_OFFSETS = arrayOf(
        floatArrayOf(-1f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, -1f), floatArrayOf(0f, 1f),
        floatArrayOf(-0.7f, -0.7f), floatArrayOf(0.7f, -0.7f), floatArrayOf(-0.7f, 0.7f), floatArrayOf(0.7f, 0.7f),
    )

    /**
     * Rasterizes [resId] into a square [sizePx] bitmap, optionally recolored by [tint] and inset by
     * [insetPx] on every side (a positive inset shrinks the artwork; a negative one zooms/crops it).
     */
    @JvmStatic
    @JvmOverloads
    fun rasterize(context: Context, @DrawableRes resId: Int, sizePx: Int, tint: Int? = null, insetPx: Int = 0): Bitmap {
        val drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
        if (tint != null) drawable.setTint(tint)
        val bitmap = createBitmap(sizePx, sizePx)
        drawable.setBounds(insetPx, insetPx, sizePx - insetPx, sizePx - insetPx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /**
     * Draws a pin_base teardrop tinted [pinColor] filling `[0,contentPx]` on [canvas], then a
     * [glyphRes] glyph tinted [glyphColor] centered on the head at [glyphSize] grid units. When
     * [outline] > 0 each is given a black hairline outline of that width. The heading arrow (vehicles)
     * is layered on top by the caller.
     */
    fun drawPinAndGlyph(
        canvas: Canvas,
        context: Context,
        contentPx: Int,
        scale: Float,
        pinColor: Int,
        @DrawableRes glyphRes: Int,
        glyphColor: Int,
        glyphSize: Float,
        outline: Float,
    ) {
        val pin = ContextCompat.getDrawable(context, R.drawable.pin_base)!!.mutate()
        pin.setBounds(0, 0, contentPx, contentPx)
        drawOutlined(canvas, pin, outline, pinColor)

        val glyph = ContextCompat.getDrawable(context, glyphRes)!!.mutate()
        val half = glyphSize / 2f
        glyph.setBounds(
            ((HEAD_CX - half) * scale).toInt(), ((HEAD_CY - half) * scale).toInt(),
            ((HEAD_CX + half) * scale).toInt(), ((HEAD_CY + half) * scale).toInt(),
        )
        drawOutlined(canvas, glyph, outline, glyphColor)
    }

    /** Draws [drawable] tinted [fill], preceded (when [outline] > 0) by a black-outline dilate. */
    fun drawOutlined(canvas: Canvas, drawable: Drawable, outline: Float, fill: Int) {
        if (outline > 0f) {
            drawable.setTint(Color.BLACK)
            stampOffsets(canvas, outline) { drawable.draw(canvas) }
        }
        drawable.setTint(fill)
        drawable.draw(canvas)
    }

    /** Runs [draw] once per [OUTLINE_OFFSETS] entry, translated by [outline] — the black-outline dilate. */
    fun stampOffsets(canvas: Canvas, outline: Float, draw: () -> Unit) {
        for (o in OUTLINE_OFFSETS) {
            canvas.withTranslation(o[0] * outline, o[1] * outline) {
                draw()
            }
        }
    }
}
