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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.Interlines
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.util.COLOURLESS_RIDE_HUE_ANCHOR

/**
 * JVM tests for the joined route badge a ride draws — the planned route plus whatever else the leg can be
 * ridden on (#2010), or plus whatever the vehicle becomes under the rider (#2049) — shared by the
 * itinerary option cards and the directions drawer.
 */
class RouteBadgesTest {

    /** The badge reads in natural route order, not plan order, so a corridor looks the same either way. */
    @Test
    fun joinsPlannedAndAlternativeRoutesInNaturalOrder() {
        val badge = legBadge(
            planned = RouteBadge("2 Line", null),
            alternatives = listOf(RouteBadge("1 Line", null)),
            mode = TransitMode.RAIL
        )

        assertEquals(listOf("1 Line", "2 Line"), badge.routes.map { it.shortName })
        assertTrue(badge.isInterchangeable)
    }

    /** …and the mirror-image plan produces the identical badge. */
    @Test
    fun badgeIsTheSameWhicheverRouteThePlanPicked() {
        val plannedTwo = legBadge(RouteBadge("2 Line", null), listOf(RouteBadge("1 Line", null)), TransitMode.RAIL)
        val plannedOne = legBadge(RouteBadge("1 Line", null), listOf(RouteBadge("2 Line", null)), TransitMode.RAIL)

        assertEquals(plannedTwo, plannedOne)
    }

    @Test
    fun naturalOrderSortsRouteNumbersNumerically() {
        val badge = legBadge(
            planned = RouteBadge("40", null),
            alternatives = listOf(RouteBadge("550", null), RouteBadge("8", null)),
            mode = TransitMode.BUS
        )

        assertEquals(listOf("8", "40", "550"), badge.routes.map { it.shortName })
    }

    /** A leg with nothing interchangeable is a plain one-route badge. */
    @Test
    fun singleRouteLegIsNotInterchangeable() {
        val badge = legBadge(planned = RouteBadge("8", null), alternatives = emptyList(), mode = TransitMode.BUS)

        assertEquals(listOf("8"), badge.routes.map { it.shortName })
        assertFalse(badge.isInterchangeable)
    }

    /** A route can't appear twice in one badge, however it reached the list. */
    @Test
    fun dropsADuplicateOfThePlannedRoute() {
        val badge = legBadge(RouteBadge("8", null), listOf(RouteBadge("8", null), RouteBadge("7", null)), TransitMode.BUS)

        assertEquals(listOf("7", "8"), badge.routes.map { it.shortName })
    }

    /**
     * A leg whose route publishes no colour — every WSF ferry — badges the colourless-ride hue, which is
     * what the map draws that ride's line in. It used to carry no colour at all and the chip fell back to
     * the theme's neutral container, so the ferry's roundel came out grey beside its own coral line.
     */
    @Test
    fun aColourlessRouteBadgesTheColourlessRideHue() {
        val ferry = TripLeg(mode = TripMode.FERRY, routeId = "95_128", routeLongName = "Kingston - Edmonds")

        assertEquals(COLOURLESS_RIDE_HUE_ANCHOR, ferry.plannedBadge()?.routeColor)
    }

    /** A leg badges its route's short name. (Parsing the GTFS color needs the Android graphics stack,
     *  so that half is asserted on-device — see `DirectionRowAlternativeRoutesTest`.) */
    @Test
    fun legBadgeUsesTheRouteShortName() {
        val badge = TripLeg(
            mode = TripMode.BUS,
            routeId = "1_100",
            routeShortName = "8",
            routeLongName = "Rainier Ave"
        ).plannedBadge()

        assertEquals("8", badge?.shortName)
    }

    /**
     * A route that publishes no short name badges its long name — never its GTFS id, which is an
     * identifier and not a name. Washington State Ferries' "Seattle - Bremerton" is the real case: it
     * used to badge as its OTP2 gtfsId "95:74", which reads like a route number the rider could look
     * for. (The renderer caps the roundel's width; the name it holds is this one, in full.)
     */
    @Test
    fun legWithNoShortNameBadgesItsLongNameNotItsRouteId() {
        val badge = TripLeg(
            mode = TripMode.FERRY,
            routeId = "95:74",
            routeLongName = "Seattle - Bremerton"
        ).plannedBadge()

        assertEquals("Seattle - Bremerton", badge?.shortName)
    }

    /** A route that names itself in no way at all has no badge; the card falls back to its mode. */
    @Test
    fun legWithNoNameAtAllYieldsAnUnnamedBadgeCarryingItsMode() {
        val leg = TripLeg(mode = TripMode.FERRY, routeId = "95:74")

        assertNull(leg.plannedBadge())

        val badge = legBadge(leg, emptyList())

        assertTrue(badge.routes.isEmpty())
        assertTrue(badge.isUnnamed)
        assertEquals(TransitMode.FERRY, badge.mode)
    }

