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

import org.onebusaway.android.util.neutralBadgeChipColor
import org.onebusaway.android.util.neutralBadgeChipTextColor
import org.onebusaway.android.util.routeBadgeChipColor
import org.onebusaway.android.util.routeBadgeChipTextColor

/*
 * What a stop marker's route label reads (#2107), how it is laid out and how it is coloured: the cells of
 * the pill drawn beside a stop once the camera reaches the transit-centre band. Pure and shared, so the
 * two map flavors label a stop with the same routes in the same arrangement and the decisions are
 * unit-testable off-device; the drawing is StopRouteLabelBitmaps.
 */

/**
 * How tall a stop's label may grow before it widens instead. A label is read at a glance beside a marker,
 * and a downtown bay serving a dozen routes would otherwise draw a column taller than the block it
 * labels — covering the very neighbours the rider is comparing it against. Tunable.
 */
const val STOP_ROUTE_LABEL_MAX_ROWS = 5

/**
 * The routes [stop]'s label names at [band] — empty below [StopBand.ROUTES], which draws no label at all.
 * The single place either flavor asks "is this stop naming its routes right now", so a band crossing can't
 * show a label on one map and not the other.
 *
 * Compared as an ordering rather than for equality, because [StopBand] widens: a band added above
 * [StopBand.ROUTES] would show everything this one does and more, and an equality test would silently
 * switch the labels back off at the very zoom that wants them most.
 */
fun stopRouteLabel(stop: StopMarker, band: StopBand): List<StopRoute> = if (band >= StopBand.ROUTES) stop.routes else emptyList()

/**
 * [routes] laid out in columns of at most [STOP_ROUTE_LABEL_MAX_ROWS], read top to bottom and then left to
 * right, so a stop naming more routes than fit in one column widens rather than lengthening — and names
 * every one of them. A `+N` overflow row was the alternative and is worse where it matters: a rider
 * hunting their route at a transit centre is exactly the person for whom "and 6 more" is no answer.
 *
 * The columns are **balanced**, not filled to the cap and then spilled: seven routes read 4 + 3, not
 * 5 + 2. Both are as wide, so the taller one is only taller, and a nearly-empty second column reads as an
 * afterthought rather than as one label.
 *
 * The grid is rectangular — [badgeGrid][ContinuationBadgeBitmaps.badgeGrid] requires it, since a hole is a
 * hole in the pill — so a trailing remainder is padded with `null`, a blank cell for
 * [stopRouteLabelGrid] to colour. That padding is why this is internal: the nullable cell is how the
 * layout meets the drawing, not something a caller outside this file should have to reason about.
 */
internal fun stopRouteLabelColumns(routes: List<StopRoute>): List<List<StopRoute?>> {
    if (routes.isEmpty()) return emptyList()
    val columns = ceilingDivide(routes.size, STOP_ROUTE_LABEL_MAX_ROWS)
    val rows = ceilingDivide(routes.size, columns)
    // Padded to `columns * rows` first, so the chunks come out rectangular without a special last case.
    return List(columns * rows) { routes.getOrNull(it) }.chunked(rows)
}

/**
 * [stopRouteLabelColumns] rendered for drawing: the arrivals drawer's route badge, cell for cell — the
 * agency's hue at the badge's capped chroma and light tone, with the deep same-hue ink that tone is paired
 * with ([routeBadgeChipColor] / [routeBadgeChipTextColor]), or the neutral chip for a route that publishes
 * no usable colour and for the blank cells padding the last column.
 *
 * Deliberately the *drawer's* policy rather than the basemap's (`mapRouteLineColorOrNull`), which is what
 * every route **line** on this map is drawn in. A stop label is not a line: it is a small block of type
 * that a rider reads against the route badges elsewhere in the app — the arrivals list this label is a
 * shortcut into, above all — so it takes the colour those badges have. The faded fill also matters at
 * this size: a column of fully saturated bars beside every marker in a transit centre reads as the
 * subject of the map, which the stops around the rider are not.
 *
 * Resolved here, at draw time, because these colours flip with [dark] (see [StopRoute]).
 */
fun stopRouteLabelGrid(routes: List<StopRoute>, dark: Boolean): List<List<BadgedRoute>> = stopRouteLabelColumns(routes).map { column ->
    column.map { route ->
        BadgedRoute(
            // A blank cell names nothing; it exists only so the pill stays a rectangle.
            route?.shortName.orEmpty(),
            // Both tones come from the same source, so they are null together — an achromatic or absent
            // route colour takes the whole neutral chip, exactly as `rememberRouteBadgeColors` gives the
            // drawer, and so does a blank cell (which has no source at all).
            routeBadgeChipColor(route?.routeColor, dark) ?: neutralBadgeChipColor(dark),
            routeBadgeChipTextColor(route?.routeColor, dark) ?: neutralBadgeChipTextColor(dark)
        )
    }
}

/** [dividend] / [divisor] rounded up, for positive operands — how many columns hold this many routes. */
private fun ceilingDivide(dividend: Int, divisor: Int): Int = (dividend + divisor - 1) / divisor
