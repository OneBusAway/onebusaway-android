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
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.ui.compose.components.RouteBadge

/**
 * JVM tests for the joined route badge a transit leg draws (#2010) — the planned route plus whatever
 * else the leg can be ridden on, shared by the itinerary option cards and the directions drawer.
 */
class RouteBadgesTest {

    /** The badge reads in natural route order, not plan order, so a corridor looks the same either way. */
    @Test
    fun joinsPlannedAndAlternativeRoutesInNaturalOrder() {
        val badge = legBadge(
            planned = RouteBadge("2 Line", null),
            alternatives = listOf(RouteBadge("1 Line", null))
        )

        assertEquals(listOf("1 Line", "2 Line"), badge.routes.map { it.shortName })
        assertTrue(badge.isInterchangeable)
    }

    /** …and the mirror-image plan produces the identical badge. */
    @Test
    fun badgeIsTheSameWhicheverRouteThePlanPicked() {
        val plannedTwo = legBadge(RouteBadge("2 Line", null), listOf(RouteBadge("1 Line", null)))
        val plannedOne = legBadge(RouteBadge("1 Line", null), listOf(RouteBadge("2 Line", null)))

        assertEquals(plannedTwo, plannedOne)
    }

    @Test
    fun naturalOrderSortsRouteNumbersNumerically() {
        val badge = legBadge(
            planned = RouteBadge("40", null),
            alternatives = listOf(RouteBadge("550", null), RouteBadge("8", null))
        )

        assertEquals(listOf("8", "40", "550"), badge.routes.map { it.shortName })
    }

    /** A leg with nothing interchangeable is a plain one-route badge. */
    @Test
    fun singleRouteLegIsNotInterchangeable() {
        val badge = legBadge(planned = RouteBadge("8", null), alternatives = emptyList())

        assertEquals(listOf("8"), badge.routes.map { it.shortName })
        assertFalse(badge.isInterchangeable)
    }

    /** A route can't appear twice in one badge, however it reached the list. */
    @Test
    fun dropsADuplicateOfThePlannedRoute() {
        val badge = legBadge(RouteBadge("8", null), listOf(RouteBadge("8", null), RouteBadge("7", null)))

        assertEquals(listOf("7", "8"), badge.routes.map { it.shortName })
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

        assertEquals("8", badge.shortName)
    }

    /** No short name (some feeds only name a route long-form): fall back to the route/id, no color. */
    @Test
    fun legBadgeFallsBackToTheRouteIdWhenUnnamed() {
        val badge = TripLeg(mode = TripMode.BUS, routeId = "1_100").plannedBadge()

        assertEquals("1_100", badge.shortName)
        assertNull(badge.routeColor)
    }

    /** A leg's badge is its planned route joined by the routes ruled interchangeable with it. */
    @Test
    fun legBadgeJoinsTheLegsRouteWithItsInterchangeableOnes() {
        val leg = TripLeg(mode = TripMode.BUS, routeId = "1_100", routeShortName = "8")

        val badge = legBadge(leg, listOf(interchangeableRoute(shortName = "7")))

        assertEquals(listOf("7", "8"), badge.routes.map { it.shortName })
    }

    /** An interchangeable route badges the same way the planned one does; an unnamed one falls back
     *  to its route id, so a badge segment is never blank. */
    @Test
    fun interchangeableRouteBadgesItsDisplayName() {
        assertEquals("1 Line", interchangeableRoute(shortName = "1 Line").badge().shortName)
        assertEquals("40:100479", interchangeableRoute(shortName = null).badge().shortName)
    }

    private fun interchangeableRoute(shortName: String?) = InterchangeableRoute(
        routeId = "40:100479",
        shortName = shortName,
        routeColor = null,
        agencyId = "40:1",
        agencyName = "Sound Transit",
        headsign = "Federal Way Downtown"
    )
}
