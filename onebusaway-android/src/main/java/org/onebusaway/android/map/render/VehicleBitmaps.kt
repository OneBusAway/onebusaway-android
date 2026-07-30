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
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.MathUtils
import org.onebusaway.android.util.ScheduleDeviation

/**
 * Flavor-neutral generation of vehicle marker bitmaps. Lives in `src/main` so both the Google flavor
 * (wrapping each Bitmap in a `BitmapDescriptor`) and the maplibre flavor (wrapping it in an `Icon`)
 * share one implementation + the LRU cache. This is the icon half of the old `VehicleOverlay`.
 *
 * The marker is composed at draw time — a disc filled with the color its route is currently drawn
 * with, a mode glyph (bus/rail/…) centered on it, and a heading arrow at the rim, both in whichever
 * of black/white reads on that disc — rather than decoding one of
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
    // the JVM) doesn't touch android.graphics — only an on-device render allocates the Paint.
    private val blackPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK } }

    /**
     * Returns the vehicle marker bitmap for [vehicle] (the legacy getVehicleIcon body).
     *
     * The arrow points along the vehicle's live movement bearing on the route shape
     * ([VehicleMarker.getBearing], compass degrees, 0°=N clockwise) when known — so it follows the
     * extrapolation glide — and falls back to the status's reported orientation off-shape (NaN bearing).
     */
    fun vehicleBitmap(
        context: Context,
        vehicle: VehicleMarker,
        response: RouteTrips,
        sizeScale: Float = 1f
    ): Bitmap = getBitmap(
        context,
        vehicleType(vehicle, response),
        discColor(context, vehicle),
        directionIndex(vehicle),
        sizeScale
    )

    /**
     * A stable key identifying the icon [vehicleBitmap] returns for this vehicle — its type, heading
     * octant, disc color, and size scale, the only inputs that change the bitmap. A renderer caches
     * one wrapper (a Google `BitmapDescriptor`) per key so it reuses it across frames even when the
     * bounded bitmap LRU evicts and recreates the underlying [Bitmap] on a busy route.
     *
     * The color component is the **resolved ARGB value**, not a color resource id: a route color
     * (#2043) is a raw ARGB int off the wire with no resource id to name it. Keying on the resolved
     * value also closes a latent staleness bug the resource-id key had — the same id resolves to a
     * different color after a light/dark switch, which the old key could not tell apart. Only the
     * disc takes part — the glyph color is derived from it, so it adds no distinguishing power.
     */
    fun iconKey(
        context: Context,
        vehicle: VehicleMarker,
        response: RouteTrips,
        sizeScale: Float = 1f
    ): String = "veh:" +
        createBitmapCacheKey(
            vehicleType(vehicle, response),
            directionIndex(vehicle),
            discColor(context, vehicle),
            sizeScale
        )

    /** The vehicle's route type, normalizing cablecar to tram so both the bitmap and key paths agree. */
    private fun vehicleType(vehicle: VehicleMarker, response: RouteTrips): Int = routeTypeFor(response, vehicle.status.activeTripId)

    /**
     * The route type behind [activeTripId], or [DEFAULT_VEHICLE_TYPE] when it can't be resolved.
     *
     * Both hops are nullable **by contract** — [RouteTrips.trip] and [RouteTrips.route] resolve out of
     * whatever `references` the poll happened to carry — and a vehicle mid-block-interline can
     * legitimately report an `activeTripId` this route's poll never fetched (which is why
     * `TripVehiclesDataSource.trip` exists at all, #1691); a blank id does it too (#2003). This was a
     * `!!` chain inherited from the former Java, which turned that ordinary data gap into a foreground
     * process death on the reactive poll path (#2020) — even though `vehicleTitle`, called one line
     * away in the same reconcile loop, already degraded to "".
     *
     * Falling back to the default glyph keeps a real vehicle on the map rather than dropping it, and
     * because `iconKey` and `vehicleBitmap` both come through here they can't disagree about which
     * bitmap the fallback names.
     */
    @VisibleForTesting
    internal fun routeTypeFor(response: RouteTrips, activeTripId: String?): Int {
        val trip = response.trip(activeTripId) ?: return DEFAULT_VEHICLE_TYPE
        val route = response.route(trip.routeId) ?: return DEFAULT_VEHICLE_TYPE
        return normalizeVehicleType(route.type)
    }

    /**
     * Collapses cablecar onto tram so a cablecar route and the equivalent tram route resolve to the same
     * icon (and therefore the same [iconKey]); every other type passes through unchanged.
     */
    @VisibleForTesting
    fun normalizeVehicleType(routeType: Int): Int = if (routeType == ObaRoute.TYPE_CABLECAR) ObaRoute.TYPE_TRAM else routeType

    /**
     * The disc color: the vehicle's **route color** when it's live, gray when it isn't. So the marker
     * encodes route identity + liveness, never punctuality (#2043).
     *
     * At map zoom a colored disc reads as identity — "which line is this?" — not as a schedule
     * judgement, and a rider comparing two discs has no way to tell a hue that means "late" from one
     * that means "the 44". Deviation still has a home on the map: the info window's status chip,
     * which is where OBA iOS puts it too.
     *
     * The value is [VehicleMarker.routeColor] — the color the map is currently drawing that route's
     * line with, which is *not* always its GTFS color; see there — under the same
     * [DEFAULT_ROUTE_LINE_COLOR] fallback [RoutePolyline.resolvedColor] applies, so a vehicle and its
     * line always match.
     */
    @VisibleForTesting
    internal fun discColor(context: Context, vehicle: VehicleMarker): Int = if (vehicle.isRealtime) {
        vehicle.routeColor ?: DEFAULT_ROUTE_LINE_COLOR
    } else {
        ContextCompat.getColor(context, ScheduleDeviation.Status.SCHEDULED.displayColorRes)
    }

    /**
     * The 8-way heading slot (0..7) the icon for [vehicle] uses. Exposed so the renderer can cheaply
     * detect when a gliding vehicle's direction arrow needs re-stamping — the tinted bitmap only changes
     * when this index does (the disc color is the route's, so it doesn't change between polls). A live
     * vehicle always has a heading, so
     * the undirected slot ([UNDIRECTED]) isn't reachable from here.
     */
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
        color: Int,
        halfWind: Int,
        sizeScale: Float
    ): Bitmap {
        val key = createBitmapCacheKey(vehicleType, halfWind, color, sizeScale)
        return sColoredIconCache.get(key)
            ?: renderMarker(context, vehicleType, halfWind, color, sizeScale)
                .also { sColoredIconCache.put(key, it) }
    }

    /** [color] is a resolved ARGB value — see [iconKey] for why it can't be a resource id. */
    private fun createBitmapCacheKey(
        vehicleType: Int,
        halfWind: Int,
        color: Int,
        sizeScale: Float
    ): String {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        return "$type $halfWind $color ${sizeScale.toBits()}"
    }

    /**
     * Uncached render of a single marker for a given type/heading/color. Exposed for the
     * `@Preview` grid (and tests); the production path goes through [vehicleBitmap] which caches.
     */
    @VisibleForTesting
    fun previewBitmap(context: Context, vehicleType: Int, halfWind: Int, color: Int): Bitmap = renderMarker(context, vehicleType, halfWind, color, 1f)

    /**
     * Composites the colored disc, the mode glyph, and (unless undirected) the heading arrow — each
     * with a hairline black outline (a cheap 8-way dilate: the element stamped black at [OUTLINE_GRID]
     * offsets, then the fill on top) so the disc, glyph, and arrow read distinctly against each other
     * and the map. The disc is centered in the padded bitmap, so consumers anchor it at its center.
     *
     * The glyph and arrow take whichever of black/white reads on [color] rather than a hardcoded
     * white, since a route may be drawn in a shade too pale to carry white.
     */
    private fun renderMarker(
        context: Context,
        vehicleType: Int,
        halfWind: Int,
        color: Int,
        sizeScale: Float
    ): Bitmap {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        val scale = context.resources.displayMetrics.density *
            MARKER_SIZE_DP *
            sizeScale /
            MarkerRendering.GRID
        val pad = PAD_GRID * scale
        val contentPx = (MarkerRendering.GRID * scale).toInt()
        val sizePx = (MarkerRendering.GRID * scale + 2f * pad).toInt()
        val outline = OUTLINE_GRID * scale
        val onColor = MarkerRendering.legibleOn(color)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        // Draw inside a [pad] border so the outline halo has room; the grid geometry is relative to
        // this translated content origin.
        canvas.translate(pad, pad)

        // Colored disc (the route's display color, or gray when not real-time) + mode glyph, outlined.
        MarkerRendering.drawCircleAndGlyph(canvas, context, contentPx, scale, color, glyphRes(type), onColor, GLYPH_SIZE, outline)

        // Heading arrow, rotated about the disc center by the octant (undirected = no arrow).
        if (halfWind != UNDIRECTED) {
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = onColor }
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
                canvas.drawPath(arrow, arrowPaint)
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

    private fun supportedVehicleType(vehicleType: Int): Boolean = vehicleType == ObaRoute.TYPE_BUS ||
        vehicleType == ObaRoute.TYPE_FERRY ||
        vehicleType == ObaRoute.TYPE_TRAM ||
        vehicleType == ObaRoute.TYPE_SUBWAY ||
        vehicleType == ObaRoute.TYPE_RAIL
}
