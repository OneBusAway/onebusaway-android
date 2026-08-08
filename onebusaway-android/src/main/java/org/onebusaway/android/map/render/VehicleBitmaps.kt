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
import android.graphics.Path
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.ScheduleDeviation
import org.onebusaway.android.util.requireDrawable

/**
 * Flavor-neutral generation of vehicle marker bitmaps. Lives in `src/main` so both the Google flavor
 * (wrapping each Bitmap in a `BitmapDescriptor`) and the maplibre flavor (wrapping it in an `Icon`)
 * share one implementation + the LRU cache. This is the icon half of the old `VehicleOverlay`.
 *
 * The marker is composed at draw time — a disc filled with the color its route is currently drawn
 * with, a mode glyph (bus/rail/…) centered on it, and, for a vehicle that reports how full it is, a
 * rectangular tab under the disc holding three occupancy pips — rather than decoding one of the ~225
 * pre-composited `ic_marker_with_*` rasters. It's a **centered** badge (anchored at its center, not a
 * teardrop tip) so a vehicle sits on the route centerline like the trip map's estimate marker, rather
 * than floating off the line as a pin (#1752).
 *
 * ### Two silhouettes, one anchor
 *
 * A vehicle with no fullness data is a plain disc; one that reports fullness is the **union** of that
 * disc and the tab, drawn as a single outlined path so no seam shows where they meet. The tab is a
 * reserved zone: its three pips are always all drawn, filling left to right, so the row's length reads
 * as a scale and only the ink inside it varies — a rider sees "1 of 3", not "one pip".
 *
 * Both silhouettes share one bitmap geometry, kept **vertically symmetric about the disc center** by
 * mirroring the tab's depth as transparent padding above the disc. That is what lets every vehicle
 * marker keep a plain center anchor in both flavors — maplibre's classic `Marker` centers an icon on
 * its point with no anchor control at all — while the disc, not the union's centroid, stays on the
 * route centerline. It also means the disc doesn't shift when a feed gaps and a vehicle's fullness
 * comes and goes between polls: the tab appears and disappears beneath a marker that doesn't move.
 */
object VehicleBitmaps {

    private const val DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS // fall back on bus

    // Bounded in **bytes**, not entries: one marker is ~62 KiB at xxhdpi but ~113 KiB at xxxhdpi, and a
    // zoomed-out (half-scale) one is a quarter of that, so an entry count would mean wildly different
    // memory on different devices for the same nominal size. 4 MiB holds ~64 full-scale markers there.
    //
    // The working set is 5 fullness states (absent + 0..3 filled) per (mode, disc colour) — a handful for
    // one route once its scheduled vehicles are counted, and stop-focus/continuation views draw several
    // routes at once, so this is sized for a few of those rather than one. Overflow only costs a
    // re-render. The Google flavor has a second-level BitmapDescriptor cache in front of this; maplibre
    // doesn't, which is why the bound lives here.
    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024

    /** The composited marker fills a square this many dp on a side (the former raster's size). */
    @VisibleForTesting
    internal const val MARKER_SIZE_DP = 40f

    /** Transparent padding (grid units) around the disc so the black outline halo isn't clipped. */
    @VisibleForTesting
    internal const val PAD_GRID = 0.6f

    private const val GLYPH_SIZE = 10.8f // the glyph's 24-grid box (its artwork fills ~70% of this)

    /**
     * The occupancy tab (grid units): a rounded rectangle centered under the disc, unioned with it.
     *
     * [TAB_TOP_GRID] is buried well inside the disc — the disc's half-width there is 10.4, wider than
     * the tab's 9.3 — so the union is a clean silhouette whose vertical sides emerge from the disc's
     * lower flanks (around y 19.6) rather than a rectangle stuck onto a circle. Only the *lower* two
     * corners are ever seen rounded; the upper two are inside the disc, where the union erases them.
     *
     * [TAB_DEPTH_GRID] is how far it hangs below the disc, and so also the transparent padding mirrored
     * above the disc that keeps the bitmap symmetric about the disc's center — see the class KDoc.
     */
    @VisibleForTesting
    internal const val TAB_DEPTH_GRID = 6.6f

