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
     * How far above the stop's point the pill's bottom edge sits, in dp. The stop circle is drawn from
     * `map_stop_shadow_size_6` (22dp across, 1.35× that in the Google flavor for its route glyph, and
     * 1.5× again when focused), so this clears the largest of those with a few dp to spare — and stays
     * put when a stop is focused, since a label that jumped on selection would read as a second marker.
     * A visual offset rather than a derived geometry, so it's one tunable here rather than three
     * flavor-private constants imported into one place.
     */
    private const val LIFT_DP = 26f

    /**
     * The label bitmap naming [routes], ready to be anchored at the **centre** on the stop's own point.
     *
     * Centre-anchored — with the lift baked in as transparent padding below the pill rather than applied
     * as an anchor offset — because that is the only placement both flavors can express: maplibre's
     * classic Marker has no per-marker anchor at all and always centres its icon (the same reason the
     * route-continuation badge is centred in its bitmap). The cost is a bitmap about twice the pill's
     * height, most of it empty, which is why callers cache these by [labelKey] rather than per stop:
     * every stop served by the same routes shares one.
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
     * is). Delegates to the pill's own key — the pill is the whole of the drawing that varies — under a
     * distinct prefix, so a stop label and a line's label naming the same routes can't collide in a
     * renderer that happens to cache both in one map. Scale and lift are fixed here, so neither
     * distinguishes anything.
     */
    fun labelKey(routes: List<StopRoute>, darkMode: Boolean): String = "stop-route-label:" + ContinuationBadgeBitmaps.badgeGridKey(stopRouteLabelGrid(routes, darkMode), darkMode, SCALE)

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
