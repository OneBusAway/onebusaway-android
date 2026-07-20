/*
 * Copyright (C) 2014-2024 University of South Florida (sjbarbeau@gmail.com)
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
@file:Suppress("DEPRECATION") // classic Icon/IconFactory; SymbolManager migration tracked in #1728

package org.onebusaway.android.map.maplibre

import android.content.Context
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.onebusaway.android.R
import org.onebusaway.android.map.render.StopBitmaps
import org.onebusaway.android.map.render.StopDirection

/**
 * Stateless factory for the maplibre bus-stop marker icons, extracted from the old maplibre
 * StopOverlay. Holds the 9-direction normal + focused icon caches and wraps them as maplibre [Icon]s;
 * the declarative MapRenderState renderer asks it for an icon per stop. Built once on first use.
 * (maplibre markers have no per-marker anchor, so the direction offset math is dropped.)
 *
 * The circle + direction-arrow drawing lives in the shared [StopBitmaps.directionalStopMarker] (also
 * used by the Google flavor, which adds a route-type glyph this flavor omits).
 *
 * Wraps bitmaps as the deprecated-but-functional classic [Icon]/[IconFactory]; the move to the
 * SymbolManager style-image model is part of the feature-level rewrite tracked in issue #1728.
 */
object MapLibreStopIcons {

    private var loaded = false

    // The Icons (native texture wrappers) are built once so re-iconing markers — e.g. swapping every
    // stop full icon ⇄ dot on a zoom-band crossing, up to the full stop cache at once (default 200, up
    // to 2000) — reuses them instead of wrapping a fresh Icon per marker (the cost that made the
    // transition stutter). Each array is indexed by [StopDirection.ordinal].
    private lateinit var stopIcons: Array<Icon>
    private lateinit var stopIconsFocused: Array<Icon>

    /** The small directionless dot shown in place of the full icon at distant zoom (declutter). */
    private lateinit var dotStopIcon: Icon

    /**
     * The focused variant of [dotStopIcon]: a larger ([StopBitmaps.FOCUSED_DOT_SCALE]) accent dot, so
     * the selected stop stays visible and clearly larger than its neighbours far out.
     */
    private lateinit var dotStopIconFocused: Icon

    /**
     * The distinctive star shown for a starred (favorite) stop (#1680), in place of its directional
     * icon up close / its dot far out — with a focused (enlarged) variant of each. The full-band star
     * keeps the direction arrow the other stop markers carry, so it's per-direction; the dot-band star
     * has no arrow (matching the plain dot), so it's a single direction-agnostic icon.
     */
    private lateinit var starStopIcons: Array<Icon>
    private lateinit var starStopIconsFocused: Array<Icon>

    private lateinit var starDotStopIcon: Icon
    private lateinit var starDotStopIconFocused: Icon

    private const val FOCUS_ICON_SCALE = 1.5f

    private var basePx = 0

