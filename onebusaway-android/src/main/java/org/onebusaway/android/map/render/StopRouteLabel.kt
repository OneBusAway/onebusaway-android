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

/**
 * What a stop marker's route label reads (#2107) and how it is coloured: the rows of the pill drawn
 * beside a stop once the camera reaches the transit-centre band. Pure and shared, so the two map flavors
 * label a stop with the same routes in the same order and the decision is unit-testable off-device; the
 * drawing is [StopRouteLabelBitmaps].
 */

/**
 * The most routes a stop's label names before it overflows. A label is read at a glance beside a marker,
 * and a downtown bay serving a dozen routes would otherwise draw a column taller than the block it
 * labels — covering the very neighbours the rider is comparing it against. Tunable.
 */
const val STOP_ROUTE_LABEL_MAX_ROWS = 5

/**
 * The routes [stop]'s label names at [band] — empty below [StopBand.ROUTES], which draws no label at all.
 * The single place either flavor asks "does this stop name its routes right now", so a band crossing
 * can't show a label on one map and not the other.
 */
fun stopRouteLabel(stop: StopMarker, band: StopBand): List<StopRoute> = if (band == StopBand.ROUTES) stopRouteLabel(stop.routes) else emptyList()

/**
 * [routes] as label rows: every route when they fit in [maxRows], otherwise the first `maxRows - 1` plus
 * a final `+N` row counting the rest. Overflowing is stated rather than silent — a label that just
 * stopped at five would read as the whole truth about a stop and be wrong about it, which is worse for a
 * rider hunting a route than being told there are more.
 *
 * The overflow row carries no colour, so it draws as the neutral chip a colourless route would: it is a
 * count, not one more route, and giving it a hue of its own would say otherwise.
 */
fun stopRouteLabel(routes: List<StopRoute>, maxRows: Int = STOP_ROUTE_LABEL_MAX_ROWS): List<StopRoute> {
    // Two is the smallest label that can overflow at all (one route plus the count); at one there'd be
    // nothing left to name beside the "+N", so the label would say only that it isn't saying anything.
    require(maxRows >= 2) { "a route label of $maxRows row(s) has no room to both name a route and count the rest" }
    if (routes.size <= maxRows) return routes
    val shown = routes.take(maxRows - 1)
    return shown + StopRoute("+${routes.size - shown.size}", routeColor = null)
}

/**
 * [routes] rendered for drawing: the arrivals drawer's route badge, row for row — the agency's hue at the
 * badge's capped chroma and light tone, with the deep same-hue ink that tone is paired with
 * ([routeBadgeChipColor] / [routeBadgeChipTextColor]), or the neutral chip when a route publishes no
 * usable colour.
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
fun stopRouteLabelRows(routes: List<StopRoute>, dark: Boolean): List<BadgedRoute> = routes.map { route ->
    BadgedRoute(
        route.shortName,
        // Both tones come from the same source, so they are null together — an achromatic or absent route
        // colour takes the whole neutral chip, exactly as `rememberRouteBadgeColors` gives the drawer.
        routeBadgeChipColor(route.routeColor, dark) ?: neutralBadgeChipColor(dark),
        routeBadgeChipTextColor(route.routeColor, dark) ?: neutralBadgeChipTextColor(dark)
    )
}
