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

/** Smallest focused route-stop circle scale at the zoomed-out end of the detail ramp. */
const val STOP_FOCUS_ROUTE_MIN_SCALE = 0.3f

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
 * Stop-circle-specific detail scale applied only while a stop is focused. Ordinary single-route mode
 * stays 1x; interpolation machinery and zoom bounds remain shared with route-line width.
 */
fun focusedRouteStopScale(zoom: Float): Float = detailZoomRamp(
    zoom,
    startZoom = DETAIL_RAMP_START_ZOOM,
    endZoom = DETAIL_RAMP_END_ZOOM,
    distantValue = STOP_FOCUS_ROUTE_MIN_SCALE,
    closeValue = 1f,
)

/**
 * The icon variants a stop marker can show: the full directional icon or the far-zoom dot (each
 * normal/focused), the distinctive star a starred (favorite) stop gets in place of either (#1680),
 * likewise normal/focused. Route stops are native circles owned by the flavor-specific circle layer.
 */
enum class StopIconKind {
    FULL, FULL_FOCUSED, DOT, DOT_FOCUSED,
    FAVORITE, FAVORITE_FOCUSED, FAVORITE_DOT, FAVORITE_DOT_FOCUSED,
}

/**
 * The icon a stop marker should show given whether it's the [focused] stop, whether it's a
 * [favorite] (starred) stop, and the current zoom [band]. A starred stop gets its distinctive star
 * instead of the directional icon/dot (#1680). The focused stop always gets the matching focused
 * variant so a selection stays visible. Pure, so renderer icon-change decisions are unit-testable and
 * identical across both map flavors.
 */
fun stopIconKind(
    focused: Boolean,
    band: StopBand,
    favorite: Boolean = false,
): StopIconKind = when {
    favorite && band == StopBand.DOT ->
        if (focused) StopIconKind.FAVORITE_DOT_FOCUSED else StopIconKind.FAVORITE_DOT
    favorite -> if (focused) StopIconKind.FAVORITE_FOCUSED else StopIconKind.FAVORITE
    band == StopBand.DOT -> if (focused) StopIconKind.DOT_FOCUSED else StopIconKind.DOT
    focused -> StopIconKind.FULL_FOCUSED
    else -> StopIconKind.FULL
}
