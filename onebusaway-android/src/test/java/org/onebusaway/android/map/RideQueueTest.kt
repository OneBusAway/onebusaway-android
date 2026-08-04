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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.models.ObaTripSchedule

/** JVM tests for queue-driven ride vehicle selection (#2124) — see [RideQueue.kt]. */
class RideQueueTest {

    private fun schedule(
        vararg stops: Pair<String, Double>,
        nextTripId: String? = null
    ): ObaTripSchedule = TripScheduleData(
        stops.map { (stopId, distance) ->
            StopTimeData(stopId = stopId, distanceAlongTrip = distance)
        }.toTypedArray<ObaTripSchedule.StopTime>(),
        nextTripId = nextTripId
    )

    private val ridden = schedule("board" to 100.0, "mid" to 500.0, "alight" to 1000.0)

    private fun trip(
        tripId: String,
        routeId: String = "route_a",
        schedule: ObaTripSchedule? = ridden,
        distanceAlongTrip: Double? = null
    ) = RideTrip(
        tripId = tripId,
        routeId = routeId,
        shapeId = "shape",
        schedule = schedule,
        shape = null,
        distanceAlongTrip = distanceAlongTrip
    )

    private fun group(
        routeId: String = "route_a",
        headsign: String? = "Downtown",
        vararg tripIds: String
    ) = RideRouteGroup(routeId, headsign, tripIds.toList())

    /** A plain leg on route_a: no continuations, alighting at "alight". */
    private fun ride(
        vararg segments: RouteFocusSegment,
        headsign: String? = "Downtown",
        alightStopId: String? = "alight"
    ) = RideFocus("route_a", headsign, segments.toList(), alightStopId)

    private fun alternative(routeId: String, headsign: String? = "Downtown") = RouteFocusSegment(routeId, anchorStopId = "board", relationship = RouteFocusRelationship.INTERCHANGEABLE, directionHeadsign = headsign)

    private fun continuation(routeId: String) = RouteFocusSegment(routeId, anchorStopId = "seam", relationship = RouteFocusRelationship.STAY_ABOARD)

    private fun candidates(queue: RideQueue) = (queue as RideQueue.Known).tripIds

    // -- rideQueueFrom: the stop's arrival rows become the ride's candidates --

    @Test
    fun plannedRouteContributesItsMatchingDirectionGroup() {
        val queue = rideQueueFrom(
            listOf(group(tripIds = arrayOf("t1", "t2"))),
            ride()
        )
        assertEquals(listOf("t1", "t2"), candidates(queue))
    }

    @Test
    fun theOppositeDirectionGroupOfTheSameRouteIsNotACandidate() {
        val queue = rideQueueFrom(
            listOf(
                group(headsign = "Northgate", tripIds = arrayOf("outbound")),
                group(headsign = "Downtown", tripIds = arrayOf("inbound"))
            ),
            ride()
        )
        assertEquals(listOf("inbound"), candidates(queue))
    }

    @Test
    fun anUnmatchedHeadsignFallsBackToTheRoutesFirstGroup() {
        val queue = rideQueueFrom(
            listOf(group(headsign = "Somewhere Else", tripIds = arrayOf("t1"))),
            ride()
        )
        assertEquals(listOf("t1"), candidates(queue))
    }

    @Test
    fun interchangeableAlternativesContributeTheirOwnDepartures() {
        val queue = rideQueueFrom(
            listOf(group(tripIds = arrayOf("t1")), group(routeId = "route_b", tripIds = arrayOf("alt1"))),
            ride(alternative("route_b"))
        )
        assertEquals(listOf("t1", "alt1"), candidates(queue))
    }

    @Test
    fun aRouteListedTwiceContributesEachDepartureOnce() {
        val queue = rideQueueFrom(
            listOf(group(tripIds = arrayOf("t1"))),
            ride(alternative("route_a"))
        )
        assertEquals(listOf("t1"), candidates(queue))
    }

    @Test
    fun aStopServingNoneOfTheRidesRoutesIsUnservedRatherThanEmpty() {
        // The distinction the map depends on: Unserved draws everything, an empty Known draws nothing.
        val queue = rideQueueFrom(
            listOf(group(routeId = "route_z", tripIds = arrayOf("other"))),
            ride()
        )
        assertEquals(RideQueue.Unserved, queue)
    }

    @Test
    fun aRouteWithAGroupButNoUpcomingDeparturesIsServedAndEmpty() {
        // Served-but-empty must stay Known: the stop can answer, it just has nothing right now.
        val queue = rideQueueFrom(listOf(group()), ride())
        assertEquals(RideQueue.Known(emptyList()), queue)
    }

    // -- rideContinuations: bounded by the plan, not by the block --

