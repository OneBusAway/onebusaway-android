/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.NEARBY_ROUTES_HOOP_WIDTH_PROFILE
import org.onebusaway.android.map.render.NEARBY_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.GeoPoint

class NearbyRoutesTest {

    private val center = GeoPoint(47.6, -122.33)
    private val hoop = NearbyRoutesHoop(center, 800.0)

    // ----- The route set -----

    @Test
    fun `only routes serving a stop inside the hoop are drawn`() {
        val ids = nearbyRouteIds(
            hoop,
            listOf(
                stopMarker("inside", offsetMeters(0.0, 100.0), "inner"),
                stopMarker("outside", offsetMeters(0.0, 2000.0), "outer")
            ),
            limit = 10
        )

        assertEquals(listOf("inner"), ids)
    }

    @Test
    fun `routes rank by their nearest serving stop and the cap keeps the closest`() {
        val ids = nearbyRouteIds(
            hoop,
            listOf(
                stopMarker("far", offsetMeters(0.0, 700.0), "far-route"),
                stopMarker("near", offsetMeters(0.0, 50.0), "near-route"),
                // The same route served twice: its *nearest* stop decides its rank.
                stopMarker("mid-far", offsetMeters(0.0, 600.0), "mid-route"),
                stopMarker("mid-near", offsetMeters(0.0, 300.0), "mid-route")
            ),
            limit = 2
        )

        assertEquals(listOf("near-route", "mid-route"), ids)
    }

    @Test
    fun `equidistant routes break ties on id so the drawn set is stable`() {
        val stops = listOf(stopMarker("shared", offsetMeters(0.0, 100.0), "b-route", "a-route"))

        assertEquals(listOf("a-route", "b-route"), nearbyRouteIds(hoop, stops, limit = 10))
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

    @Test
    fun `the ring closes on itself at the hoop radius`() {
        val ring = hoopRing(hoop)

        assertEquals(ring.first(), ring.last())
        ring.forEach { assertEquals(800.0, haversineMeters(center, it), 1.0) }
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

        val routeLines = presentation.polylines.drop(1)
        assertEquals(2, routeLines.size)
        assertEquals(1, routeLines.map { it.resolvedColor }.distinct().size)
        assertEquals(listOf("44"), presentation.badges.map { it.routeShortName })
        // The badge keeps the palette colour at full opacity; the lines are drawn back.
        assertEquals(RED, presentation.badges.single().color)
        assertNotEquals(RED, routeLines.first().resolvedColor)
        assertEquals(RED and 0x00FFFFFF, routeLines.first().resolvedColor and 0x00FFFFFF)
    }

    @Test
    fun `the ring is drawn first so route lines sit on top of it`() {
        val presentation = assembleNearbyRoutesPresentation(hoop, emptyList(), emptyMap())

        assertEquals(1, presentation.polylines.size)
        assertEquals(NEARBY_ROUTES_HOOP_WIDTH_PROFILE, presentation.polylines.single().widthProfile)
        assertEquals(emptyList<Any>(), presentation.badges)
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
            listOf(NEARBY_ROUTES_HOOP_WIDTH_PROFILE, NEARBY_ROUTE_LINE_WIDTH_PROFILE),
            presentation.polylines.map { it.widthProfile }
        )
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

    private fun stopMarker(id: String, point: GeoPoint, vararg routeIds: String) = StopMarker(
        id,
        point,
        "null",
        ObaRoute.TYPE_BUS,
        ObaStopElement(
            id = id,
            lat = point.latitude,
            lon = point.longitude,
            routeIds = arrayOf(*routeIds)
        )
    )

    private companion object {
        const val RED = 0xFFCC0000.toInt()
    }
}
