/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
import android.graphics.Path
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.MathUtils
import java.util.concurrent.TimeUnit

/**
 * Flavor-neutral generation of vehicle marker bitmaps. Lives in `src/main` so both the Google flavor
 * (wrapping each Bitmap in a `BitmapDescriptor`) and the maplibre flavor (wrapping it in an `Icon`)
 * share one implementation + the LRU cache. This is the icon half of the old `VehicleOverlay`.
 *
 * The marker is composed at draw time — a [pin_base][R.drawable.pin_base] teardrop filled with the
 * schedule-deviation color, a white mode glyph (bus/rail/…) centered in the head, and a white heading
 * arrow — rather than decoding one of the ~225 pre-composited `ic_marker_with_*` rasters.
 */
object VehicleBitmaps {

    private const val NUM_DIRECTIONS = 9 // 8 directions + undirected vehicles

    private const val UNDIRECTED = NUM_DIRECTIONS - 1

    private const val DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS // fall back on bus

    private const val MAX_CACHE_SIZE = 15

    /** The composited marker fills a square this many dp on a side (the former raster's size). */
    private const val MARKER_SIZE_DP = 40f

    /** pin_base is authored on a 24-unit grid; all geometry below is in those units. */
    private const val GRID = 24f

    /** Transparent padding (grid units) around the pin so the black outline halo isn't clipped. */
    private const val PAD_GRID = 0.6f

    /**
     * Marker anchor as a fraction of the padded bitmap: horizontally centered, vertically at the pin
     * tip (grid y=24). Consumers anchor here so the tip — not the padded bitmap edge — sits on the
     * vehicle's location.
     */
    const val ANCHOR_U = 0.5f
    val ANCHOR_V = (GRID + PAD_GRID) / (GRID + 2f * PAD_GRID)

    // The mode glyph is centered on the pin head — concentric with the direction-arrow ring.
    private const val GLYPH_CX = 12f
    private const val GLYPH_CY = 8f
    private const val GLYPH_SIZE = 10.8f // the glyph's 24-grid box (its artwork fills ~70% of this)

    // The heading arrow: a triangle at the top of the head (NORTH), rotated about the head center by
    // the heading octant. Tip points outward.
    private const val ARROW_CX = 12f
    private const val ARROW_CY = 8f // rotation pivot = pin head center

    /** Hairline black outline width, in 24-grid units (scales with the marker); ~1px on screen. */
    private const val OUTLINE_GRID = 0.25f

    /** 8-way unit offsets used to stamp the black outline around each element (a cheap dilate). */
    private val OUTLINE_OFFSETS = arrayOf(
        floatArrayOf(-1f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, -1f), floatArrayOf(0f, 1f),
        floatArrayOf(-0.7f, -0.7f), floatArrayOf(0.7f, -0.7f), floatArrayOf(-0.7f, 0.7f), floatArrayOf(0.7f, 0.7f),
    )

    private val sColoredIconCache = LruCache<String, Bitmap>(MAX_CACHE_SIZE)