    private const val TAB_HALF_WIDTH_GRID = 9.3f
    private const val TAB_TOP_GRID = 18f
    private const val TAB_BOTTOM_GRID = MarkerRendering.GRID + TAB_DEPTH_GRID
    private const val TAB_CORNER_RADIUS_GRID = 2.4f

    // The pip row: three person silhouettes in the tab, at 4.35 grid units each (~7.3 dp) with the row
    // spanning 14.25 of the tab's 18.6 width.
    //
    // The row sits above the tab's vertical middle. ic_occupancy is drawn to the full height of its own
    // box and ends in a flat-bottomed body, so its lowest pixels — plus the black dilate under them —
    // carry far more weight than its top; optically centering it would leave that heavy edge fused with
    // the tab's bottom rim, which reads as a clipped pip rather than a seated one.
    private const val PIP_SIZE_GRID = 4.35f
    private const val PIP_SPACING_GRID = 0.6f
    private const val PIP_ROW_CY_GRID = 25.8f

    /** The pips in the tab — always all drawn, [occupancyFill] of them inked. */
    internal const val MAX_PIPS = 3

    /** Hairline black outline width, in 24-grid units (scales with the marker); ~1px on screen. */
    private const val OUTLINE_GRID = 0.25f

    /**
     * An empty pip's fill: white at 50%, so the unfilled slots read as present but quiet — the row's
     * length still states the scale without the empty end shouting louder than the full one.
     */
    private const val EMPTY_PIP_FILL = 0x80FFFFFF.toInt()

    private val sColoredIconCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Returns the vehicle marker bitmap for [vehicle] (the legacy getVehicleIcon body). */
    fun vehicleBitmap(
        context: Context,
        vehicle: VehicleMarker,
        response: RouteTrips,
        sizeScale: Float = 1f
    ): Bitmap = getBitmap(
        context,
        vehicleType(vehicle, response),
        discColor(context, vehicle),
        occupancyFill(vehicle),
        sizeScale
    )

    /**
     * A stable key identifying the icon [vehicleBitmap] returns for this vehicle — its type, disc color,
     * fullness, and size scale, the only inputs that change the bitmap. A renderer caches one wrapper (a
     * Google `BitmapDescriptor`) per key so it reuses it across frames even when the bounded bitmap LRU
     * evicts and recreates the underlying [Bitmap] on a busy route.
     *
     * Note what is *not* here: the vehicle's heading. The marker carried a heading arrow until #2194's
     * occupancy tab took the space below the disc that the arrow's southern octants swing through; with
     * the arrow gone the icon no longer varies with bearing, which is why neither renderer re-stamps a
     * gliding vehicle's icon between polls any more.
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
            discColor(context, vehicle),
            occupancyFill(vehicle),
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
     * How many of this vehicle's [MAX_PIPS] pips are inked (0..[MAX_PIPS]), or **null when it reports no
     * fullness at all** — in which case the marker is a plain disc with no tab.
     *
     * Null for a scheduled vehicle too: it has no observed occupancy, and a gray marker that grew a tab
     * would be reporting data it doesn't have (#959).
     */
    internal fun occupancyFill(vehicle: VehicleMarker): Int? = if (vehicle.isRealtime) {
        occupancyFill(vehicle.status.occupancyStatus)
    } else {
        null
    }

    /**
     * The GTFS-realtime occupancy bucketed onto the tab's three pips — a coarse empty/some/most/full
     * scale, since seven named levels can't be told apart at map scale.
     *
     * The distinction that earns the tab its own silhouette is **null vs [Occupancy.EMPTY]**: "we have no
     * fullness data" and "we do, and the vehicle is empty" are different facts, and the old flat pip
     * count could not tell them apart — both drew nothing, so an unequipped fleet and an empty bus
     * looked alike. A tab with three hollow pips says "empty" out loud; no tab says "not reported".
     */
    @VisibleForTesting
    fun occupancyFill(occupancy: Occupancy?): Int? = when (occupancy) {
        null -> null
        Occupancy.EMPTY -> 0
        Occupancy.MANY_SEATS_AVAILABLE -> 1
        Occupancy.FEW_SEATS_AVAILABLE, Occupancy.STANDING_ROOM_ONLY -> 2
        Occupancy.CRUSHED_STANDING_ROOM_ONLY, Occupancy.FULL, Occupancy.NOT_ACCEPTING_PASSENGERS -> MAX_PIPS
    }

