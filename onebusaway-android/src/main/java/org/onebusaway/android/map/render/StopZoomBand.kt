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
 * The zoom at or above which a stop marker also names the routes serving it (#2107) — the transit-centre
 * band. A rider standing in a transit centre otherwise has to open every bay in turn to find the one that
 * serves their route, and the zoom the map is already at when they do that is what this keys off.
 *
 * Chosen for the viewport width it corresponds to. Marker zoom is density-independent (256**dp** tiles),
 * so at zoom z a map covers 156543·cos(latitude)/2^z metres per dp: on a ~400dp-wide phone at mid
 * latitudes that is ~160 m at zoom 18 — one or two city blocks, the "about a block wide" the issue asks
 * for — against ~320 m at 17, which is a neighbourhood and would label far more stops than fit. Tunable.
 */
const val STOP_ROUTES_ZOOM_THRESHOLD = 18f

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

/**
 * A stop's route label (#2107) draws above every stop marker — including the enlarged focused one, whose
 * icon would otherwise cover the label of the stop behind it. Below the route labels a selected line
 * carries (`ROUTE_BADGE_Z_INDEX`), which name the map's current subject rather than what's merely nearby.
 */
const val STOP_ROUTE_LABEL_Z_INDEX = 1f

/**
 * How much of a stop a marker shows at the current zoom: a small dot far out (declutter), its full
 * directional icon closer in, and closer still that icon plus a label naming the routes that serve it
 * (#2107). Widening bands, so a [ROUTES] stop draws everything a [FULL] one does and more — which is why
 * [stopIconKind] treats the two alike.
 */
enum class StopBand { DOT, FULL, ROUTES }

/**
 * The [StopBand] a stop falls in at [zoom]: a dot below [STOP_DOT_ZOOM_THRESHOLD], its full icon from
 * there, and from [STOP_ROUTES_ZOOM_THRESHOLD] that icon plus its route label.
 */
fun stopZoomBand(zoom: Float): StopBand = when {
    zoom < STOP_DOT_ZOOM_THRESHOLD -> StopBand.DOT
    zoom < STOP_ROUTES_ZOOM_THRESHOLD -> StopBand.FULL
    else -> StopBand.ROUTES
}

/**
 * Stop-circle-specific detail scale applied while a route is focused, whether through focused-stop
 * adjacency or the single-route view. Interpolation machinery and zoom bounds remain shared with
 * route-line width.
 */
fun focusedRouteStopScale(zoom: Float): Float = detailZoomRamp(
    zoom,
    startZoom = DETAIL_RAMP_START_ZOOM,
    endZoom = DETAIL_RAMP_END_ZOOM,
    distantValue = STOP_FOCUS_ROUTE_MIN_SCALE,
    closeValue = 1f
)

/**
 * The icon variants a stop marker can show: the full directional icon or the far-zoom dot (each
 * normal/focused), the distinctive star a starred (favorite) stop gets in place of either (#1680),
 * likewise normal/focused. Route stops are native circles owned by the flavor-specific circle layer.
 */
enum class StopIconKind {
    FULL,
    FULL_FOCUSED,
    DOT,
    DOT_FOCUSED,
    FAVORITE,
    FAVORITE_FOCUSED,
    FAVORITE_DOT,
    FAVORITE_DOT_FOCUSED
}

/**
 * The icon a stop marker should show given whether it's the [focused] stop, whether it's a
 * [favorite] (starred) stop, and the current zoom [band]. A starred stop gets its distinctive star
 * instead of the directional icon/dot (#1680). The focused stop always gets the matching focused
 * variant so a selection stays visible. Pure, so renderer icon-change decisions are unit-testable and
 * identical across both map flavors.
 *
 * [StopBand.ROUTES] takes the same icon as [StopBand.FULL]: what that band adds is the separate route
 * label beside the marker (#2107, see [stopRouteLabel]), not a different icon.
 */
fun stopIconKind(
    focused: Boolean,
    band: StopBand,
    favorite: Boolean = false
): StopIconKind = when {
    favorite && band == StopBand.DOT ->
        if (focused) StopIconKind.FAVORITE_DOT_FOCUSED else StopIconKind.FAVORITE_DOT
    favorite -> if (focused) StopIconKind.FAVORITE_FOCUSED else StopIconKind.FAVORITE
    band == StopBand.DOT -> if (focused) StopIconKind.DOT_FOCUSED else StopIconKind.DOT
    focused -> StopIconKind.FULL_FOCUSED
    else -> StopIconKind.FULL
}
