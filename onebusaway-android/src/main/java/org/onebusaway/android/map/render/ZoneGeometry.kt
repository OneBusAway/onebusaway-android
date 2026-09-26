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

import org.onebusaway.android.util.GeoPoint

/** Stroke width of a zone outline, in pixels, on both flavors. */
const val ZONE_STROKE_WIDTH_PX = 3f

private const val FILL_ALPHA = 0x33
private const val STROKE_ALPHA = 0xCC
private const val RGB_MASK = 0x00FFFFFF

/** The zone fill: the route's colour at ~20 % opacity, so stops and lines stay legible over it. */
fun zoneFillColor(routeColor: Int?): Int = withAlpha(routeColor ?: DEFAULT_ROUTE_LINE_COLOR, FILL_ALPHA)

/** The zone outline: the same hue, ~80 % opaque. */
fun zoneStrokeColor(routeColor: Int?): Int = withAlpha(routeColor ?: DEFAULT_ROUTE_LINE_COLOR, STROKE_ALPHA)

private fun withAlpha(argb: Int, alpha: Int): Int = (alpha shl 24) or (argb and RGB_MASK)

/**
 * Even-odd ray cast of [point] against [ring] (closed or not). Planar lat/lon is exact enough for a
 * tap hit-test on a drawn polygon; it is **not** the service's containment semantics — those are the
 * server's `matchReason`.
 */
fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        val crosses = (a.latitude > point.latitude) != (b.latitude > point.latitude)
        if (crosses) {
            val lonAtLat = (b.longitude - a.longitude) * (point.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
            if (point.longitude < lonAtLat) inside = !inside
        }
        j = i
    }
    return inside
}

/** True inside the exterior ring and outside every hole. */
fun ZonePolygon.contains(point: GeoPoint): Boolean {
    val exterior = rings.firstOrNull() ?: return false
    return pointInRing(point, exterior) && rings.drop(1).none { pointInRing(point, it) }
}
