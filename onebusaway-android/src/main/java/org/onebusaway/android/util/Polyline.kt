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

import android.location.Location
import java.util.Arrays

/**
 * An ordered sequence of geographic points with fast distance-based interpolation. Cumulative
 * distances are precomputed at construction so that [interpolate] and [subPolyline] are O(log n)
 * via binary search.
 */
class Polyline(val points: List<Location>) {

    private val cumulativeDistances: DoubleArray =
            points
                    .zipWithNext { prev, cur ->
                        LocationUtils.haversineDistance(
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

    /** Returns the interpolated position at the given distance along the polyline. */
    fun interpolate(distance: Double): Location? {
        val out = Location("")
        return if (interpolateInto(distance, out)) out else null
    }

    /** Interpolates using a pre-computed [seg] from [segmentIndex]. */
    fun interpolate(distance: Double, seg: Int): Location? {
        val out = Location("")
        return if (interpolateInto(distance, seg, out)) out else null
    }

    /** Writes the interpolated position into [out]. Returns false if the polyline is empty. */
    fun interpolateInto(distance: Double, out: Location): Boolean {
        if (points.isEmpty()) return false
        if (points.size < 2 || distance <= 0) {
            out.set(points.first())
            return true
        }
        return interpolateInto(distance, segmentIndex(distance), out)
    }

    /** Interpolates into [out] using a pre-computed [seg] from [segmentIndex]. */
    fun interpolateInto(distance: Double, seg: Int, out: Location): Boolean {
        if (seg < 0) {
            if (points.isEmpty()) return false
            out.set(points.first())
            return true
        }
        val segLen = cumulativeDistances[seg + 1] - cumulativeDistances[seg]
        if (segLen <= 0) {
            out.set(points[seg])
            return true
        }
        val fraction = ((distance - cumulativeDistances[seg]) / segLen).coerceIn(0.0, 1.0)
        val p0 = points[seg]
        val p1 = points[seg + 1]
        out.latitude = p0.latitude + fraction * (p1.latitude - p0.latitude)
        out.longitude = p0.longitude + fraction * (p1.longitude - p0.longitude)
        return true
    }

    /** Returns the bearing (0-360) of the polyline segment at the given distance, or NaN. */
    fun bearingAt(distance: Double): Float = bearingAt(segmentIndex(distance))

    /** Returns the bearing using a pre-computed [seg] from [segmentIndex]. */
    fun bearingAt(seg: Int): Float {
        if (seg < 0) return Float.NaN
        return (points[seg].bearingTo(points[seg + 1]) + 360) % 360
    }

    /** Returns the sub-polyline between two distances, with interpolated endpoints. */
    fun subPolyline(startDist: Double, endDist: Double): List<Location>? {
        val result = mutableListOf<Location>()
        return if (subPolylineInto(startDist, endDist, result)) result else null
    }

    /**
     * Fills [out] with the sub-polyline between two distances, with interpolated endpoints. The
     * list is cleared first. Returns true if the sub-polyline was produced.
     */
    fun subPolylineInto(startDist: Double, endDist: Double, out: MutableList<Location>): Boolean {
        out.clear()
        val start = interpolate(startDist) ?: return false
        val end = interpolate(endDist) ?: return false
        out.add(start)
        vertexRange(startDist, endDist)?.let { (from, to) ->
            for (i in from until to) out.add(points[i])
        }
        out.add(end)
        return true
    }

    /**
     * Maps the sub-polyline between two distances into [out] via [transform], using [scratch] for
     * interpolated endpoints. The list is cleared first.
     */
    fun <T> subPolylineMapInto(
            startDist: Double,
            endDist: Double,
            out: MutableList<T>,
            scratch: Location,
            transform: (Location) -> T
    ): Boolean {
        out.clear()
        if (!interpolateInto(startDist, scratch)) return false
        out.add(transform(scratch))
        vertexRange(startDist, endDist)?.let { (from, to) ->
            for (i in from until to) out.add(transform(points[i]))
        }
        if (!interpolateInto(endDist, scratch)) return false
        out.add(transform(scratch))
        return true
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
