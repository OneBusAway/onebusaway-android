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
package org.onebusaway.android.map

import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.haversineDistance

/**
 * Presenting a trip-plan leg's **ridden segment** over its route in route focus: thin the full route to
 * faint context, draw the segment at normal weight on top, and keep only the segment's stops. Pure
 * geometry over flavor-neutral [GeoPoint]/[RoutePolyline]/[ObaStop] (like [RouteViewGeometry] /
 * [projectStopsOntoPolylines]), so it stays JVM-testable and out of [RouteMapController]'s state plumbing.
 */

/** How close a stop must sit to the ridden path to count as "on the segment" — well below transit stop
 *  spacing, so the nearest off-segment stop is never wrongly included. */
const val SEGMENT_STOP_TOLERANCE_METERS = 50.0

/** A ridden segment worth drawing/framing: a polyline needs at least two points. Below that it's the
 *  "no segment" case — plain route focus (whole route drawn/framed, all stops kept). */
internal fun List<GeoPoint>.isDrawableSegment() = size >= 2

/**
 * Compose the route's polylines when [segment] is highlighted: the full route [base] de-emphasized
 * (thin, no arrows) under the segment drawn at normal width in [routeColor], last so it sits on top.
 * Returns [base] unchanged when there's no drawable segment (plain route focus).
 */
internal fun routePolylinesWithSegment(
    base: List<RoutePolyline>,
    segment: List<GeoPoint>,
    routeColor: Int?
): List<RoutePolyline> {
    val overlay = segment.takeIf { it.isDrawableSegment() }?.let {
        RoutePolyline(color = routeColor, points = it, widthProfile = ROUTE_LINE_WIDTH_PROFILE, directional = true)
    } ?: return base
    return base.asDeemphasizedRouteUnderlay() + overlay
}

/**
 * Keep only the stops within [toleranceMeters] of [segment]'s path — the ride's stops, not the whole
 * route's. All stops are kept when there's no drawable segment (plain route focus). [segment] must be the
 * clipped board→alight polyline (not the full route line), or every stop falls within tolerance and none
 * are dropped.
 */
internal fun List<ObaStop>.onSegment(
    segment: List<GeoPoint>,
    toleranceMeters: Double = SEGMENT_STOP_TOLERANCE_METERS
): List<ObaStop> {
    val line = segment.takeIf { it.isDrawableSegment() }?.let { Polyline(it) } ?: return this
    return filter { stop ->
        val nearest = line.nearestPoint(stop.latitude, stop.longitude) ?: return@filter false
        haversineDistance(stop.latitude, stop.longitude, nearest.latitude, nearest.longitude) <= toleranceMeters
    }
}