    private suspend fun continuations(
        seed: Set<String>,
        hops: Int,
        schedules: Map<String, ObaTripSchedule>,
        routes: Map<String, String>
    ) = rideContinuations(
        seed = seed,
        // The walk's depth is the ride's own stay-aboard count, so build a ride with that many.
        ride = ride(*Array(hops) { continuation("route_b") }),
        scheduleOf = { schedules[it] },
        neighbourRouteOf = { routes[it] }
    )

    @Test
    fun oneHopAdmitsTheStayAboardContinuation() = runTest {
        val found = continuations(
            seed = setOf("t1"),
            hops = 1,
            schedules = mapOf("t1" to schedule("board" to 0.0, nextTripId = "t2")),
            routes = mapOf("t2" to "route_b")
        )
        assertEquals(setOf("t2"), found)
    }

    @Test
    fun aLegWithNoPlannedContinuationFollowsNothingAtAll() = runTest {
        // The regression guard against admitting a vehicle's whole service day: KCM block 1_8128824
        // chains eleven route-40 trips, and every one of them has a next trip.
        val found = continuations(
            seed = setOf("t1"),
            hops = 0,
            schedules = mapOf("t1" to schedule("board" to 0.0, nextTripId = "t2")),
            routes = mapOf("t2" to "route_a")
        )
        assertEquals(emptySet<String>(), found)
    }

    @Test
    fun aSelfInterlineContinuationOnTheSameRouteIsAdmitted() = runTest {
        val found = continuations(
            seed = setOf("t1"),
            hops = 1,
            schedules = mapOf("t1" to schedule("board" to 0.0, nextTripId = "t2")),
            routes = mapOf("t2" to "route_a")
        )
        assertEquals(setOf("t2"), found)
    }

    @Test
    fun aNeighbourOnARouteTheRideIsNotTravelledOnIsRefused() = runTest {
        val found = continuations(
            seed = setOf("t1"),
            hops = 1,
            schedules = mapOf("t1" to schedule("board" to 0.0, nextTripId = "t2")),
            routes = mapOf("t2" to "route_z")
        )
        assertEquals(emptySet<String>(), found)
    }

    @Test
    fun aBlockEndTerminatesTheWalk() = runTest {
        // The last trip of a block has no next one. OBA spells that ""; it is blanked to null at the
        // wire boundary (#2003, TripAdapters.toObaTripSchedule), so it reaches here as a plain null.
        val found = continuations(
            seed = setOf("t1"),
            hops = 2,
            schedules = mapOf("t1" to schedule("board" to 0.0, nextTripId = null)),
            routes = emptyMap()
        )
        assertEquals(emptySet<String>(), found)
    }

    @Test
    fun aTwoContinuationChainResolvesExactlyItsTwoHops() = runTest {
        val found = continuations(
            seed = setOf("t1"),
            hops = 2,
            schedules = mapOf(
                "t1" to schedule("board" to 0.0, nextTripId = "t2"),
                "t2" to schedule("board" to 0.0, nextTripId = "t3"),
                "t3" to schedule("board" to 0.0, nextTripId = "t4")
            ),
            routes = mapOf("t2" to "route_b", "t3" to "route_a", "t4" to "route_b")
        )
        assertEquals(setOf("t2", "t3"), found)
    }

    @Test
    fun aBlockThatLinksBackOnItselfCannotLoopForever() = runTest {
        val found = continuations(
            seed = setOf("t1"),
            hops = 5,
            schedules = mapOf(
                "t1" to schedule("board" to 0.0, nextTripId = "t2"),
                "t2" to schedule("board" to 0.0, nextTripId = "t1")
            ),
            routes = mapOf("t1" to "route_a", "t2" to "route_b")
        )
        assertEquals(setOf("t2"), found)
    }

    // -- provablyPastAlight: one-sided, every unknown keeps the vehicle --

    @Test
    fun aVehiclePastTheAlightingStopIsProvablyPast() {
        assertTrue(provablyPastAlight(trip("t", distanceAlongTrip = 1200.0), "alight"))
    }

    @Test
    fun aVehicleStandingAtTheAlightingStopStillCarriesTheRider() {
        assertFalse(provablyPastAlight(trip("t", distanceAlongTrip = 1000.0), "alight"))
    }

    @Test
    fun anUnknownAlightingStopNeverRetiresAVehicle() {
        assertFalse(provablyPastAlight(trip("t", distanceAlongTrip = 1200.0), null))
    }

    @Test
    fun aTripWithNoBackfilledScheduleIsNotRetired() {
        assertFalse(provablyPastAlight(trip("t", schedule = null, distanceAlongTrip = 1200.0), "alight"))
    }

