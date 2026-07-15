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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.MathUtils

/**
 * Flavor-neutral generation of vehicle marker bitmaps. Lives in `src/main` so both the Google flavor
 * (wrapping each Bitmap in a `BitmapDescriptor`) and the maplibre flavor (wrapping it in an `Icon`)
 * share one implementation + the LRU cache. This is the icon half of the old `VehicleOverlay`.
 *
 * The marker is composed at draw time — a disc filled with the schedule-deviation color, a white mode
 * glyph (bus/rail/…) centered on it, and a white heading arrow at the rim — rather than decoding one of
 * the ~225 pre-composited `ic_marker_with_*` rasters. It's a **centered** badge (anchored at its center,
 * not a teardrop tip) so a vehicle sits on the route centerline like the trip map's estimate marker,
 * rather than floating off the line as a pin (#1752).
 */
object VehicleBitmaps {

    private const val NUM_DIRECTIONS = 9 // 8 directions + undirected vehicles

    private const val UNDIRECTED = NUM_DIRECTIONS - 1

    private const val DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS // fall back on bus

    private const val MAX_CACHE_SIZE = 15

    /** The composited marker fills a square this many dp on a side (the former raster's size). */
    private const val MARKER_SIZE_DP = 40f

    /** Transparent padding (grid units) around the disc so the black outline halo isn't clipped. */
    private const val PAD_GRID = 0.6f

    private const val GLYPH_SIZE = 10.8f // the glyph's 24-grid box (its artwork fills ~70% of this)

    // Heading-arrow chevron geometry, in 24-grid units: tip just inside the disc's top rim, pointing
    // outward, then rotated about the disc center by the heading octant. Mirrors the former pin arrow.
    private const val ARROW_TIP_GRID = 1f
    private const val ARROW_HALF_WIDTH_GRID = 2.16f
    private const val ARROW_HEIGHT_GRID = 2.34f

    /** Hairline black outline width, in 24-grid units (scales with the marker); ~1px on screen. */
    private const val OUTLINE_GRID = 0.25f

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
    fun vehicleBitmap(
        context: Context,
        vehicle: VehicleMarker,
        response: RouteTrips,
        sizeScale: Float = 1f,
    ): Bitmap = getBitmap(
        context,
        vehicleType(vehicle, response),
        colorResource(vehicle),
        directionIndex(vehicle),
        sizeScale,
    )

    /**
     * A stable key identifying the icon [vehicleBitmap] returns for this vehicle — its type, heading
     * octant, schedule-deviation color, and size scale, the only inputs that change the bitmap. A
     * renderer caches one wrapper (a Google `BitmapDescriptor`) per key so it reuses it across frames
     * even when the bounded bitmap LRU evicts and recreates the underlying [Bitmap] on a busy route.
     */
    @JvmStatic
    fun iconKey(vehicle: VehicleMarker, response: RouteTrips, sizeScale: Float = 1f): String =
        "veh:" + createBitmapCacheKey(
            vehicleType(vehicle, response),
            directionIndex(vehicle),
            colorResource(vehicle),
            sizeScale,
        )

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
        val deviationMin = vehicle.status.scheduleDeviation.inWholeMinutes
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

    private fun getBitmap(
        context: Context,
        vehicleType: Int,
        colorResource: Int,
        halfWind: Int,
        sizeScale: Float,
    ): Bitmap {
        val color = ContextCompat.getColor(context, colorResource)
        val key = createBitmapCacheKey(vehicleType, halfWind, colorResource, sizeScale)
        return sColoredIconCache.get(key)
            ?: renderMarker(context, vehicleType, halfWind, color, sizeScale)
                .also { sColoredIconCache.put(key, it) }
    }

    private fun createBitmapCacheKey(
        vehicleType: Int,
        halfWind: Int,
        colorResource: Int,
        sizeScale: Float,
    ): String {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        return "$type $halfWind $colorResource ${sizeScale.toBits()}"
    }

    /**
     * Uncached render of a single marker for a given type/heading/color. Exposed for the
     * `@Preview` grid (and tests); the production path goes through [vehicleBitmap] which caches.
     */
    @VisibleForTesting
    fun previewBitmap(context: Context, vehicleType: Int, halfWind: Int, color: Int): Bitmap =
        renderMarker(context, vehicleType, halfWind, color, 1f)

    /**
     * Composites the colored disc, white mode glyph, and (unless undirected) white heading arrow — each
     * with a hairline black outline (a cheap 8-way dilate: the element stamped black at [OUTLINE_GRID]
     * offsets, then the fill on top) so the disc, glyph, and arrow read distinctly against each other
     * and the map. The disc is centered in the padded bitmap, so consumers anchor it at its center.
     */
    private fun renderMarker(
        context: Context,
        vehicleType: Int,
        halfWind: Int,
        color: Int,
        sizeScale: Float,
    ): Bitmap {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        val scale = context.resources.displayMetrics.density * MARKER_SIZE_DP * sizeScale /
            MarkerRendering.GRID
        val pad = PAD_GRID * scale
        val contentPx = (MarkerRendering.GRID * scale).toInt()
        val sizePx = (MarkerRendering.GRID * scale + 2f * pad).toInt()
        val outline = OUTLINE_GRID * scale
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        // Draw inside a [pad] border so the outline halo has room; the grid geometry is relative to
        // this translated content origin.
        canvas.translate(pad, pad)

        // Colored disc (schedule-deviation color) + white mode glyph, each outlined.
        MarkerRendering.drawCircleAndGlyph(canvas, context, contentPx, scale, color, glyphRes(type), Color.WHITE, GLYPH_SIZE, outline)

        // Heading arrow, white, rotated about the disc center by the octant (undirected = no arrow).
        if (halfWind != UNDIRECTED) {
            val center = MarkerRendering.GRID / 2f
            canvas.withRotation(halfWind * 45f, center * scale, center * scale) {
                val arrow = Path().apply {
                    // Chevron with its tip just inside the disc's top rim, pointing outward.
                    moveTo(center * scale, ARROW_TIP_GRID * scale)
                    lineTo((center + ARROW_HALF_WIDTH_GRID) * scale, (ARROW_TIP_GRID + ARROW_HEIGHT_GRID) * scale)
                    lineTo((center - ARROW_HALF_WIDTH_GRID) * scale, (ARROW_TIP_GRID + ARROW_HEIGHT_GRID) * scale)
                    close()
                }
                MarkerRendering.stampOffsets(canvas, outline) { canvas.drawPath(arrow, blackPaint) }
                canvas.drawPath(arrow, whitePaint)
            }
        }
        return bitmap
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
