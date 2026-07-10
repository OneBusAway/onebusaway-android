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
package org.onebusaway.android.map.googlemapsv2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.util.SparseArray
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import org.onebusaway.android.R
import org.onebusaway.android.map.render.MarkerRendering
import org.onebusaway.android.map.render.StopBitmaps
import org.onebusaway.android.map.render.StopDirection
import org.onebusaway.android.models.ObaRoute
import kotlin.math.roundToInt

/**
 * Stateless factory for bus-stop marker icons, extracted from the old StopOverlay. It holds the
 * pre-rendered direction/route-type bitmap caches (normal + focused) and the anchor offsets; the
 * declarative ObaMapContent renderer asks it for a [BitmapDescriptor] + anchor per stop while the
 * imperative marker bookkeeping (MarkerData, focus, clicks) goes away. Icons are built once on first
 * use.
 *
 * The circle + direction-arrow drawing lives in the shared [StopBitmaps.directionalStopMarker] (also
 * used by the maplibre flavor); this factory adds the Google-specific route-type glyph, descriptor
 * caching, and per-direction marker anchors.
 */
object StopIconFactory {

    private const val TAG = "StopIconFactory"

    private var loaded = false

    /**
     * Descriptor cache keyed by route type, each a BitmapDescriptor array indexed by
     * [StopDirection.ordinal]. The descriptors (native texture wrappers) are built once so re-iconing
     * markers — e.g. swapping every stop full icon ⇄ dot on a zoom-band crossing, up to the full stop
     * cache at once (default 200, up to 2000) — reuses them instead of minting a fresh texture per
     * marker (the cost that made the transition stutter).
     */
    private val stopDescriptors = SparseArray<Array<BitmapDescriptor>>()

    /** Focused (selected) variant of [stopDescriptors]. */
    private val stopDescriptorsFocused = SparseArray<Array<BitmapDescriptor>>()

    /** The small directionless dot shown in place of the full icon at distant zoom (declutter). */
    private lateinit var dotDescriptor: BitmapDescriptor

    /**
     * The focused variant of [dotDescriptor]: a larger ([StopBitmaps.FOCUSED_DOT_SCALE]) accent dot,
     * so the selected stop stays visible and clearly larger than its neighbours far out.
     */
    private lateinit var dotDescriptorFocused: BitmapDescriptor

    /**
     * The distinctive star shown for a starred (favorite) stop (#1680), in place of its directional
     * icon up close / its dot far out — with a focused (enlarged) variant of each.
     *
     * The full-band star keeps the direction arrow the other stop markers carry, so it's per-direction
     * (indexed by [StopDirection.ordinal]) and anchored the same way. The dot-band star has no arrow
     * (matching the plain dot), so it's a single direction-agnostic descriptor anchored at the marker
     * center (0.5, 0.5). Route-type agnostic in both bands (no route glyph on the star).
     */
    private lateinit var favoriteDescriptors: Array<BitmapDescriptor>

    private lateinit var favoriteDescriptorsFocused: Array<BitmapDescriptor>

    private lateinit var starDotDescriptor: BitmapDescriptor

    private lateinit var starDotDescriptorFocused: BitmapDescriptor

    /**
     * Route types that get distinct stop icons on the map.
     * TYPE_CABLECAR uses TYPE_TRAM icon; TYPE_GONDOLA/TYPE_FUNICULAR fall back to TYPE_BUS.
     */
    private val ICON_ROUTE_TYPES = intArrayOf(
        ObaRoute.TYPE_BUS,
        ObaRoute.TYPE_RAIL,
        ObaRoute.TYPE_SUBWAY,
        ObaRoute.TYPE_TRAM,
        ObaRoute.TYPE_FERRY,
    )

    // (The primary-route-type priority that used to live here is now the pure primaryRouteType() in
    // src/main, called by GoogleMapHost when it builds StopMarkers.)

    private const val FOCUS_ICON_SCALE = 1.5f

    /**
     * Scale factor for stop icons to make the vehicle glyph clearly visible inside the circle.
     * All stop types (bus, rail, subway, tram, ferry) display a glyph, matching iOS/Wayfinder.
     */
    private const val GLYPH_ICON_SCALE = 1.35f

    /**
     * Fraction of the (GLYPH_ICON_SCALE-scaled) icon size occupied by the route-type glyph inside the
     * stop circle. The mode glyphs are standard 24dp Material icons whose artwork fills the ~20x20dp
     * live area, so they are shrunk here to sit within the circle with margin. This is the single knob
     * for glyph size and applies to all modes uniformly (bus/rail/subway/tram/ferry). Lower = smaller.
     */
    private const val GLYPH_SIZE_FRACTION = 0.60f

    private var basePx = 0 // Bus stop icon size