    @Test
    fun aTripThatNeverServesTheAlightingStopIsNotRetired() {
        // The leading trip of an interlined ride alights on its continuation; a short-turn never gets there.
        assertFalse(provablyPastAlight(trip("t", distanceAlongTrip = 1200.0), "somewhere_else"))
    }

    @Test
    fun aStopTheTripServesTwiceCannotRetireIt() {
        val loop = schedule("alight" to 100.0, "mid" to 500.0, "alight" to 900.0)
        assertFalse(provablyPastAlight(trip("t", schedule = loop, distanceAlongTrip = 1200.0), "alight"))
    }

    @Test
    fun aVehicleReportingNoProgressIsNotRetired() {
        assertFalse(provablyPastAlight(trip("t", distanceAlongTrip = null), "alight"))
    }

    // -- rideSelection: admit, then retire --

    @Test
    fun aPendingQueueShowsOnlyTheExplicitlyTappedTrip() {
        val selection = rideSelection(
            queue = RideQueue.Pending,
            seedTripIds = setOf("tapped"),
            previouslyAdmitted = emptySet(),
            pollTrips = listOf(trip("tapped"), trip("other")),
            continuationTripIds = emptySet(),
            ride = ride()
        )
        assertEquals(RideVisibility.Only(setOf("tapped")), selection.visibility)
    }

    @Test
    fun aStopThatCannotAnswerForTheRideDrawsEverything() {
        val selection = rideSelection(
            queue = RideQueue.Unserved,
            seedTripIds = emptySet(),
            previouslyAdmitted = emptySet(),
            pollTrips = listOf(trip("t1")),
            continuationTripIds = emptySet(),
            ride = ride()
        )
        assertEquals(RideVisibility.All, selection.visibility)
    }

    @Test
    fun aQueuedTripThatIsRunningIsDrawn() {
        val selection = rideSelection(
            queue = RideQueue.Known(listOf("t1")),
            seedTripIds = emptySet(),
            previouslyAdmitted = emptySet(),
            pollTrips = listOf(trip("t1"), trip("t2")),
            continuationTripIds = emptySet(),
            ride = ride()
        )
        assertEquals(RideVisibility.Only(setOf("t1")), selection.visibility)
    }

    @Test
    fun aTripThatHasLeftTheQueueButIsStillRunningStaysDrawn() {
        // The rider is aboard by the time the arrival drops off the board stop's list.
        val selection = rideSelection(
            queue = RideQueue.Known(emptyList()),
            seedTripIds = emptySet(),
            previouslyAdmitted = setOf("t1"),
            pollTrips = listOf(trip("t1", distanceAlongTrip = 600.0)),
            continuationTripIds = emptySet(),
            ride = ride()
        )
        assertEquals(RideVisibility.Only(setOf("t1")), selection.visibility)
    }

    @Test
    fun aTripThatHasFinishedRunningIsForgotten() {
        val selection = rideSelection(
            queue = RideQueue.Known(emptyList()),
            seedTripIds = emptySet(),
            previouslyAdmitted = setOf("t1"),
            pollTrips = emptyList(),
            continuationTripIds = emptySet(),
            ride = ride()
        )
        assertEquals(RideVisibility.Only(emptySet()), selection.visibility)
        assertEquals(emptySet<String>(), selection.admitted)
    }

    @Test
    fun aContinuationIsAdmittedBeforeItsFirstPollArrives() {
        val selection = rideSelection(
            queue = RideQueue.Known(listOf("t1")),
            seedTripIds = emptySet(),
            previouslyAdmitted = emptySet(),
            pollTrips = listOf(trip("t1")),
            continuationTripIds = setOf("t2"),
            ride = ride()
        )
        assertEquals(RideVisibility.Only(setOf("t1", "t2")), selection.visibility)
    }

    @Test
    fun retirementWinsOverAContinuationReAdmittingTheSameTrip() {
        val selection = rideSelection(
            queue = RideQueue.Known(emptyList()),
            seedTripIds = emptySet(),
            previouslyAdmitted = setOf("t1"),
            pollTrips = listOf(trip("t1", distanceAlongTrip = 1200.0)),
            continuationTripIds = setOf("t1"),
            ride = ride()
        )
        assertEquals(RideVisibility.Only(emptySet()), selection.visibility)
    }

    @Test
    fun theAdmittedSetCarriedForwardExcludesRetiredTrips() {
        val selection = rideSelection(
            queue = RideQueue.Known(listOf("t1")),
            seedTripIds = emptySet(),
            previouslyAdmitted = setOf("done"),
            pollTrips = listOf(trip("t1"), trip("done", distanceAlongTrip = 1200.0)),
            continuationTripIds = emptySet(),
            ride = ride()
        )
        assertEquals(setOf("t1"), selection.admitted)
    }
}
