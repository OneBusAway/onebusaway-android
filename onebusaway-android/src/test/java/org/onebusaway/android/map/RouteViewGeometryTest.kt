/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute

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

    @Test
    fun `route badges retain every drawn path and follow focused geometry order`() {
        val firstPath = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        val secondPath = listOf(GeoPoint(1.0, 0.0), GeoPoint(1.0, 1.0))
        val geometry = FocusedTripGeometry(
            linkedMapOf(
                "b-shape" to FocusedTripShape("b-shape", "route-b", null, firstPath),
                "a-shape-1" to FocusedTripShape("a-shape-1", "route-a", 0xFF123456.toInt(), firstPath),
                "a-shape-2" to FocusedTripShape("a-shape-2", "route-a", 0xFF123456.toInt(), secondPath),
            )
        )

        val badges = geometry.toRouteBadges(
            listOf(route("route-a", "A", 0xFF654321.toInt()), route("route-b", "B", 0xFFABCDEF.toInt()))
        )

        assertEquals(listOf("route-b", "route-a"), badges.map { it.routeId })
        assertEquals(listOf("B", "A"), badges.map { it.routeShortName })
        assertEquals(0xFFABCDEF.toInt(), badges[0].color)
        assertEquals(0xFF123456.toInt(), badges[1].color)
        assertEquals(listOf(firstPath, secondPath), badges[1].paths)
    }

    @Test
    fun `route badge requires both drawable geometry and route metadata`() {
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        val geometry = FocusedTripGeometry(
            linkedMapOf(
                "known" to FocusedTripShape("known", "known-route", 1, points),
                "missing-metadata" to FocusedTripShape("missing-metadata", "missing-route", 2, points),
                "degenerate" to FocusedTripShape("degenerate", "degenerate-route", 3, listOf(points.first())),
            )
        )

        val badges = geometry.toRouteBadges(
            listOf(route("known-route", "Known"), route("degenerate-route", "Degenerate"))
        )

        assertEquals(listOf("known-route"), badges.map { it.routeId })
    }

    private fun route(id: String, shortName: String, routeColor: Int? = null) = object : ObaRoute {
        override val id = id
        override val shortName = shortName
        override val longName: String? = null
        override val description: String? = null
        override val type = ObaRoute.TYPE_BUS
        override val url: String? = null
        override val color = routeColor
        override val textColor: Int? = null
        override val agencyId = "agency"
    }
}
