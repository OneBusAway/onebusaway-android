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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import org.onebusaway.android.R
import org.onebusaway.android.map.rental.RentalChargeBand
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.rentalChargeBand

/**
 * Flavor-neutral generation of the rental marker bitmaps (#2168) — the small dot, the big circular
 * pucks, and the range label beneath one — so the Google flavor wraps them as `BitmapDescriptor`s and
 * maplibre as `Icon`s. Descended from `BikeBitmaps`, which drew exactly two teardrop pins (a dock and
 * a "floating bike") in one navy.
 *
 * **A rental draws as a disc centred on its point, not a teardrop above it.** A parked vehicle *is* at
 * that coordinate to within a metre or two, so a pin whose tip merely points at the spot puts the
 * artwork somewhere the vehicle isn't; the circular form is the same call the trip map already made
 * for its vehicle badges (#1752, [MarkerRendering.drawCircleAndGlyph]), and it leaves a ring of
 * marker edge free to carry state. Taking the cue from the Lime app, that ring is the vehicle's
 * charge.
 *
 * **The ring is battery, not range** — see [big]. Its arc needs a fraction, and only `fuel.percent`
 * is one.
 */
object RentalBitmaps {

    /** Big markers fill a square this many dp on a side (the former raster's size). */
    private const val BIG_SIZE_DP = 32f

    /**
     * The glyph's 24-grid box. Larger than the teardrop's 11 because a disc has no tail to leave room
     * for — the glyph gets the whole face.
     */
    private const val GLYPH_SIZE = 13f

    /**
     * The charge ring's *visible* stroke, in dp — what a reader measures off the screen.
     *
     * The ring is actually stroked [RING_UNDERLAP_DP] wider than this and the disc drawn over that
     * excess, so the two meet with no seam. Stroking them exactly tangent instead leaves a hairline of
     * background showing between them wherever the two antialiased edges don't quite agree, which is
     * the artifact this pair of constants exists to kill.
     */
    private const val RING_WIDTH_DP = 3.9f

    /** How far the disc is drawn *under* the ring, hiding the junction. Never visible. */
    private const val RING_UNDERLAP_DP = 1f

    /** The range label's type size and its padding, in dp. */
    private const val LABEL_TEXT_DP = 10f
    private const val LABEL_PADDING_DP = 3f

    /** Gap between the marker and the label chip, in dp. */
    private const val LABEL_GAP_DP = 1f

    /** The unfilled part of the charge ring — a neutral track the arc reads against. */
    private const val RING_TRACK = 0x33000000

    /**
     * The charge arc is quantized to this many steps for caching. A 32dp ring cannot show finer than
     * this anyway (one step is under 2° of sweep), and it bounds the distinct bitmaps at
     * `steps × layers × kinds` instead of one per battery reading in the viewport. This is a *drawing*
     * resolution, not a reading of the data — [org.onebusaway.android.map.rental.RentalDetail] still
     * reports the exact percentage, and the filter still reads the exact range.
     */
    private const val CHARGE_STEPS = 20

    // The unlabelled icons never vary per vehicle unless they carry a charge ring, so cache them by
    // everything that can change them — the maplibre renderer clears + redraws every marker on each
    // snapshot, so without this it would re-render these per marker per render.
    private var sSmall: Bitmap? = null
    private val bigCache = HashMap<String, Bitmap>()

    /**
     * Labelled markers vary per vehicle, so they get a bounded cache instead: a viewport holds at most
     * the density budget's worth of markers and their range strings collapse to a few dozen distinct
     * values, so this is sized to hold a screen's worth without growing without limit.
     */
    private val labelledCache = LruCache<String, Bitmap>(128)

    /**
     * A marker bitmap and where on it the marker's point actually is.
     *
     * [anchorV] is the vertical fraction the point sits at — 0.5 for a bare disc (centred, which is
     * the whole reason for the circular form) and above that when a label hangs below it, since the
     * disc then sits in the upper part of a taller bitmap. The Google flavor passes it to
     * `MarkerOptions.anchor`; the maplibre flavor centres every icon on its point and so ignores it,
     * which is why [labelled] pads the bitmap symmetrically — the disc's centre stays the bitmap's
     * centre, so a labelled marker sits exactly where an unlabelled one would there.
     */
    data class RentalIcon(val bitmap: Bitmap, val anchorV: Float)

