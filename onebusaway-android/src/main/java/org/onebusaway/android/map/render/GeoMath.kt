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
import org.onebusaway.android.util.haversineDistance
import org.onebusaway.android.util.initialBearing

/**
 * Great-circle distance in meters between two points. Takes flavor-neutral [GeoPoint]s and delegates
 * to the shared [haversineDistance], which matches the server's distance-along-trip values (same
 * Earth radius); neither carries an Android dependency, so callers stay pure / JVM-testable.
 */
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)

/**
 * How much of a line's start [leadingBearing] reads its direction over. A decoded shape's first vertices
 * can be a metre apart, or duplicated outright, so the first segment alone is a noisy answer to "which way
 * does this line leave here"; a window a few vertices deep gives the direction of the line rather than of
 * its first hop. Short enough that it is still the *local* direction at the start — a line that turns a
 * corner within this distance is turning at the point being marked.
 */
private const val LEADING_BEARING_WINDOW_METERS = 20.0

/**
 * The direction of travel where [points] begins, in compass degrees (0 = north, clockwise), measured as the
 * chord over the first [LEADING_BEARING_WINDOW_METERS] of the line. Null when that window holds no direction
 * at all (an empty or single-point line, or one whose vertices all coincide), which is a line no mark can be
 * oriented against rather than a value to guess at.
 *
 * A renderer that anchors a symbol to a line's start uses this to orient it (the maplibre interline seam
 * mark, #2127); the gms flavor gets the same orientation from the SDK, which aligns a line cap itself.
 */
internal fun leadingBearing(points: List<GeoPoint>): Float? {
    val start = points.firstOrNull() ?: return null
    var travelled = 0.0
    var ahead: GeoPoint? = null
    for (index in 1 until points.size) {
        travelled += haversineMeters(points[index - 1], points[index])
        ahead = points[index]
        if (travelled >= LEADING_BEARING_WINDOW_METERS) break
    }
    val end = ahead ?: return null
    if (haversineMeters(start, end) <= 0.0) return null
    return ((initialBearing(start.latitude, start.longitude, end.latitude, end.longitude) + 360) % 360).toFloat()
}

/**
 * Web-Mercator ground resolution at zoom 0 on the equator, in meters per pixel (256px tiles). Scale to
 * a given zoom/latitude with `× cos(lat) / 2^zoom`. The single source for this constant, shared by the
 * route-render pipeline and the map-ping radius math.
 */
internal const val METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO = 156543.03392804097
