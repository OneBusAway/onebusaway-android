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
 * Geometry for [Polyline], including [Polyline.nearestProjection] — the inverse of
 * [Polyline.interpolate]: a position in, an arc length along the line out. The cases below build
 * their geometry from equator segments so every expected distance is analytic rather than a
 * hard-coded metre count.
 */
class PolylineTest {

    @Test
    fun nearestProjection_reportsPointAndDistanceAlongLine() {
        val line = Polyline(
            listOf(
                GeoPoint(47.60, -122.33),
                GeoPoint(47.62, -122.33),
                GeoPoint(47.64, -122.33)
            )
        )

        val projection = requireNotNull(line.nearestProjection(47.63, -122.32))

        assertEquals(47.63, projection.point.latitude, 0.000001)
        assertEquals(-122.33, projection.point.longitude, 0.000001)
        assertEquals(
            haversineDistance(47.60, -122.33, 47.63, -122.33),
            projection.distanceAlong,
            0.1
        )
        assertEquals(
            haversineDistance(47.63, -122.32, 47.63, -122.33),
            projection.distanceToPoint,
            0.1
        )
    }

    private fun gp(lat: Double, lng: Double) = GeoPoint(lat, lng)

    /** Three points along the equator, each one degree (~111 km) apart. */
    private val poly = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(0.0, 2.0)))

    /** One segment's length by the same haversine metric the polyline uses internally. */
    private val segLen = haversineDistance(0.0, 0.0, 0.0, 1.0)

    @Test
    fun emptyPolylineProjectsToNull() {
        assertNull(Polyline(emptyList()).nearestProjection(0.0, 0.0))
    }

    @Test
    fun singlePointPolylineProjectsToItsOnlyPointAtZero() {
        val single = Polyline(listOf(gp(0.0, 1.0)))
        val projection = single.nearestProjection(0.0, 0.0)!!

        assertEquals(0.0, projection.distanceAlong, 1e-9)
        assertEquals(1.0, projection.point.longitude, 1e-12)
        assertEquals(segLen, projection.distanceToPoint, segLen * 1e-6)
    }

    @Test
    fun pointOnTheLineProjectsToItsOwnArcLength() {
        val projection = poly.nearestProjection(0.0, 0.5)!!

        assertEquals(segLen / 2, projection.distanceAlong, segLen * 1e-6)
        assertEquals(0.0, projection.distanceToPoint, 1e-6)
    }

    @Test
    fun exactVertexProjectsToTheVertexDistance() {
        val projection = poly.nearestProjection(0.0, 1.0)!!

        assertEquals(segLen, projection.distanceAlong, segLen * 1e-6)
        assertEquals(0.0, projection.distanceToPoint, 1e-6)
    }

    @Test
    fun pointOnTheSecondSegmentAccumulatesTheFirst() {
        val projection = poly.nearestProjection(0.0, 1.5)!!

        assertEquals(segLen * 1.5, projection.distanceAlong, segLen * 1e-6)
    }

    @Test
    fun pointBeforeTheStartClampsToZero() {
        val projection = poly.nearestProjection(0.0, -0.5)!!

        assertEquals(0.0, projection.distanceAlong, 1e-9)
        // The offset is the real distance back to the start, not zero.
        assertEquals(segLen / 2, projection.distanceToPoint, segLen * 1e-3)
    }

    @Test
    fun pointBeyondTheEndClampsToTheTotalLength() {
        val projection = poly.nearestProjection(0.0, 2.5)!!

        assertEquals(segLen * 2, projection.distanceAlong, segLen * 1e-6)
    }

    @Test
    fun offLinePointKeepsItsArcLengthAndReportsItsOffset() {
        // A tenth of a degree north of the midpoint of the first segment.
        val projection = poly.nearestProjection(0.1, 0.5)!!

        assertEquals(segLen / 2, projection.distanceAlong, segLen * 1e-3)
        assertEquals(haversineDistance(0.0, 0.5, 0.1, 0.5), projection.distanceToPoint, 1.0)
    }

    @Test
    fun projectionIsMonotoneAlongACurvingRoute() {
        // An L: east along the equator, then north. A rider travelling it gets steadily further from
        // the start in arc length even while the straight-line distance to the far end does not fall
        // monotonically — which is the whole reason this coordinate exists.
        val corner = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(1.0, 1.0)))
        val along = listOf(
            corner.nearestProjection(0.0, 0.25)!!.distanceAlong,
            corner.nearestProjection(0.0, 0.75)!!.distanceAlong,
            corner.nearestProjection(0.25, 1.0)!!.distanceAlong,
            corner.nearestProjection(0.75, 1.0)!!.distanceAlong
        )

        assertEquals(along.sorted(), along)
        assertTrue("arc length must exceed the first leg once past the corner", along.last() > segLen)
    }

    @Test
    fun anAnchorKeepsAnOutAndBackPathOnTheBranchBeingTravelled() {
        // Out along the equator and straight back — the two branches are the same ground but a whole
        // out-and-back apart in arc length. Unanchored, a point on the return branch is answered with
        // the outbound branch's arc length, which reads as the traveller having jumped backwards.
        val outAndBack = Polyline(listOf(gp(0.0, 0.0), gp(0.0, 1.0), gp(0.0, 0.0)))
        val total = outAndBack.nearestProjection(0.0, 0.0, segLen * 1.5)!!.distanceAlong

        val unanchored = outAndBack.nearestProjection(0.0, 0.5)!!.distanceAlong
        val anchored = outAndBack.nearestProjection(0.0, 0.5, segLen)!!.distanceAlong

        assertEquals("the globally nearest answer sits on the outbound branch", segLen / 2, unanchored, segLen * 1e-3)
        assertEquals("anchored past the turn, the same point reads on the return branch", segLen * 1.5, anchored, segLen * 1e-3)
        assertEquals(segLen * 2, total, segLen * 1e-6)
    }

    @Test
    fun anAnchorBeyondTheEndOfTheLineHasNoAnswer() {
        assertNull(poly.nearestProjection(0.0, 0.5, segLen * 2 + 1.0))
    }

    @Test
    fun nearestPointStillReturnsTheProjectedPoint() {
        val nearest = poly.nearestPoint(0.1, 0.5)!!
        val projection = poly.nearestProjection(0.1, 0.5)!!

        assertEquals(projection.point, nearest)
        assertEquals(0.0, nearest.latitude, 1e-9)
    }
}
