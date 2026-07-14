/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.models.ObaRoute

class RouteStopPresentationTest {

    @Test
    fun `focused trips emphasize only exact scheduled stops and dim every other nearby stop`() {
        val exact = stop("exact")
        val other = stop("same-route-but-not-trip")
        val presentation = RouteStopPresentation(
            stops = listOf(exact),
            routes = emptyList(),
            routeStopIds = setOf(exact.id),
            projectedPoints = mapOf(exact.id to GeoPoint(48.0, -123.0)),
            includeNearbyStops = true,
            dimNonRouteStops = true,
        )

        val result = applyRouteStopPresentation(
            nearby = listOf(marker(exact), marker(other)),
            focusedStopId = exact.id,
            presentation = presentation,
            markerFor = ::marker,
        ).associateBy(StopMarker::id)

        assertTrue(result.getValue(exact.id).routeStop)
        assertFalse(result.getValue(exact.id).dimmed)
        assertFalse(result.getValue(other.id).routeStop)
        assertTrue(result.getValue(other.id).dimmed)
    }

    @Test
    fun `empty displayed trip set dims all nearby stops`() {
        val nearby = stop("nearby")
        val presentation = RouteStopPresentation(
            stops = emptyList(), routes = emptyList(), routeStopIds = emptySet(),
            projectedPoints = emptyMap(), includeNearbyStops = true, dimNonRouteStops = true,
        )

        val result = applyRouteStopPresentation(
            listOf(marker(nearby)), nearby.id, presentation, ::marker,
        )

        assertTrue(result.single().dimmed)
        assertFalse(result.single().routeStop)
    }

    private fun stop(id: String) = ObaStopElement(id = id, lat = 47.0, lon = -122.0)

    private fun marker(stop: org.onebusaway.android.models.ObaStop) = StopMarker(
        stop.id, GeoPoint(stop.latitude, stop.longitude), "null", ObaRoute.TYPE_BUS, stop,
    )
}
