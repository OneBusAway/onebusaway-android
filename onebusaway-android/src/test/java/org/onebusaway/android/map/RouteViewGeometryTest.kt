/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey

class RouteViewGeometryTest {

    @Test
    fun `focused trip shape uses one uniform directional line`() {
        val geometry = FocusedTripGeometry(
            listOf(
                FocusedTripShape(
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
        assertEquals(emptyList<Any>(), FocusedTripGeometry(emptyList()).toRoutePolylines())
    }

    @Test
    fun `selected route is one and a half times normal while siblings render thin underneath`() {
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0))
        val geometry = FocusedTripGeometry(
            listOf(
                FocusedTripShape("selected-shape", "selected", 1, points, directionId = 0),
                FocusedTripShape("other-shape", "other", 2, points, directionId = 1),
            )
        )

        val lines = geometry.toRoutePolylines(
            emphasizedRoute = RouteDirectionKey("selected", 0),
            routeColors = mapOf(
                RouteDirectionKey("selected", 0) to 10,
                RouteDirectionKey("other", 1) to 20,
            ),
        )

        assertEquals(listOf(20, 10), lines.map { it.color })
        assertEquals(
            listOf(
                ROUTE_DEEMPHASIZED_LINE_WIDTH_DP,
                ROUTE_LINE_WIDTH_DP * 1.5f,
            ),
            lines.map { it.widthDp },
        )
        assertEquals(listOf(false, true), lines.map { it.directional })
    }

    @Test
    fun `scheduled stops carry every presented route that serves them`() {
        val trips = setOf(
            FocusedTrip("trip-45", "45", null, null, directionId = 0),
            FocusedTrip("trip-79", "79", null, null, directionId = 1),
        )
        val stops = FocusedTripStops(
            stopIdsByTripId = mapOf(
                "trip-45" to listOf("u-district"),
                "trip-79" to listOf("u-district", "20th-and-50th"),
            ),
            stopsById = emptyMap(),
        )

        assertEquals(
            mapOf(
                "u-district" to setOf(RouteDirectionKey("45", 0), RouteDirectionKey("79", 1)),
                "20th-and-50th" to setOf(RouteDirectionKey("79", 1)),
            ),
            stops.routeDirectionsByStopId(trips),
        )
        assertEquals(
            mapOf("u-district" to setOf(RouteDirectionKey("45", 0))),
            stops.routeDirectionsByStopId(trips, route = RouteDirectionKey("45", 0)),
        )
    }

    @Test
    fun `route badges retain every drawn path and follow focused geometry order`() {
        val firstPath = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        val secondPath = listOf(GeoPoint(1.0, 0.0), GeoPoint(1.0, 1.0))
        val geometry = FocusedTripGeometry(
            listOf(
                FocusedTripShape("b-shape", "route-b", null, firstPath, directionId = 1),
                FocusedTripShape(
                    "a-shape-1", "route-a", 0xFF123456.toInt(), firstPath, directionId = 0,
                ),
                FocusedTripShape(
                    "a-shape-2", "route-a", 0xFF123456.toInt(), secondPath, directionId = 1,
                ),
            )
        )

        val badges = geometry.toRouteBadges(
            routes = listOf(
                route("route-a", "A", 0xFF654321.toInt()),
                route("route-b", "B", 0xFFABCDEF.toInt()),
            ),
            routeColors = mapOf(
                RouteDirectionKey("route-a", 0) to 10,
                RouteDirectionKey("route-a", 1) to 30,
                RouteDirectionKey("route-b", 1) to 20,
            ),
        )

        assertEquals(listOf("route-b", "route-a", "route-a"), badges.map { it.routeId })
        assertEquals(listOf("B", "A", "A"), badges.map { it.routeShortName })
        assertEquals(listOf(20, 10, 30), badges.map { it.color })
        assertEquals(listOf(1, 0, 1), badges.map { it.directionId })
        assertEquals(GeoPoint(0.0, 0.5), badges[0].point)
        assertTrue(haversineMeters(badges[0].point, badges[1].point) >= 299.9)
    }

    @Test
    fun `route badge requires both drawable geometry and route metadata`() {
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        val geometry = FocusedTripGeometry(
            listOf(
                FocusedTripShape("known", "known-route", 1, points),
                FocusedTripShape("missing-metadata", "missing-route", 2, points),
                FocusedTripShape("degenerate", "degenerate-route", 3, listOf(points.first())),
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
