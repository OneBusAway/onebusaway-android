/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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

import java.util.Arrays
import kotlin.math.cos

/**
 * An ordered sequence of flavor-neutral [GeoPoint]s with fast distance-based interpolation. Cumulative
 * distances are precomputed at construction so that [interpolate] and [bearingAt] are O(log n) via
 * binary search; [subPolyline] is O(log n + k) in the number of vertices it returns. Carries no
 * `android.location.Location` dependency — it's pure geometry, so it (and its callers) run on plain JVM.
 */
class Polyline(points: List<GeoPoint>) {

    /** Owned copy, so a caller mutating its list can't desync it from [cumulativeDistances]. */
    val points: List<GeoPoint> = points.toList()

    private val cumulativeDistances: DoubleArray =
            points
                    .zipWithNext { prev, cur ->
                        haversineDistance(
                                prev.latitude,
                                prev.longitude,
                                cur.latitude,
                                cur.longitude
                        )
                    }
                    .runningFold(0.0) { acc, dist -> acc + dist }
                    .toDoubleArray()

    /**
     * Returns the index of the segment start for the given distance, or -1 if the polyline has
     * fewer than 2 points. The distance is clamped to the polyline bounds. Reuse the result across
     * [interpolate] and [bearingAt] to avoid repeated binary searches.
     */
    fun segmentIndex(distance: Double): Int {
        if (points.size < 2) return -1
        val d = distance.coerceIn(0.0, cumulativeDistances.last())
        val idx = Arrays.binarySearch(cumulativeDistances, d)
        return when {
            idx >= 0 -> idx.coerceAtMost(points.size - 2)
            else -> (-idx - 2).coerceIn(0, points.size - 2)
        }
    }

    /** Returns the interpolated position at the given distance along the polyline, or null if empty. */
    fun interpolate(distance: Double): GeoPoint? {
        if (points.isEmpty()) return null
        if (points.size < 2 || distance <= 0) return points.first()
        return interpolate(distance, segmentIndex(distance))
    }

    /** Interpolates using a pre-computed [seg] from [segmentIndex]. */
    fun interpolate(distance: Double, seg: Int): GeoPoint? {
        if (seg < 0) return points.firstOrNull()
        val segLen = cumulativeDistances[seg + 1] - cumulativeDistances[seg]
        if (segLen <= 0) return points[seg]
        val fraction = ((distance - cumulativeDistances[seg]) / segLen).coerceIn(0.0, 1.0)
        val p0 = points[seg]
        val p1 = points[seg + 1]
        return GeoPoint(
                p0.latitude + fraction * (p1.latitude - p0.latitude),
                p0.longitude + fraction * (p1.longitude - p0.longitude)
        )
    }

    /** Returns the bearing (0-360) of the polyline segment at the given distance, or NaN. */
    fun bearingAt(distance: Double): Float = bearingAt(segmentIndex(distance))

    /** Returns the bearing using a pre-computed [seg] from [segmentIndex]. */
    fun bearingAt(seg: Int): Float {
        if (seg < 0) return Float.NaN
        val p0 = points[seg]
        val p1 = points[seg + 1]
        val bearing = initialBearing(p0.latitude, p0.longitude, p1.latitude, p1.longitude)
        return ((bearing + 360) % 360).toFloat()
    }

    /**
     * The point on this polyline closest to ([latitude], [longitude]), or null if the polyline is
     * empty. Each segment is projected in a local equirectangular frame (longitude scaled by
     * cos(latitude), which keeps the two axes to the same metric scale near the query point), the
     * projection parameter clamped to the segment, and the true closest projected point returned. Used
     * to sit a route stop on the route centerline (#1752). This is an exact geometric projection, not a
     * magnitude guess; the planar approximation is negligible at the stop-to-line distances involved.
     */
    fun nearestPoint(latitude: Double, longitude: Double): GeoPoint? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points.first()
        val cosLat = cos(Math.toRadians(latitude))
        // Query point in the local frame.
        val px = longitude * cosLat
        val py = latitude
        var bestLat = 0.0
        var bestLon = 0.0
        var bestDist2 = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val ax = a.longitude * cosLat
            val ay = a.latitude
            val dx = b.longitude * cosLat - ax
            val dy = b.latitude - ay
            val segLen2 = dx * dx + dy * dy
            val t = if (segLen2 <= 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / segLen2).coerceIn(0.0, 1.0)
            val projLat = a.latitude + t * (b.latitude - a.latitude)
            val projLon = a.longitude + t * (b.longitude - a.longitude)
            val ex = projLon * cosLat - px
            val ey = projLat - py
            val dist2 = ex * ex + ey * ey
            if (dist2 < bestDist2) {
                bestDist2 = dist2
                bestLat = projLat
                bestLon = projLon
            }
        }
        return GeoPoint(bestLat, bestLon)
    }

    /** Returns the sub-polyline between two distances, with interpolated endpoints. */
    fun subPolyline(startDist: Double, endDist: Double): List<GeoPoint>? {
        val start = interpolate(startDist) ?: return null
        val end = interpolate(endDist) ?: return null
        val result = mutableListOf(start)
        vertexRange(startDist, endDist)?.let { (from, to) ->
            for (i in from until to) result.add(points[i])
        }
        result.add(end)
        return result
    }

    /**
     * Finds the range of vertex indices whose cumulative distances fall strictly between
     * [startDist] and [endDist]. Returns a pair (from, to) for use as a half-open range, or null if
     * no vertices fall in range.
     */
    private fun vertexRange(startDist: Double, endDist: Double): Pair<Int, Int>? {
        if (cumulativeDistances.isEmpty() || startDist >= endDist) return null
        val rawStart = Arrays.binarySearch(cumulativeDistances, startDist)
        val from = if (rawStart >= 0) rawStart + 1 else -rawStart - 1
        val rawEnd = Arrays.binarySearch(cumulativeDistances, endDist)
        val to = if (rawEnd >= 0) rawEnd else -rawEnd - 1
        return if (from < to) Pair(from, to) else null
    }
}
