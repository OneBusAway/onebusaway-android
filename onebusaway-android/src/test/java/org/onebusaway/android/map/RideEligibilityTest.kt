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

    // -- rideBoundVerdict: one trip against one bounding stop --

    @Test
    fun tripServingTheBoundingStopAheadOfTheVehicleIsEligible() {
        assertEquals(RideBoundVerdict.ELIGIBLE, rideBoundVerdict(trip, 400.0, "alight"))
    }

    @Test
    fun vehicleStandingExactlyAtTheBoundingStopIsStillEligible() {
        // <= : a vehicle at the alighting stop still carries the rider, matching the geometric bound.
        assertEquals(RideBoundVerdict.ELIGIBLE, rideBoundVerdict(trip, 1000.0, "alight"))
    }

    @Test
    fun vehiclePastTheBoundingStopHasPassed() {
        assertEquals(RideBoundVerdict.PASSED, rideBoundVerdict(trip, 1000.1, "alight"))
    }

    @Test
    fun tripNotServingTheBoundingStopIsNotServed_evenWithoutVehicleProgress() {
        // Absence from the stop sequence needs no progress, so it must not degrade to UNKNOWN.
        assertEquals(RideBoundVerdict.NOT_SERVED, rideBoundVerdict(trip, null, "elsewhere"))
    }

    @Test
    fun missingScheduleIsUnknown() {
        assertEquals(RideBoundVerdict.UNKNOWN, rideBoundVerdict(null, 400.0, "alight"))
    }

    @Test
    fun missingVehicleProgressIsUnknownWhenTheStopIsServed() {
        assertEquals(RideBoundVerdict.UNKNOWN, rideBoundVerdict(trip, null, "alight"))
    }

    @Test
    fun stopServedTwiceIsUnknown() {
        // A loop/out-and-back has no single "the rider's alighting" — refuse rather than guess.
        val loop = schedule("board" to 0.0, "alight" to 500.0, "alight" to 900.0)
        assertEquals(RideBoundVerdict.UNKNOWN, rideBoundVerdict(loop, 100.0, "alight"))
    }

    @Test
    fun nullBoundingStopIsUnknown() {
        assertEquals(RideBoundVerdict.UNKNOWN, rideBoundVerdict(trip, 400.0, null))
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
    fun notServingAnInterchangeableBoundIsUnknown_notARejection() {
        // A parallel route may alight at a different platform's stop id, so "doesn't serve this
        // exact id" leaves the verdict to the geometric fallback.
        val bounds = listOf(RideBound("elsewhere", restrictive = false))
        assertEquals(RideEligibility.UNKNOWN, rideEligibility(trip, 400.0, bounds))
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

    // -- rideBoundsByRoute: deriving each focused route's end-of-ride stops --

    @Test
    fun plainLegBoundsTheLeaderRestrictivelyAtTheAlightingStop() {
        assertEquals(
            mapOf("route_1" to listOf(RideBound("alight", restrictive = true))),
            rideBoundsByRoute("route_1", emptyList(), "alight")
        )
    }

    @Test
    fun stayAboardChainBoundsEachRouteAtTheNextSeam_andTheLastAtTheAlightingStop() {
        val segments = listOf(
            RouteFocusSegment("route_2", anchorStopId = "seam_1"),
            RouteFocusSegment("route_3", anchorStopId = "seam_2")
        )
        assertEquals(
            mapOf(
                "route_1" to listOf(RideBound("seam_1", restrictive = true)),
                "route_2" to listOf(RideBound("seam_2", restrictive = true)),
                "route_3" to listOf(RideBound("alight", restrictive = true))
            ),
            rideBoundsByRoute("route_1", segments, "alight")
        )
    }

    @Test
    fun selfInterlineAccumulatesSeamAndAlightBoundsUnderOneRouteId() {
        // The same route continues onto itself (its other direction): the rider leaves the first
        // phase at the seam and the second at the alighting stop — one route id, both bounds.
        val segments = listOf(RouteFocusSegment("route_1", anchorStopId = "seam"))
        assertEquals(
            mapOf(
                "route_1" to listOf(
                    RideBound("seam", restrictive = true),
                    RideBound("alight", restrictive = true)
                )
            ),
            rideBoundsByRoute("route_1", segments, "alight")
        )
    }

    @Test
    fun interchangeableRoutesGetANonRestrictiveAlightBound() {
        val segments = listOf(
            RouteFocusSegment(
                "route_9",
                anchorStopId = "board",
                relationship = RouteFocusRelationship.INTERCHANGEABLE
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
    fun unresolvedAlightingStopLeavesNullBounds() {
        // An OTP→OBA id-resolution failure flows through as a null bound (→ UNKNOWN → geometric
        // fallback), never a guess.
        assertEquals(
            mapOf("route_1" to listOf(RideBound(null, restrictive = true))),
            rideBoundsByRoute("route_1", emptyList(), null)
        )
    }
}
