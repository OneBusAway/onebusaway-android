/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.tripresults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * JVM tests for the lines a directions board row draws for a ride (#2151): one per route the rider may
 * board it on, each naming where that route is headed, with "whichever comes first" under them when
 * there is more than one.
 *
 * The list's *size* is the instruction — one line is a ride, two are a choice — so what these pin is
 * which routes end up on it, in what order, and how a route that names itself unusually lands.
 *
 * Every fixture leaves the GTFS colour null: parsing one goes through `android.graphics.Color`, which a
 * JVM test can't call (`DirectionRowAlternativeRoutesTest` covers the colour on-device), and the colour
 * is not what decides a line.
 */
class BoardableRoutesTest {

    /** The ordinary ride: one line, the route boarded, headed where the leg says. */
    @Test
    fun anOrdinaryRideOffersItsOwnRouteAlone() {
        val routes = ride(shortName = "8", headsign = "Rainier Beach").boardableRoutes()

        assertEquals(1, routes.size)
        assertEquals("8", routes.single().badge?.shortName)
        assertEquals("Rainier Beach", routes.single().headsign)
        assertNull("a badged route is named by its roundel alone", routes.single().name)
    }

    /**
     * Interchangeable routes each get a line, in natural name order rather than plan order — the same
     * order the option card's joined roundel reads in, so the corridor looks the same on both surfaces
     * whichever of its lines the planner happened to pick.
     */
    @Test
    fun interchangeableRoutesEachGetALineInNaturalOrder() {
        val routes = ride(shortName = "2 Line", headsign = "Downtown Redmond")
            .withAlternatives(alternative("1 Line", "Federal Way Downtown"))
            .boardableRoutes()

        assertEquals(listOf("1 Line", "2 Line"), routes.map { it.badge?.shortName })
    }

    /** …and each line keeps its *own* headsign: the pair shares the track, not where it ends up. */
    @Test
    fun eachLineNamesWhereItsOwnRouteIsHeaded() {
        val routes = ride(shortName = "2 Line", headsign = "Downtown Redmond")
            .withAlternatives(alternative("1 Line", "Federal Way Downtown"))
            .boardableRoutes()

        assertEquals(
            listOf("1 Line" to "Federal Way Downtown", "2 Line" to "Downtown Redmond"),
            routes.map { it.badge?.shortName to it.headsign }
        )
    }

    /** Same-named routes remain distinct because their headsigns tell the rider which vehicle is which. */
    @Test
    fun routesSharingAPublicNameKeepTheirHeadsignLines() {
        val routes = ride(shortName = "40", headsign = "Downtown Seattle")
            .withAlternatives(alternative("40", "Northgate"))
            .boardableRoutes()

        assertEquals(
            listOf("40" to "Downtown Seattle", "40" to "Northgate"),
            routes.map { it.badge?.shortName to it.headsign }
        )
    }

    /**
     * A route publishing no short name has no roundel to draw, so its line leads with the fuller name
     * instead — the ferry case. It is still the route the rider boards, and must not drop off the list.
     */
    @Test
    fun aRouteWithNoShortNameLeadsWithItsFullerName() {
        val routes = ride(shortName = null, headsign = "Bremerton", displayName = "Seattle - Bremerton").boardableRoutes()

        assertNull(routes.single().badge)
        assertEquals("Seattle - Bremerton", routes.single().name)
        assertEquals("Bremerton", routes.single().headsign)
    }

    private fun ride(shortName: String?, headsign: String?, displayName: String? = shortName) = TripLogEntry.Transit(
        routeShortName = shortName,
        routeDisplayName = displayName,
        mode = TransitMode.BUS,
        routeColorHex = null,
        headsign = headsign,
        reachStop = null,
        boardTime = ServerTime(0L),
        exitTime = ServerTime(60_000L),
        durationMinutes = 1,
        rideEvents = emptyList(),
        routeLeg = RouteLegRef(routeId = "1_100", headsign = headsign, board = null, alight = null)
    )

    private fun TripLogEntry.Transit.withAlternatives(vararg alternatives: AlternativeRouteRef) = copy(routeLeg = routeLeg.copy(alternatives = alternatives.toList()))

    private fun alternative(shortName: String, headsign: String) = AlternativeRouteRef(
        routeId = "1_$shortName",
        headsign = headsign,
        shortName = shortName,
        routeColor = null
    )
}
