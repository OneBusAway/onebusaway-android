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
import android.graphics.RectF
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import org.onebusaway.android.R
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalLayer

/**
 * Flavor-neutral generation of the rental marker bitmaps (#2168) — the small dot, the big pins, and
 * the range label beneath one — so the Google flavor wraps them as `BitmapDescriptor`s and maplibre
 * as `Icon`s. Descended from `BikeBitmaps`, which drew exactly two big pins (a dock and a
 * "floating bike") in one navy.
 *
 * Big markers are composited from [pin_base][R.drawable.pin_base] tinted the layer's colour plus a
 * white glyph, rather than decoding a pre-rendered raster. The teardrop fills the bitmap edge-to-edge
 * with the tip at the bottom, matching the former rasters so the renderers' marker anchors are
 * unchanged — see [RentalIcon.anchorV] for the one case that moves them.
 */
object RentalBitmaps {

    /** Big markers fill a square this many dp on a side (the former raster's size). */
    private const val BIG_SIZE_DP = 32f

    /** The glyph's 24-grid box (its artwork fills ~70% of this). */
    private const val GLYPH_SIZE = 11f

    /** The range label's type size and its padding, in dp. */
    private const val LABEL_TEXT_DP = 10f
    private const val LABEL_PADDING_DP = 3f

    /** Gap between the pin's tip and the label chip, in dp. */
    private const val LABEL_GAP_DP = 1f

    // The unlabelled icons never vary, so cache them once — the maplibre renderer clears + redraws
    // every marker on each snapshot, so without this it would re-render these per marker per render.
    private var sSmall: Bitmap? = null
    private val bigCache = HashMap<Pair<RentalLayer, RentalKind>, Bitmap>()

    /**
     * Labelled markers vary per vehicle, so they get a bounded cache instead: a viewport holds at most
     * the density budget's worth of markers and their range strings collapse to a few dozen distinct
     * values, so this is sized to hold a screen's worth without growing without limit.
     */
    private val labelledCache = LruCache<String, Bitmap>(128)

    /**
     * A marker bitmap and where on it the point actually is.
     *
     * [anchorV] is the vertical fraction at which the pin's **tip** sits — 1f for a plain pin (tip at
     * the bottom edge, the platform default) and less than that when a label hangs below it. The
     * Google flavor passes it to `MarkerOptions.anchor`; the maplibre flavor centers every icon on its
     * point and so ignores it, which is why [labelled] pads the bitmap symmetrically: the pin's centre
     * stays the bitmap's centre, so a labelled marker sits exactly where an unlabelled one would there.
     */
    data class RentalIcon(val bitmap: Bitmap, val anchorV: Float)

    /** The small dot shown in the mid-zoom band, drawn from the [bike_marker_small][R.drawable.bike_marker_small] vector. */
    fun small(context: Context): Bitmap = sSmall ?: run {
        val px = context.resources.getDimensionPixelSize(R.dimen.bikeshare_small_marker_size)
        MarkerRendering.rasterize(context, R.drawable.bike_marker_small, px).also { sSmall = it }
    }

    /**
     * The big pin for a place on [layer] of [kind]: the layer's colour, with a dock glyph for a
     * station and the layer's own vehicle glyph for a free-floating one. That pairing is the fix for
     * the defect this issue names — before it, a parked scooter drew the same dock pin a docking
     * station did.
     */
    fun big(context: Context, layer: RentalLayer, kind: RentalKind): Bitmap = bigCache.getOrPut(layer to kind) { bigMarker(context, layer.pinColor(context), glyphFor(layer, kind)) }

    /**
     * [base] with [label] on a chip beneath it — the fuel/range readout the rental layer shows above
     * its label zoom threshold.
     *
     * The bitmap is padded above the pin by exactly the label block's height so the pin stays
     * vertically centred (see [RentalIcon.anchorV]).
     */
    fun labelled(context: Context, base: Bitmap, label: String, cacheKey: String): RentalIcon {
        val density = context.resources.displayMetrics.density
        val paint = labelPaint(density)
        val padPx = LABEL_PADDING_DP * density
        val gapPx = LABEL_GAP_DP * density
        val chipHeight = paint.textSize + padPx * 2f
        val blockPx = gapPx + chipHeight
        val height = (base.height + blockPx * 2f).toInt()
        val chipWidth = paint.measureText(label) + padPx * 2f
        val width = maxOf(base.width.toFloat(), chipWidth).toInt()

        val bitmap = labelledCache.get(cacheKey) ?: createBitmap(width, height).also { out ->
            val canvas = Canvas(out)
            val left = (width - base.width) / 2f
            canvas.drawBitmap(base, left, blockPx, null)
            val chipLeft = (width - chipWidth) / 2f
            val chipTop = blockPx + base.height + gapPx
            val chip = RectF(chipLeft, chipTop, chipLeft + chipWidth, chipTop + chipHeight)
            val radius = chipHeight / 2f
            canvas.drawRoundRect(chip, radius, radius, chipBackgroundPaint())
            // Baseline placed off the font metrics rather than by eye, so the chip stays centred at
            // any density and for any script.
            val baseline = chip.centerY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(label, chip.centerX(), baseline, paint)
            labelledCache.put(cacheKey, out)
        }
        return RentalIcon(bitmap, anchorV = (blockPx + base.height) / bitmap.height)
    }

    /** The plain, unlabelled form of [big] — stated as a [RentalIcon] so callers take one path. */
    fun bigIcon(context: Context, layer: RentalLayer, kind: RentalKind): RentalIcon = RentalIcon(big(context, layer, kind), anchorV = 1f)

    private fun glyphFor(layer: RentalLayer, kind: RentalKind): Int = when (kind) {
        // Any dock is a dock — the rider is looking for a rack, whatever is racked in it.
        RentalKind.STATION -> R.drawable.bike_dock
        RentalKind.VEHICLE -> when (layer) {
            RentalLayer.BIKES -> R.drawable.ic_directions_bike
            RentalLayer.SCOOTERS -> R.drawable.ic_kick_scooter
        }
    }

    private fun RentalLayer.pinColor(context: Context): Int = ContextCompat.getColor(
        context,
        when (this) {
            RentalLayer.BIKES -> R.color.layer_bikeshare_color
            RentalLayer.SCOOTERS -> R.color.layer_scooters_color
        }
    )

    /** Composites pin_base (tinted [pinColor]) with a centered white [glyphRes] — no outline, tip at the bottom. */
    private fun bigMarker(context: Context, pinColor: Int, @DrawableRes glyphRes: Int): Bitmap {
        val scale = context.resources.displayMetrics.density * BIG_SIZE_DP / MarkerRendering.GRID
        val sizePx = (MarkerRendering.GRID * scale).toInt()
        val bitmap = createBitmap(sizePx, sizePx)
        MarkerRendering.drawPinAndGlyph(
            Canvas(bitmap), context, sizePx, scale, pinColor, glyphRes, Color.WHITE, GLYPH_SIZE, outline = 0f
        )
        return bitmap
    }

    private fun labelPaint(density: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LABEL_TEXT_DP * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** A near-black chip, so the label reads over any basemap without borrowing the pin's colour. */
    private fun chipBackgroundPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHIP_BACKGROUND }

    private const val CHIP_BACKGROUND = 0xCC1A1A1A.toInt()
}
