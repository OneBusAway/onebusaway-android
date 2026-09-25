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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class ZoneGeometryTest {

    private val outer = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 10.0), GeoPoint(10.0, 10.0), GeoPoint(10.0, 0.0), GeoPoint(0.0, 0.0))
    private val hole = listOf(GeoPoint(4.0, 4.0), GeoPoint(4.0, 6.0), GeoPoint(6.0, 6.0), GeoPoint(6.0, 4.0), GeoPoint(4.0, 4.0))
    private val zone = ZonePolygon("svc", "Zone", listOf(outer, hole), null)

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
