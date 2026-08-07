/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.render

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline

/**
 * Striping a ride shared by several routes (#2100). The point of these is that the stripes are a *reading*
 * of one line rather than a second thing drawn on it: the runs cover the ride exactly, in the order the
 * label names the routes, ending where the ride ends and marked only there.
 */
class RouteLineStripesTest {

    private val red = 0xFFB3261E.toInt()

    private val blue = 0xFF0B57D0.toInt()

    private val green = 0xFF146C2E.toInt()

    @Test
    fun `an ordinary line is not cut, and the list it came in keeps its identity`() {
        val lines = listOf(ride(kilometres = 4.0))

        assertSame(lines, stripe(lines))
    }

    @Test
    fun `a shared ride cycles its own colour and the routes it is shared with, in turn`() {
        val striped = stripe(listOf(ride(kilometres = 4.0, stripeColors = listOf(blue))))

        // Its own colour leads — the line is stroked in the route the plan picked, and the alternatives
        // are striped through it.
        assertEquals(red, striped.first().color)
        assertEquals(
            List(striped.size) { if (it % 2 == 0) red else blue },
            striped.map { it.color }
        )
        // Each run is a plain line: the stripes are already cut, so nothing downstream re-cuts them.
        assertTrue(striped.all { it.stripeColors.isEmpty() })
    }

    @Test
    fun `the runs cover the whole ride, end to end, with no gap between them`() {
        val ride = ride(kilometres = 4.0, stripeColors = listOf(blue, green))

        val striped = stripe(listOf(ride))

        assertEquals(ride.points.first(), striped.first().points.first())
        assertEquals(ride.points.last(), striped.last().points.last())
        striped.zipWithNext { earlier, later ->
            assertEquals(earlier.points.last(), later.points.first())
        }
        // ...and the runs are equal in length, so the alternation reads as a rhythm rather than as a
        // sequence of unrelated segments.
        val lengths = striped.map { Polyline(it.points).lengthMeters }
        lengths.forEach { assertEquals(lengths.first(), it, 1.0) }
    }

    @Test
    fun `only the ends of the ride are marked, never the cuts inside it`() {
        val ride = ride(
            kilometres = 4.0,
            stripeColors = listOf(blue),
            startMark = RouteLineMark.INTERLINE_CUT,
            endMark = RouteLineMark.BULB
        )

        val striped = stripe(listOf(ride))

        assertEquals(RouteLineMark.INTERLINE_CUT, striped.first().startMark)
        assertEquals(RouteLineMark.BULB, striped.last().endMark)
        // A bulb pair means "alight here, board there"; one at every stripe boundary would tell the rider
        // to get off a dozen times along a single ride.
        assertTrue(striped.drop(1).all { it.startMark == RouteLineMark.NONE })
        assertTrue(striped.dropLast(1).all { it.endMark == RouteLineMark.NONE })
    }

    @Test
    fun `a stripe holds its length on the screen, so a closer camera cuts more of them`() {
        val ride = ride(kilometres = 4.0, stripeColors = listOf(blue))

        val overview = stripe(listOf(ride), zoom = 12.0)
        val close = stripe(listOf(ride), zoom = 15.0)

        assertTrue("$overview stripes vs $close", overview.size < close.size)
    }

    @Test
    fun `panning across a ride does not re-cut it`() {
        // The cut is geometry: anything it varies with redraws every native line the moment the camera
        // settles there. So it reads the *line's* latitude and a whole zoom level, and a pan — or the
        // drift within one zoom level that a pinch passes through — leaves it exactly as it was.
        val ride = ride(kilometres = 4.0, stripeColors = listOf(blue))

        val settled = stripe(listOf(ride), zoom = 13.0, center = GeoPoint(47.6, -122.3))

        assertEquals(settled, stripe(listOf(ride), zoom = 13.0, center = GeoPoint(1.0, 100.0)))
        assertEquals(settled, stripe(listOf(ride), zoom = 13.9, center = GeoPoint(47.6, -122.3)))
    }

    @Test
    fun `every route reaches a ride too short to stripe at its own rhythm`() {
        // A block-long ride at overview zoom is shorter than one stripe. Drawn in the planned route's
        // colour alone it would say the rider must board that route, which is the thing #2100 is about.
        val striped = stripe(listOf(ride(kilometres = 0.02, stripeColors = listOf(blue, green))), zoom = 10.0)

        assertEquals(listOf(red, blue, green), striped.map { it.color })
    }

    @Test
    fun `a ride far longer than the screen is capped, not cut into hundreds of lines`() {
        val striped = stripe(listOf(ride(kilometres = 400.0, stripeColors = listOf(blue))), zoom = 18.0)

        assertEquals(MAX_STRIPES, striped.size)
    }

    @Test
    fun `until the first camera settles a shared ride draws whole, in its planned colour`() {
        val lines = listOf(ride(kilometres = 4.0, stripeColors = listOf(blue)))

        assertSame(lines, StripeRoutePolylinePass().apply(lines, RoutePolylineRenderContext(camera = null)))
    }

    private fun stripe(
        lines: List<RoutePolyline>,
        zoom: Double = 14.0,
        center: GeoPoint = GeoPoint(47.6, -122.3)
    ) = StripeRoutePolylinePass().apply(lines, RoutePolylineRenderContext(camera(center, zoom)))

    /** A transit leg running [kilometres] due east from a fixed Seattle-ish anchor. */
    private fun ride(
        kilometres: Double,
        stripeColors: List<Int> = emptyList(),
        startMark: RouteLineMark = RouteLineMark.NONE,
        endMark: RouteLineMark = RouteLineMark.NONE
    ): RoutePolyline {
        val start = GeoPoint(47.6, -122.3)
        val east = Math.toDegrees(kilometres * 1000.0 / (EARTH_RADIUS_METERS * cos(Math.toRadians(start.latitude))))
        return RoutePolyline(
            color = red,
            points = listOf(start, GeoPoint(start.latitude, start.longitude + east)),
            stripeColors = stripeColors,
            widthProfile = ITINERARY_RIDE_WIDTH_PROFILE,
            case = RouteLineCase.OUTLINE,
            startMark = startMark,
            endMark = endMark
        )
    }

    private fun camera(center: GeoPoint, zoom: Double) = CameraSnapshot(
        center = center,
        zoom = zoom,
        latSpan = 0.1,
        lonSpan = 0.1,
        southWest = GeoPoint(center.latitude - 0.05, center.longitude - 0.05),
        northEast = GeoPoint(center.latitude + 0.05, center.longitude + 0.05)
    )
}
