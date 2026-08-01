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
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.models.ObaTripSchedule

/** JVM tests for the symbolic route-focus vehicle filter (#2124) — see [rideEligibility]. */
class RideEligibilityTest {

    /** A trip serving each (stopId, distanceAlongTrip) in order. */
    private fun schedule(vararg stops: Pair<String, Double>): ObaTripSchedule = TripScheduleData(
        stops.map { (stopId, distance) ->
            StopTimeData(stopId = stopId, distanceAlongTrip = distance)
        }.toTypedArray<ObaTripSchedule.StopTime>()
    )

    private val trip = schedule("board" to 100.0, "mid" to 500.0, "alight" to 1000.0)

    private fun bound(stopId: String?, restrictive: Boolean = true) = RideBound(stopId, restrictive)

    // -- rideBoundEligibility: one trip against one end-of-ride bound --

    @Test
    fun tripServingTheBoundingStopAheadOfTheVehicleIsEligible() {
        assertEquals(RideEligibility.ELIGIBLE, rideBoundEligibility(trip, 400.0, bound("alight")))
    }

    @Test
    fun vehicleStandingExactlyAtTheBoundingStopIsStillEligible() {
        // <= : a vehicle at the alighting stop still carries the rider, matching the geometric bound.
        assertEquals(RideEligibility.ELIGIBLE, rideBoundEligibility(trip, 1000.0, bound("alight")))
    }

    @Test
    fun vehiclePastTheBoundingStopIsIneligible() {
        assertEquals(RideEligibility.INELIGIBLE, rideBoundEligibility(trip, 1000.1, bound("alight")))
    }

    @Test
    fun notServingARestrictiveBoundIsIneligible_evenWithoutVehicleProgress() {
        // Absence from the stop sequence needs no progress, so it must not degrade to UNKNOWN. This
        // is the short-turn (and reverse-direction) rejection geometry could never make.
        assertEquals(RideEligibility.INELIGIBLE, rideBoundEligibility(trip, null, bound("elsewhere")))
    }

    @Test
    fun notServingAnInterchangeableBoundIsUnknown_notARejection() {
        // A parallel route may alight at a different platform's stop id, so "doesn't serve this
        // exact id" leaves the verdict to the geometric fallback.
        assertEquals(
            RideEligibility.UNKNOWN,
            rideBoundEligibility(trip, 400.0, bound("elsewhere", restrictive = false))
        )
    }

    @Test
    fun missingScheduleIsUnknown() {
        assertEquals(RideEligibility.UNKNOWN, rideBoundEligibility(null, 400.0, bound("alight")))
    }

    @Test
    fun missingVehicleProgressIsUnknownWhenTheStopIsServed() {
        assertEquals(RideEligibility.UNKNOWN, rideBoundEligibility(trip, null, bound("alight")))
    }

    @Test
    fun stopServedTwiceIsUnknown() {
        // A loop/out-and-back has no single "the rider's alighting" — refuse rather than guess.
        val loop = schedule("board" to 0.0, "alight" to 500.0, "alight" to 900.0)
        assertEquals(RideEligibility.UNKNOWN, rideBoundEligibility(loop, 100.0, bound("alight")))
    }

    @Test
    fun nullBoundingStopIsUnknown() {
        assertEquals(RideEligibility.UNKNOWN, rideBoundEligibility(trip, 400.0, bound(null)))
    }

    // -- rideEligibility: combining a route's bounds --

    @Test
    fun anyEligibleBoundKeepsTheTripOverPassedAndUnknownSiblings() {
        val bounds = listOf(
            RideBound("board", restrictive = true), // passed
            RideBound(null, restrictive = true), // unknown
            RideBound("alight", restrictive = true) // eligible
        )
        assertEquals(RideEligibility.ELIGIBLE, rideEligibility(trip, 400.0, bounds))
    }

    @Test
    fun everyBoundDecidedAgainstTheTripIsIneligible() {
        val bounds = listOf(
            RideBound("board", restrictive = true), // passed at 400 m
            RideBound("elsewhere", restrictive = true) // restrictively not served
        )
        assertEquals(RideEligibility.INELIGIBLE, rideEligibility(trip, 400.0, bounds))
    }

    @Test
    fun anyUndecidedBoundBlocksARejection() {
        val bounds = listOf(
            RideBound("board", restrictive = true), // passed
            RideBound(null, restrictive = true) // unknown — an unresolved seam/alight stop id
        )
        assertEquals(RideEligibility.UNKNOWN, rideEligibility(trip, 400.0, bounds))
    }

