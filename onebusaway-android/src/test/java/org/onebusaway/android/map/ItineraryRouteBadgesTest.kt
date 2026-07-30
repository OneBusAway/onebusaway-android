/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.util.GeoPoint

/**
 * The route labels drawn on a directions itinerary's transit lines (#2066). The point of these is that a
 * label names *a ride* — one per route, however many legs it spans, and every route the rider may board
 * for it (#2083) — takes the colour of the line it names, and never becomes a tap target that would
 * navigate away from the trip being read.
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

        // Exactly the colour its own line is stroked with, so a label and its line can't disagree.
        assertEquals(listOf(BadgedRoute("45", rideStyle.color)), badge.routes)
        assertEquals(GeoPoint(0.0, 0.5), badge.point)
        // No tap target at all, so there is nothing for a stray tap to navigate to.
        assertNull(badge.tap)
    }

    @Test
    fun `two legs of one route share a single label`() {
        val first = TripLeg(mode = TripMode.BUS, routeId = "route-5", routeShortName = "5")
        val second = TripLeg(mode = TripMode.BUS, routeId = "route-5", routeShortName = "5")

        val badges = itineraryRouteBadges(
            listOf(drawable(0, first, eastward), drawable(2, second, northward))
        )

        assertEquals(listOf(listOf("5")), badges.map(::names))
    }

    @Test
    fun `two routes ridden along the same corridor are labelled apart`() {
        val badges = itineraryRouteBadges(
            listOf(
                drawable(0, TripLeg(mode = TripMode.BUS, routeId = "a", routeShortName = "A"), eastward),
                drawable(1, TripLeg(mode = TripMode.RAIL, routeId = "b", routeShortName = "B"), eastward)
            )
        )

        assertEquals(listOf(listOf("A"), listOf("B")), badges.map(::names))
        assertNotEquals(badges[0].point, badges[1].point)
    }

    @Test
    fun `OTP1 legs, which identify no route, are grouped by the name they display`() {
        // An OTP1 response names a route without identifying it, so the displayed name is all these two
        // legs have to be recognized as one ride by — they still get a single shared label.
        val badges = itineraryRouteBadges(
            listOf(
                drawable(0, TripLeg(mode = TripMode.BUS, routeId = null, route = "45"), eastward),
                drawable(1, TripLeg(mode = TripMode.BUS, routeId = null, route = "45"), northward)
            )
        )

        assertEquals(listOf(listOf("45")), badges.map(::names))
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

        assertEquals(emptyList<List<String>>(), badges.map(::names))
    }

    @Test
    fun `an interchangeable ride is labelled with every route it may be ridden as, in name order`() {
        // The planner picked the 2 Line; the label gives it no pride of place, exactly as the drawer's
        // joined badge doesn't — the routes are interchangeable, so the label reads the same either way.
        val ride = TripLeg(mode = TripMode.RAIL, routeId = "route-2", routeShortName = "2 Line")

        val badge = itineraryRouteBadges(
            listOf(drawable(0, ride, eastward, interchangeable = listOf(interchangeable("1 Line"))))
        ).single()

        assertEquals(listOf("1 Line", "2 Line"), names(badge))
        // Still informational: naming several routes doesn't turn the label into a navigation target.
        assertNull(badge.tap)
    }

    @Test
    fun `an interchangeable route is coloured as the map would draw its own line`() {
        val ride = TripLeg(mode = TripMode.RAIL, routeId = "route-2", routeShortName = "2 Line")
        val alternative = interchangeable("1 Line", routeColor = 0xFF0000FF.toInt())

        val badge = itineraryRouteBadges(
            listOf(drawable(0, ride, eastward, interchangeable = listOf(alternative)))
        ).single()

        assertEquals(
            itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = 0xFF0000FF.toInt()).color,
            badge.routes.single { it.routeShortName == "1 Line" }.color
        )
    }

    @Test
    fun `two legs of one route offered different substitutes are labelled separately`() {
        // The first leg shares its corridor with the 1 Line; the second doesn't. One shared label would
        // claim on both segments what is only true of one, so the ride is labelled where it is true.
        val first = TripLeg(mode = TripMode.RAIL, routeId = "route-2", routeShortName = "2 Line")
        val second = TripLeg(mode = TripMode.RAIL, routeId = "route-2", routeShortName = "2 Line")

        val badges = itineraryRouteBadges(
            listOf(
                drawable(0, first, eastward, interchangeable = listOf(interchangeable("1 Line"))),
                drawable(1, second, northward)
            )
        )

        assertEquals(listOf(listOf("1 Line", "2 Line"), listOf("2 Line")), badges.map(::names))
    }

    private fun names(badge: RouteBadge) = badge.routes.map(BadgedRoute::routeShortName)

    /** An interchangeable route as the controller hands it over: named, and already colour-parsed. */
    private fun interchangeable(displayName: String, routeColor: Int? = null) = interchangeableBadgedRoute(
        InterchangeableRoute(
            routeId = "route-for-$displayName",
            displayName = displayName,
            routeColor = null,
            agencyId = null,
            agencyName = null,
            headsign = null
        ),
        routeColor
    )

    private fun drawable(
        index: Int,
        leg: TripLeg,
        points: List<GeoPoint>,
        kind: ItineraryLegKind = ItineraryLegKind.TRANSIT,
        interchangeable: List<BadgedRoute> = emptyList()
    ) = ItineraryDrawableLeg(index, leg, points, itineraryLegStyle(kind, routeColor = null), interchangeable)
}
