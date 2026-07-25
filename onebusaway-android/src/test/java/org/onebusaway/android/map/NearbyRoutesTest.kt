/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.NEARBY_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.GeoPoint

class NearbyRoutesTest {

    private val center = GeoPoint(47.6, -122.33)
    private val hoop = NearbyRoutesHoop(center, 800.0)

    // ----- Ranking the route set down to the cap -----

    @Test
    fun `routes rank by their nearest serving stop and the cap keeps the closest`() {
        val ranked = rankRoutesByNearestStop(
            hoop,
            routes = listOf(route("far-route"), route("near-route"), route("mid-route")),
            stops = listOf(
                stop("far", offsetMeters(0.0, 700.0), "far-route"),
                stop("near", offsetMeters(0.0, 50.0), "near-route"),
                // The same route served twice: its *nearest* stop decides its rank.
                stop("mid-far", offsetMeters(0.0, 600.0), "mid-route"),
                stop("mid-near", offsetMeters(0.0, 300.0), "mid-route")
            ),
            limit = 2
        )

        assertEquals(listOf("near-route", "mid-route"), ranked.map { it.id })
    }

    @Test
    fun `a stop outside the hoop doesn't rank its route`() {
        val ranked = rankRoutesByNearestStop(
            hoop,
            routes = listOf(route("inner"), route("outer")),
            stops = listOf(
                stop("inside", offsetMeters(0.0, 100.0), "inner"),
                stop("outside", offsetMeters(0.0, 2000.0), "outer")
            ),
            limit = 10
        )

        // Both stay in the set — routes-for-location is authoritative about membership — but the one
        // the sample places nowhere near the centre sorts last.
        assertEquals(listOf("inner", "outer"), ranked.map { it.id })
    }

    @Test
    fun `a route the stop sample never mentions keeps its place and sorts last`() {
        val ranked = rankRoutesByNearestStop(
            hoop,
            routes = listOf(route("unsampled"), route("sampled")),
            stops = listOf(stop("known", offsetMeters(0.0, 100.0), "sampled")),
            limit = 10
        )

        assertEquals(listOf("sampled", "unsampled"), ranked.map { it.id })
    }

    @Test
    fun `equidistant routes break ties on id so the drawn set is stable`() {
        val ranked = rankRoutesByNearestStop(
            hoop,
            routes = listOf(route("b-route"), route("a-route")),
            stops = listOf(stop("shared", offsetMeters(0.0, 100.0), "b-route", "a-route")),
            limit = 10
        )

        assertEquals(listOf("a-route", "b-route"), ranked.map { it.id })
    }

    @Test
    fun `the hoop's query box squares the circle and widens with latitude`() {
        val (latSpan, lonSpan) = hoop.spanDegrees()

        // A 1.6 km box: 2 x 800 m of latitude.
        assertEquals(1600.0 / 111_195.0, latSpan, 1e-5)
        // Longitude degrees are shorter at 47.6 N, so the box spans more of them.
        assertTrue(lonSpan > latSpan)
        assertEquals(latSpan / Math.cos(Math.toRadians(47.6)), lonSpan, 1e-9)
    }

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

    private fun stop(id: String, point: GeoPoint, vararg routeIds: String) = ObaStopElement(
        id = id,
        lat = point.latitude,
        lon = point.longitude,
        routeIds = arrayOf(*routeIds)
    )

    private fun route(routeId: String) = object : ObaRoute {
        override val id = routeId
        override val shortName = routeId
        override val longName: String? = null
        override val description: String? = null
        override val type = ObaRoute.TYPE_BUS
        override val url: String? = null
        override val color: Int? = null
        override val textColor: Int? = null
        override val agencyId = "agency"
    }

    private companion object {
        const val RED = 0xFFCC0000.toInt()
    }
}
