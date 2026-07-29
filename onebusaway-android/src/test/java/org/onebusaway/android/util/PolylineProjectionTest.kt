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
package org.onebusaway.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM coverage for [Polyline.project] — the inverse of [Polyline.interpolate]: a position in,
 * an arc length along the line out. Geometry is built from equator segments so every expected
 * distance is analytic rather than a hard-coded metre count.
 */
class PolylineProjectionTest {

    private fun gp(lat: Double, lng: Double) = GeoPoint(lat, lng)

    /** Three points along the equator, each one degree (~111 km) apart. */
    private val poly = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(0.0, 2.0)))

    /** One segment's length by the same haversine metric the polyline uses internally. */
    private val segLen = haversineDistance(0.0, 0.0, 0.0, 1.0)

    @Test
    fun emptyPolylineProjectsToNull() {
        assertNull(Polyline(emptyList()).project(0.0, 0.0))
    }

    @Test
    fun singlePointPolylineProjectsToItsOnlyPointAtZero() {
        val single = Polyline(listOf(gp(0.0, 1.0)))
        val projection = single.project(0.0, 0.0)!!

        assertEquals(0.0, projection.distanceAlongMeters, 1e-9)
        assertEquals(1.0, projection.point.longitude, 1e-12)
        assertEquals(segLen, projection.offsetMeters, segLen * 1e-6)
    }

    @Test
    fun pointOnTheLineProjectsToItsOwnArcLength() {
        val projection = poly.project(0.0, 0.5)!!

        assertEquals(segLen / 2, projection.distanceAlongMeters, segLen * 1e-6)
        assertEquals(0.0, projection.offsetMeters, 1e-6)
    }

    @Test
    fun exactVertexProjectsToTheVertexDistance() {
        val projection = poly.project(0.0, 1.0)!!

        assertEquals(segLen, projection.distanceAlongMeters, segLen * 1e-6)
        assertEquals(0.0, projection.offsetMeters, 1e-6)
    }

    @Test
    fun pointOnTheSecondSegmentAccumulatesTheFirst() {
        val projection = poly.project(0.0, 1.5)!!

        assertEquals(segLen * 1.5, projection.distanceAlongMeters, segLen * 1e-6)
    }

    @Test
    fun pointBeforeTheStartClampsToZero() {
        val projection = poly.project(0.0, -0.5)!!

        assertEquals(0.0, projection.distanceAlongMeters, 1e-9)
        // The offset is the real distance back to the start, not zero.
        assertEquals(segLen / 2, projection.offsetMeters, segLen * 1e-3)
    }

    @Test
    fun pointBeyondTheEndClampsToTheTotalLength() {
        val projection = poly.project(0.0, 2.5)!!

        assertEquals(poly.lengthMeters, projection.distanceAlongMeters, segLen * 1e-6)
        assertEquals(segLen * 2, poly.lengthMeters, segLen * 1e-6)
    }

    @Test
    fun offLinePointKeepsItsArcLengthAndReportsItsOffset() {
        // A tenth of a degree north of the midpoint of the first segment.
        val projection = poly.project(0.1, 0.5)!!

        assertEquals(segLen / 2, projection.distanceAlongMeters, segLen * 1e-3)
        assertEquals(haversineDistance(0.0, 0.5, 0.1, 0.5), projection.offsetMeters, 1.0)
    }

    @Test
    fun projectionIsMonotoneAlongACurvingRoute() {
        // An L: east along the equator, then north. A rider travelling it gets steadily further from
        // the start in arc length even while the straight-line distance to the far end does not fall
        // monotonically — which is the whole reason this coordinate exists.
        val corner = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(1.0, 1.0)))
        val along = listOf(
            corner.project(0.0, 0.25)!!.distanceAlongMeters,
            corner.project(0.0, 0.75)!!.distanceAlongMeters,
            corner.project(0.25, 1.0)!!.distanceAlongMeters,
            corner.project(0.75, 1.0)!!.distanceAlongMeters
        )

        assertEquals(along.sorted(), along)
        assertTrue("arc length must exceed the first leg once past the corner", along.last() > segLen)
    }

    @Test
    fun nearestPointStillReturnsTheProjectedPoint() {
        val nearest = poly.nearestPoint(0.1, 0.5)!!
        val projection = poly.project(0.1, 0.5)!!

        assertEquals(projection.point, nearest)
        assertEquals(0.0, nearest.latitude, 1e-9)
    }
}
