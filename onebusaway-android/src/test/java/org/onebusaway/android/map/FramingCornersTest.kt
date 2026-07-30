/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.MIN_FRAMING_SPAN_DEG
import org.onebusaway.android.map.render.WALK_LEG_MIN_FRAMING_SPAN_DEG
import org.onebusaway.android.map.render.framingCorners
import org.onebusaway.android.util.GeoPoint

class FramingCornersTest {

    private fun span(corners: Pair<GeoPoint, GeoPoint>): Pair<Double, Double> {
        val (sw, ne) = corners
        return (ne.latitude - sw.latitude) to (ne.longitude - sw.longitude)
    }

    @Test
    fun `empty points return null`() {
        assertNull(framingCorners(emptyList()))
    }

    @Test
    fun `a degenerate box is inflated to the minimum span, centered on the point`() {
        val p = GeoPoint(47.6, -122.3)
        val corners = framingCorners(listOf(p))!!
        val (latSpan, lonSpan) = span(corners)

        assertEquals(MIN_FRAMING_SPAN_DEG, latSpan, 1e-9)
        assertEquals(MIN_FRAMING_SPAN_DEG, lonSpan, 1e-9)
        // Centered on the original point.
        assertEquals(p.latitude, (corners.first.latitude + corners.second.latitude) / 2, 1e-9)
        assertEquals(p.longitude, (corners.first.longitude + corners.second.longitude) / 2, 1e-9)
    }

    @Test
    fun `the walk-leg floor frames a short hop tighter than the default`() {
        // A ~street-crossing hop: two points a few meters apart.
        val hop = listOf(GeoPoint(47.6000, -122.3000), GeoPoint(47.6001, -122.3001))

        val walkCorners = framingCorners(hop, WALK_LEG_MIN_FRAMING_SPAN_DEG)!!
        val defaultCorners = framingCorners(hop)!!

        val (walkLat, _) = span(walkCorners)
        val (defaultLat, _) = span(defaultCorners)

        assertEquals(WALK_LEG_MIN_FRAMING_SPAN_DEG, walkLat, 1e-9)
        assertEquals(MIN_FRAMING_SPAN_DEG, defaultLat, 1e-9)
        assertTrue("walk-leg floor should be tighter", walkLat < defaultLat)
    }

    @Test
    fun `a box already larger than the floor is left as-is`() {
        val far = listOf(GeoPoint(47.60, -122.30), GeoPoint(47.70, -122.20))
        val corners = framingCorners(far, WALK_LEG_MIN_FRAMING_SPAN_DEG)!!
        val (latSpan, lonSpan) = span(corners)

        assertEquals(0.10, latSpan, 1e-9)
        assertEquals(0.10, lonSpan, 1e-9)
    }
}