    // Lazy so loading this object for the pure-logic helpers (e.g. normalizeVehicleType, unit-tested on
    // the JVM) doesn't touch android.graphics — only an on-device render allocates the Paints.
    private val whitePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE } }

    private val blackPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK } }

    /**
     * Returns the vehicle marker bitmap for [vehicle] (the legacy getVehicleIcon body).
     *
     * The arrow points along the vehicle's live movement bearing on the route shape
     * ([VehicleMarker.getBearing], compass degrees, 0°=N clockwise) when known — so it follows the
     * extrapolation glide — and falls back to the status's reported orientation off-shape (NaN bearing).
     */
    @JvmStatic
    fun vehicleBitmap(context: Context, vehicle: VehicleMarker, response: RouteTrips): Bitmap =
        getBitmap(context, vehicleType(vehicle, response), colorResource(vehicle), directionIndex(vehicle))

    /**
     * A stable key identifying the icon [vehicleBitmap] returns for this vehicle — its type, heading
     * octant, and schedule-deviation color, the only inputs that change the bitmap. A renderer caches
     * one wrapper (a Google `BitmapDescriptor`) per key so it reuses it across frames even when the
     * bounded bitmap LRU evicts and recreates the underlying [Bitmap] on a busy route.
     */
    @JvmStatic
    fun iconKey(vehicle: VehicleMarker, response: RouteTrips): String =
        "veh:" + createBitmapCacheKey(vehicleType(vehicle, response), directionIndex(vehicle), colorResource(vehicle))

    /** The vehicle's route type, normalizing cablecar to tram so both the bitmap and key paths agree. */
    private fun vehicleType(vehicle: VehicleMarker, response: RouteTrips): Int {
        val status = vehicle.status
        // Non-null chain matches the former Java (it NPE'd on a missing trip/route too).
        val type = response.route(response.trip(status.activeTripId)!!.routeId)!!.type
        return normalizeVehicleType(type)
    }

    /**
     * Collapses cablecar onto tram so a cablecar route and the equivalent tram route resolve to the same
     * icon (and therefore the same [iconKey]); every other type passes through unchanged.
     */
    @JvmStatic
    @VisibleForTesting
    fun normalizeVehicleType(routeType: Int): Int =
        if (routeType == ObaRoute.TYPE_CABLECAR) ObaRoute.TYPE_TRAM else routeType

    /** The schedule-deviation color (realtime) or the scheduled color — constant between polls. */
    private fun colorResource(vehicle: VehicleMarker): Int {
        val deviationMin = TimeUnit.SECONDS.toMinutes(vehicle.status.scheduleDeviation)
        return ArrivalInfoUtils.statusColor(vehicle.isRealtime, deviationMin)
    }

    /**
     * The 8-way heading slot (0..7) the icon for [vehicle] uses. Exposed so the renderer can cheaply
     * detect when a gliding vehicle's direction arrow needs re-stamping — the tinted bitmap only changes
     * when this index does (the color is constant between polls). A live vehicle always has a heading, so
     * the undirected slot ([UNDIRECTED]) isn't reachable from here.
     */
    @JvmStatic
    fun directionIndex(vehicle: VehicleMarker): Int {
        // The path bearing is already a compass direction; the server orientation needs converting.
        val pathBearing = vehicle.bearing
        val direction = if (pathBearing.isNaN()) {
            MathUtils.toDirection(vehicle.status.orientation!!)
        } else {
            pathBearing.toDouble()
        }
        return MathUtils.getHalfWindIndex(direction.toFloat(), UNDIRECTED)
    }

    private fun getBitmap(context: Context, vehicleType: Int, colorResource: Int, halfWind: Int): Bitmap {
        val color = ContextCompat.getColor(context, colorResource)
        val key = createBitmapCacheKey(vehicleType, halfWind, colorResource)
        return sColoredIconCache.get(key)
            ?: renderMarker(context, vehicleType, halfWind, color).also { sColoredIconCache.put(key, it) }
    }

    private fun createBitmapCacheKey(vehicleType: Int, halfWind: Int, colorResource: Int): String {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        return "$type $halfWind $colorResource"
    }

    /**
     * Uncached render of a single marker for a given type/heading/color. Exposed for the
     * `@Preview` grid (and tests); the production path goes through [vehicleBitmap] which caches.
     */
    @VisibleForTesting
    fun previewBitmap(context: Context, vehicleType: Int, halfWind: Int, color: Int): Bitmap =
        renderMarker(context, vehicleType, halfWind, color)

    /**
     * Composites the pin frame, white mode glyph, and (unless undirected) white heading arrow — each
     * with a hairline black outline (a cheap 8-way dilate: the element stamped black at [OUTLINE_GRID]
     * offsets, then the fill on top) so the frame, glyph, and arrow read distinctly against each other
     * and the map.
     */
    private fun renderMarker(context: Context, vehicleType: Int, halfWind: Int, color: Int): Bitmap {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        val scale = context.resources.displayMetrics.density * MARKER_SIZE_DP / GRID
        val pad = PAD_GRID * scale
        val contentPx = (GRID * scale).toInt()
        val sizePx = (GRID * scale + 2f * pad).toInt()
        val outline = OUTLINE_GRID * scale
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Draw the pin/glyph/arrow inside a [pad] border so the outline halo has room; the grid
        // geometry below is all relative to this translated content origin.
        canvas.translate(pad, pad)

        // 1. Pin frame, tinted to the schedule-deviation color.
        val pin = ContextCompat.getDrawable(context, R.drawable.pin_base)!!.mutate()
        pin.setBounds(0, 0, contentPx, contentPx)
        drawOutlined(canvas, pin, outline, color)

        // 2. Mode glyph, white, centered in the head.
        val glyph = ContextCompat.getDrawable(context, glyphRes(type))!!.mutate()
        val half = GLYPH_SIZE / 2f
        glyph.setBounds(
            ((GLYPH_CX - half) * scale).toInt(), ((GLYPH_CY - half) * scale).toInt(),
            ((GLYPH_CX + half) * scale).toInt(), ((GLYPH_CY + half) * scale).toInt(),
        )
        drawOutlined(canvas, glyph, outline, Color.WHITE)

        // 3. Heading arrow, white, rotated about the head center by the octant (undirected = no arrow).
        if (halfWind != UNDIRECTED) {
            canvas.save()
            canvas.rotate(halfWind * 45f, ARROW_CX * scale, ARROW_CY * scale)
            val arrow = Path().apply {
                // Wide chevron, tip grazing the head edge; scaled 0.9 about the tip.
                moveTo(12f * scale, 0.1f * scale)
                lineTo(14.16f * scale, 2.44f * scale)
                lineTo(9.84f * scale, 2.44f * scale)
                close()
            }
            for (o in OUTLINE_OFFSETS) {
                canvas.save()
                canvas.translate(o[0] * outline, o[1] * outline)
                canvas.drawPath(arrow, blackPaint)
                canvas.restore()
            }
            canvas.drawPath(arrow, whitePaint)
            canvas.restore()
        }
        return bitmap
    }

    /** Draws [drawable] with a black outline: stamped black at the [OUTLINE_OFFSETS], then [fill] on top. */
    private fun drawOutlined(canvas: Canvas, drawable: Drawable, outline: Float, fill: Int) {
        drawable.setTint(Color.BLACK)
        for (o in OUTLINE_OFFSETS) {
            canvas.save()
            canvas.translate(o[0] * outline, o[1] * outline)
            drawable.draw(canvas)
            canvas.restore()
        }
        drawable.setTint(fill)
        drawable.draw(canvas)
    }

    @DrawableRes
    private fun glyphRes(vehicleType: Int): Int = when (vehicleType) {
        ObaRoute.TYPE_FERRY -> R.drawable.ic_ferry
        ObaRoute.TYPE_TRAM -> R.drawable.ic_tram
        ObaRoute.TYPE_SUBWAY -> R.drawable.ic_subway
        ObaRoute.TYPE_RAIL -> R.drawable.ic_train
        else -> R.drawable.ic_bus
    }

    private fun supportedVehicleType(vehicleType: Int): Boolean =
        vehicleType == ObaRoute.TYPE_BUS ||
            vehicleType == ObaRoute.TYPE_FERRY ||
            vehicleType == ObaRoute.TYPE_TRAM ||
            vehicleType == ObaRoute.TYPE_SUBWAY ||
            vehicleType == ObaRoute.TYPE_RAIL
}