    /** The small dot shown in the mid-zoom band, drawn from the [bike_marker_small][R.drawable.bike_marker_small] vector. */
    fun small(context: Context): Bitmap = sSmall ?: run {
        val px = context.resources.getDimensionPixelSize(R.dimen.bikeshare_small_marker_size)
        MarkerRendering.rasterize(context, R.drawable.bike_marker_small, px).also { sSmall = it }
    }

    /**
     * The big disc for a place on [layer] of [kind]: a white puck bearing the layer's colour as its
     * glyph — a dock glyph for a station, the layer's own vehicle glyph for a free-floating one — ringed
     * in that same colour. That glyph pairing is the fix for the defect #2168 names: before it, a parked
     * scooter drew the same dock marker a docking station did.
     *
     * [chargeFraction] (0..1) fills the ring clockwise from twelve o'clock; null draws the ring whole.
     *
     * **It is battery, and only ever battery.** OTP publishes two fuel facts — `fuel.percent`, "expressed
     * from 0 to 1", and `fuel.range`, unbounded metres — and an arc can only render the first, because
     * the second has no published maximum to be a fraction *of*. Deriving one (a "typical full charge",
     * the largest range on screen, a per-form-factor constant) would be exactly the invented magic
     * threshold CLAUDE.md forbids, and it would rot the moment an operator's fleet changed. So a feed
     * that omits `percent` gets an unfilled ring rather than a guessed one, and the range it *did*
     * publish is stated as text by [labelled]. The Lime app this takes its cue from draws the same
     * split: the ring is charge, the number beside it is range.
     */
    fun big(context: Context, layer: RentalLayer, kind: RentalKind, chargeFraction: Float? = null): Bitmap {
        val step = chargeFraction?.let { (it.coerceIn(0f, 1f) * CHARGE_STEPS).toInt() }
        return bigCache.getOrPut("$layer/$kind/$step") {
            discMarker(context, layer, kind, step?.let { it.toFloat() / CHARGE_STEPS })
        }
    }

    /**
     * [base] with [label] on a chip beneath it — the range readout the rental layer shows above its
     * label zoom threshold.
     *
     * The bitmap is padded above the disc by exactly the label block's height so the disc stays
     * vertically centred (see [RentalIcon.anchorV]).
     */
    fun labelled(context: Context, base: Bitmap, label: String, cacheKey: String): RentalIcon {
        val density = context.resources.displayMetrics.density
        // The label block's height depends only on the type size and padding, never on the string, so
        // the anchor resolves without measuring text — which is what keeps a cache *hit* free of any
        // Paint allocation or text measurement. This runs once per marker per render (up to the
        // density budget's 500) on the main thread, so paying for a measure here would be 500 wasted
        // measurements on every camera settle.
        val blockPx = (LABEL_GAP_DP + LABEL_TEXT_DP + LABEL_PADDING_DP * 2f) * density
        val bitmap = labelledCache.get(cacheKey)
            ?: drawLabelled(density, base, label, blockPx).also { labelledCache.put(cacheKey, it) }
        // The disc's centre, not the bitmap's bottom: the point is under the middle of the marker.
        return RentalIcon(bitmap, anchorV = (blockPx + base.height / 2f) / bitmap.height)
    }

    /** Renders one labelled marker — the cache-miss half of [labelled], and the only text work. */
    private fun drawLabelled(density: Float, base: Bitmap, label: String, blockPx: Float): Bitmap {
        val paint = labelPaint(density)
        val padPx = LABEL_PADDING_DP * density
        val chipHeight = paint.textSize + padPx * 2f
        val chipWidth = paint.measureText(label) + padPx * 2f
        val width = maxOf(base.width.toFloat(), chipWidth).toInt()
        val out = createBitmap(width, (base.height + blockPx * 2f).toInt())
        val canvas = Canvas(out)
        canvas.drawBitmap(base, (width - base.width) / 2f, blockPx, null)
        val chipLeft = (width - chipWidth) / 2f
        val chipTop = blockPx + base.height + LABEL_GAP_DP * density
        val chip = RectF(chipLeft, chipTop, chipLeft + chipWidth, chipTop + chipHeight)
        val radius = chipHeight / 2f
        canvas.drawRoundRect(chip, radius, radius, chipBackgroundPaint())
        // Baseline placed off the font metrics rather than by eye, so the chip stays centred at any
        // density and for any script.
        canvas.drawText(label, chip.centerX(), chip.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)
        return out
    }