    @Test
    fun noBoundsIsUnknown() {
        assertEquals(RideEligibility.UNKNOWN, rideEligibility(trip, 400.0, emptyList()))
    }

    @Test
    fun missingScheduleVerdictIsRecomputedAfterBackfill() {
        val memo = HashMap<String, RideEligibility>()
        val bounds = listOf(bound("alight"))

        assertEquals(
            RideEligibility.UNKNOWN,
            memoizedRideEligibility(memo, "trip", null, 400.0, bounds)
        )
        assertEquals(emptyMap<String, RideEligibility>(), memo)

        assertEquals(
            RideEligibility.ELIGIBLE,
            memoizedRideEligibility(memo, "trip", trip, 400.0, bounds)
        )
        assertEquals(mapOf("trip" to RideEligibility.ELIGIBLE), memo)
    }

    // -- rideBoundsByRoute: deriving each focused route's end-of-ride stops --

    @Test
    fun plainLegBoundsTheLeaderRestrictivelyAtItsEndStop() {
        assertEquals(
            mapOf("route_1" to listOf(RideBound("alight", restrictive = true))),
            rideBoundsByRoute("route_1", emptyList(), "alight")
        )
    }

    @Test
    fun interchangeableEtaLeaderGetsANonRestrictiveBound() {
        assertEquals(
            mapOf("route_9" to listOf(RideBound("planned-platform", restrictive = false))),
            rideBoundsByRoute(
                leaderRouteId = "route_9",
                extraSegments = emptyList(),
                leaderEndStopId = "planned-platform",
                leaderRestrictive = false
            )
        )
    }

    @Test
    fun everyRouteInAStayAboardChainIsBoundedWhereItsOwnPhaseEnds() {
        val segments = listOf(
            RouteFocusSegment("route_2", anchorStopId = "seam_1", endStopId = "seam_2"),
            RouteFocusSegment("route_3", anchorStopId = "seam_2", endStopId = "alight")
        )
        assertEquals(
            mapOf(
                "route_1" to listOf(RideBound("seam_1", restrictive = true)),
                "route_2" to listOf(RideBound("seam_2", restrictive = true)),
                "route_3" to listOf(RideBound("alight", restrictive = true))
            ),
            rideBoundsByRoute("route_1", segments, "seam_1")
        )
    }

    @Test
    fun aDroppedContinuationCostsOnlyItsOwnBound() {
        // The producer omits a leg whose route can't be resolved to an OBA id. Since every segment
        // states its own end, the surviving routes keep their true bounds — no shifted chain hands
        // one of them a stop two legs downstream that its trips never serve.
        val withoutTheMiddleLeg = listOf(RouteFocusSegment("route_3", anchorStopId = "seam_2", endStopId = "alight"))
        assertEquals(
            mapOf(
                "route_1" to listOf(RideBound("seam_1", restrictive = true)),
                "route_3" to listOf(RideBound("alight", restrictive = true))
            ),
            rideBoundsByRoute("route_1", withoutTheMiddleLeg, "seam_1")
        )
    }

    @Test
    fun selfInterlineAccumulatesBothPhaseBoundsUnderOneRouteId() {
        // The same route continues onto itself (its other direction): the rider leaves the first
        // phase at the seam and the second at the alighting stop — one route id, both bounds.
        val segments = listOf(RouteFocusSegment("route_1", anchorStopId = "seam", endStopId = "alight"))
        assertEquals(
            mapOf(
                "route_1" to listOf(
                    RideBound("seam", restrictive = true),
                    RideBound("alight", restrictive = true)
                )
            ),
            rideBoundsByRoute("route_1", segments, "seam")
        )
    }

    @Test
    fun interchangeableRoutesGetANonRestrictiveBound() {
        val segments = listOf(
            RouteFocusSegment(
                "route_9",
                anchorStopId = "board",
                relationship = RouteFocusRelationship.INTERCHANGEABLE,
                endStopId = "alight"
            )
        )
        assertEquals(
            mapOf(
                "route_1" to listOf(RideBound("alight", restrictive = true)),
                "route_9" to listOf(RideBound("alight", restrictive = false))
            ),
            rideBoundsByRoute("route_1", segments, "alight")
        )
    }

    @Test
    fun unresolvedEndStopLeavesNullBounds() {
        // An OTP→OBA id-resolution failure flows through as a null bound (→ UNKNOWN → geometric
        // fallback), never a guess.
        assertEquals(
            mapOf("route_1" to listOf(RideBound(null, restrictive = true))),
            rideBoundsByRoute("route_1", emptyList(), null)
        )
    }
}
