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
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

/** Unit tests for [haversineMeters] — the pure great-circle distance — and [leadingBearing]. */
class GeoMathTest {

    @Test
    fun zeroForSamePoint() {
        assertEquals(0.0, haversineMeters(GeoPoint(47.6, -122.3), GeoPoint(47.6, -122.3)), 1e-9)
    }

    @Test
    fun oneDegreeOfLatitude_isAboutOneEleventhOfEarthCircumference() {
        // 1° of latitude ≈ 111.2 km on a 6_371_010 m sphere.
        val d = haversineMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun `a line's leading bearing is a compass heading`() {
        // Due north and due east from the same origin, each far enough to fill the window.
        assertEquals(0f, leadingBearing(listOf(GeoPoint(47.6, -122.3), GeoPoint(47.61, -122.3)))!!, BEARING_TOLERANCE)
        assertEquals(90f, leadingBearing(listOf(GeoPoint(47.6, -122.3), GeoPoint(47.6, -122.29)))!!, BEARING_TOLERANCE)
        // West is 270, not -90: the caller rotates a symbol by this, so the whole circle is positive.
        assertEquals(270f, leadingBearing(listOf(GeoPoint(47.6, -122.3), GeoPoint(47.6, -122.31)))!!, BEARING_TOLERANCE)
    }

    @Test
    fun `the direction is read over a window, not off the first hop`() {
        // A decoded shape's first vertices can be centimetres apart and point anywhere. Here the first hop
        // goes east and everything after it goes north; over the window the line is heading north.
        val jitteryStart = listOf(
            GeoPoint(47.6, -122.3),
            GeoPoint(47.6, -122.29999), // ~0.7 m east
            GeoPoint(47.601, -122.29999),
            GeoPoint(47.602, -122.29999)
        )

        // Not exactly 0: the first hop's 0.7 m of east survives in the chord, as ~2° over the 20 m window.
        // That residue shrinking as the window grows is the whole mechanism — off the first hop alone this
        // line reads due east.
        assertEquals(0f, leadingBearing(jitteryStart)!!, 3f)
    }

    @Test
    fun `a line with no direction at its start reports none`() {
        assertNull(leadingBearing(emptyList()))
        assertNull(leadingBearing(listOf(GeoPoint(47.6, -122.3))))
        // Every vertex on top of the first: there is no heading here to guess at.
        assertNull(leadingBearing(List(3) { GeoPoint(47.6, -122.3) }))
    }

    @Test
    fun `ground resolution halves with each zoom level and narrows towards the poles`() {
        // The 256px-tile scale everything screen-sized on this map is sized through: one tile spans the
        // world at zoom 0, so a pixel covers 156543 m at the equator and half that per level down.
        assertEquals(156543.03, metersPerPixel(latitude = 0.0, zoom = 0.0), 0.01)
        assertEquals(metersPerPixel(0.0, 10.0) / 2.0, metersPerPixel(0.0, 11.0), 1e-9)
        // A degree of longitude is shorter at 60°N by exactly a half, and so is the ground under a pixel.
        assertEquals(metersPerPixel(0.0, 12.0) / 2.0, metersPerPixel(60.0, 12.0), 1e-6)
    }

    @Test
    fun `both runaway inputs are clamped, so every caller gets a finite answer`() {
        // The formula runs away at the projection's cutoff and at absurd zooms, and the callers that used to
        // write it out each guarded a different one of the two.
        assertEquals(
            metersPerPixel(MAX_MERCATOR_LATITUDE, zoom = 12.0),
            metersPerPixel(latitude = 90.0, zoom = 12.0),
            1e-9
        )
        assertEquals(metersPerPixel(0.0, 30.0), metersPerPixel(0.0, zoom = 40.0), 1e-9)
        assertEquals(metersPerPixel(0.0, 0.0), metersPerPixel(0.0, zoom = -5.0), 1e-9)
    }

    private companion object {
        // The window's chord is measured over a spherical earth; a degree of slack is far tighter than any
        // difference that would matter to a mark drawn across a line.
        const val BEARING_TOLERANCE = 0.5f
    }
}
