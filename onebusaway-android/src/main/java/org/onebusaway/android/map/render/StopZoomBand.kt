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
 * The zoom at or above which stops show their full directional icon; below it they collapse to a
 * small dot to reduce clutter when a lot of stops are on screen. Tunable — mirrors the spirit of the
 * bike zoom bands ([bikeZoomBand]).
 */
const val STOP_DOT_ZOOM_THRESHOLD = 15f

/**
 * Marker-group ordering. Native map SDKs always place markers above route polylines, but adjacent
 * route stops must still win every marker overlap; favorites remain above ordinary nearby stops.
 */
fun stopZIndex(routeStop: Boolean, favorite: Boolean): Float = when {
    routeStop -> 0.75f
    favorite -> 0.5f
    else -> 0f
}

/** Whether a stop renders as a small dot (distant zoom) or its full directional icon (close zoom). */
enum class StopBand { DOT, FULL }

/** The [StopBand] a stop falls in at [zoom]: a dot below [STOP_DOT_ZOOM_THRESHOLD], else the full icon. */
fun stopZoomBand(zoom: Float): StopBand =
    if (zoom < STOP_DOT_ZOOM_THRESHOLD) StopBand.DOT else StopBand.FULL

/**
 * The icon variants a stop marker can show: the full directional icon or the far-zoom dot (each
 * normal/focused), the distinctive star a starred (favorite) stop gets in place of either (#1680),
 * likewise normal/focused, and — while a route is shown — the on-centerline circle that matches the
 * trip map's stop styling (#1752), normal/focused.
 */
enum class StopIconKind {
    FULL, FULL_FOCUSED, DOT, DOT_FOCUSED,
    FAVORITE, FAVORITE_FOCUSED, FAVORITE_DOT, FAVORITE_DOT_FOCUSED,
    ROUTE_CIRCLE, ROUTE_CIRCLE_FOCUSED,
}

/**
 * The icon a stop marker should show given whether it's the [focused] stop, whether it's a
 * [favorite] (starred) stop, whether it's a [routeStop] on the shown route, and the current zoom
 * [band]. A [routeStop] always draws the on-centerline circle (matching the trip map, #1752) — it
 * ignores the star and zoom band so single-route mode's stops read uniformly. Otherwise a starred
 * stop gets its distinctive star instead of the directional icon/dot (#1680). The focused stop always
 * gets the matching focused variant so a selection stays visible. Pure, so renderer icon-change
 * decisions are unit-testable and identical across both map flavors.
 */
fun stopIconKind(
    focused: Boolean,
    band: StopBand,
    favorite: Boolean = false,
    routeStop: Boolean = false,
): StopIconKind = when {
    routeStop -> if (focused) StopIconKind.ROUTE_CIRCLE_FOCUSED else StopIconKind.ROUTE_CIRCLE
    favorite && band == StopBand.DOT ->
        if (focused) StopIconKind.FAVORITE_DOT_FOCUSED else StopIconKind.FAVORITE_DOT
    favorite -> if (focused) StopIconKind.FAVORITE_FOCUSED else StopIconKind.FAVORITE
    band == StopBand.DOT -> if (focused) StopIconKind.DOT_FOCUSED else StopIconKind.DOT
    focused -> StopIconKind.FULL_FOCUSED
    else -> StopIconKind.FULL
}
