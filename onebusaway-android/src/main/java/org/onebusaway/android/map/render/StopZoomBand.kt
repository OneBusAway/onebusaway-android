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
 * **Set on device**, by zooming to where the map reads as one transit centre rather than a neighbourhood
 * and taking the number off the debug zoom readout. It is a judgement about when the labels start
 * earning their clutter, which is not a thing to derive: for scale, marker zoom is density-independent
 * (256**dp** tiles), so at zoom z a map covers 156543·cos(latitude)/2^z metres per dp — on a ~400dp-wide
 * phone at mid latitudes about 230 m here, against ~160 m at 18 (which held the labels back until the
 * rider had already zoomed past the centre) and ~320 m at 17. Tunable, but retune it the same way.
 */
const val STOP_ROUTES_ZOOM_THRESHOLD = 17.5f

/** Smallest focused route-stop circle scale at the zoomed-out end of the detail ramp. */
const val STOP_FOCUS_ROUTE_MIN_SCALE = 0.3f

/**
 * Marker-group ordering **within the stop group**. Native map SDKs always place markers above route
 * polylines, but adjacent route stops must still win every overlap with another *stop*; favorites remain
 * above ordinary nearby stops. Vehicles and the selected trip's estimate markers deliberately outrank the
 * whole group — see the Google renderer's `VEHICLE_Z_INDEX`, which derives from
 * [STOP_ROUTE_LABEL_Z_INDEX] for exactly that reason.
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
 *
 * Being the highest thing the stop group draws, this is also the group's **ceiling** for anything that has
 * to stay tappable over a stop: the label is a wide pill floating above its point and is itself a tap
 * target for that stop, so clearing [stopZIndex] alone doesn't clear the group. The renderers place the
 * vehicle + trip-estimate markers relative to this constant rather than to a literal.
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
 * Whether this band is close enough in for the transit-centre arrivals drawer (#2107) — the zoom at
 * which route *labels* appear is the zoom at which their *departures* are worth listing.
 *
 * One definition, read by both the query that asks and the sheet decision that shows the answer: if
 * they disagreed, the drawer could gate on a band the query does not serve (an empty drawer) or the
 * query could poll a band nothing displays (wasted requests every minute).
 *
 * An ordering rather than equality, so a band added above [StopBand.ROUTES] keeps the drawer instead
 * of silently switching it off at the zoom that wants it most — the rule `stopRouteLabel` follows for
 * the same reason.
 */
val StopBand.showsNearbyArrivals: Boolean get() = this >= StopBand.ROUTES

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