    /**
     * The drawer's narrower rule (#2071): a short name roundels, a long name never does. The two board
     * and seam rows both use it, and each has room to print a long name as the row's title instead — so
     * a ferry that reaches the drawer draws no roundel where an option card would draw a wide one.
     */
    @Test
    fun shortNameBadgeRoundelsOnlyAShortName() {
        val bus = TripLeg(mode = TripMode.BUS, routeId = "1_100", routeShortName = "8", routeLongName = "Rainier Ave")
        val ferry = TripLeg(mode = TripMode.FERRY, routeId = "95:74", routeLongName = "Seattle - Bremerton")

        assertEquals("8", bus.shortNameBadge()?.shortName)
        assertEquals("Seattle - Bremerton", ferry.plannedBadge()?.shortName)
        assertNull(ferry.shortNameBadge())
    }

    /** OTP1 legs carry a flat display string instead of a short name; it still badges. */
    @Test
    fun legBadgeUsesTheOtp1FlatRouteStringWhenThereIsNoShortName() {
        val badge = TripLeg(mode = TripMode.BUS, routeId = "1_100", route = "8").plannedBadge()

        assertEquals("8", badge?.shortName)
    }

    /** A leg's badge is its planned route joined by the routes ruled interchangeable with it. */
    @Test
    fun legBadgeJoinsTheLegsRouteWithItsInterchangeableOnes() {
        val leg = TripLeg(mode = TripMode.BUS, routeId = "1_100", routeShortName = "8")

        val badge = legBadge(leg, listOf(interchangeableRoute("7")))

        assertEquals(listOf("7", "8"), badge.routes.map { it.shortName })
    }

    /**
     * An interchangeable route badges the same way the planned one does — and names itself by its long
     * name, not its id, when it publishes no short name. (A route with neither is dropped upstream by
     * `interchangeableRoutes()`, so it never reaches a badge at all.)
     */
    @Test
    fun interchangeableRouteBadgesItsDisplayName() {
        assertEquals("1 Line", interchangeableRoute("1 Line").badge().shortName)
        assertEquals("Seattle - Bremerton", interchangeableRoute("Seattle - Bremerton").badge().shortName)
    }

    /**
     * A ride the vehicle changes route during badges every route it runs as, in ride order and joined by
     * chevrons (#2049) — the picker used to promise a 5 all the way while the drawer's own "stay on
     * board" row said the vehicle becomes the 12.
     *
     * Ride order, again on a pair natural name order would reverse: what the rider boards comes first.
     */
    @Test
    fun rideBadgeChevronsTheRoutesAnInterlinedVehicleRunsAs() {
        val badge = rideBadgeOf(transitLeg("12"), transitLeg("10", interline = true))

        assertEquals(listOf("12", "10"), badge.routes.map { it.shortName })
        assertEquals(RouteBadgeJoin.THEN, badge.join)
        assertTrue(badge.isJoined)
        // "Board either one" is the wrong instruction here: there is one vehicle, and it becomes the 10.
        assertFalse(badge.isInterchangeable)
    }

    /** A self-interline is invisible to the rider — same route, same vehicle — so it badges once. */
    @Test
    fun rideBadgeOfASelfInterlineNamesTheRouteOnce() {
        val badge = rideBadgeOf(transitLeg("12"), transitLeg("12", interline = true))

        assertEquals(listOf("12"), badge.routes.map { it.shortName })
        assertFalse(badge.isJoined)
    }

    /** A ride with no route change is the leg's own badge: its interchangeable routes, slashed. */
    @Test
    fun rideBadgeOfAnOrdinaryRideOffersItsInterchangeableRoutes() {
        val legs = listOf(transitLeg("2 Line"))

        val badge = rideBadge(legs, Interlines.chains(legs).single(), listOf(interchangeableRoute("1 Line")))

        assertEquals(listOf("1 Line", "2 Line"), badge.routes.map { it.shortName })
        assertEquals(RouteBadgeJoin.ANY_OF, badge.join)
        assertTrue(badge.isInterchangeable)
    }

    /**
     * A continuation whose route names itself in no way at all drops out of the badge rather than
     * leaving it a blank segment; the ride still names every route that can be named, and the drawer's
     * transition row still announces the change (falling back to the headsign).
     */
    @Test
    fun rideBadgeDropsAContinuationThatNamesNoRoute() {
        val unnamed = TripLeg(mode = TripMode.BUS, routeId = "1_999", interlineWithPreviousLeg = true)

        val badge = rideBadgeOf(transitLeg("12"), unnamed)

        assertEquals(listOf("12"), badge.routes.map { it.shortName })
        assertEquals(TransitMode.BUS, badge.mode)
    }

    private fun transitLeg(shortName: String, interline: Boolean = false) = TripLeg(
        mode = TripMode.BUS,
        routeId = "1_$shortName",
        routeShortName = shortName,
        interlineWithPreviousLeg = interline
    )

    /** The badge for the single ride [legs] describe, with nothing interchangeable. */
    private fun rideBadgeOf(vararg legs: TripLeg): LegBadge {
        val ride = legs.toList()
        return rideBadge(ride, Interlines.chains(ride).single(), emptyList())
    }

    private fun interchangeableRoute(displayName: String) = InterchangeableRoute(
        routeId = "40:100479",
        displayName = displayName,
        routeColor = null,
        agencyId = "40:1",
        agencyName = "Sound Transit",
        headsign = "Federal Way Downtown"
    )
}
