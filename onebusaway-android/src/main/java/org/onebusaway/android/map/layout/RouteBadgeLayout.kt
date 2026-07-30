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

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint

/** One shape on which a route badge may be anchored. */
data class RouteBadgePath(val points: List<GeoPoint>)

/**
 * One badge identity and the geographic shapes on which it may be anchored. [route] is an opaque token
 * — the layout never reads it, only echoes it back on the placement — so a caller keys by whatever
 * identifies a badge in its own view: adjacency by [RouteDirectionKey], an itinerary (which has no
 * direction axis) by something else entirely.
 */
data class RouteBadgeLayoutInput<K>(
    val route: K,
    val paths: List<RouteBadgePath>
)

/** A stable geographic badge anchor. Input order determines collision priority. */
data class RouteBadgePlacement<K>(
    val route: K,
    val point: GeoPoint,
    val overlaps: Boolean
)

/**
 * Places one badge per input identity in geographic space, following the line-center
 * convention used for road shields. The midpoint by distance of the route's longest shape is
 * preferred. When that is too close to an earlier route's badge, candidates alternate upstream and
 * downstream by a fixed geographic distance; if none clears [minimumSeparationMeters], the least-conflicting candidate
 * wins. The result is computed only when route geometry changes and remains fixed across pan/zoom.
 */
fun <K> layoutRouteBadges(
    inputs: List<RouteBadgeLayoutInput<K>>,
    minimumSeparationMeters: Double = DEFAULT_BADGE_SEPARATION_METERS,
    maxStaggerSteps: Int = DEFAULT_MAX_STAGGER_STEPS
): List<RouteBadgePlacement<K>> {
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

/**
 * One route label to place: the route(s) it names (stacked, in reading order — see [RouteBadge.routes]),
 * where a tap on it navigates (null for an informational label — see [RouteBadge.tap]), and the shapes it
 * may be anchored on. List order is collision priority, as in [layoutRouteBadges].
 */
internal data class RouteBadgeRequest(
    val routes: List<BadgedRoute>,
    val paths: List<RouteBadgePath>,
    val tap: RouteDirectionKey? = null
)

/**
 * The shared tail behind every route label on the map — focused-stop adjacency (#1827) and a directions
 * itinerary's rides (#2066): anchor each request via [layoutRouteBadges], preserve the caller's order,
 * and drop a request the layout couldn't measure (no anchor, so no label). Callers differ only in how
 * they group geometry into requests, which is the half that is actually view-specific.
 */
internal fun placeRouteBadges(requests: List<RouteBadgeRequest>): List<RouteBadge> {
    // A request's own position is its layout key: unique by construction, and since the layout only
    // echoes the key back, neither caller has to invent an identity it would never read.
    val placements = layoutRouteBadges(
        requests.mapIndexed { index, request -> RouteBadgeLayoutInput(index, request.paths) }
    ).associateBy { it.route }
    return requests.mapIndexedNotNull { index, request ->
        placements[index]?.let { placement ->
            RouteBadge(routes = request.routes, point = placement.point, tap = request.tap)
        }
    }
}

private data class MeasuredPath(
    val points: List<GeoPoint>,
    val cumulativeMeters: DoubleArray
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
    val x = startWeight *
        cos(startLatitude) *
        cos(startLongitude) +
        endWeight *
        cos(endLatitude) *
        cos(endLongitude)
    val y = startWeight *
        cos(startLatitude) *
        sin(startLongitude) +
        endWeight *
        cos(endLatitude) *
        sin(endLongitude)
    val z = startWeight * sin(startLatitude) + endWeight * sin(endLatitude)
    return GeoPoint(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
}

private const val DEFAULT_BADGE_SEPARATION_METERS = 300.0
private const val DEFAULT_MAX_STAGGER_STEPS = 4
