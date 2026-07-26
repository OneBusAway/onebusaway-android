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

    // ----- Ranking the nearby routes, and sizing the ring to them -----

    @Test
    fun `routes rank by their nearest serving stop`() {
        val ranked = rankRoutesByNearestStop(
            center,
            stops = listOf(
                stop("far", offsetMeters(0.0, 700.0), "far-route"),
                stop("near", offsetMeters(0.0, 50.0), "near-route"),
                // The same route served twice: its *nearest* stop is the one that counts.
                stop("mid-far", offsetMeters(0.0, 600.0), "mid-route"),
                stop("mid-near", offsetMeters(0.0, 300.0), "mid-route")
            ),
            routes = listOf(route("far-route"), route("near-route"), route("mid-route"))
        )

        assertEquals(listOf("near-route", "mid-route", "far-route"), ranked.map { it.route.id })
        assertEquals(50.0, ranked.first().meters, 1.0)
    }

    @Test
    fun `a route id the reference pool can't resolve is dropped rather than drawn nameless`() {
        val ranked = rankRoutesByNearestStop(
            center,
            stops = listOf(stop("s", offsetMeters(0.0, 100.0), "known", "unknown")),
            routes = listOf(route("known"))
        )

        assertEquals(listOf("known"), ranked.map { it.route.id })
    }

    @Test
    fun `equidistant routes break ties on id so the drawn set is stable`() {
        val ranked = rankRoutesByNearestStop(
            center,
            stops = listOf(stop("shared", offsetMeters(0.0, 100.0), "b-route", "a-route")),
            routes = listOf(route("b-route"), route("a-route"))
        )

        assertEquals(listOf("a-route", "b-route"), ranked.map { it.route.id })
    }

    @Test
    fun `the ring shows the searched radius when it holds no more than the target`() {
        // The ring is the search area, so it stays at full reach and simply holds fewer routes. Hugging
        // the farthest of them would misreport how far was searched — and would make the ring jitter
        // between settles as the outermost route came and went.
        assertEquals(MAX_RADIUS, hoopRadiusForTarget(emptyList(), 15, MAX_RADIUS), 0.001)
        assertEquals(MAX_RADIUS, hoopRadiusForTarget(listOf(ranked("a", 100.0)), 15, MAX_RADIUS), 0.001)
        // Exactly the target still fits, so it still shows the whole search area.
        val atTarget = (1..15).map { ranked("route-$it", it * 100.0) }
        assertEquals(MAX_RADIUS, hoopRadiusForTarget(atTarget, 15, MAX_RADIUS), 0.001)
    }

    @Test
    fun `the ring closes in only once the searched radius holds more than the target`() {
        val ranked = (1..5).map { ranked("route-$it", it * 100.0) }

        // Five routes, target three: too many to read, so it pulls in to the third.
        assertEquals(300.0, hoopRadiusForTarget(ranked, 3, MAX_RADIUS), 0.001)
        // The denser the surroundings, the tighter it closes — the whole point of deriving the radius.
        assertEquals(60.0, hoopRadiusForTarget((1..5).map { ranked("r$it", it * 20.0) }, 3, MAX_RADIUS), 0.001)
    }

    @Test
    fun `routes sharing a stop share a distance, so the ring takes them all`() {
        // Downtown's shape: one busy stop puts several routes at the same distance. The radius lands on
        // that distance, and everything at it is drawn — cutting to exactly the target would mean
        // choosing arbitrarily between routes the data says are equally near.
        val ranked = listOf(
            ranked("a", 150.0),
            ranked("b", 150.0),
            ranked("c", 150.0),
            ranked("d", 900.0)
        )

        val radius = hoopRadiusForTarget(ranked, 2, MAX_RADIUS)

        assertEquals(150.0, radius, 0.001)
        assertEquals(3, ranked.count { it.meters <= radius })
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

    private fun ranked(routeId: String, meters: Double) = RankedRoute(route(routeId), meters)

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
        const val MAX_RADIUS = 2000.0
    }
}