    /**
     * Builds the icon caches on first use. The [context] is used only to read app resources while
     * rendering the bitmaps; only the first call (which populates the caches) touches it.
     */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (!loaded) {
            loadIcons(context)
            loaded = true
        }
    }

    /** The normal (unfocused) stop icon for a direction string ("N".."NW" / "null"). */
    @Synchronized
    fun iconForDirection(context: Context, direction: String): Icon {
        ensureLoaded(context)
        return stopIcons[StopDirection.fromKey(direction).ordinal]
    }

    /** The focused (1.5x) stop icon for a direction string. */
    @Synchronized
    fun focusedIconForDirection(context: Context, direction: String): Icon {
        ensureLoaded(context)
        return stopIconsFocused[StopDirection.fromKey(direction).ordinal]
    }

    /**
     * The small dot shown in place of the full icon at distant zoom — directionless and route-type
     * agnostic (a neutral themed point). maplibre centers the icon on the position, so it lands on the
     * stop without anchor math.
     */
    @Synchronized
    fun dotIcon(context: Context): Icon {
        ensureLoaded(context)
        return dotStopIcon
    }

    /** The focused (accent) dot, shown for the selected stop at distant zoom. */
    @Synchronized
    fun focusedDotIcon(context: Context): Icon {
        ensureLoaded(context)
        return dotStopIconFocused
    }

    /** The distinctive star (with the direction arrow) for a starred stop up close. */
    @Synchronized
    fun favoriteIcon(context: Context, direction: String): Icon {
        ensureLoaded(context)
        return starStopIcons[StopDirection.fromKey(direction).ordinal]
    }

    /** The focused (enlarged) star (with the direction arrow) for the selected starred stop up close. */
    @Synchronized
    fun focusedFavoriteIcon(context: Context, direction: String): Icon {
        ensureLoaded(context)
        return starStopIconsFocused[StopDirection.fromKey(direction).ordinal]
    }

    /** The smaller star shown for a starred stop in the far-zoom dot band. */
    @Synchronized
    fun favoriteDotIcon(context: Context): Icon {
        ensureLoaded(context)
        return starDotStopIcon
    }

    /** The focused (enlarged) star for the selected starred stop in the dot band. */
    @Synchronized
    fun focusedFavoriteDotIcon(context: Context): Icon {
        ensureLoaded(context)
        return starDotStopIconFocused
    }

    private fun loadIcons(context: Context) {
        val r = context.resources
        basePx = r.getDimensionPixelSize(R.dimen.map_stop_shadow_size_6)

        val iconFactory = IconFactory.getInstance(context)

        // Star colors: the normal star is the gold gradient (light→dark); a selected star uses the same
        // focus color as every other selected stop (solid). The arrow keeps the theme primary→accent.
        val starLight = ContextCompat.getColor(context, R.color.map_stop_favorite_light)
        val starDark = ContextCompat.getColor(context, R.color.map_stop_favorite_dark)
        val focusColor = ContextCompat.getColor(context, R.color.map_stop_focus)
        val arrowTip = ContextCompat.getColor(context, R.color.theme_primary)
        val arrowBase = ContextCompat.getColor(context, R.color.theme_accent)
        // The star outline matches the plain circle's ring width (same for full- and dot-band).
        val starOutlinePx = StopBitmaps.STAR_OUTLINE_WIDTH_DP * r.displayMetrics.density
        val starPx = (basePx * StopBitmaps.STAR_SIZE_SCALE).roundToInt()

        stopIcons = StopDirection.entries.map {
            iconFactory.fromBitmap(createBusStopIcon(context, it, selected = false, arrowTip, arrowBase))
        }.toTypedArray()
        stopIconsFocused = StopDirection.entries.map {
            iconFactory.fromBitmap(
                StopBitmaps.scale(
                    createBusStopIcon(context, it, selected = true, arrowTip, arrowBase),
                    FOCUS_ICON_SCALE
                )
            )
        }.toTypedArray()

        // Starred-stop full-band icon: an inflated star with the normal-sized direction arrow drawn on
        // top; the focused variant is the selected-color star, enlarged as a whole.
        fun stars(topColor: Int, bottomColor: Int, focused: Boolean): Array<Icon> = StopDirection.entries.map { direction ->
            val marker = StopBitmaps.favoriteMarker(
                basePx, starPx, direction != StopDirection.NONE, direction.compassAngle,
                topColor, bottomColor, arrowTip, arrowBase, starOutlinePx
            )
            iconFactory.fromBitmap(if (focused) StopBitmaps.scale(marker, FOCUS_ICON_SCALE) else marker)
        }.toTypedArray()
        starStopIcons = stars(starLight, starDark, focused = false)
        starStopIconsFocused = stars(focusColor, focusColor, focused = true)

        val dot = StopBitmaps.dot(basePx, arrowTip)
        val focusedDot = StopBitmaps.dot(basePx, focusColor, StopBitmaps.FOCUSED_DOT_SCALE)
        dotStopIcon = iconFactory.fromBitmap(dot)
        dotStopIconFocused = iconFactory.fromBitmap(focusedDot)

        // Dot-band starred stops: a plain star (no arrow, matching the plain dot), dot-sized and enlarged
        // when focused. Gold gradient normally, the selected color when focused; same thin outline.
        val starDotPx = (basePx * 0.5f * StopBitmaps.STAR_SIZE_SCALE).roundToInt()
        val starDot = StopBitmaps.star(starDotPx, starLight, starDark, starOutlinePx)
        val focusedStarDot = StopBitmaps.star(
            (starDotPx * StopBitmaps.FOCUSED_DOT_SCALE).roundToInt(),
            focusColor,
            focusColor,
            starOutlinePx
        )
        starDotStopIcon = iconFactory.fromBitmap(starDot)
        starDotStopIconFocused = iconFactory.fromBitmap(focusedStarDot)
    }

    /** The shared circle + direction arrow, at this flavor's raw (non-glyph-scaled) icon size. */
    private fun createBusStopIcon(
        context: Context,
        direction: StopDirection,
        selected: Boolean,
        arrowTip: Int,
        arrowBase: Int
    ) = StopBitmaps.directionalStopMarker(
        requireNotNull(
            ContextCompat.getDrawable(
                context,
                if (selected) R.drawable.selected_map_stop_icon else R.drawable.map_stop_icon
            )
        ),
        direction,
        basePx,
        arrowTip,
        arrowBase
    )
}
