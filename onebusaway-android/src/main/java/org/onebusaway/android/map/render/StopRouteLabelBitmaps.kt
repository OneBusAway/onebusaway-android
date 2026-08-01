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
import androidx.core.graphics.createBitmap
import kotlin.math.ceil

/**
 * The label naming the routes that serve a stop, floated just above its marker at transit-centre zoom
 * (#2107). The pill itself is the map's one route label ([ContinuationBadgeBitmaps.badgeGrid]) — same
 * cells, same casing, same dividers — so the map draws one kind of route label however many things it
 * labels; the size, the placement, the columns and the colours ([stopRouteLabelGrid], which takes the
 * arrivals drawer's badge rather than the basemap's line colour) are this label's own. Flavor-neutral
 * (like [StopBitmaps]), so each renderer just wraps the [Bitmap] as its marker icon.
 */
object StopRouteLabelBitmaps {

    /**
     * How large the pill is drawn relative to a route label on a line. Smaller, deliberately: a line's
     * label names the map's subject, while these name every stop in the viewport at once, so at full size
     * a transit centre's worth of them would cover the centre. Tunable.
     */
    private const val SCALE = 0.6f

    /**
     * How far above the stop's point the pill's bottom edge sits, in dp — the label's whole distance from
     * the stop, not the visible gap, which is this less whatever the marker itself reaches up to.
     *
     * Set on device to leave a few dp of daylight over an ordinary stop circle, which is drawn from
     * `map_stop_shadow_size_6` (22dp across, 1.35× that in the Google flavor for its route glyph) and so
     * reaches about 15dp above the point. Two things deliberately reach further and are *let* into the
     * label, which draws above them ([STOP_ROUTE_LABEL_Z_INDEX]): a focused stop's circle, which grows
     * half again, and a direction arrow that happens to point north. Both were cleared by an earlier,
     * larger lift, and the daylight that bought over every other stop — the overwhelming majority — read
     * as the label belonging to nothing in particular.
     *
     * One value for every stop, rather than one that clears whatever each marker reaches: a per-stop lift
     * would set the labels of neighbouring bays at different heights, and a lift that answered focus would
     * make a label jump on selection and read as a second marker. A visual offset rather than a derived
     * geometry, so it's one tunable here rather than three flavor-private constants imported into one place.
     */
    private const val LIFT_DP = 18.5f

    /**
     * The label bitmap naming [routes], ready to be anchored at the **centre** on the stop's own point.
     *
     * Centre-anchored — with the lift baked in as transparent padding below the pill rather than applied
     * as an anchor offset — because that is the only placement both flavors can express: maplibre's
     * classic Marker has no per-marker anchor at all and always centres its icon (the same reason the
     * route-continuation badge is centred in its bitmap). The cost is a bitmap about twice the pill's
     * height, most of it empty, which is why callers cache these by [labelKey] rather than per stop:
     * every stop served by the same routes shares one, and a transit centre's bays repeat each other's
     * routes heavily.
     */
    fun label(context: Context, routes: List<StopRoute>, darkMode: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        return liftedAbove(
            ContinuationBadgeBitmaps.badgeGrid(stopRouteLabelGrid(routes, darkMode), density, darkMode, SCALE),
            LIFT_DP * density
        )
    }

    /**
     * A stable key identifying the bitmap [label] draws for these inputs, beside the function itself so
     * the two can't disagree about which of them the key names (as [ContinuationBadgeBitmaps.badgeKey]
     * is). Its own prefix keeps it clear of a line's label naming the same routes, in a renderer that
     * caches both in one map. Scale and lift are fixed here, so neither distinguishes anything.
     *
     * Keyed on the **unrendered** routes rather than on the grid they draw as, unlike
     * [ContinuationBadgeBitmaps.badgeGridKey], which keys a grid it is handed. The grid is a pure function
     * of exactly these inputs, so the two keys separate the same bitmaps — and this one costs a string
     * where that one would cost the whole colour policy (four HCT conversions per cell, over every route
     * of every labelled stop) on a path a cache *hit* also walks. Layout and colour are then run once, on
     * a miss, inside [label].
     */
    fun labelKey(routes: List<StopRoute>, darkMode: Boolean): String = routes.joinToString(
        separator = "|",
        prefix = "stop-route-label:$darkMode:"
    ) { "${it.shortName}:${it.routeColor}" }

    /**
     * [pill] drawn at the top of a canvas tall enough that the canvas centre — where the marker is
     * anchored — sits [liftPx] below the pill's bottom edge, leaving the pill floating that far above the
     * stop's point.
     */
    private fun liftedAbove(pill: Bitmap, liftPx: Float): Bitmap {
        val bitmap = createBitmap(pill.width, ceil(2f * (pill.height + liftPx)).toInt())
        Canvas(bitmap).drawBitmap(pill, 0f, 0f, null)
        return bitmap
    }
}
