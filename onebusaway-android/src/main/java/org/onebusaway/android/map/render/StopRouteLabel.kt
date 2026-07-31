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

/**
 * What a stop marker's route label reads (#2107): the rows of the pill drawn beside a stop once the
 * camera reaches the transit-centre band. Pure and shared, so the two map flavors label a stop with the
 * same routes in the same order and the decision is unit-testable off-device; the drawing is
 * [StopRouteLabelBitmaps].
 */

/**
 * The most routes a stop's label names before it overflows. A label is read at a glance beside a marker,
 * and a downtown bay serving a dozen routes would otherwise draw a column taller than the block it
 * labels — covering the very neighbours the rider is comparing it against. Tunable.
 */
const val STOP_ROUTE_LABEL_MAX_ROWS = 5

/**
 * The overflow row's fill: a plain neutral, so it reads as a count rather than as one more route with a
 * colour of its own. A fixed dark grey (white text lands on it via [MarkerRendering.legibleOn]) rather
 * than a theme-aware one, matching the deliberate theme independence of every other route colour on this
 * basemap (see `MapRouteColors`).
 */
private const val OVERFLOW_ROW_COLOR = 0xFF444444.toInt()

/**
 * The routes [stop]'s label names at [band] — empty below [StopBand.ROUTES], which draws no label at all.
 * The single place either flavor asks "does this stop name its routes right now", so a band crossing
 * can't show a label on one map and not the other.
 */
fun stopRouteLabel(stop: StopMarker, band: StopBand): List<BadgedRoute> = if (band == StopBand.ROUTES) stopRouteLabel(stop.routes) else emptyList()

/**
 * [routes] as label rows: every route when they fit in [maxRows], otherwise the first `maxRows - 1` plus
 * a final `+N` row counting the rest. Overflowing is stated rather than silent — a label that just
 * stopped at five would read as the whole truth about a stop and be wrong about it, which is worse for a
 * rider hunting a route than being told there are more.
 */
fun stopRouteLabel(routes: List<BadgedRoute>, maxRows: Int = STOP_ROUTE_LABEL_MAX_ROWS): List<BadgedRoute> {
    // Two is the smallest label that can overflow at all (one route plus the count); at one there'd be
    // nothing left to name beside the "+N", so the label would say only that it isn't saying anything.
    require(maxRows >= 2) { "a route label of $maxRows row(s) has no room to both name a route and count the rest" }
    if (routes.size <= maxRows) return routes
    val shown = routes.take(maxRows - 1)
    return shown + BadgedRoute("+${routes.size - shown.size}", OVERFLOW_ROW_COLOR)
}
