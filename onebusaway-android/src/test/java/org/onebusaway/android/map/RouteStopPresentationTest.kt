/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey

class RouteStopPresentationTest {

    @Test
    fun `focused trips show only the exact scheduled stops`() {
        val exact = stop("exact")
        val other = stop("same-route-but-not-trip")
        val presentation = RouteStopPresentation(
            stops = listOf(exact),
            routes = emptyList(),
            routeDirectionsByStopId = mapOf(exact.id to setOf(RouteDirectionKey("62", 1))),
            projectedPoints = mapOf(exact.id to GeoPoint(48.0, -123.0)),
        )

        val result = applyRouteStopPresentation(
            nearby = listOf(marker(exact), marker(other)),
            focusedStopId = exact.id,
            presentation = presentation,
            markerFor = ::marker,
        ).associateBy(StopMarker::id)

        assertEquals(setOf(RouteDirectionKey("62", 1)), result.getValue(exact.id).presentedRoutes)
        assertFalse(result.containsKey(other.id))
    }

    @Test
    fun `empty displayed trip set keeps only the focused stop`() {
        val focused = stop("focused")
        val other = stop("other")
        val presentation = RouteStopPresentation(
            stops = emptyList(), routes = emptyList(), routeDirectionsByStopId = emptyMap(),
            projectedPoints = emptyMap(),
        )

        val result = applyRouteStopPresentation(
            listOf(marker(focused), marker(other)), focused.id, presentation, ::marker,
        )

        assertEquals(focused.id, result.single().id)
        assertFalse(result.single().routeStop)
    }

    private fun stop(id: String) = ObaStopElement(id = id, lat = 47.0, lon = -122.0)

    private fun marker(stop: org.onebusaway.android.models.ObaStop) = StopMarker(
        stop.id, GeoPoint(stop.latitude, stop.longitude), "null", ObaRoute.TYPE_BUS, stop,
    )
}
