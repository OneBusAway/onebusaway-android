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
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.simplifyRoutePolyline
import org.onebusaway.android.util.EARTH_RADIUS_METERS

class RoutePolylineSimplifierTest {

    @Test
    fun `dense collinear points collapse to endpoints`() {
        val points = (0..100).map { index -> point(eastMeters = index.toDouble()) }

        val simplified = simplifyRoutePolyline(points, toleranceMeters = 2.0)

        assertEquals(listOf(points.first(), points.last()), simplified)
    }

    @Test
    fun `sub-tolerance deviation is removed`() {
        val points = listOf(
            point(eastMeters = 0.0),
            point(eastMeters = 10.0, northMeters = 1.0),
            point(eastMeters = 20.0),
        )

        val simplified = simplifyRoutePolyline(points, toleranceMeters = 2.0)

        assertEquals(listOf(points.first(), points.last()), simplified)
    }

    @Test
    fun `meaningful bend and endpoints are retained`() {
        val points = listOf(
            point(eastMeters = 0.0),
            point(eastMeters = 10.0, northMeters = 3.0),
            point(eastMeters = 20.0),
        )

        assertEquals(points, simplifyRoutePolyline(points, toleranceMeters = 2.0))
    }

    @Test
    fun `closed route loop retains meaningful corners`() {
        val start = point(eastMeters = 0.0)
        val points = listOf(
            start,
            point(eastMeters = 10.0),
            point(eastMeters = 10.0, northMeters = 10.0),
            point(eastMeters = 0.0, northMeters = 10.0),
            start,
        )

        assertEquals(points, simplifyRoutePolyline(points, toleranceMeters = 2.0))
    }

    @Test
    fun `disabled simplification returns the original list`() {
        val points = listOf(point(eastMeters = 0.0), point(eastMeters = 1.0), point(eastMeters = 2.0))

        assertSame(points, simplifyRoutePolyline(points, toleranceMeters = 0.0))
    }

    private fun point(eastMeters: Double, northMeters: Double = 0.0): GeoPoint =
        GeoPoint(
            latitude = Math.toDegrees(northMeters / EARTH_RADIUS_METERS),
            longitude = Math.toDegrees(eastMeters / EARTH_RADIUS_METERS),
        )
}
