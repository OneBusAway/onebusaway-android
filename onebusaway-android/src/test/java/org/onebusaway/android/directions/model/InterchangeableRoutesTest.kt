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
package org.onebusaway.android.directions.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * JVM tests for [interchangeableRoutes] — which of OTP's alternative departures the drawer may tell a
 * rider to board instead of the planned one (#2010).
 *
 * The rule under test: a candidate route qualifies when its ride time fits inside the planned leg's
 * ride time plus the wait the itinerary already budgets before the next transit leg. So a leg
 * followed by a slack transfer can offer a somewhat slower route, the last leg of a trip can't offer
 * any slower route at all, and a local never stands in for the express a plan is built around.
 */
class InterchangeableRoutesTest {

    private val t0 = ServerTime(0L)

    /** The motivating case: two lines sharing track between the same two platforms, same ride time. */
    @Test
    fun offersAnotherRouteOverTheSameStopsAtTheSameRideTime() {
        val leg = transitLeg(
            routeId = "40:1LINE",
            rideTime = 30.minutes,
            alternatives = listOf(alternative(routeId = "40:2LINE", shortName = "2 Line", rideTime = 30.minutes))
        )
        val routes = itinerary(leg).interchangeableRoutes()[0]

        assertEquals(listOf("2 Line"), routes.map { it.displayName })
    }

    /** Later trips of the leg's *own* route are alternatives to that departure, not to the route. */
    @Test
    fun ignoresLaterTripsOfTheLegsOwnRoute() {
        val leg = transitLeg(
            routeId = "40:1LINE",
            rideTime = 30.minutes,
            alternatives = listOf(alternative(routeId = "40:1LINE", shortName = "1 Line", rideTime = 30.minutes))
        )
        assertEquals(emptyList<String>(), itinerary(leg).interchangeableRoutes()[0].map { it.displayName })
    }

    /**
     * On the trip's last transit leg there is no transfer left to protect, so the slack is zero: a
     * route that rides even slightly longer than the planned one is not offered. (The 3rd Ave case
     * from #2010 — the 13 rides 254s where the planned D Line rides 244s.)
     */
    @Test
    fun lastTransitLegOffersOnlyRoutesNoSlowerThanThePlannedOne() {
        val leg = transitLeg(
            routeId = "1_DLINE",
            rideTime = 244.seconds,
            alternatives = listOf(
                alternative(routeId = "1_1", shortName = "1", rideTime = 238.seconds),
                alternative(routeId = "1_13", shortName = "13", rideTime = 254.seconds),
                alternative(routeId = "1_ELINE", shortName = "E Line", rideTime = 107.seconds)
            )
        )
        // A trailing walk leg doesn't make this any less the last *transit* leg.
        val plan = itinerary(leg, walkLeg(duration = 6.minutes))

        assertEquals(listOf("1", "E Line"), plan.interchangeableRoutes()[0].map { it.displayName })
    }

    /**
     * With a transfer downstream, the slack is the wait the plan budgets at the transfer point: the
     * next transit leg's departure, less this leg's arrival, less the transfer walk. Here the leg
     * arrives at 10:20, the rider walks 5 minutes, and the connection leaves at 10:33 — 8 minutes of
     * slack, so a route riding up to 8 minutes longer still makes it.
     */
    @Test
    fun transferSlackLetsASlowerRouteQualifyOnlyUpToTheConnection() {
        val leg = transitLeg(
            routeId = "1_100",
            startTime = ServerTime(minutesMs(0)),
            rideTime = 20.minutes,
            alternatives = listOf(
                alternative(routeId = "1_101", shortName = "101", rideTime = 28.minutes),
                alternative(routeId = "1_102", shortName = "102", rideTime = 29.minutes)
            )
        )
        val plan = itinerary(
            leg,
            walkLeg(duration = 5.minutes),
            transitLeg(routeId = "1_200", startTime = ServerTime(minutesMs(33)), rideTime = 10.minutes)
        )

        // 101 rides 8 minutes longer and still catches the connection; 102's 9 minutes misses it.
        assertEquals(listOf("101"), plan.interchangeableRoutes()[0].map { it.displayName })
    }

    /** A plan that leaves no wait at the transfer behaves like the last leg: no slower route. */
    @Test
    fun timedTransferWithNoWaitAllowsNoSlowerRoute() {
        val leg = transitLeg(
            routeId = "1_100",
            startTime = ServerTime(minutesMs(0)),
            rideTime = 20.minutes,
            alternatives = listOf(
                alternative(routeId = "1_101", shortName = "101", rideTime = 20.minutes),
                alternative(routeId = "1_102", shortName = "102", rideTime = 21.minutes)
            )
        )
        val plan = itinerary(
            leg,
            transitLeg(routeId = "1_200", startTime = ServerTime(minutesMs(20)), rideTime = 10.minutes)
        )

        assertEquals(listOf("101"), plan.interchangeableRoutes()[0].map { it.displayName })
    }

    /**
     * A route runs some fast trips and some slow ones; the rider boards whichever turns up, so the
     * route is judged — and described — by its slowest run.
     */
    @Test
    fun judgesARouteByItsSlowestCandidateTrip() {
        val leg = transitLeg(
            routeId = "1_100",
            rideTime = 20.minutes,
            alternatives = listOf(
                alternative(routeId = "1_101", shortName = "101", rideTime = 18.minutes),
                alternative(routeId = "1_101", shortName = "101", rideTime = 25.minutes)
            )
        )
        assertEquals(emptyList<String>(), itinerary(leg).interchangeableRoutes()[0].map { it.displayName })
    }

    /** A candidate that doesn't run between this leg's own two stops is dropped, not trusted. */
    @Test
    fun dropsCandidatesThatDontServeTheLegsStops() {
        val leg = transitLeg(
            routeId = "1_100",
            rideTime = 20.minutes,
            alternatives = listOf(
                alternative(routeId = "1_101", shortName = "101", rideTime = 15.minutes, toStopId = "1_999"),
                alternative(routeId = "1_102", shortName = "102", rideTime = 15.minutes, fromStopId = "1_888")
            )
        )
        assertEquals(emptyList<String>(), itinerary(leg).interchangeableRoutes()[0].map { it.displayName })
    }

    /** Names sort naturally ("8" before "40" before "550"), like the arrivals list's route rows. */
    @Test
    fun sortsQualifyingRoutesByNaturalRouteName() {
        val leg = transitLeg(
            routeId = "1_100",
            rideTime = 20.minutes,
            alternatives = listOf(
                alternative(routeId = "1_550", shortName = "550", rideTime = 15.minutes),
                alternative(routeId = "1_8", shortName = "8", rideTime = 15.minutes),
                alternative(routeId = "1_40", shortName = "40", rideTime = 15.minutes)
            )
        )
        assertEquals(listOf("8", "40", "550"), itinerary(leg).interchangeableRoutes()[0].map { it.displayName })
    }

    /** The result is index-aligned to the itinerary's legs, so walk legs hold their (empty) place. */
    @Test
    fun resultIsAlignedToTheLegList() {
        val plan = itinerary(
            walkLeg(duration = 2.minutes),
            transitLeg(
                routeId = "1_100",
                rideTime = 20.minutes,
                alternatives = listOf(alternative(routeId = "1_101", shortName = "101", rideTime = 20.minutes))
            ),
            walkLeg(duration = 2.minutes)
        )
        val routes = plan.interchangeableRoutes()

        assertEquals(3, routes.size)
        assertEquals(emptyList<String>(), routes[0].map { it.displayName })
        assertEquals(listOf("101"), routes[1].map { it.displayName })
        assertEquals(emptyList<String>(), routes[2].map { it.displayName })
    }

    /** An OTP1 plan carries no candidates at all — every leg comes back empty rather than erroring. */
    @Test
    fun legWithoutCandidatesHasNoAlternatives() {
        val leg = transitLeg(routeId = "1_100", rideTime = 20.minutes)
        assertEquals(emptyList<String>(), itinerary(leg).interchangeableRoutes()[0].map { it.displayName })
    }

    private fun itinerary(vararg legs: TripLeg) = TripItinerary(startTime = t0, legs = legs.toList())

    private fun transitLeg(
        routeId: String,
        rideTime: Duration,
        startTime: ServerTime = t0,
        alternatives: List<TripLegAlternative> = emptyList()
    ) = TripLeg(
        mode = TripMode.BUS,
        routeId = routeId,
        duration = rideTime,
        startTime = startTime,
        endTime = startTime + rideTime,
        from = TripPlace(name = "Board", stopId = BOARD_STOP),
        to = TripPlace(name = "Alight", stopId = ALIGHT_STOP),
        alternatives = alternatives
    )

    private fun walkLeg(duration: Duration) = TripLeg(mode = TripMode.WALK, duration = duration)

    private fun alternative(
        routeId: String,
        shortName: String,
        rideTime: Duration,
        fromStopId: String = BOARD_STOP,
        toStopId: String = ALIGHT_STOP
    ) = TripLegAlternative(
        routeId = routeId,
        routeShortName = shortName,
        duration = rideTime,
        fromStopId = fromStopId,
        toStopId = toStopId
    )

    private fun minutesMs(minutes: Long) = minutes * 60_000L

    private companion object {
        const val BOARD_STOP = "1_500"
        const val ALIGHT_STOP = "1_501"
    }
}
