/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class MapRenderStateNearbyRoutesTest {

    private val focusLine = line(0xFF0000FF.toInt())
    private val hoopLine = line(0x59757575)

    @Test
    fun `the hoop layer draws beneath the focused route geometry`() {
        val snapshot = MapRenderSnapshot(
            routePolylines = listOf(focusLine),
            nearbyRoutePolylines = listOf(hoopLine)
        )

        assertEquals(listOf(hoopLine, focusLine), snapshot.allRoutePolylines)
    }

    @Test
    fun `one empty layer hands back the other list itself, keeping the reconciler's identity check`() {
        val routes = listOf(focusLine)
        val nearby = listOf(hoopLine)

        assertSame(routes, MapRenderSnapshot(routePolylines = routes).allRoutePolylines)
        assertSame(nearby, MapRenderSnapshot(nearbyRoutePolylines = nearby).allRoutePolylines)
    }

    @Test
    fun `both badge sets are drawn`() {
        val adjacency = badge("44")
        val nearby = badge("8")
        val snapshot = MapRenderSnapshot(
            routeBadges = listOf(adjacency),
            nearbyRouteBadges = listOf(nearby)
        )

        assertEquals(listOf(adjacency, nearby), snapshot.allRouteBadges)

        val adjacencyOnly = listOf(adjacency)
        assertSame(adjacencyOnly, MapRenderSnapshot(routeBadges = adjacencyOnly).allRouteBadges)
    }

    @Test
    fun `the static-layer comparison strips both route line layers`() {
        val stripped = MapRenderSnapshot(
            routePolylines = listOf(focusLine),
            nearbyRoutePolylines = listOf(hoopLine),
            nearbyRouteBadges = listOf(badge("8")),
            focusedStopId = "stop"
        ).withoutRouteGeometry()

        assertEquals(emptyList<RoutePolyline>(), stripped.routePolylines)
        assertEquals(emptyList<RoutePolyline>(), stripped.nearbyRoutePolylines)
        // Badges + focus are static-layer content, so they must survive the strip.
        assertEquals(listOf("8"), stripped.allRouteBadges.map { it.routeShortName })
        assertEquals("stop", stripped.focusedStopId)
    }

    @Test
    fun `publishing the hoop layer replaces both of its fields at once`() {
        val state = MapRenderState()
        state.setRoutePolylines(listOf(focusLine))

        state.setNearbyRoutes(listOf(hoopLine), listOf(badge("8")))
        assertEquals(listOf(hoopLine, focusLine), state.snapshot.value.allRoutePolylines)
        assertEquals(listOf("8"), state.snapshot.value.allRouteBadges.map { it.routeShortName })

        state.clearNearbyRoutes()
        assertEquals(listOf(focusLine), state.snapshot.value.allRoutePolylines)
        assertEquals(emptyList<RouteBadge>(), state.snapshot.value.allRouteBadges)
    }

    private fun line(color: Int) = RoutePolyline(
        color = color,
        points = listOf(GeoPoint(47.6, -122.3), GeoPoint(47.61, -122.3))
    )

    private fun badge(shortName: String) = RouteBadge(
        routeId = shortName,
        routeShortName = shortName,
        color = 0xFF000000.toInt(),
        point = GeoPoint(47.6, -122.3),
        directionId = null
    )
}
