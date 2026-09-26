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
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.ceil
import kotlin.math.max
import org.onebusaway.android.R

/**
 * The region-level zone pin (spec §3.1): a 38 dp disc in the service colour with a 3 dp white border
 * and the car glyph, the service name below in 12 sp semibold with a white halo. Drawn with
 * `android.graphics` synchronously, so a viewport of pins is stamped in one pass; callers cache by
 * name and colour.
 */
object ZonePinBitmaps {

    /** The bitmap and the fraction of its height at which the disc's centre sits — the marker anchor. */
    data class ZonePin(val bitmap: Bitmap, val anchorY: Float)

    private const val DISC_DP = 38f
    private const val BORDER_DP = 3f
    private const val GLYPH_DP = 20f
    private const val TEXT_SP = 12f
    private const val HALO_DP = 3f
    private const val GAP_DP = 3f
    private const val MAX_LABEL_DP = 150f

    fun pin(context: Context, name: String, color: Int): ZonePin {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, metrics)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            this.color = Color.BLACK
        }
        val haloPaint = TextPaint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = HALO_DP * density
            strokeJoin = Paint.Join.ROUND
            this.color = Color.WHITE
        }
        val label = TextUtils.ellipsize(name, textPaint, MAX_LABEL_DP * density, TextUtils.TruncateAt.END).toString()
        val disc = DISC_DP * density
        val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val width = ceil(max(disc, textPaint.measureText(label) + 2 * HALO_DP * density) + 2 * density).toInt()
        val height = ceil(disc + GAP_DP * density + textHeight + HALO_DP * density).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val centreX = width / 2f
        val centreY = disc / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = Color.WHITE
        canvas.drawCircle(centreX, centreY, disc / 2f, fill)
        fill.color = color
        canvas.drawCircle(centreX, centreY, disc / 2f - BORDER_DP * density, fill)
        ContextCompat.getDrawable(context, R.drawable.ic_directions_car)?.mutate()?.let { glyph ->
            DrawableCompat.setTint(glyph, Color.WHITE)
            val half = (GLYPH_DP * density / 2f).toInt()
            glyph.setBounds(centreX.toInt() - half, centreY.toInt() - half, centreX.toInt() + half, centreY.toInt() + half)
            glyph.draw(canvas)
        }
        val baseline = disc + GAP_DP * density - textPaint.fontMetrics.ascent
        canvas.drawText(label, centreX, baseline, haloPaint)
        canvas.drawText(label, centreX, baseline, textPaint)
        return ZonePin(bitmap, anchorY = centreY / height)
    }
}
