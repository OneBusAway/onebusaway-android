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
import kotlin.math.sqrt
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
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
 * Whichever it draws, the bitmap is **vertically symmetric about the disc center**: a tabbed marker
 * mirrors the tab's depth as transparent padding above the disc, and a tabless one reserves neither.
 * Symmetry, not a shared box, is the property that matters — it puts the disc at the bitmap's center in
 * both cases, so every vehicle marker keeps a plain center anchor in both flavors (maplibre's classic
 * `Marker` centers an icon on its point with no anchor control at all) and the disc, not the union's
 * centroid, stays on the route centerline. It also means the disc doesn't shift when a feed gaps and a
 * vehicle's fullness comes and goes between polls: the tab appears and disappears beneath a marker that
 * doesn't move, even though the two bitmaps are different sizes.
 */
object VehicleBitmaps {

    private const val DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS // fall back on bus

    // Bounded in **bytes**, not entries: a tabbed marker is ~100 KiB at xxhdpi and ~180 KiB at xxxhdpi
    // (a tabless one ~64/~113 KiB, since it reserves no tab depth), and a zoomed-out half-scale one is a
    // quarter of that — so an entry count would mean wildly different memory on different devices for the
    // same nominal size. 4 MiB holds ~40 full-scale tabbed markers at xxhdpi.
    //
    // Those per-entry figures grew when occupancy moved into the tab, but the working set shrank far more:
    // it was 8 heading octants x 4 pip levels = 32 icons per (mode, disc colour), and is now 5 fullness
    // states (absent + 0..3 filled) — a handful for one route once its scheduled vehicles are counted, and
    // stop-focus/continuation views draw several routes at once, so this is sized for a few of those
    // rather than one. Overflow only costs a re-render. The Google flavor has a second-level
    // BitmapDescriptor cache in front of this; maplibre doesn't, which is why the bound lives here.
    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024

    /** The composited marker fills a square this many dp on a side (the former raster's size). */
    @VisibleForTesting
    internal const val MARKER_SIZE_DP = 40f

    /** Transparent padding (grid units) around the disc so the outline halo isn't clipped. */
    @VisibleForTesting
    internal const val PAD_GRID = 0.6f

    /**
     * The occupancy tab (grid units): a rounded rectangle centered under the disc, unioned with it.
     * [TAB_DEPTH_GRID] is how far it hangs below the disc, and so also the transparent padding mirrored
     * above the disc that keeps a tabbed bitmap symmetric about the disc's center — see the class KDoc.
     */
    @VisibleForTesting
    internal const val TAB_DEPTH_GRID = 7.6f

    private const val TAB_HALF_WIDTH_GRID = 10.7f
    private const val TAB_BOTTOM_GRID = MarkerRendering.GRID + TAB_DEPTH_GRID
    private const val TAB_CORNER_RADIUS_GRID = 2.8f

    /**
     * The rim's width in grid units, converted from the shared [MarkerRendering.MARKER_STROKE_DP] through
     * this marker's own size — so a vehicle badge and the fast-estimate / last-fix markers beside it on
     * the trip map carry the same drawn rim, rather than this one's hairline against their 2 dp.
     *
     * Derived rather than written out so the two can't drift apart when either size moves.
     */
    private const val OUTLINE_GRID = MarkerRendering.MARKER_STROKE_DP * MarkerRendering.GRID / MARKER_SIZE_DP

    /**
     * The margin between the drawn silhouette and the nominal 24-grid box — enough that the antialiased
     * rim isn't flush with the bounds.
     *
     * Deliberately **independent of [OUTLINE_GRID]**, which is the whole point of naming it. The geometry
     * used to hang off the rim's width (the path was inset 1.5x the outline, putting the drawn edge at
     * `12 - outline`), a rule inherited from when the rim was a hairline and the difference didn't show.
     * Widening the rim under that rule would have pulled the entire silhouette inward — an 8% smaller
     * marker as a side effect of a stroke change. Anchoring the outer edge instead lets the rim thicken
     * *inward*, which is what "make the rim wider" means.
     */
    private const val SILHOUETTE_MARGIN_GRID = 0.25f

    /** Where the rim's outer edge lands: the marker's drawn radius, whatever the rim's width. */
    private const val SILHOUETTE_RADIUS_GRID = MarkerRendering.GRID / 2f - SILHOUETTE_MARGIN_GRID

