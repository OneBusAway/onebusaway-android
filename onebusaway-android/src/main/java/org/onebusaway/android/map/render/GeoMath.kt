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

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Earth radius used by the server's distance-along-trip values (matches LocationUtils). */
private const val EARTH_RADIUS_METERS = 6371010.0

/**
 * Great-circle distance in meters between two points. Mirrors [LocationUtils.haversineDistance]
 * (same [EARTH_RADIUS_METERS]) but takes flavor-neutral [GeoPoint]s and carries no Android
 * dependency, so callers stay pure / JVM-testable.
 */
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)
    val cosLat1 = cos(lat1)
    val cosLat2 = cos(lat2)
    val sinLat1 = sin(lat1)
    val sinLat2 = sin(lat2)
    val cosDeltaLon = cos(deltaLon)
    val ny = cosLat2 * sin(deltaLon)
    val nx = cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDeltaLon
    val y = sqrt(ny * ny + nx * nx)
    val x = sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDeltaLon
    return EARTH_RADIUS_METERS * atan2(y, x)
}
