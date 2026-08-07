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

import kotlin.math.cos
import kotlin.math.pow
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
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
    // [Polyline] owns walking a distance along a shape, and clamps to the end of a line shorter than the
    // window — which is the right answer here: a short line's whole direction is its leading one.
    val ahead = Polyline(points).interpolate(LEADING_BEARING_WINDOW_METERS) ?: return null
    if (haversineMeters(start, ahead) <= 0.0) return null
    return ((initialBearing(start.latitude, start.longitude, ahead.latitude, ahead.longitude) + 360) % 360).toFloat()
}

/**
 * Web-Mercator ground resolution at zoom 0 on the equator, in meters per pixel (256px tiles). Scaled to a
 * given zoom/latitude by [metersPerPixel], which every caller goes through.
 */
private const val METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO = 156543.03392804097

/** The latitude Web Mercator is cut off at — beyond it the projection runs away to infinity. */
internal const val MAX_MERCATOR_LATITUDE = 85.05112878

/**
 * Web-Mercator ground resolution at [latitude] and [zoom], in meters per pixel — how much ground one
 * screen pixel covers, and so the conversion between a distance meant in *screen* terms and the geometry
 * that has to realize it.
 *
 * The single place this scaling is written: it was copied out three times (route simplification, stripe
 * length, the ping radius) and the copies had already drifted apart on their guard rails, each clamping a
 * different subset of the two inputs the formula runs away on. Both clamps live here now — [latitude] to
 * the projection's own cutoff, [zoom] to a range no camera exceeds — so a caller gets a finite answer
 * whatever it hands over.
 */
internal fun metersPerPixel(latitude: Double, zoom: Double): Double = METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO *
    cos(Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))) /
    2.0.pow(zoom.coerceIn(0.0, MAX_MERCATOR_ZOOM))

/** Past this the tiles are smaller than a pixel; no map SDK the app drives goes near it. */
private const val MAX_MERCATOR_ZOOM = 30.0
