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
package org.onebusaway.android.map.layout

import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One shape on which a route-direction badge may be anchored. */
data class RouteBadgePath(val points: List<GeoPoint>)

/** One route-direction identity and the geographic shapes on which its badge may be anchored. */
data class RouteBadgeLayoutInput(
    val route: RouteDirectionKey,
    val paths: List<RouteBadgePath>,
)

/** A stable geographic badge anchor. Input order determines collision priority. */
data class RouteBadgePlacement(
    val route: RouteDirectionKey,
    val point: GeoPoint,
    val overlaps: Boolean,
)

/**
 * Places one badge per route-direction identity in geographic space, following the line-center
 * convention used for road shields. The midpoint by distance of the route's longest shape is
 * preferred. When that is too close to an earlier route's badge, candidates alternate upstream and
 * downstream by a fixed geographic distance; if none clears [minimumSeparationMeters], the least-conflicting candidate
 * wins. The result is computed only when route geometry changes and remains fixed across pan/zoom.
 */
fun layoutRouteBadges(
    inputs: List<RouteBadgeLayoutInput>,
    minimumSeparationMeters: Double = DEFAULT_BADGE_SEPARATION_METERS,
    maxStaggerSteps: Int = DEFAULT_MAX_STAGGER_STEPS,
): List<RouteBadgePlacement> {
    val separation = minimumSeparationMeters.coerceAtLeast(0.0)
    val occupied = mutableListOf<GeoPoint>()
    return buildList {
        for (input in inputs) {
            val path = input.paths.mapNotNull(::measurePath).maxByOrNull(MeasuredPath::lengthMeters)
                ?: continue
            val candidates = candidateDistances(path.lengthMeters, separation, maxStaggerSteps)
                .map(path::pointAt)
            if (candidates.isEmpty()) continue

            val clear = candidates.firstOrNull { candidate ->
                occupied.all { other -> haversineMeters(candidate, other) >= separation }
            }
            val point = clear ?: candidates.maxBy { candidate ->
                occupied.minOfOrNull { other -> haversineMeters(candidate, other) }
                    ?: Double.POSITIVE_INFINITY
            }
            val overlaps = clear == null && occupied.isNotEmpty()
            occupied += point
            add(RouteBadgePlacement(input.route, point, overlaps))
        }
    }
}

private data class MeasuredPath(
    val points: List<GeoPoint>,
    val cumulativeMeters: DoubleArray,
) {
    val lengthMeters: Double get() = cumulativeMeters.last()

    fun pointAt(distanceMeters: Double): GeoPoint {
        val distance = distanceMeters.coerceIn(0.0, lengthMeters)
        val upper = cumulativeMeters.binarySearch(distance).let { index ->
            if (index >= 0) index else -index - 1
        }.coerceIn(1, points.lastIndex)
        val lower = upper - 1
        val segmentLength = cumulativeMeters[upper] - cumulativeMeters[lower]
        if (segmentLength <= 0.0) return points[lower]
        val fraction = (distance - cumulativeMeters[lower]) / segmentLength
        return interpolate(points[lower], points[upper], fraction)
    }
}

private fun measurePath(path: RouteBadgePath): MeasuredPath? {
    val points = path.points
    if (points.size < 2) return null
    val cumulative = DoubleArray(points.size)
    for (index in 1..points.lastIndex) {
        cumulative[index] = cumulative[index - 1] + haversineMeters(points[index - 1], points[index])
    }
    return MeasuredPath(points, cumulative).takeIf { it.lengthMeters > 0.0 }
}

private fun candidateDistances(lengthMeters: Double, separationMeters: Double, maxSteps: Int): List<Double> {
    val midpoint = lengthMeters / 2.0
    if (separationMeters <= 0.0 || maxSteps <= 0) return listOf(midpoint)
    return buildList {
        add(midpoint)
        for (step in 1..maxSteps) {
            val offset = separationMeters * step
            if (midpoint - offset >= 0.0) add(midpoint - offset)
            if (midpoint + offset <= lengthMeters) add(midpoint + offset)
        }
    }
}

private fun interpolate(start: GeoPoint, end: GeoPoint, fraction: Double): GeoPoint {
    val startLatitude = Math.toRadians(start.latitude)
    val startLongitude = Math.toRadians(start.longitude)
    val endLatitude = Math.toRadians(end.latitude)
    val endLongitude = Math.toRadians(end.longitude)
    val angle = haversineMeters(start, end) / EARTH_RADIUS_METERS
    val sinAngle = sin(angle)
    if (sinAngle < 1e-12) return start

    val startWeight = sin((1.0 - fraction) * angle) / sinAngle
    val endWeight = sin(fraction * angle) / sinAngle
    val x = startWeight * cos(startLatitude) * cos(startLongitude) +
        endWeight * cos(endLatitude) * cos(endLongitude)
    val y = startWeight * cos(startLatitude) * sin(startLongitude) +
        endWeight * cos(endLatitude) * sin(endLongitude)
    val z = startWeight * sin(startLatitude) + endWeight * sin(endLatitude)
    return GeoPoint(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
}

private const val DEFAULT_BADGE_SEPARATION_METERS = 300.0
private const val DEFAULT_MAX_STAGGER_STEPS = 4
