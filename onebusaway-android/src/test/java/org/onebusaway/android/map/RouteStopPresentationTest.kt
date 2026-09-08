/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.compose.stopChoicesAt
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint

class RouteStopPresentationTest {

    @Test
    fun `westbound focus keeps the eastbound alternative at its own location and in the chooser`() {
        val west = ObaStopElement(id = "west", code = "1", lat = 32.748, lon = -117.16, direction = "W")
        val east = ObaStopElement(id = "east", code = "2", lat = 32.7479, lon = -117.16, direction = "E")
        val eastMarker = marker(east).copy(favorite = true, routes = listOf(StopRoute("11", null)))
        val nearby = listOf(marker(west), eastMarker)
        val presentation = RouteStopPresentation(
            stops = listOf(west),
            routes = emptyList(),
            routeDirectionsByStopId = mapOf(west.id to setOf(RouteDirectionKey("11", 0))),
            keepNearbyStops = true
        )

        val result = applyRouteStopPresentation(nearby, west.id, presentation, ::marker)
        val focused = result.single { it.id == west.id }
        val alternative = result.single { it.id == east.id }
        assertEquals(2, result.size)
        assertEquals(nearby.first().point, focused.point)
        assertTrue(focused.routeStop)
        assertFalse(focused.compact)
        assertEquals(eastMarker.point, alternative.point)
        assertFalse(alternative.routeStop)
        assertTrue(alternative.compact)
        assertTrue(alternative.favorite)
        assertFalse(alternative.showRouteLabel)
        assertEquals(eastMarker.routes, alternative.routes)
        assertSame(east, alternative.stop)

        val choices = stopChoicesAt(focused, result, ScreenOffset(0f, 0f), MapProjector { ScreenOffset(0f, 0f) }, 48f, 48f)
        assertEquals(listOf(west.id, east.id), choices.map { it.id })
        assertEquals("E", choices.last().stop.direction)

        // A handoff rebuilds from nearby data: the old westbound stop keeps its geographic point
        // and loses the old route membership, while the selected eastbound stop gains its own.
        val switched = applyRouteStopPresentation(
            nearby,
            east.id,
            presentation.copy(stops = listOf(east), routeDirectionsByStopId = mapOf(east.id to setOf(RouteDirectionKey("11", 1)))),
            ::marker
        ).associateBy(StopMarker::id)
        assertEquals(nearby.first().point, switched.getValue(west.id).point)
        assertFalse(switched.getValue(west.id).routeStop)
        assertTrue(switched.getValue(west.id).compact)
        assertEquals(setOf(RouteDirectionKey("11", 1)), switched.getValue(east.id).presentedRoutes)
        assertFalse(switched.getValue(east.id).compact)
        assertTrue(nearby.all { it.showRouteLabel && !it.compact && !it.routeStop })
    }

    @Test
    fun `stop focus retains nearby stops even when no trips are available`() {
        val focused = marker(stop("focused"))
        val other = marker(stop("other"))
        val presentation = RouteStopPresentation(emptyList(), emptyList(), emptyMap(), emptyMap(), keepNearbyStops = true)

        val result = applyRouteStopPresentation(listOf(focused, other), focused.id, presentation, ::marker)

        assertEquals(listOf(focused.id, other.id), result.map { it.id })
        assertFalse(result.first().compact)
        assertTrue(result.last().compact)
        assertTrue(result.none { it.routeStop || it.showRouteLabel })
    }

    @Test
    fun `focused trips show only the exact scheduled stops`() {
        val exact = stop("exact")
        val other = stop("same-route-but-not-trip")
        val presentation = RouteStopPresentation(
            stops = listOf(exact),
            routes = emptyList(),
            routeDirectionsByStopId = mapOf(exact.id to setOf(RouteDirectionKey("62", 1)))
        )

        val result = applyRouteStopPresentation(
            nearby = listOf(marker(exact), marker(other)),
            focusedStopId = exact.id,
            presentation = presentation,
            markerFor = ::marker
        ).associateBy(StopMarker::id)

        assertEquals(setOf(RouteDirectionKey("62", 1)), result.getValue(exact.id).presentedRoutes)
        assertFalse(result.containsKey(other.id))
        assertEquals(GeoPoint(exact.latitude, exact.longitude), result.getValue(exact.id).point)
    }

    @Test
    fun `empty displayed trip set keeps only the focused stop`() {
        val focused = stop("focused")
        val other = stop("other")
        val presentation = RouteStopPresentation(
            stops = emptyList(),
            routes = emptyList(),
            routeDirectionsByStopId = emptyMap()
        )

        val result = applyRouteStopPresentation(
            listOf(marker(focused), marker(other)),
            focused.id,
            presentation,
            ::marker
        )

        assertEquals(focused.id, result.single().id)
        assertFalse(result.single().routeStop)
    }

    @Test
    fun `a presentation hides map labels but keeps routes available to the stop chooser`() {
        val focused = stop("focused")
        val labelled = marker(focused).copy(routes = listOf(StopRoute("62", 0xFF00FF00.toInt())))
        val presentation = RouteStopPresentation(
            stops = emptyList(),
            routes = emptyList(),
            routeDirectionsByStopId = emptyMap()
        )

        val result = applyRouteStopPresentation(listOf(labelled), focused.id, presentation, ::marker)

        assertEquals(labelled.routes, result.single().routes)
        assertEquals(emptyList<StopRoute>(), org.onebusaway.android.map.render.stopRouteLabel(result.single(), org.onebusaway.android.map.render.StopBand.ROUTES))
    }

    @Test
    fun `markers take the displayed line color and shared stops stay neutral`() {
        val a = RouteDirectionKey("a", 0)
        val b = RouteDirectionKey("b", 0)
        val red = 0xFFCC0000.toInt()
        val blue = 0xFF0000CC.toInt()
        val stops = listOf(stop("a-only"), stop("shared"))
        val presentation = RouteStopPresentation(
            stops,
            emptyList(),
            mapOf("a-only" to setOf(a), "shared" to setOf(a, b)),
            routeColors = mapOf(a to red, b to blue)
        )
        val markers = applyRouteStopPresentation(emptyList(), null, presentation, ::marker)
        assertEquals(red, markers.first().routeColor)
        assertEquals(null, markers.last().routeColor)
        val sameColor = applyRouteStopPresentation(
            emptyList(),
            null,
            presentation.copy(routeColors = mapOf(a to blue, b to blue)),
            ::marker
        )
        assertTrue(sameColor.all { it.routeColor == blue })
        val selectedRoute = applyRouteStopPresentation(
            emptyList(),
            "shared",
            presentation.copy(routeDirectionsByStopId = mapOf("shared" to setOf(b))),
            ::marker
        )
        assertEquals(blue, selectedRoute.last().routeColor)
        assertEquals(markers.last().point, selectedRoute.last().point)
    }

    private fun stop(id: String) = ObaStopElement(id = id, lat = 47.0, lon = -122.0)

    private fun marker(stop: org.onebusaway.android.models.ObaStop) = StopMarker(
        stop.id,
        GeoPoint(stop.latitude, stop.longitude),
        "null",
        ObaRoute.TYPE_BUS,
        stop
    )
}