    /**
     * The disc color: the vehicle's **route color** when it's live, gray when it isn't. So the marker
     * encodes route identity + liveness, never punctuality (#2043).
     *
     * At map zoom a colored disc reads as identity — "which line is this?" — not as a schedule
     * judgement, and a rider comparing two discs has no way to tell a hue that means "late" from one
     * that means "the 44". Deviation is read in the arrival listings, which is where a rider decides
     * whether to run for it; the map's job is to say which vehicle is which and where it is. (It had a
     * second home in the vehicle info window until #2194 retired that.)
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

    private fun getBitmap(
        context: Context,
        vehicleType: Int,
        color: Int,
        fill: Int?,
        sizeScale: Float
    ): Bitmap {
        val key = createBitmapCacheKey(vehicleType, color, fill, sizeScale)
        return sColoredIconCache.get(key)
            ?: renderMarker(context, vehicleType, color, fill, sizeScale)
                .also { sColoredIconCache.put(key, it) }
    }

    /** [color] is a resolved ARGB value — see [iconKey] for why it can't be a resource id. */
    private fun createBitmapCacheKey(
        vehicleType: Int,
        color: Int,
        fill: Int?,
        sizeScale: Float
    ): String {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        // -1 rather than "null": the tabless marker is its own icon, and a distinct numeric slot keeps
        // the key a fixed shape.
        return "$type $color ${fill ?: -1} ${sizeScale.toBits()}"
    }

    /**
     * Uncached render of a single marker for a given type/color/fullness. Exposed for the `@Preview`
     * grid (and tests); the production path goes through [vehicleBitmap] which caches.
     */
    @VisibleForTesting
    fun previewBitmap(
        context: Context,
        vehicleType: Int,
        color: Int,
        fill: Int?
    ): Bitmap = renderMarker(context, vehicleType, color, fill, 1f)