    // % offset to position the stop icon, so the selection marker hits the middle of the circle
    private var percentOffset = 0.5f

    /**
     * Cached route type glyph bitmaps (ic_train, ic_tram, etc.) keyed by ObaRoute.TYPE_* constant.
     * Loaded once during loadIcons() and drawn inside the stop circle for all stop types.
     */
    private val routeTypeGlyphs = SparseArray<Bitmap>()

    // The glyphs are pre-rendered white in loadRouteTypeGlyphs (rasterize tint), so this just blits them.
    private val glyphPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    /**
     * Builds the icon caches on first use (the old constructor called loadIcons() each time). The
     * [context] is used only to read app resources while rendering the bitmaps; only the first call
     * (which populates the caches) touches it.
     */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (!loaded) {
            loadIcons(context)
            loaded = true
        }
    }

    /** The normal (unfocused) stop icon for a direction + primary route type. */
    @Synchronized
    fun stopIcon(context: Context, direction: String, routeType: Int): BitmapDescriptor {
        ensureLoaded(context)
        return lookupStopIcon(stopDescriptors, direction, routeType)
    }

    /** The focused (1.5x) stop icon for a direction + primary route type. */
    @Synchronized
    fun focusedStopIcon(context: Context, direction: String, routeType: Int): BitmapDescriptor {
        ensureLoaded(context)
        return lookupStopIcon(stopDescriptorsFocused, direction, routeType)
    }

    /**
     * The small dot shown in place of the full icon at distant zoom. Directionless and route-type
     * agnostic (a neutral themed point), so the caller anchors it at the marker center (0.5, 0.5).
     */
    @Synchronized
    fun dotStopIcon(context: Context): BitmapDescriptor {
        ensureLoaded(context)
        return dotDescriptor
    }

    /** The focused (accent) dot, shown for the selected stop at distant zoom. */
    @Synchronized
    fun focusedDotStopIcon(context: Context): BitmapDescriptor {
        ensureLoaded(context)
        return dotDescriptorFocused
    }

    /** The distinctive star (with the direction arrow) for a starred stop up close. */
    @Synchronized
    fun favoriteStopIcon(context: Context, direction: String): BitmapDescriptor {
        ensureLoaded(context)
        return favoriteDescriptors[StopDirection.fromKey(direction).ordinal]
    }

    /** The focused (enlarged) star (with the direction arrow) for the selected starred stop up close. */
    @Synchronized
    fun focusedFavoriteStopIcon(context: Context, direction: String): BitmapDescriptor {
        ensureLoaded(context)
        return favoriteDescriptorsFocused[StopDirection.fromKey(direction).ordinal]
    }

    /** The smaller star shown for a starred stop in the far-zoom dot band. */
    @Synchronized
    fun favoriteDotStopIcon(context: Context): BitmapDescriptor {
        ensureLoaded(context)
        return starDotDescriptor
    }

    /** The focused (enlarged) star for the selected starred stop in the dot band. */
    @Synchronized
    fun focusedFavoriteDotStopIcon(context: Context): BitmapDescriptor {
        ensureLoaded(context)
        return starDotDescriptorFocused
    }

    /**
     * Marker anchor X for the given direction (positions the pin tip on the circle center). Takes
     * [context] to [ensureLoaded] first: the anchor can be requested before any icon for the same stop
     * (see GoogleMapRenderer), and it's the icon load that populates [percentOffset] — without this the
     * first marker would anchor off the default 0.5f.
     */
    @Synchronized
    fun anchorX(context: Context, direction: String): Float {
        ensureLoaded(context)
        return 0.5f + StopDirection.fromKey(direction).xSign * percentOffset
    }

    /** Marker anchor Y for the given direction. See [anchorX]. */
    @Synchronized
    fun anchorY(context: Context, direction: String): Float {
        ensureLoaded(context)
        return 0.5f + StopDirection.fromKey(direction).ySign * percentOffset
    }

    /**
     * Cache the BitmapDescriptors that hold the images used for icons
     */
    private fun loadIcons(context: Context) {
        // Initialize variables used for all marker icons
        val r = context.resources
        basePx = r.getDimensionPixelSize(R.dimen.map_stop_shadow_size_6)
        // Offset that lands the selection marker on the middle of the stop circle (see anchorX/anchorY);
        // derived from the shared arrow geometry so it can't desync from directionalStopMarker.
        percentOffset = StopBitmaps.anchorPercentOffset(basePx)

        // The theme arrow colors (tip→base gradient), fetched once and threaded into every icon build.
        val arrowTip = ContextCompat.getColor(context, R.color.theme_primary)
        val arrowBase = ContextCompat.getColor(context, R.color.theme_accent)

        // Pre-scale route type glyph icons to the target circle size
        val px = (basePx * GLYPH_ICON_SCALE).toInt()
        val glyphSizePx = (px * GLYPH_SIZE_FRACTION).toInt()
        loadRouteTypeGlyphs(context, glyphSizePx)

        for (routeType in ICON_ROUTE_TYPES) {
            val icons = StopDirection.entries.map {
                createStopIcon(context, it, selected = false, routeType, arrowTip, arrowBase)
            }.toTypedArray()
            // Scale the focused icons to be larger than the normal icons
            val iconsFocused = StopDirection.entries.map {
                StopBitmaps.scale(
                    createStopIcon(context, it, selected = true, routeType, arrowTip, arrowBase),
                    FOCUS_ICON_SCALE,
                )
            }.toTypedArray()
            stopDescriptors.put(routeType, toDescriptors(icons))
            stopDescriptorsFocused.put(routeType, toDescriptors(iconsFocused))
        }

        // Star colors: the normal star is the gold gradient (light→dark); a selected star uses the same
        // focus color as every other selected stop (solid). The arrow keeps the theme primary→accent.
        val starLight = ContextCompat.getColor(context, R.color.map_stop_favorite_light)
        val starDark = ContextCompat.getColor(context, R.color.map_stop_favorite_dark)
        val focusColor = ContextCompat.getColor(context, R.color.map_stop_focus)
        // The star outline matches the plain circle's ring width (same for full- and dot-band), drawn at
        // the star's own scale so the star inflation doesn't thicken it.
        val starOutlinePx = StopBitmaps.STAR_OUTLINE_WIDTH_DP * r.displayMetrics.density
        val starPx = (basePx * StopBitmaps.STAR_SIZE_SCALE).roundToInt()

        // Starred-stop full-band icons: an inflated star with the normal-sized direction arrow drawn on
        // top, per direction. Route-type agnostic (no route glyph). The focused variant is the selected-
        // color star, enlarged like every other focused marker.
        fun favoriteStars(topColor: Int, bottomColor: Int, focused: Boolean): Array<BitmapDescriptor> =
            toDescriptors(
                StopDirection.entries.map { direction ->
                    val marker = StopBitmaps.favoriteMarker(
                        px, starPx, direction != StopDirection.NONE, direction.compassAngle,
                        topColor, bottomColor, arrowTip, arrowBase, starOutlinePx,
                    )
                    if (focused) StopBitmaps.scale(marker, FOCUS_ICON_SCALE) else marker
                }.toTypedArray(),
            )
        favoriteDescriptors = favoriteStars(starLight, starDark, focused = false)
        favoriteDescriptorsFocused = favoriteStars(focusColor, focusColor, focused = true)

        dotDescriptor = BitmapDescriptorFactory.fromBitmap(StopBitmaps.dot(basePx, arrowTip))
        dotDescriptorFocused = BitmapDescriptorFactory.fromBitmap(
            StopBitmaps.dot(basePx, focusColor, StopBitmaps.FOCUSED_DOT_SCALE),
        )

        // Dot-band starred stops: a plain star (no arrow, matching the plain dot), dot-sized and enlarged
        // when focused. Gold gradient normally, the selected color when focused; same thin outline.
        val starDotPx = (basePx * 0.5f * StopBitmaps.STAR_SIZE_SCALE).roundToInt()
        starDotDescriptor = BitmapDescriptorFactory.fromBitmap(
            StopBitmaps.star(starDotPx, starLight, starDark, starOutlinePx),
        )
        starDotDescriptorFocused = BitmapDescriptorFactory.fromBitmap(
            StopBitmaps.star(
                (starDotPx * StopBitmaps.FOCUSED_DOT_SCALE).roundToInt(),
                focusColor, focusColor, starOutlinePx,
            ),
        )
    }

    /** Wraps each pre-rendered bitmap into a BitmapDescriptor once, so callers can reuse them. */
    private fun toDescriptors(bitmaps: Array<Bitmap>): Array<BitmapDescriptor> =
        Array(bitmaps.size) { BitmapDescriptorFactory.fromBitmap(bitmaps[it]) }

    /**
     * Creates a stop icon: the shared circle + direction arrow, with this flavor's route-type glyph
     * stamped in the center of the circle (on top of the arrow) via the builder's onCircle callback.
     *
     * @param direction stop direction; [StopDirection.NONE] draws no direction arrow
     * @param selected  true to use the selected icon style, false for normal icon style
     * @param routeType one of ObaRoute.TYPE_* constants indicating the stop's primary route type
     * @param arrowTip  tip color of the direction arrow's gradient (theme primary)
     * @param arrowBase base color of the direction arrow's gradient (theme accent)
     */
    private fun createStopIcon(
        context: Context,
        direction: StopDirection,
        selected: Boolean,
        routeType: Int,
        arrowTip: Int,
        arrowBase: Int,
    ): Bitmap {
        // All stops get a slightly larger circle so the vehicle glyph is clearly visible
        val px = (basePx * GLYPH_ICON_SCALE).toInt()
        val shape = requireNotNull(
            ContextCompat.getDrawable(
                context,
                if (selected) R.drawable.selected_map_stop_icon else R.drawable.map_stop_icon,
            ),
        )
        return StopBitmaps.directionalStopMarker(shape, direction, px, arrowTip, arrowBase) { canvas, bounds ->
            drawRouteTypeSymbol(canvas, bounds, routeType)
        }
    }

    /**
     * Loads and pre-scales route type glyph bitmaps (ic_train, ic_tram, etc.) into the cache.
     * These are the same icons used in TripDetailsListFragment for route type display.
     * Pre-scaling avoids repeated Bitmap.createScaledBitmap() calls during icon generation.
     *
     * @param context     Context to load drawables from
     * @param glyphSizePx target glyph size in pixels (already scaled for circle size)
     */
    private fun loadRouteTypeGlyphs(context: Context, glyphSizePx: Int) {
        val glyphMapping = arrayOf(
            ObaRoute.TYPE_BUS to R.drawable.ic_bus,
            ObaRoute.TYPE_RAIL to R.drawable.ic_train,
            ObaRoute.TYPE_SUBWAY to R.drawable.ic_subway,
            ObaRoute.TYPE_TRAM to R.drawable.ic_tram,
            ObaRoute.TYPE_FERRY to R.drawable.ic_ferry,
        )
        for ((type, drawableRes) in glyphMapping) {
            // Rasterize white via the shared helper (its SRC_IN tint recolors the glyph, like
            // VehicleBitmaps/BikeBitmaps), so the glyph is stamped onto the stop circle as-is.
            routeTypeGlyphs.put(
                type,
                MarkerRendering.rasterize(context, drawableRes, glyphSizePx, tint = Color.WHITE),
            )
        }
    }

    /**
     * Draws the pre-scaled route type glyph icon in the center of the stop icon circle.
     * Uses the existing ic_bus, ic_train, ic_tram, ic_subway, ic_ferry PNG assets — the same
     * icons used in TripDetailsListFragment and matching the iOS/Wayfinder transport glyphs.
     *
     * @param canvas       the canvas to draw on
     * @param circleBounds the bounds of the circle drawable
     * @param routeType    one of ObaRoute.TYPE_* constants
     */
    private fun drawRouteTypeSymbol(canvas: Canvas, circleBounds: Rect, routeType: Int) {
        val normalizedType = normalizeRouteType(routeType)
        val glyph = routeTypeGlyphs.get(normalizedType)
        if (glyph == null) {
            Log.w(TAG, "No glyph loaded for route type $routeType")
            return
        }

        val cx = circleBounds.centerX().toFloat()
        val cy = circleBounds.centerY().toFloat()

        canvas.drawBitmap(
            glyph,
            cx - glyph.width / 2f,
            cy - glyph.height / 2f,
            glyphPaint,
        )
    }

    /**
     * Normalizes a route type to one that has a distinct icon.
     * TYPE_CABLECAR maps to TYPE_TRAM; unsupported types fall back to TYPE_BUS.
     *
     * @param routeType one of ObaRoute.TYPE_* constants
     * @return the normalized route type that has a pre-rendered icon set
     */
    private fun normalizeRouteType(routeType: Int): Int = when (routeType) {
        ObaRoute.TYPE_RAIL, ObaRoute.TYPE_SUBWAY, ObaRoute.TYPE_TRAM, ObaRoute.TYPE_FERRY -> routeType
        ObaRoute.TYPE_CABLECAR -> ObaRoute.TYPE_TRAM
        else -> ObaRoute.TYPE_BUS
    }

    /**
     * Looks up a cached stop descriptor by direction and route type.
     * Falls back to TYPE_BUS if the requested type is not found, then to the default marker.
     *
     * @param cache     descriptor cache to look up from (normal or focused)
     * @param direction stop direction string
     * @param routeType one of ObaRoute.TYPE_* constants
     * @return BitmapDescriptor for the stop icon
     */
    private fun lookupStopIcon(
        cache: SparseArray<Array<BitmapDescriptor>>,
        direction: String,
        routeType: Int,
    ): BitmapDescriptor {
        val normalizedType = normalizeRouteType(routeType)
        val icons = cache.get(normalizedType) ?: cache.get(ObaRoute.TYPE_BUS)
        if (icons == null) {
            Log.w(TAG, "Stop icons not initialized for type $routeType")
            return BitmapDescriptorFactory.defaultMarker()
        }
        return icons[StopDirection.fromKey(direction).ordinal]
    }
}
