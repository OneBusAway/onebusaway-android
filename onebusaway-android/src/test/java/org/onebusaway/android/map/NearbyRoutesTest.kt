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

    // ----- Selecting the routes that pass through the hoop -----

    @Test
    fun `a line crossing the hoop enters it`() {
        assertTrue(entersHoop(listOf(offsetMeters(-2000.0, 0.0), offsetMeters(2000.0, 0.0)), hoop))
    }

    @Test
    fun `a line that only reaches the hoop on a later segment still enters it`() {
        // The first segments run well clear of the ring; only the last one crosses. A membership test
        // that gave up before walking the whole shape would miss a route that passes the rider late.
        val line = listOf(
            offsetMeters(-4000.0, 3000.0),
            offsetMeters(-2000.0, 3000.0),
            offsetMeters(0.0, 3000.0),
            offsetMeters(0.0, 0.0)
        )

        assertTrue(entersHoop(line, hoop))
    }

    @Test
    fun `a line entirely outside the hoop does not enter it`() {
        assertFalse(entersHoop(listOf(offsetMeters(2000.0, 2000.0), offsetMeters(3000.0, 3000.0)), hoop))
    }

    @Test
    fun `a line that passes near the hoop without reaching it does not enter it`() {
        // Parallel to the ring and just outside it: the nearest approach matters, not the endpoints.
        val line = listOf(offsetMeters(-3000.0, 900.0), offsetMeters(3000.0, 900.0))

        assertFalse(entersHoop(line, hoop))
    }

    @Test
    fun `a line entirely inside the hoop enters it`() {
        val line = listOf(offsetMeters(-100.0, 0.0), offsetMeters(0.0, 100.0), offsetMeters(100.0, 0.0))

        assertTrue(entersHoop(line, hoop))
    }

    @Test
    fun `a degenerate shape enters nothing`() {
        assertFalse(entersHoop(listOf(center), hoop))
        assertFalse(entersHoop(emptyList(), hoop))
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
        val presentation = assembleNearbyRoutesPresentation(emptyList(), emptyMap())

        assertEquals(emptyList<Any>(), presentation.polylines)
        assertEquals(emptyList<Any>(), presentation.badges)
    }

    @Test
    fun `a qualifying route is drawn in full, far beyond the hoop`() {
        // Enters the hoop from the west and runs 4 km past it: the hoop selects the route, it does
        // not crop it, so the drawn line keeps every point.
        val shape = listOf(offsetMeters(-200.0, 0.0), offsetMeters(4000.0, 0.0))
        val presentation = assembleNearbyRoutesPresentation(
            listOf(NearbyRouteShapes("3", "3", listOf(shape))),
            colors = mapOf("3" to RED)
        )

        val routeLine = presentation.polylines.last()
        assertEquals(shape, routeLine.points)
        assertEquals(
            setOf(RoutePolylineTransform.VIEWPORT_CLIP, RoutePolylineTransform.ZOOM_SIMPLIFY),
            routeLine.transforms
        )
        // And it is labelled along that full shape, at the whole route's midpoint — well past the ring.
        assertTrue(haversineMeters(center, presentation.badges.single().point) > hoop.radiusMeters)
    }

    @Test
    fun `a route whose shape only skirts the hoop is not selected`() {
        // Serving a stop inside the ring is not enough — the shape has to pass through — and this is
        // what the survey filters on before a route ever reaches the render plan.
        val through = listOf(offsetMeters(-500.0, 0.0), offsetMeters(500.0, 0.0))
        val elsewhere = listOf(offsetMeters(3000.0, 0.0), offsetMeters(4000.0, 0.0))

        assertTrue(entersHoop(through, hoop))
        assertFalse(entersHoop(elsewhere, hoop))
    }

    @Test
    fun `everything handed to the render plan is drawn in full`() {
        val presentation = assembleNearbyRoutesPresentation(
            listOf(NearbyRouteShapes("passing", "5", listOf(listOf(offsetMeters(-500.0, 0.0), offsetMeters(500.0, 0.0))))),
            colors = mapOf("passing" to RED)
        )

        assertEquals(listOf("5"), presentation.badges.map { it.routeShortName })
        assertEquals(
            listOf(NEARBY_ROUTE_LINE_WIDTH_PROFILE),
            presentation.polylines.map { it.widthProfile }
        )
    }

    @Test
    fun `badges ride the whole route, never the stretch inside the hoop`() {
        // Two routes, each clipping the hoop's western edge and running 20 km east.
        val routes = (1..2).map { index ->
            NearbyRouteShapes(
                "route-$index",
                "$index",
                listOf(listOf(offsetMeters(-700.0, index * 100.0), offsetMeters(20_000.0, index * 100.0)))
            )
        }

        val presentation = assembleNearbyRoutesPresentation(
            routes,
            colors = routes.associate { it.routeId to RED }
        )

        assertEquals(2, presentation.badges.size)
        // Anchored at the whole route's midpoint, far out along the line — not stacked in the ring.
        assertTrue(presentation.badges.all { haversineMeters(center, it.point) > hoop.radiusMeters })
    }

    @Test
    fun `a route lying entirely within the hoop still gets its badge`() {
        // The whole-route anchoring must not assume the route leaves the circle: a short route that
        // begins and ends inside it is still drawn, so it still has to be labelled.
        val shape = listOf(offsetMeters(-300.0, 0.0), offsetMeters(300.0, 0.0))

        val presentation = assembleNearbyRoutesPresentation(
            listOf(NearbyRouteShapes("short", "9", listOf(shape))),
            colors = mapOf("short" to RED)
        )

        assertEquals(listOf("9"), presentation.badges.map { it.routeShortName })
        assertTrue(haversineMeters(center, presentation.badges.single().point) <= hoop.radiusMeters)
    }

    @Test
    fun `a hoop badge carries no direction so a tap enters the route's default`() {
        val presentation = assembleNearbyRoutesPresentation(
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
