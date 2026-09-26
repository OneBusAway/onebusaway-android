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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.util.GeoPoint

class ZoneGeometryTest {

    private val outer = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 10.0), GeoPoint(10.0, 10.0), GeoPoint(10.0, 0.0), GeoPoint(0.0, 0.0))
    private val hole = listOf(GeoPoint(4.0, 4.0), GeoPoint(4.0, 6.0), GeoPoint(6.0, 6.0), GeoPoint(6.0, 4.0), GeoPoint(4.0, 4.0))
    private val zone = ZonePolygon("svc", "Zone", listOf(outer, hole), null)

    // A 0.1° square: lat 45.0–45.1, lon −85.2 to −85.0.
    private val square = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))
    private val squareBounds = GeoPoint(45.0, -85.2) to GeoPoint(45.1, -85.0)
    private val innerHole = listOf(GeoPoint(45.03, -85.13), GeoPoint(45.03, -85.07), GeoPoint(45.07, -85.07), GeoPoint(45.07, -85.13), GeoPoint(45.03, -85.13))
    private val uShape = listOf(
        GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.05),
        GeoPoint(45.02, -85.05), GeoPoint(45.02, -85.15), GeoPoint(45.1, -85.15), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2)
    )

    private fun areaOf(vararg polygons: List<List<GeoPoint>>): ServiceArea {
        val points = polygons.toList().flatten().flatten()
        return ServiceArea("a", null, null, GeoPoint(points.minOf { it.latitude }, points.minOf { it.longitude }), GeoPoint(points.maxOf { it.latitude }, points.maxOf { it.longitude }), polygons.toList(), null, null)
    }

    @Test
    fun `the label point of a convex ring is inside it`() {
        val label = labelPoint(listOf(square), squareBounds)
        assertTrue(pointInRing(label, square))
        assertEquals(45.05, label.latitude, 1e-9)
        assertEquals(-85.1, label.longitude, 1e-9)
    }

    @Test
    fun `the label point of a U shape lands in an arm, not the notch`() {
        val label = labelPoint(listOf(uShape), squareBounds)
        assertTrue(pointInRing(label, uShape))
        assertNotEquals(-85.1, label.longitude, 1e-6)
    }

    @Test
    fun `the label point of a holed polygon avoids the hole`() {
        val label = labelPoint(listOf(square, innerHole), squareBounds)
        assertTrue(pointInRing(label, square))
        assertFalse(pointInRing(label, innerHole))
    }

    @Test
    fun `a vertex on the mid-latitude and a degenerate ring fall back`() {
        val diamond = listOf(GeoPoint(45.05, -85.2), GeoPoint(45.0, -85.1), GeoPoint(45.05, -85.0), GeoPoint(45.1, -85.1), GeoPoint(45.05, -85.2))
        val label = labelPoint(listOf(diamond), squareBounds)
        assertTrue(pointInRing(label, diamond))
        // Two points make no ring: the bbox centre is the only honest answer.
        val twoPoints = labelPoint(listOf(listOf(GeoPoint(45.0, -85.2), GeoPoint(45.1, -85.0))), squareBounds)
        assertEquals(45.05, twoPoints.latitude, 1e-9)
        assertEquals(-85.1, twoPoints.longitude, 1e-9)
        // No rings at all also uses the bbox centre.
        val none = labelPoint(emptyList(), squareBounds)
        assertEquals(45.05, none.latitude, 1e-9)
        assertEquals(-85.1, none.longitude, 1e-9)
        // A rectangle with a downward notch whose tip vertex sits exactly on the mid-latitude
        // (both notch flanks lie above it): 4 crossings at -85.2, -85.1, -85.1, -85.0 by hand
        // (see the task report — a cyclic ring's crossing count is always even, so this and the
        // diamond above are the two shapes of "vertex on the line" this rule can ever produce,
        // not an odd count). The two widest runs tie at 0.1°; the pairing loop keeps the first,
        // landing left of the notch.
        val notchedRectangle = listOf(
            GeoPoint(45.0, -85.2),
            GeoPoint(45.0, -85.0),
            GeoPoint(45.1, -85.0),
            GeoPoint(45.1, -85.08),
            GeoPoint(45.05, -85.1),
            GeoPoint(45.1, -85.12),
            GeoPoint(45.1, -85.2)
        )
        val notchLabel = labelPoint(listOf(notchedRectangle), squareBounds)
        assertTrue(pointInRing(notchLabel, notchedRectangle))
        assertEquals(-85.15, notchLabel.longitude, 1e-9)
    }

    @Test
    fun `the pin goes in the largest polygon of a two-polygon service`() {
        val small = listOf(GeoPoint(45.2, -85.2), GeoPoint(45.2, -85.19), GeoPoint(45.21, -85.19), GeoPoint(45.21, -85.2), GeoPoint(45.2, -85.2))
        val pin = requireNotNull(pinPointFor(listOf(areaOf(listOf(small), listOf(square)))))
        assertTrue(pointInRing(pin, square))
    }

    @Test
    fun `the nearest edge of a point just inside the north side is north and close`() {
        val edge = requireNotNull(nearestBoundaryPoint(GeoPoint(45.0999, -85.1), listOf(areaOf(listOf(square)))))
        assertEquals(11.1, edge.distanceMeters, 0.3)
        assertEquals(CompassDirection.NORTH, edge.edgeDirection)
        assertEquals(CompassDirection.SOUTH, edge.riderDirection)
        assertEquals(45.1, edge.point.latitude, 1e-6)
    }

    @Test
    fun `a point due south of the square is south of the zone`() {
        val edge = requireNotNull(nearestBoundaryPoint(GeoPoint(44.95, -85.1), listOf(areaOf(listOf(square)))))
        assertEquals(5560.0, edge.distanceMeters, 3.0)
        assertEquals(CompassDirection.NORTH, edge.edgeDirection)
        assertEquals(CompassDirection.SOUTH, edge.riderDirection)
    }

    @Test
    fun `a hole's edge counts as a boundary`() {
        val edge = requireNotNull(nearestBoundaryPoint(GeoPoint(45.05, -85.135), listOf(areaOf(listOf(square, innerHole)))))
        assertEquals(393.0, edge.distanceMeters, 3.0)
        assertEquals(CompassDirection.EAST, edge.edgeDirection)
    }

    @Test
    fun `no areas gives no edge`() {
        assertEquals(null, nearestBoundaryPoint(GeoPoint(45.05, -85.1), emptyList()))
    }

    @Test
    fun `compass buckets split at 22 point 5 degrees`() {
        assertEquals(CompassDirection.NORTH, compassDirection(0.0))
        assertEquals(CompassDirection.NORTH, compassDirection(22.49))
        assertEquals(CompassDirection.NORTHEAST, compassDirection(22.5))
        assertEquals(CompassDirection.EAST, compassDirection(67.5))
        assertEquals(CompassDirection.SOUTH, compassDirection(180.0))
        assertEquals(CompassDirection.SOUTHWEST, compassDirection(202.5))
        assertEquals(CompassDirection.NORTH, compassDirection(337.5))
        assertEquals(CompassDirection.NORTH, compassDirection(359.9))
        assertEquals(CompassDirection.WEST, compassDirection(-90.0))
    }

    @Test
    fun `bearing degrees runs clockwise from north`() {
        val origin = GeoPoint(0.0, 0.0)
        assertEquals(0.0, bearingDegrees(origin, GeoPoint(1.0, 0.0)), 1e-6)
        assertEquals(90.0, bearingDegrees(origin, GeoPoint(0.0, 1.0)), 1e-6)
        assertEquals(180.0, bearingDegrees(origin, GeoPoint(-1.0, 0.0)), 1e-6)
        assertEquals(270.0, bearingDegrees(origin, GeoPoint(0.0, -1.0)), 1e-6)
        assertEquals(45.0, bearingDegrees(origin, GeoPoint(1.0, 1.0)), 1e-6)
    }

    @Test
    fun `a point inside the exterior and outside the hole is contained`() {
        assertTrue(zone.contains(GeoPoint(1.0, 1.0)))
        assertTrue(zone.contains(GeoPoint(7.0, 5.0)))
    }

    @Test
    fun `a point inside the hole is not contained`() {
        assertFalse(zone.contains(GeoPoint(5.0, 5.0)))
    }

    @Test
    fun `a point outside the exterior is not contained`() {
        assertFalse(zone.contains(GeoPoint(11.0, 5.0)))
        assertFalse(zone.contains(GeoPoint(-1.0, -1.0)))
    }

    @Test
    fun `an unclosed ring still works`() {
        assertTrue(pointInRing(GeoPoint(1.0, 1.0), outer.dropLast(1)))
    }

    @Test
    fun `fill and stroke keep the route hue and set alpha`() {
        assertEquals(0x33112233, zoneFillColor(0xFF112233.toInt()))
        assertEquals(0xCC112233.toInt(), zoneStrokeColor(0xFF112233.toInt()))
        assertEquals(0x33 shl 24 or (DEFAULT_ROUTE_LINE_COLOR and 0x00FFFFFF), zoneFillColor(null))
    }
}