    /**
     * The radius [bodyPath] is built at: half a stroke inside the silhouette, since
     * [MarkerRendering.drawOutlinedPath] strokes the rim **centered on the path** — so half of it falls
     * outside, and the outer half is what has to land on [SILHOUETTE_RADIUS_GRID].
     */
    private const val DISC_RADIUS_GRID = SILHOUETTE_RADIUS_GRID - OUTLINE_GRID / 2f

    /**
     * How far inside its nominal bounds [bodyPath] is built, in grid units.
     *
     * Named rather than inlined because it shrinks the tab's *effective* half-width too, which the fit
     * rule below has to account for — that omission is what let the tab's corners drift outside the disc.
     */
    private const val PATH_INSET_GRID = MarkerRendering.GRID / 2f - DISC_RADIUS_GRID

    /**
     * The mode glyph's box, **derived** as the largest square that fits inside the rim.
     *
     * The bound is stated on the box rather than on the ink because the ink is not knowable here: each
     * mode drawable leaves a margin of its own, and they don't agree (the five run to roughly 70-85% of
     * their viewport). Fitting the box means a glyph drawn edge to edge would still land on the fill
     * rather than lap the rim, so this holds for artwork that doesn't exist yet.
     *
     * Derived for the same reason [TAB_TOP_GRID] is: the fit depends on [SILHOUETTE_RADIUS_GRID] and
     * [OUTLINE_GRID], and a literal with the arithmetic in prose beside it goes quietly wrong the next
     * time one of those moves. The square's half-diagonal is `GLYPH_SIZE/2 * √2`, so setting that equal
     * to the rim's inner edge and solving gives the expression below.
     *
     * It therefore *falls* when the rim widens — the rim eats its room from the inside. Matching the rim
     * to the trip markers' 2 dp cost the glyph about 8%, leaving it ~14.9 units against the 10.8 it sat
     * at before #2055.
     */
    private val GLYPH_SIZE: Float = (SILHOUETTE_RADIUS_GRID - OUTLINE_GRID) * 2f / sqrt(2f)

    /**
     * The tab's top edge, **derived** so its upper corners stay buried in the disc.
     *
     * The tab has to clear the disc by a whole corner radius, not just by its own width: the corner arc
     * reaches the tab's full half-width [TAB_CORNER_RADIUS_GRID] below the top edge, and the disc narrows
     * as it descends. Set the top too low and that arc's lower flank pokes out, so the *upper* rounding
     * shows as a flare on the shoulder instead of the clean tangent the silhouette wants.
     *
     * So: find the depth at which the disc has narrowed to the tab's effective half-width, and put the
     * arc's bottom [TAB_TOP_CLEARANCE_GRID] above it. Everything above that is inside the disc and the
     * union erases it, which is why lowering the top edge further costs nothing and is not tuned by eye.
     * Deriving it means a change to the tab's width, depth or corner radius can't silently break the fit
     * the way hand-recomputed prose did.
     */
    private val TAB_TOP_GRID: Float = run {
        val halfWidth = TAB_HALF_WIDTH_GRID - PATH_INSET_GRID
        val flankDepth = sqrt(DISC_RADIUS_GRID * DISC_RADIUS_GRID - halfWidth * halfWidth)
        MarkerRendering.GRID / 2f + flankDepth - TAB_CORNER_RADIUS_GRID - TAB_TOP_CLEARANCE_GRID
    }

    /** Margin by which the tab's corner arc clears the disc's flank; free, since it is all buried. */
    private const val TAB_TOP_CLEARANCE_GRID = 0.5f

    // The pip row: three person silhouettes in the tab, at 5 grid units each (~8.3 dp) with the row
    // spanning 16.4 of the tab's 21.4 width.
    //
    // The row is seated a fixed distance off the tab's bottom rather than centered in it. ic_occupancy is
    // drawn to the full height of its own box and ends in a flat-bottomed body, so its lowest pixels
    // carry far more weight than its top; centering it optically would leave that heavy edge fused with
    // the rim, which reads as a clipped pip rather than a seated one. Stating it as a seat off the bottom
    // means a change to [TAB_DEPTH_GRID] carries the row with it instead of silently unseating it.
    private const val PIP_SIZE_GRID = 5f
    private const val PIP_SPACING_GRID = 0.7f
    private const val PIP_SEAT_GRID = 5.5f
    private const val PIP_ROW_CY_GRID = TAB_BOTTOM_GRID - PIP_SEAT_GRID