    /**
     * Composites the marker body — a disc, unioned with the occupancy tab when [fill] is non-null — then
     * the mode glyph centered on the disc, then the tab's three pips. Each element carries a hairline
     * black outline (the body stroked, the glyph and pips given a cheap 8-way dilate at [OUTLINE_GRID]
     * offsets) so they read distinctly against each other and the map.
     *
     * The bitmap reserves [TAB_DEPTH_GRID] above the disc as well as below, so the disc's center is the
     * bitmap's center whether or not a tab is drawn — see the class KDoc for why that matters.
     *
     * The **glyph** takes whichever of black/white reads on [color] rather than a hardcoded white, since
     * a route may be drawn in a shade too pale to carry white. The **pips** deliberately do not: they use
     * a fixed white-empty / black-full polarity (see the pip loop), so a rider learns one reading of the
     * row rather than one per route colour. Neither uses a colour ramp of its own — the disc's colour
     * already means route identity (#2043), and a second colour scale on a 40 dp icon would compete with
     * it (#1079).
     */
    private fun renderMarker(
        context: Context,
        vehicleType: Int,
        color: Int,
        fill: Int?,
        sizeScale: Float
    ): Bitmap {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        val scale = context.resources.displayMetrics.density *
            MARKER_SIZE_DP *
            sizeScale /
            MarkerRendering.GRID
        val pad = PAD_GRID * scale
        val widthPx = (MarkerRendering.GRID * scale + 2f * pad).toInt()
        val heightPx = ((MarkerRendering.GRID + 2f * TAB_DEPTH_GRID) * scale + 2f * pad).toInt()
        val outline = OUTLINE_GRID * scale
        val onColor = MarkerRendering.legibleOn(color)
        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        // Draw inside a [pad] border so the outline has room, and below the mirrored tab depth, so the
        // grid geometry below is the plain 24-unit disc box exactly as it was before the tab existed.
        canvas.translate(pad, pad + TAB_DEPTH_GRID * scale)

        // The body: disc ∪ tab, filled with the route's display color (gray when not real-time) and
        // stroked black as one silhouette, so no seam shows where the tab meets the disc.
        MarkerRendering.drawOutlinedPath(canvas, bodyPath(scale, hasTab = fill != null, outline), color, outline)
        MarkerRendering.drawGlyph(
            canvas,
            context,
            glyphRes(type),
            MarkerRendering.GRID / 2f * scale,
            MarkerRendering.GRID / 2f * scale,
            GLYPH_SIZE / 2f * scale,
            outline,
            onColor
        )

        // The pip row: all [MAX_PIPS] silhouettes always drawn, the first [fill] of them full and the rest
        // empty, so the row reads as a filled fraction of a fixed scale rather than as a bare count.
        //
        // The polarity is **fixed** — empty is white, full is black, on every disc colour — rather than
        // following [onColor] as the glyph does. Following it would tie the row's meaning to the route's
        // colour, and on a dark disc, where onColor is already white, a white "full" pip would be
        // indistinguishable from a white "empty" one; the row would stop saying anything at all. The
        // black rim both states share is what keeps the white ones legible on a pale disc.
        //
        // An empty pip is washed to 50% ([EMPTY_PIP_FILL]) so it recedes behind the full ones. It takes
        // two passes: the tab's own colour first, then the wash. [MarkerRendering.drawOutlined] builds
        // its rim by stamping the artwork black at eight offsets, which lays solid black under the whole
        // silhouette, not just around it — so a translucent fill painted straight onto that composites
        // over black and comes out grey, i.e. heavier than an opaque white pip rather than lighter. The
        // opaque first pass covers that underlay with the tab colour, leaving the wash to land on the
        // tab exactly as a 50% white would, with the rim still at full strength.
        //
        // The cost, accepted deliberately: on a near-black route colour the black full pips sink into the
        // tab. Reaching for [MarkerRendering.legibleOn] here would fix that and re-introduce the
        // collision, so the fix — if that turns out to matter — is a tab that carries its own surface
        // colour, not a per-route pip polarity.
        //
        // One drawable, re-bounded and re-tinted per pip — they're the same artwork, so loading it three
        // times would repeat the vector inflate and rasterize for nothing.
        if (fill != null) {
            val pitch = PIP_SIZE_GRID + PIP_SPACING_GRID
            val firstCx = (MarkerRendering.GRID - (MAX_PIPS - 1) * pitch) / 2f
            val half = PIP_SIZE_GRID / 2f * scale
            val cy = PIP_ROW_CY_GRID * scale
            val pip = requireDrawable(context, R.drawable.ic_occupancy).mutate()
            repeat(MAX_PIPS) { i ->
                val cx = (firstCx + i * pitch) * scale
                pip.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
                if (i < fill) {
                    MarkerRendering.drawOutlined(canvas, pip, outline, Color.BLACK)
                } else {
                    MarkerRendering.drawOutlined(canvas, pip, outline, color)
                    pip.setTint(EMPTY_PIP_FILL)
                    pip.draw(canvas)
                }
            }
        }
        return bitmap
    }

    /**
     * The marker's silhouette in the translated content space: the disc, unioned with the occupancy tab
     * when [hasTab]. Inset by 1.5×[outline] so that stroking it [outline] wide puts the black rim's
     * outer edge exactly where the disc's rim used to sit — the tabless marker is pixel-wise the disc
     * this replaced.
     */
    private fun bodyPath(scale: Float, hasTab: Boolean, outline: Float): Path {
        val center = MarkerRendering.GRID / 2f * scale
        val inset = 1.5f * outline
        val path = Path().apply { addCircle(center, center, center - inset, Path.Direction.CW) }
        if (hasTab) {
            val radius = TAB_CORNER_RADIUS_GRID * scale
            val tab = Path().apply {
                // Rounded on all four corners, but only the bottom pair survives the union — the top pair
                // sits inside the disc, which swallows it.
                addRoundRect(
                    center - TAB_HALF_WIDTH_GRID * scale + inset,
                    TAB_TOP_GRID * scale,
                    center + TAB_HALF_WIDTH_GRID * scale - inset,
                    TAB_BOTTOM_GRID * scale - inset,
                    radius,
                    radius,
                    Path.Direction.CW
                )
            }
            path.op(tab, Path.Op.UNION)
        }
        return path
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
