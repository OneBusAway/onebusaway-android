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
