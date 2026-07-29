/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.util.GeoPoint

/**
 * The route labels drawn on a directions itinerary's transit lines (#2066). The point of these is that a
 * label names *a ride* — one per route, however many legs it spans — takes the colour of the line it
 * names, and never becomes a tap target that would navigate away from the trip being read.
 */
class ItineraryRouteBadgesTest {

    private val eastward = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))

    private val northward = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))

    @Test
    fun `each ridden route is labelled at its midpoint, in its own line colour, and is not tappable`() {
        val ride = TripLeg(mode = TripMode.BUS, routeId = "route-45", routeShortName = "45")
        val rideStyle = itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = 0xFF0000FF.toInt())

        val badge = itineraryRouteBadges(
            listOf(
                drawable(0, TripLeg(mode = TripMode.WALK), northward, ItineraryLegKind.WALK),
                ItineraryDrawableLeg(1, ride, eastward, rideStyle)
            )
        ).single()

        assertEquals("45", badge.routeShortName)
        assertEquals("route-45", badge.routeId)
        assertEquals(GeoPoint(0.0, 0.5), badge.point)
        // Exactly the colour its own line is stroked with, so a label and its line can't disagree.
        assertEquals(rideStyle.color, badge.color)
        assertFalse(badge.interactive)
    }

    @Test
    fun `two legs of one route share a single label`() {
        val first = TripLeg(mode = TripMode.BUS, routeId = "route-5", routeShortName = "5")
        val second = TripLeg(mode = TripMode.BUS, routeId = "route-5", routeShortName = "5")

        val badges = itineraryRouteBadges(
            listOf(drawable(0, first, eastward), drawable(2, second, northward))
        )

        assertEquals(listOf("route-5"), badges.map { it.routeId })
    }

    @Test
    fun `two routes ridden along the same corridor are labelled apart`() {
        val badges = itineraryRouteBadges(
            listOf(
                drawable(0, TripLeg(mode = TripMode.BUS, routeId = "a", routeShortName = "A"), eastward),
                drawable(1, TripLeg(mode = TripMode.RAIL, routeId = "b", routeShortName = "B"), eastward)
            )
        )

        assertEquals(listOf("A", "B"), badges.map { it.routeShortName })
        assertNotEquals(badges[0].point, badges[1].point)
    }

    @Test
    fun `an OTP1 leg, which identifies no route, is identified by the name it displays`() {
        val badge = itineraryRouteBadges(
            listOf(drawable(0, TripLeg(mode = TripMode.BUS, routeId = null, route = "45"), eastward))
        ).single()

        assertEquals("45", badge.routeId)
        assertEquals("45", badge.routeShortName)
    }

    @Test
    fun `a route that names itself in no way, and a line too short to anchor on, are left unlabelled`() {
        val nameless = TripLeg(mode = TripMode.BUS, routeId = "route-x")
        val degenerate = TripLeg(mode = TripMode.BUS, routeId = "route-y", routeShortName = "Y")

        val badges = itineraryRouteBadges(
            listOf(
                drawable(0, nameless, eastward),
                drawable(1, degenerate, listOf(GeoPoint(0.0, 0.0)))
            )
        )

        assertEquals(emptyList<String>(), badges.map { it.routeId })
    }

    private fun drawable(
        index: Int,
        leg: TripLeg,
        points: List<GeoPoint>,
        kind: ItineraryLegKind = ItineraryLegKind.TRANSIT
    ) = ItineraryDrawableLeg(index, leg, points, itineraryLegStyle(kind, routeColor = null))
}