    /** The pips in the tab — always all drawn, [OccupancyBucket.pips] of them inked. */
    internal const val MAX_PIPS = 3

    /**
     * An empty pip's fill: white at 80%, so the unfilled slots read as present but a shade quieter than
     * the full ones — the row's length states the scale, and the black pips still lead it.
     */
    private const val EMPTY_PIP_FILL = 0xCCFFFFFF.toInt()

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
        occupancyBucket(vehicle),
        sizeScale
    )

    /**
     * A stable key identifying the icon [vehicleBitmap] returns for this vehicle — its type, disc color,
     * rim color, fullness, and size scale, the only inputs that change the bitmap. A renderer caches one wrapper (a
     * Google `BitmapDescriptor`) per key so it reuses it across frames even when the bounded bitmap LRU
     * evicts and recreates the underlying [Bitmap] on a busy route.
     *
     * Note what is *not* here: the vehicle's heading. The marker carried a heading arrow until #2194's
     * occupancy tab took the space below the disc that the arrow's southern octants swing through; with
     * the arrow gone the icon no longer varies with bearing, which is why neither renderer re-stamps a
     * gliding vehicle's icon between polls any more.
     *
     * Both color components are the **resolved ARGB value**, not a color resource id: a route color
     * (#2043) is a raw ARGB int off the wire with no resource id to name it. Keying on the resolved
     * value also closes a latent staleness bug the resource-id key had — the same id resolves to a
     * different color after a light/dark switch, which the old key could not tell apart. That is not
     * hypothetical for the rim, whose whole point is to differ by mode (#2055): it is the reason the
     * rim is keyed on at all, since it is otherwise constant across every vehicle in a frame. The
     * glyph and pip colors are derived from the disc/rim, so they add no distinguishing power.
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
            outlineColor(context),
            occupancyBucket(vehicle),
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
     * How full this vehicle is, or **null when it reports nothing** — in which case the marker is a plain
     * disc with no tab. The bucketing itself lives on [OccupancyBucket]; this adds the one rule that is
     * about the *marker* rather than the data.
     *
     * That rule: a vehicle without real-time reports nothing, whatever its status field says. It has no
     * observed occupancy, and a gray marker that grew a tab would be claiming data it doesn't have (#959).
     */
    @VisibleForTesting
    internal fun occupancyBucket(vehicle: VehicleMarker): OccupancyBucket? = if (vehicle.isRealtime) OccupancyBucket.of(vehicle.status.occupancyStatus) else null

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
     *
     * The one exception is the **selected** vehicle, which takes [VehicleMarker.bandColor] instead: it is
     * the median estimate at the middle of its own uncertainty band, and drawing it in the band's colour
     * (as the two markers bounding that band are) says so (#1990). Identity is not lost with it — the
     * selected vehicle is the one the rider just picked out, and the band beneath it is drawn along its
     * line. Liveness still wins over both: a scheduled vehicle stays gray, which is also why a selection
     * can only reach this branch while it is live, exactly as the band itself can.
     */
    @VisibleForTesting
    internal fun discColor(context: Context, vehicle: VehicleMarker): Int = if (vehicle.isRealtime) {
        vehicle.bandColor ?: vehicle.routeColor ?: DEFAULT_ROUTE_LINE_COLOR
    } else {
        ContextCompat.getColor(context, ScheduleDeviation.Status.SCHEDULED.displayColorRes)
    }

    /**
     * The rim drawn around the marker's silhouette: gray in light mode, white in dark (#2055).
     *
     * The rim's job is to hold the marker apart from what's *behind* it, and what's behind it is a base
     * map the app restyles by mode (`R.raw.light_map` / `R.raw.dark_map`) — so a rim fixed at one dark
     * value separated the marker from a pale basemap and then dissolved into the dark one, taking the
     * disc's edge with it on any route color close to the night basemap. It resolves from a qualified
     * resource rather than branching on `ThemeUtils.isInDarkMode`, so it flips in the same place as the
     * other rims on the same map.
     *
     * The gray is the one the route-stop circles beside it already use (`route_stop_outline` carries the
     * same pair), so the two rims a rider sees over the base map agree. It is deliberately *not* shared
     * with [TripMarkerBitmaps]: those discs take the uncertainty band's colour as their fill (#1990), so
     * their ring answers to that fill via [MarkerRendering.legibleOn] rather than to the base map, and a
     * rim that went white in dark mode would vanish on a pale band. What the two families do share is the
     * width, [MarkerRendering.MARKER_STROKE_DP].
     *
     * Nothing *on* the marker follows the mode: the glyph and pips sit on the disc, whose color comes off
     * the wire and doesn't move with the theme, so they keep taking their ink from it — see [renderMarker].
     */
    @VisibleForTesting
    internal fun outlineColor(context: Context): Int = ContextCompat.getColor(context, R.color.map_marker_outline)

    private fun getBitmap(
        context: Context,
        vehicleType: Int,
        color: Int,
        occupancy: OccupancyBucket?,
        sizeScale: Float
    ): Bitmap {
        // The rim isn't threaded through as a value: both the key and the render read it from this same
        // context through [outlineColor], so they can't name different icons (as `routeTypeFor` below).
        val key = createBitmapCacheKey(vehicleType, color, outlineColor(context), occupancy, sizeScale)
        return sColoredIconCache.get(key)
            ?: renderMarker(context, vehicleType, color, occupancy, sizeScale)
                .also { sColoredIconCache.put(key, it) }
    }

    /** [color] and [outlineColor] are resolved ARGB values — see [iconKey] for why they can't be resource ids. */
    private fun createBitmapCacheKey(
        vehicleType: Int,
        color: Int,
        outlineColor: Int,
        occupancy: OccupancyBucket?,
        sizeScale: Float
    ): String {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        // "none" rather than an empty slot: the tabless marker is its own icon, distinct from an empty one.
        return "$type $color $outlineColor ${occupancy?.name ?: "none"} ${sizeScale.toBits()}"
    }

    /**
     * Uncached render of a single marker for a given type/color/fullness, rimmed for [context]'s current
     * mode. Exposed for the `@Preview` grid (and tests); the production path goes through [vehicleBitmap]
     * which caches.
     */
    @VisibleForTesting
    internal fun previewBitmap(
        context: Context,
        vehicleType: Int,
        color: Int,
        occupancy: OccupancyBucket?
    ): Bitmap = renderMarker(context, vehicleType, color, occupancy, 1f)

    /**
     * Composites the marker body — a disc, unioned with the occupancy tab when [occupancy] is non-null —
     * then the mode glyph centered on the disc, then the tab's pips. **Only the body is rimmed**, stroked
     * in [outlineColor] at [OUTLINE_GRID]: it is the one part with a base map behind it, and the rim is
     * what holds its silhouette off that map. What goes on top of it — glyph, pips — is already on a
     * surface chosen to carry it, so each of those draws flat (see the glyph and pip comments below).
     *
     * A tabbed bitmap reserves [TAB_DEPTH_GRID] above the disc as well as below; a tabless one reserves
     * neither, since with no tab both bands would be empty — 39% of the bitmap, on the marker every
     * scheduled vehicle and every occupancy-less feed draws. Either way the disc lands at the bitmap's
     * center, which is the property the anchor depends on — see the class KDoc.
     *
     * The **glyph** takes whichever of black/white reads on [color] rather than a hardcoded white, since
     * a route may be drawn in a shade too pale to carry white — and with the glyph now drawn flat, that
     * choice is the only thing keeping it off the disc. The **pips** deliberately do not: they use
     * a fixed white-empty / black-full polarity (see the pip loop), so a rider learns one reading of the
     * row rather than one per route colour. Neither uses a colour ramp of its own — the disc's colour
     * already means route identity (#2043), and a second colour scale on a 40 dp icon would compete with
     * it (#1079).
     */
    private fun renderMarker(
        context: Context,
        vehicleType: Int,
        color: Int,
        occupancy: OccupancyBucket?,
        sizeScale: Float
    ): Bitmap {
        val type = if (supportedVehicleType(vehicleType)) vehicleType else DEFAULT_VEHICLE_TYPE
        val scale = context.resources.displayMetrics.density *
            MARKER_SIZE_DP *
            sizeScale /
            MarkerRendering.GRID
        val pad = PAD_GRID * scale
        val reserve = if (occupancy != null) TAB_DEPTH_GRID * scale else 0f
        val widthPx = (MarkerRendering.GRID * scale + 2f * pad).toInt()
        val heightPx = (MarkerRendering.GRID * scale + 2f * reserve + 2f * pad).toInt()
        val outline = OUTLINE_GRID * scale
        val outlineColor = outlineColor(context)
        val onColor = MarkerRendering.legibleOn(color)
        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        // Draw inside a [pad] border so the outline has room, and below the mirrored tab depth, so the
        // grid geometry below is the plain 24-unit disc box exactly as it was before the tab existed.
        canvas.translate(pad, pad + reserve)

        // The body: disc ∪ tab, filled with the route's display color (gray when not real-time) and
        // stroked as one silhouette, so no seam shows where the tab meets the disc.
        MarkerRendering.drawOutlinedPath(canvas, bodyPath(scale, hasTab = occupancy != null), color, outline, outlineColor)
        // The mode glyph, rimless: a black dilate under a black glyph — which is what a light route
        // colour asks for — thickened it into a blot rather than defining it.
        MarkerRendering.drawGlyph(
            canvas,
            context,
            glyphRes(type),
            MarkerRendering.GRID / 2f * scale,
            MarkerRendering.GRID / 2f * scale,
            GLYPH_SIZE / 2f * scale,
            glyphColor = onColor
        )

        // The pip row: all [MAX_PIPS] silhouettes always drawn, the first [OccupancyBucket.pips] of them
        // full and the rest empty, so the row reads as a filled fraction of a fixed scale rather than as
        // a bare count.
        //
        // The polarity is **fixed** — empty takes [EMPTY_PIP_FILL], full takes black, on every disc
        // colour — rather than following [onColor] as the glyph does. Following it would tie the
        // row's meaning to the route's colour, and on a dark disc, where onColor is already white, a
        // white "full" pip would be indistinguishable from a white "empty" one; the row would stop
        // saying anything at all.
        //
        // Like the glyph, and unlike the body, the pips carry **no rim**. They sit inside the tab, which
        // is itself rimmed and holds nothing else, so they have no neighbour to be separated from — and
        // at this size a rim on a 5-unit silhouette thickens it more than it defines it. Dropping it
        // also lets the wash go down in one pass: with no dilate laying black under the silhouette, a
        // translucent fill composites straight onto the tab instead of onto black.
        //
        // The cost, accepted deliberately: on a near-black route colour the black full pips sink into the
        // tab. Reaching for [MarkerRendering.legibleOn] here would fix that and re-introduce the
        // collision, so the fix — if that turns out to matter — is a tab that carries its own surface
        // colour, not a per-route pip polarity.
        //
        // One drawable, re-bounded and re-tinted per pip — they're the same artwork, so loading it three
        // times would repeat the vector inflate and rasterize for nothing.
        if (occupancy != null) {
            val pitch = PIP_SIZE_GRID + PIP_SPACING_GRID
            val firstCx = (MarkerRendering.GRID - (MAX_PIPS - 1) * pitch) / 2f
            val half = PIP_SIZE_GRID / 2f * scale
            val cy = PIP_ROW_CY_GRID * scale
            val pip = requireDrawable(context, R.drawable.ic_occupancy).mutate()
            repeat(MAX_PIPS) { i ->
                val cx = (firstCx + i * pitch) * scale
                pip.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
                pip.setTint(if (i < occupancy.pips) Color.BLACK else EMPTY_PIP_FILL)
                pip.draw(canvas)
            }
        }
        return bitmap
    }

    /**
     * The marker's silhouette in the translated content space: the disc, unioned with the occupancy tab
     * when [hasTab]. Built inset by [PATH_INSET_GRID] so that stroking it puts the rim's outer edge
     * exactly where the disc's rim used to sit — the tabless marker is pixel-wise the disc this replaced.
     */
    private fun bodyPath(scale: Float, hasTab: Boolean): Path {
        val center = MarkerRendering.GRID / 2f * scale
        val inset = PATH_INSET_GRID * scale
        val path = Path().apply { addCircle(center, center, DISC_RADIUS_GRID * scale, Path.Direction.CW) }
        if (hasTab) {
            val radius = TAB_CORNER_RADIUS_GRID * scale
            val tab = Path().apply {
                // Rounded on all four corners, but only the bottom pair survives the union — the top pair
                // sits inside the disc, which swallows it (guaranteed by [TAB_TOP_GRID]'s derivation).
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