    /** The white puck, its coloured glyph, and the charge ring around it. */
    private fun discMarker(
        context: Context,
        layer: RentalLayer,
        kind: RentalKind,
        chargeFraction: Float?
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (BIG_SIZE_DP * density).toInt()
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val tint = layer.markerColor(context)
        val center = sizePx / 2f
        // The ring is stroked wider than it reads, and the disc covers the excess — see RING_WIDTH_DP.
        val visibleRing = RING_WIDTH_DP * density
        val ringStroke = visibleRing + RING_UNDERLAP_DP * density
        val ringRadius = center - ringStroke / 2f

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringStroke
            strokeCap = Paint.Cap.ROUND
        }
        if (chargeFraction == null) {
            // Nothing published to fill it with, so the ring is simply the marker's edge, and it wears
            // the layer's own colour — there is no charge for a band to describe.
            ring.color = tint
            canvas.drawCircle(center, center, ringRadius, ring)
        } else {
            ring.color = RING_TRACK
            canvas.drawCircle(center, center, ringRadius, ring)
            // The arc is the gauge, so it takes the charge band rather than the layer colour. The
            // glyph inside keeps the layer colour, so a bike is still told from a scooter.
            ring.color = ContextCompat.getColor(context, chargeBandColor(rentalChargeBand(chargeFraction)))
            // Clockwise from twelve o'clock, the direction a gauge is read.
            canvas.drawArc(
                RectF(center - ringRadius, center - ringRadius, center + ringRadius, center + ringRadius),
                -90f,
                360f * chargeFraction,
                false,
                ring
            )
        }

        // The puck, drawn by the shared circle-and-glyph routine the trip map's vehicle badges use —
        // inset by the ring's *visible* width, so it laps over the ring's hidden inner edge.
        val inset = visibleRing
        val discPx = sizePx - 2 * inset
        canvas.withTranslation(inset, inset) {
            MarkerRendering.drawCircleAndGlyph(
                this,
                context,
                discPx.toInt(),
                discPx / MarkerRendering.GRID,
                Color.WHITE,
                glyphFor(layer, kind),
                tint,
                GLYPH_SIZE,
                outline = 0f
            )
        }
        return bitmap
    }

    @ColorRes
    private fun chargeBandColor(band: RentalChargeBand): Int = when (band) {
        RentalChargeBand.LOW -> R.color.rental_charge_low
        RentalChargeBand.MEDIUM -> R.color.rental_charge_medium
        RentalChargeBand.HIGH -> R.color.rental_charge_high
    }

    private fun glyphFor(layer: RentalLayer, kind: RentalKind): Int = when (kind) {
        // Any dock is a dock — the rider is looking for a rack, whatever is racked in it.
        RentalKind.STATION -> R.drawable.bike_dock
        RentalKind.VEHICLE -> when (layer) {
            RentalLayer.BIKES -> R.drawable.ic_directions_bike
            RentalLayer.SCOOTERS -> R.drawable.ic_kick_scooter
        }
    }

    private fun RentalLayer.markerColor(context: Context): Int = ContextCompat.getColor(
        context,
        when (this) {
            RentalLayer.BIKES -> R.color.layer_bikeshare_color
            RentalLayer.SCOOTERS -> R.color.layer_scooters_color
        }
    )

    private fun labelPaint(density: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LABEL_TEXT_DP * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** A near-black chip, so the label reads over any basemap without borrowing the marker's colour. */
    private fun chipBackgroundPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHIP_BACKGROUND }

    private const val CHIP_BACKGROUND = 0xCC1A1A1A.toInt()
}
