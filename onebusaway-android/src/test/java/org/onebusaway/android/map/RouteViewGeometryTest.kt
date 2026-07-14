/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.RoutePolylineTransform

class RouteViewGeometryTest {

    @Test
    fun `focused trip shape splits into directional narrow upstream and wide downstream lines`() {
        val geometry = FocusedTripGeometry(
            mapOf(
                "shape" to FocusedTripShape(
                    "shape", "route", 0xFF123456.toInt(),
                    listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0)),
                )
            )
        )

        val lines = geometry.toRoutePolylines(GeoPoint(0.0, 0.5))

        assertEquals(listOf(ROUTE_UPSTREAM_LINE_WIDTH_DP, ROUTE_DOWNSTREAM_LINE_WIDTH_DP), lines.map { it.widthDp })
        assertEquals(GeoPoint(0.0, 0.5), lines[0].points.last())
        assertEquals(GeoPoint(0.0, 0.5), lines[1].points.first())
        assertTrue(lines.all { it.directional })
        lines.forEach {
            assertEquals(
                setOf(RoutePolylineTransform.VIEWPORT_CLIP, RoutePolylineTransform.ZOOM_SIMPLIFY),
                it.transforms,
            )
        }
    }

    @Test
    fun `empty focused geometry draws no route fallback`() {
        assertEquals(emptyList<Any>(), FocusedTripGeometry(emptyMap()).toRoutePolylines(GeoPoint(1.0, 2.0)))
    }
}
