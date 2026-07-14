/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.FocusedTrip

class RouteViewGeometryTest {

    @Test
    fun `focused trip shape uses one uniform directional line`() {
        val geometry = FocusedTripGeometry(
            mapOf(
                "shape" to FocusedTripShape(
                    "shape", "route", 0xFF123456.toInt(),
                    listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0)),
                )
            )
        )

        val lines = geometry.toRoutePolylines()

        assertEquals(listOf(ROUTE_LINE_WIDTH_DP), lines.map { it.widthDp })
        assertEquals(
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0)),
            lines.single().points,
        )
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
        assertEquals(emptyList<Any>(), FocusedTripGeometry(emptyMap()).toRoutePolylines())
    }

    @Test
    fun `selected route stays prominent while sibling routes become thin and render underneath`() {
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0))
        val geometry = FocusedTripGeometry(
            linkedMapOf(
                "selected-shape" to FocusedTripShape("selected-shape", "selected", 1, points),
                "other-shape" to FocusedTripShape("other-shape", "other", 2, points),
            )
        )

        val lines = geometry.toRoutePolylines("selected")

        assertEquals(listOf(2, 1), lines.map { it.color })
        assertEquals(
            listOf(
                ROUTE_DEEMPHASIZED_LINE_WIDTH_DP,
                ROUTE_LINE_WIDTH_DP,
            ),
            lines.map { it.widthDp },
        )
        assertEquals(listOf(false, true), lines.map { it.directional })
    }

    @Test
    fun `selected route exposes only stops from its focused trips`() {
        val trips = setOf(
            FocusedTrip("selected-trip", "selected", null, null),
            FocusedTrip("other-trip", "other", null, null),
        )
        val stops = FocusedTripStops(
            stopIdsByTripId = mapOf(
                "selected-trip" to listOf("focus", "selected-stop"),
                "other-trip" to listOf("focus", "other-stop"),
            ),
            stopsById = emptyMap(),
        )

        assertEquals(setOf("focus", "selected-stop"), stops.stopIdsForRoute(trips, "selected"))
        assertEquals(setOf("focus", "selected-stop", "other-stop"), stops.stopIdsForRoute(trips, null))
    }
}
