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

/** Shared zoom interval for route-detail styling. */
const val DETAIL_RAMP_START_ZOOM = 11f
const val DETAIL_RAMP_END_ZOOM = 16f

/** Route-line scale: half width when zoomed out, full width at zoom 16 and above. */
const val ROUTE_DETAIL_DISTANT_SCALE = 0.5f

/** The ordinary route stroke thickness at the close end of the detail ramp. */
const val ROUTE_LINE_WIDTH_DP = 10f

/**
 * A complete route-line width policy: its close-zoom [thicknessDp], the zoom interval over which it
 * grows, and its distant-zoom multiplier. Keeping these together lets presentation modes share an
 * exact policy instead of independently copying a base width and relying on renderer-global ramp
 * constants.
 */
data class RouteLineWidthProfile(
    val thicknessDp: Float,
    val rampStartZoom: Float = DETAIL_RAMP_START_ZOOM,
    val fullThicknessZoom: Float = DETAIL_RAMP_END_ZOOM,
    val distantThicknessMultiplier: Float = ROUTE_DETAIL_DISTANT_SCALE
) {
    fun multiplierAt(zoom: Float): Float = detailZoomRamp(
        zoom,
        startZoom = rampStartZoom,
        endZoom = fullThicknessZoom,
        distantValue = distantThicknessMultiplier,
        closeValue = 1f
    )

    fun thicknessAt(zoom: Float): Float = thicknessDp * multiplierAt(zoom)
}

/** Ordinary route presentation, before a route is selected from a focused stop. */
val ROUTE_LINE_WIDTH_PROFILE = RouteLineWidthProfile(ROUTE_LINE_WIDTH_DP)

/**
 * Adjacent routes shown in stop focus, before any route is selected. They recede to half the ordinary
 * stroke so stop focus reads distinctly from the selected-route state, where one route is thickened
 * instead (#1985).
 */
val ADJACENT_ROUTE_LINE_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.5f
)

/** Shared by single-route view and a route selected from focused-stop mode. */
val FOCUSED_ROUTE_LINE_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 1.5f
)

/** Contextual sibling routes shown underneath a route selected from focused-stop mode. */
val DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.275f
)

/**
 * The selected transit route upstream of where the rider boards it — where the bus is coming from. It is
 * the same route as the ridden segment, so it is cased like it (see [RoutePolyline.caseColor]) and drawn at
 * the itinerary's context weight, stepping down at the boarding point.
 *
 * It used to be the map's faintest line (2dp) and dashed, which put it within a hair of the receded
 * itinerary legs around it — the two competed instead of reading as different things (#2082).
 */
val ITINERARY_APPROACH_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.5f
)

/**
 * The rest of a rider's itinerary retained around a focused transit leg. It sits between the selected
 * ridden segment and the thinner, untraveled remainder of that transit route: still visibly part of the
 * journey, but no longer competing with the leg being read.
 *
 * This deliberately has its own semantic profile even though it currently shares the adjacent-route
 * value. The two presentations describe different things and can be tuned independently later.
 */
val ITINERARY_CONTEXT_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.5f
)

/**
 * A trip-plan itinerary's transit legs (#2041). The itinerary is the only thing the directions map is
 * showing — nothing competes with it — so a ride draws at the focused-route weight, and *is* that
 * profile rather than a second copy of its multiplier. It carries its own name because the two are read
 * against different backdrops (route focus sits over sibling routes and nearby stops, directions over a
 * bare basemap), so directions is the thing to give a value of its own the day they should differ.
 */
val ITINERARY_RIDE_WIDTH_PROFILE = FOCUSED_ROUTE_LINE_WIDTH_PROFILE

/**
 * An itinerary's on-street legs — walking, cycling, bikeshare. Drawn narrower than the ride it connects
 * to: a sidewalk or bike lane is a thinner thing than a transit corridor, and the step down at each
 * transition is a second cue (besides colour) for where one mode hands off to the next.
 */
val ITINERARY_STREET_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.9f
)

/**
 * How far a case (halo) extends beyond its line on each side — see [RoutePolyline.caseColor]. Deliberately
 * *not* on the zoom ramp: a case is an outline, and an outline that thins with its line stops separating it
 * from the basemap at exactly the zoom where the line is hardest to pick out.
 */
const val ROUTE_LINE_CASE_DP = 2.5f

/** Route-line scale retained for unprofiled lines and vehicle markers. */
fun routeLineWidthScale(zoom: Float): Float = ROUTE_LINE_WIDTH_PROFILE.multiplierAt(zoom)

/** Linear interpolation machinery for zoom-dependent map-detail styling. */
internal fun detailZoomRamp(
    zoom: Float,
    startZoom: Float,
    endZoom: Float,
    distantValue: Float,
    closeValue: Float
): Float {
    val progress = ((zoom - startZoom) / (endZoom - startZoom)).coerceIn(0f, 1f)
    return distantValue + (closeValue - distantValue) * progress
}
