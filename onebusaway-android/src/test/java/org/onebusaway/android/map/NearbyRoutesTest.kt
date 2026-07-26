/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.NEARBY_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.util.GeoPoint

class NearbyRoutesTest {

    private val center = GeoPoint(47.6, -122.33)
    private val hoop = NearbyRoutesHoop(center, 800.0)

    // ----- Clipping to the hoop -----

    @Test
    fun `a line crossing the hoop is clipped to the portion inside it`() {
        val line = listOf(offsetMeters(-2000.0, 0.0), offsetMeters(2000.0, 0.0))

        val clipped = clipToHoop(line, hoop).single()

        assertEquals(2, clipped.size)
        clipped.forEach { assertEquals(800.0, haversineMeters(center, it), 1.0) }
    }

    @Test
    fun `a line that enters twice yields one polyline per pass`() {
        // Out and back: in from the west, out to the north, back in from the north, out to the east.
        val line = listOf(
            offsetMeters(-2000.0, 0.0),
            offsetMeters(0.0, 0.0),
            offsetMeters(0.0, 3000.0),
            offsetMeters(400.0, 3000.0),
            offsetMeters(400.0, 0.0),
            offsetMeters(400.0, -3000.0)
        )

        val clipped = clipToHoop(line, hoop)

        assertEquals(2, clipped.size)
        clipped.flatten().forEach { assertTrue(haversineMeters(center, it) <= 801.0) }
    }

    @Test
    fun `a line entirely outside the hoop is dropped`() {
        val line = listOf(offsetMeters(2000.0, 2000.0), offsetMeters(3000.0, 3000.0))

        assertEquals(emptyList<List<GeoPoint>>(), clipToHoop(line, hoop))
    }

    @Test
    fun `a line entirely inside the hoop passes through untouched`() {
        val line = listOf(offsetMeters(-100.0, 0.0), offsetMeters(0.0, 100.0), offsetMeters(100.0, 0.0))

        assertEquals(listOf(line), clipToHoop(line, hoop))
    }

    // ----- The ring's on-screen size (it is drawn in screen space, not as map geometry) -----

    @Test
    fun `the ring's radius follows the zoom's ground resolution`() {
        // Web Mercator at zoom 15, latitude 47.6: 156543.03 * cos(47.6) / 2^15 = 3.22 m per dp.
        assertEquals(800.0 / 3.222, hoopRadiusDp(800.0, 15.0, 47.6).toDouble(), 0.5)
        // One zoom level in doubles the size on screen.
        assertEquals(
            2f * hoopRadiusDp(800.0, 15.0, 47.6),
            hoopRadiusDp(800.0, 16.0, 47.6),
            0.01f
        )
        // Mercator stretches away from the equator, so the same radius covers fewer metres per dp.
        assertTrue(hoopRadiusDp(800.0, 15.0, 60.0) > hoopRadiusDp(800.0, 15.0, 0.0))
    }

    // ----- The render plan -----

    @Test
    fun `every direction of a route shares one colour and one badge`() {
        val presentation = assembleNearbyRoutesPresentation(
            hoop,
            listOf(
                NearbyRouteShapes(
                    "44",
                    "44",
                    listOf(
                        listOf(offsetMeters(-500.0, 30.0), offsetMeters(500.0, 30.0)),
                        listOf(offsetMeters(500.0, -30.0), offsetMeters(-500.0, -30.0))
                    )
                )
            ),
            colors = mapOf("44" to RED)
        )

        val routeLines = presentation.polylines
        assertEquals(2, routeLines.size)
        assertEquals(1, routeLines.map { it.resolvedColor }.distinct().size)
        assertEquals(listOf("44"), presentation.badges.map { it.routeShortName })
        // The badge keeps the palette colour at full opacity; the lines are drawn back.
        assertEquals(RED, presentation.badges.single().color)
        assertNotEquals(RED, routeLines.first().resolvedColor)
        assertEquals(RED and 0x00FFFFFF, routeLines.first().resolvedColor and 0x00FFFFFF)
    }

    @Test
    fun `a survey with no routes draws nothing`() {
        val presentation = assembleNearbyRoutesPresentation(hoop, emptyList(), emptyMap())

        assertEquals(emptyList<Any>(), presentation.polylines)
        assertEquals(emptyList<Any>(), presentation.badges)
    }

    @Test
    fun `a qualifying route is drawn in full, far beyond the hoop`() {
        // Enters the hoop from the west and runs 4 km past it: the hoop selects the route, it does
        // not crop it, so the drawn line keeps every point.
        val shape = listOf(offsetMeters(-200.0, 0.0), offsetMeters(4000.0, 0.0))
        val presentation = assembleNearbyRoutesPresentation(
            hoop,
            listOf(NearbyRouteShapes("3", "3", listOf(shape))),
            colors = mapOf("3" to RED)
        )

        val routeLine = presentation.polylines.last()
        assertEquals(shape, routeLine.points)
        assertEquals(
            setOf(RoutePolylineTransform.VIEWPORT_CLIP, RoutePolylineTransform.ZOOM_SIMPLIFY),
            routeLine.transforms
        )
        // The badge stays on the in-hoop stretch rather than at the whole route's distant midpoint.
        assertTrue(haversineMeters(center, presentation.badges.single().point) <= hoop.radiusMeters)
    }

