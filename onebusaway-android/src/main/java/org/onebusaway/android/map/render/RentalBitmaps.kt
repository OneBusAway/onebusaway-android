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
 * Flavor-neutral generation of the rental marker bitmaps (#2168) — the small dot and the big circular
 * badges — so the Google flavor wraps them as `BitmapDescriptor`s and maplibre as `Icon`s. Descended
 * from `BikeBitmaps`, which drew exactly two teardrop pins (a dock and a "floating bike") in one navy.
 *
 * **A rental draws as a badge centred on its point, not a teardrop above it.** A parked vehicle *is* at
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
     * The glyph's 24-grid box. Well past the teardrop's 11, because a badge has no tail to leave room
     * for and no white field to read against — the glyph *is* the badge's content, so it takes most of
     * the face. Its artwork fills ~70% of this box, so the drawn mark is smaller than the number looks.
     */
    private const val GLYPH_SIZE = 15.6f

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

    /** The small dot shown in the mid-zoom band, drawn from the [bike_marker_small][R.drawable.bike_marker_small] vector. */
    fun small(context: Context): Bitmap = sSmall ?: run {
        val px = context.resources.getDimensionPixelSize(R.dimen.bikeshare_small_marker_size)
        MarkerRendering.rasterize(context, R.drawable.bike_marker_small, px).also { sSmall = it }
    }

    /**
     * The big badge for a place on [layer] of [kind]: the layer's colour bearing a white glyph — a dock
     * glyph for a station, the layer's own vehicle glyph for a free-floating one — inside its charge
     * ring. That glyph pairing is the fix for the defect #2168 names: before it, a parked scooter drew
     * the same dock marker a docking station did.
     *
     * [chargeFraction] (0..1) fills the ring clockwise from twelve o'clock; null draws the ring whole.
     *
     * **It is battery, and only ever battery.** OTP publishes two fuel facts — `fuel.percent`, "expressed
     * from 0 to 1", and `fuel.range`, unbounded metres — and an arc can only render the first, because
     * the second has no published maximum to be a fraction *of*. Deriving one (a "typical full charge",
     * the largest range on screen, a per-form-factor constant) would be exactly the invented magic
     * threshold CLAUDE.md forbids, and it would rot the moment an operator's fleet changed. So a feed
     * that omits `percent` gets an unfilled ring rather than a guessed one, and the range it *did*
     * publish is stated by the marker's detail window rather than on the marker itself.
     */
    fun big(context: Context, layer: RentalLayer, kind: RentalKind, chargeFraction: Float? = null): Bitmap {
        val step = chargeBucket(chargeFraction)
        return bigCache.getOrPut("$layer/$kind/$step") {
            discMarker(context, layer, kind, step?.let { it.toFloat() / CHARGE_STEPS })
        }
    }

    /**
     * The bucket [chargeFraction] renders as — the identity of the artwork, and the only part of a
     * charge reading that belongs in any icon cache key.
     *
     * Public because a caller wrapping these bitmaps in a platform icon has to key its own cache the
     * same way. Keying on the raw fraction instead mints one wrapper per distinct reading over a single
     * shared bitmap, which grows without bound as vehicles report new charge levels.
     */
    fun chargeBucket(chargeFraction: Float?): Int? = chargeFraction?.let { (it.coerceIn(0f, 1f) * CHARGE_STEPS).toInt() }

    /** The layer-coloured badge, its white glyph, and the charge ring around it. */
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

        // The badge, drawn by the shared circle-and-glyph routine — inset by the ring's *visible* width,
        // so it laps over the ring's hidden inner edge.
        val inset = visibleRing
        val discPx = sizePx - 2 * inset
        canvas.withTranslation(inset, inset) {
            MarkerRendering.drawCircleAndGlyph(
                this,
                context,
                discPx.toInt(),
                discPx / MarkerRendering.GRID,
                tint,
                glyphFor(layer, kind),
                Color.WHITE,
                GLYPH_SIZE
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
}
