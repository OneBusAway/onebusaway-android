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
package org.onebusaway.android.extrapolation.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.haversineDistance

/**
 * Plain-JVM coverage for [Polyline]'s interpolation / segment / bearing geometry. Runs off-device now
 * that [Polyline] operates on flavor-neutral [GeoPoint]s and carries no `android.location.Location`
 * (the GeoPoint reform, #1944) — previously it could only be exercised as an instrumented test.
 */
class InterpolateAlongPolylineTest {

    private fun gp(lat: Double, lng: Double) = GeoPoint(lat, lng)

    // Three points along the equator, each ~111km apart.
    private val poly = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(0.0, 2.0)))

    // The first segment's length by the same haversine metric the polyline uses internally.
    private val segLen = haversineDistance(0.0, 0.0, 0.0, 1.0)

    @Test
    fun emptyPolylineReturnsNull() {
        assertNull(Polyline(emptyList()).interpolate(50.0))
    }

    @Test
    fun zeroDistanceReturnsFirstPoint() {
        val result = poly.interpolate(0.0)!!
        assertEquals(0.0, result.latitude, 1e-12)
        assertEquals(0.0, result.longitude, 1e-12)
    }

    @Test
    fun negativeDistanceReturnsFirstPoint() {
        val result = poly.interpolate(-10.0)!!
        assertEquals(0.0, result.latitude, 1e-12)
        assertEquals(0.0, result.longitude, 1e-12)
    }

    @Test
    fun distanceBeyondEndReturnsLastPoint() {
        val result = poly.interpolate(999_999.0)!!
        assertEquals(0.0, result.latitude, 1e-12)
        assertEquals(2.0, result.longitude, 1e-12)
    }

    @Test
    fun midSegmentInterpolation() {
        // Halfway along the first segment should give ~0.5 degrees longitude.
        val result = poly.interpolate(segLen / 2)!!
        assertEquals(0.0, result.latitude, 1e-12)
        assertEquals(0.5, result.longitude, 0.01)
    }

    @Test
    fun exactVertexDistance() {
        // At the exact distance of the second point.
        val result = poly.interpolate(segLen)!!
        assertEquals(0.0, result.latitude, 1e-12)
        assertEquals(1.0, result.longitude, 1e-9)
    }

    @Test
    fun singlePointPolyline() {
        val single = Polyline(listOf(gp(47.6, -122.3)))
        val result = single.interpolate(50.0)!!
        assertEquals(47.6, result.latitude, 1e-12)
        assertEquals(-122.3, result.longitude, 1e-12)
    }

    @Test
    fun subPolylineReturnsEndpoints() {
        val sub = poly.subPolyline(segLen * 0.25, segLen * 0.75)
        assertNotNull(sub)
        assertEquals(0.25, sub!!.first().longitude, 0.01)
        assertEquals(0.75, sub.last().longitude, 0.01)
    }

    // --- segmentIndex tests ---

    @Test
    fun segmentIndexEmptyPolyline() {
        assertEquals(-1, Polyline(emptyList()).segmentIndex(0.0))
    }

    @Test
    fun segmentIndexSinglePoint() {
        assertEquals(-1, Polyline(listOf(gp(0.0, 0.0))).segmentIndex(0.0))
    }

    @Test
    fun segmentIndexAtStart() {
        assertEquals(0, poly.segmentIndex(0.0))
    }

    @Test
    fun segmentIndexBeyondEnd() {
        // Should clamp to last segment.
        assertEquals(1, poly.segmentIndex(999_999.0))
    }

    @Test
    fun segmentIndexMidFirstSegment() {
        assertEquals(0, poly.segmentIndex(segLen / 2))
    }

    @Test
    fun segmentIndexMidSecondSegment() {
        assertEquals(1, poly.segmentIndex(segLen * 1.5))
    }

    // --- bearingAt tests ---

    @Test
    fun bearingAtEmptyPolyline() {
        assertTrue(Polyline(emptyList()).bearingAt(0.0).isNaN())
    }

    @Test
    fun bearingAtSinglePoint() {
        assertTrue(Polyline(listOf(gp(0.0, 0.0))).bearingAt(0.0).isNaN())
    }

    @Test
    fun bearingAtEastward() {
        // Points along the equator heading east → bearing ~90.
        val bearing = poly.bearingAt(0.0)
        assertEquals(90.0, bearing.toDouble(), 1.0)
    }

    @Test
    fun bearingAtNorthward() {
        // Two points heading due north.
        val northPoly = Polyline(listOf(gp(0.0, 0.0), gp(1.0, 0.0)))
        val bearing = northPoly.bearingAt(0.0)
        assertEquals(0.0, bearing.toDouble(), 1.0)
    }

    @Test
    fun bearingAtSecondSegment() {
        // L-shaped polyline: east then north.
        val turn = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(1.0, 1.0)))
        val bearingFirst = turn.bearingAt(segLen * 0.5)
        val bearingSecond = turn.bearingAt(segLen * 1.5)
        assertEquals(90.0, bearingFirst.toDouble(), 1.0)
        assertEquals(0.0, bearingSecond.toDouble(), 1.0)
    }

    // --- segment-indexed overloads ---

    @Test
    fun interpolateWithSegmentMatchesDirect() {
        val dist = segLen * 0.75
        val direct = poly.interpolate(dist)!!
        val seg = poly.segmentIndex(dist)
        val indexed = poly.interpolate(dist, seg)!!
        assertEquals(direct.latitude, indexed.latitude, 1e-12)
        assertEquals(direct.longitude, indexed.longitude, 1e-12)
    }

    @Test
    fun bearingWithSegmentMatchesDirect() {
        val dist = segLen * 0.5
        val direct = poly.bearingAt(dist)
        val seg = poly.segmentIndex(dist)
        val indexed = poly.bearingAt(seg)
        assertEquals(direct, indexed, 1e-6f)
    }
}