    @Test
    fun `a route whose shape misses the hoop is neither drawn nor badged`() {
        val presentation = assembleNearbyRoutesPresentation(
            hoop,
            listOf(
                NearbyRouteShapes("passing", "5", listOf(listOf(offsetMeters(-500.0, 0.0), offsetMeters(500.0, 0.0)))),
                NearbyRouteShapes("elsewhere", "6", listOf(listOf(offsetMeters(3000.0, 0.0), offsetMeters(4000.0, 0.0))))
            ),
            colors = mapOf("passing" to RED, "elsewhere" to RED)
        )

        assertEquals(listOf("5"), presentation.badges.map { it.routeShortName })
        assertEquals(
            listOf(NEARBY_ROUTE_LINE_WIDTH_PROFILE),
            presentation.polylines.map { it.widthProfile }
        )
    }

    @Test
    fun `badges spread along the routes when more of them run through than the hoop can hold`() {
        // Every route crosses the hoop, so on the screen-room test alone all of them would be anchored
        // inside it — but there are more than the ring can hold at the layout's own spacing.
        val overCapacity = hoopBadgeCapacity(hoop) + 1
        // 20 km long, so that even the layout's widest collision stagger off the whole-route midpoint
        // stays far outside the ring — the assertion is about which mode was chosen, and this keeps it
        // from being confounded by how far a crowded layout wanders looking for a clear spot.
        val routes = (1..overCapacity).map { index ->
            NearbyRouteShapes(
                "route-$index",
                "$index",
                listOf(listOf(offsetMeters(-700.0, index.toDouble()), offsetMeters(20_000.0, index.toDouble())))
            )
        }

        val presentation = assembleNearbyRoutesPresentation(
            hoop,
            routes,
            colors = routes.associate { it.routeId to RED },
            badgesInHoop = true
        )

        assertEquals(overCapacity, presentation.badges.size)
        // Spread along the routes instead of stacked in the ring: a whole-route midpoint on a line
        // running 4 km east lands well outside the hoop.
        assertTrue(presentation.badges.all { haversineMeters(center, it.point) > hoop.radiusMeters })
    }

    @Test
    fun `a hoop's badge capacity is the count it can hold at the layout's own spacing`() {
        // 800 m of radius at 300 m of separation: each badge claims a 150 m-radius disc.
        assertEquals(28, hoopBadgeCapacity(hoop))
        // A bigger ring holds more, quadratically — it's an area ratio, not a diameter one.
        assertEquals(113, hoopBadgeCapacity(NearbyRoutesHoop(center, 1600.0)))
    }

    @Test
    fun `a set within capacity still anchors its badges inside the hoop`() {
        val routes = (1..hoopBadgeCapacity(hoop)).map { index ->
            NearbyRouteShapes(
                "route-$index",
                "$index",
                listOf(listOf(offsetMeters(-700.0, index.toDouble()), offsetMeters(4000.0, index.toDouble())))
            )
        }

        val presentation = assembleNearbyRoutesPresentation(
            hoop,
            routes,
            colors = routes.associate { it.routeId to RED },
            badgesInHoop = true
        )

        // At capacity the in-hoop anchoring still applies — the check is a ceiling, not a nudge toward
        // spreading whenever the layer gets busy.
        assertTrue(presentation.badges.any { haversineMeters(center, it.point) <= hoop.radiusMeters })
    }

    @Test
    fun `badges spread along the routes when the ring is too small to hold them`() {
        // A route running 4 km east, clipping the hoop's western edge.
        val shape = listOf(offsetMeters(-700.0, 0.0), offsetMeters(4000.0, 0.0))
        val routes = listOf(NearbyRouteShapes("3", "3", listOf(shape)))

        val inHoop = assembleNearbyRoutesPresentation(hoop, routes, mapOf("3" to RED), badgesInHoop = true)
        val alongRoute = assembleNearbyRoutesPresentation(hoop, routes, mapOf("3" to RED), badgesInHoop = false)

        assertTrue(haversineMeters(center, inHoop.badges.single().point) <= hoop.radiusMeters)
        // Anchored on the whole route, the badge lands at its distant midpoint instead.
        assertTrue(haversineMeters(center, alongRoute.badges.single().point) > hoop.radiusMeters)
    }

    @Test
    fun `the ring has to be about a badge wide before labels go inside it`() {
        // Zoomed in, the ring is most of the screen; zoomed out to a city it is barely a dot.
        assertTrue(badgesFitInHoop(hoopRadiusDp(800.0, 15.0, 47.6)))
        assertFalse(badgesFitInHoop(hoopRadiusDp(800.0, 11.0, 47.6)))
    }

    @Test
    fun `a hoop badge carries no direction so a tap enters the route's default`() {
        val presentation = assembleNearbyRoutesPresentation(
            hoop,
            listOf(NearbyRouteShapes("8", "8", listOf(listOf(offsetMeters(-400.0, 0.0), offsetMeters(400.0, 0.0))))),
            colors = mapOf("8" to RED)
        )

        assertEquals(null, presentation.badges.single().directionId)
        assertEquals("8", presentation.badges.single().routeId)
    }

    // ----- Helpers -----

    /** A point [east]/[north] metres from the hoop centre. */
    private fun offsetMeters(east: Double, north: Double): GeoPoint {
        val metersPerDegreeLatitude = 111_319.49
        return GeoPoint(
            center.latitude + north / metersPerDegreeLatitude,
            center.longitude + east / (metersPerDegreeLatitude * Math.cos(Math.toRadians(center.latitude)))
        )
    }

    private companion object {
        const val RED = 0xFFCC0000.toInt()
    }
}
