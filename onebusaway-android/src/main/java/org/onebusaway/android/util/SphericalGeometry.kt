/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Earth radius in meters, matching the OBA server's
 * SphericalGeometryLibrary.RADIUS_OF_EARTH_IN_KM * 1000.
 * Use this constant (not Android's WGS84 ellipsoid) when computing distances
 * that must be consistent with the server's distanceAlongTrip values.
 */
const val EARTH_RADIUS_METERS = 6371010.0

/**
 * Haversine great-circle distance matching the OBA server's
 * SphericalGeometryLibrary.distance() — same formula, same Earth radius
 * (6371.01 km). Use this instead of [android.location.Location.distanceTo] when
 * distances must align with the server's distanceAlongTrip values.
 *
 * @return distance in meters
 */
fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val rLat1 = Math.toRadians(lat1)
    val rLon1 = Math.toRadians(lon1)
    val rLat2 = Math.toRadians(lat2)
    val rLon2 = Math.toRadians(lon2)

    val deltaLon = rLon2 - rLon1
    val cosLat1 = cos(rLat1)
    val cosLat2 = cos(rLat2)
    val sinLat1 = sin(rLat1)
    val sinLat2 = sin(rLat2)
    val cosDeltaLon = cos(deltaLon)

    val ny = cosLat2 * sin(deltaLon)
    val nx = cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDeltaLon
    val y = sqrt(ny * ny + nx * nx)
    val x = sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDeltaLon

    return EARTH_RADIUS_METERS * atan2(y, x)
}

/**
 * Initial great-circle bearing from (lat1, lon1) toward (lat2, lon2), in degrees clockwise from true
 * north (0 = north, 90 = east). The pure equivalent of [android.location.Location.bearingTo], so
 * bearing math stays JVM-testable and free of the platform type. Returns a value in −180..180 (the
 * caller normalizes to 0..360 where it wants that).
 */
fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLon) * cos(rLat2)
    val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(deltaLon)
    return Math.toDegrees(atan2(y, x))
}
