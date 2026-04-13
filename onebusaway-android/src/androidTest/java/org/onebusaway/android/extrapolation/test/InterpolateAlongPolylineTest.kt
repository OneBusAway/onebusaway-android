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

import android.location.Location
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.Polyline

@RunWith(AndroidJUnit4::class)
class InterpolateAlongPolylineTest {

    private fun loc(lat: Double, lng: Double): Location = LocationUtils.makeLocation(lat, lng)

    // Three points along the equator, each ~111km apart
    private val poly = Polyline(listOf(loc(0.0, 0.0), loc(0.0, 1.0), loc(0.0, 2.0)))

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
        // Halfway along the first segment should give ~0.5 degrees longitude
        val segLen = poly.interpolate(0.0)!!.distanceTo(loc(0.0, 1.0)).toDouble()
        val result = poly.interpolate(segLen / 2)!!
        assertEquals(0.0, result.latitude, 1e-12)
        assertEquals(0.5, result.longitude, 0.01)
    }

    @Test
    fun exactVertexDistance() {
        // At the exact distance of the second point
        val segLen = poly.interpolate(0.0)!!.distanceTo(loc(0.0, 1.0)).toDouble()
        val result = poly.interpolate(segLen)!!
        assertEquals(0.0, result.latitude, 1e-12)
        // distanceTo uses a different geodesic formula than haversine, so allow small error
        assertEquals(1.0, result.longitude, 0.01)
    }

    @Test
    fun singlePointPolyline() {
        val single = Polyline(listOf(loc(47.6, -122.3)))
        val result = single.interpolate(50.0)!!
        assertEquals(47.6, result.latitude, 1e-12)
        assertEquals(-122.3, result.longitude, 1e-12)
    }

    @Test
    fun subPolylineReturnsEndpoints() {
        val segLen = poly.interpolate(0.0)!!.distanceTo(loc(0.0, 1.0)).toDouble()
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
        assertEquals(-1, Polyline(listOf(loc(0.0, 0.0))).segmentIndex(0.0))
    }

    @Test
    fun segmentIndexAtStart() {
        assertEquals(0, poly.segmentIndex(0.0))
    }

    @Test
    fun segmentIndexBeyondEnd() {
        // Should clamp to last segment
        assertEquals(1, poly.segmentIndex(999_999.0))
    }

    @Test
    fun segmentIndexMidFirstSegment() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        assertEquals(0, poly.segmentIndex(segLen / 2))
    }

    @Test
    fun segmentIndexMidSecondSegment() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        assertEquals(1, poly.segmentIndex(segLen * 1.5))
    }

    // --- bearingAt tests ---

    @Test
    fun bearingAtEmptyPolyline() {
        assertTrue(Polyline(emptyList()).bearingAt(0.0).isNaN())
    }

    @Test
    fun bearingAtSinglePoint() {
        assertTrue(Polyline(listOf(loc(0.0, 0.0))).bearingAt(0.0).isNaN())
    }

    @Test
    fun bearingAtEastward() {
        // Points along the equator heading east → bearing ~90
        val bearing = poly.bearingAt(0.0)
        assertEquals(90.0, bearing.toDouble(), 1.0)
    }

    @Test
    fun bearingAtNorthward() {
        // Two points heading due north
        val northPoly = Polyline(listOf(loc(0.0, 0.0), loc(1.0, 0.0)))
        val bearing = northPoly.bearingAt(0.0)
        assertEquals(0.0, bearing.toDouble(), 1.0)
    }

    @Test
    fun bearingAtSecondSegment() {
        // L-shaped polyline: east then north
        val turn = Polyline(listOf(loc(0.0, 0.0), loc(0.0, 1.0), loc(1.0, 1.0)))
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val bearingFirst = turn.bearingAt(segLen * 0.5)
        val bearingSecond = turn.bearingAt(segLen * 1.5)
        assertEquals(90.0, bearingFirst.toDouble(), 1.0)
        assertEquals(0.0, bearingSecond.toDouble(), 1.0)
    }

    // --- segment-indexed overloads ---

    @Test
    fun interpolateWithSegmentMatchesDirect() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val dist = segLen * 0.75
        val direct = poly.interpolate(dist)!!
        val seg = poly.segmentIndex(dist)
        val indexed = poly.interpolate(dist, seg)!!
        assertEquals(direct.latitude, indexed.latitude, 1e-12)
        assertEquals(direct.longitude, indexed.longitude, 1e-12)
    }

    @Test
    fun bearingWithSegmentMatchesDirect() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val dist = segLen * 0.5
        val direct = poly.bearingAt(dist)
        val seg = poly.segmentIndex(dist)
        val indexed = poly.bearingAt(seg)
        assertEquals(direct, indexed, 1e-6f)
    }

    // --- interpolateInto tests ---

    @Test
    fun interpolateIntoEmptyReturnsFalse() {
        val out = Location("")
        assertFalse(Polyline(emptyList()).interpolateInto(50.0, out))
    }

    @Test
    fun interpolateIntoMatchesInterpolate() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val dist = segLen * 0.75
        val allocating = poly.interpolate(dist)!!
        val out = Location("")
        assertTrue(poly.interpolateInto(dist, out))
        assertEquals(allocating.latitude, out.latitude, 1e-12)
        assertEquals(allocating.longitude, out.longitude, 1e-12)
    }

    @Test
    fun interpolateIntoZeroDistance() {
        val out = Location("")
        assertTrue(poly.interpolateInto(0.0, out))
        assertEquals(0.0, out.latitude, 1e-12)
        assertEquals(0.0, out.longitude, 1e-12)
    }

    @Test
    fun interpolateIntoNegativeDistance() {
        val out = Location("")
        assertTrue(poly.interpolateInto(-10.0, out))
        assertEquals(0.0, out.latitude, 1e-12)
        assertEquals(0.0, out.longitude, 1e-12)
    }

    @Test
    fun interpolateIntoWithSegmentMatchesDirect() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val dist = segLen * 0.75
        val seg = poly.segmentIndex(dist)
        val direct = Location("")
        poly.interpolateInto(dist, direct)
        val indexed = Location("")
        poly.interpolateInto(dist, seg, indexed)
        assertEquals(direct.latitude, indexed.latitude, 1e-12)
        assertEquals(direct.longitude, indexed.longitude, 1e-12)
    }

    @Test
    fun interpolateIntoReusesLocation() {
        val out = Location("")
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        poly.interpolateInto(segLen * 0.25, out)
        assertEquals(0.25, out.longitude, 0.01)
        // Reuse the same object for a different distance
        poly.interpolateInto(segLen * 0.75, out)
        assertEquals(0.75, out.longitude, 0.01)
    }

    // --- subPolylineMapInto tests ---

    @Test
    fun subPolylineMapIntoEmptyReturnsFalse() {
        val out = mutableListOf<Pair<Double, Double>>()
        val scratch = Location("")
        assertFalse(
                Polyline(emptyList()).subPolylineMapInto(0.0, 100.0, out, scratch) {
                    it.latitude to it.longitude
                }
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun subPolylineMapIntoMatchesSubPolyline() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val sub = poly.subPolyline(segLen * 0.25, segLen * 0.75)!!
        val out = mutableListOf<Pair<Double, Double>>()
        val scratch = Location("")
        assertTrue(
                poly.subPolylineMapInto(segLen * 0.25, segLen * 0.75, out, scratch) {
                    it.latitude to it.longitude
                }
        )
        assertEquals(sub.size, out.size)
        for (i in sub.indices) {
            assertEquals(sub[i].latitude, out[i].first, 1e-12)
            assertEquals(sub[i].longitude, out[i].second, 1e-12)
        }
    }

    @Test
    fun subPolylineMapIntoSpanningVertex() {
        // Range that spans the middle vertex — should include interpolated start,
        // the vertex, and interpolated end
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val out = mutableListOf<Pair<Double, Double>>()
        val scratch = Location("")
        assertTrue(
                poly.subPolylineMapInto(segLen * 0.5, segLen * 1.5, out, scratch) {
                    it.latitude to it.longitude
                }
        )
        assertEquals(0.5, out.first().second, 0.01)
        assertEquals(1.5, out.last().second, 0.01)
        assertTrue(out.size >= 3) // start + vertex + end
    }

    @Test
    fun subPolylineMapIntoClearsOutput() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val out = mutableListOf(0.0 to 0.0, 0.0 to 0.0, 0.0 to 0.0)
        val scratch = Location("")
        poly.subPolylineMapInto(segLen * 0.25, segLen * 0.75, out, scratch) {
            it.latitude to it.longitude
        }
        // Should have cleared the pre-existing entries
        assertEquals(0.25, out.first().second, 0.01)
    }

    @Test
    fun subPolylineMapIntoReusesScratch() {
        val segLen = loc(0.0, 0.0).distanceTo(loc(0.0, 1.0)).toDouble()
        val out = mutableListOf<Pair<Double, Double>>()
        val scratch = Location("")
        // Call twice with the same scratch and output list
        poly.subPolylineMapInto(segLen * 0.1, segLen * 0.4, out, scratch) {
            it.latitude to it.longitude
        }
        assertEquals(0.1, out.first().second, 0.01)
        poly.subPolylineMapInto(segLen * 0.6, segLen * 0.9, out, scratch) {
            it.latitude to it.longitude
        }
        assertEquals(0.6, out.first().second, 0.01)
    }
}
