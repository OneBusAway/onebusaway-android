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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripDetails
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.testing.testTripStatus
import org.onebusaway.android.time.ElapsedTime

/**
 * Unit tests for [RideSelectionController] — the wiring between the boarding stop's arrivals and the
 * vehicle set, which the pure rules in [RideQueueTest] don't reach: the identity guard that keeps the
 * decision off the 20 Hz sampler, the admitted set carried across polls, the continuation walk it
 * launches, and the resets each focus transition owes.
 *
 * The trip-observation cache enters as the two lambdas the controller calls, so no repository or map
 * host is needed. The continuation coroutine runs on an unconfined test dispatcher, so launching it
 * completes before the test body resumes and every assertion sees a settled state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RideSelectionControllerTest {

    private fun schedule(
        vararg stops: Pair<String, Double>,
        nextTripId: String? = null
    ): ObaTripSchedule = TripScheduleData(
        stops.map { (stopId, distance) -> StopTimeData(stopId = stopId, distanceAlongTrip = distance) }
            .toTypedArray<ObaTripSchedule.StopTime>(),
        nextTripId = nextTripId
    )

    private val ridden = schedule("board" to 100.0, "alight" to 1000.0)

    private fun ride(
        vararg segments: RouteFocusSegment,
        alightStopId: String? = "alight"
    ) = RideFocus("route_a", "Downtown", segments.toList(), alightStopId)

    private fun continuation(routeId: String) = RouteFocusSegment(routeId, anchorStopId = "seam", relationship = RouteFocusRelationship.STAY_ABOARD)

    private fun groups(vararg tripIds: String) = listOf(RideRouteGroup("route_a", "Downtown", tripIds.toList()))

    /** A poll reporting [tripIds], each on [routeId] with the given progress along its trip. */
    private fun poll(
        vararg tripIds: String,
        routeId: String = "route_a",
        progress: Double? = 0.0
    ): VehiclePoll = VehiclePoll(
        response = FakeRouteTrips(tripIds.toList(), routeId, progress),
        loadTime = ElapsedTime(0L)
    )

    private class Harness {
        val states = mutableMapOf<String, TripState>()
        val neighbourRoutes = mutableMapOf<String, String>()
        var neighbourLookups = 0
        var republishes = 0
        val scope = TestScope(UnconfinedTestDispatcher())

        val controller = RideSelectionController(
            lookupTripState = { states[it] },
            neighbourRouteOf = {
                neighbourLookups++
                neighbourRoutes[it]
            },
            scope = scope,
            onSelectionChanged = { republishes++ }
        )

        fun visibleTripIds() = (controller.visibility as RideVisibility.Only).tripIds
    }

    private fun harness(vararg schedules: Pair<String, ObaTripSchedule>): Harness = Harness().apply {
        schedules.forEach { (tripId, s) -> states[tripId] = TripState(tripId = tripId, schedule = s) }
    }

    // -- selection over the queue --

    @Test
    fun `a queued trip that is running is drawn, and one that is not queued is not`() {
        val h = harness("t1" to ridden, "t2" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())

        h.controller.refresh(ride(), "route_a", poll("t1", "t2"), emptyMap())

        assertEquals(setOf("t1"), h.visibleTripIds())
    }

    @Test
    fun `a plain route view selects nothing and draws every vehicle`() {
        // The null ride is what a non-directions route focus passes; without it the Pending branch
        // would render seed-only and hide the whole route.
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)

        h.controller.refresh(ride = null, "route_a", poll("t1", "t2"), emptyMap())

        assertEquals(RideVisibility.All, h.controller.visibility)
    }

    @Test
    fun `the tapped pill is drawn before the first arrivals load lands`() {
        val h = harness("tapped" to ridden)
        h.controller.start(focusTripId = "tapped")

        h.controller.refresh(ride(), "route_a", poll("tapped", "other"), emptyMap())

        assertEquals(setOf("tapped"), h.visibleTripIds())
    }

    @Test
    fun `a stop that serves none of the ride's routes draws everything`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(listOf(RideRouteGroup("route_z", "Elsewhere", listOf("zz"))), ride())

        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        assertEquals(RideVisibility.All, h.controller.visibility)
    }

    // -- the admitted set across polls --

    @Test
    fun `a trip stays drawn after it drops out of the queue, until its trip ends`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        // The vehicle passed the boarding stop, so its arrival left the list — but the rider is aboard.
        h.controller.setArrivals(groups(), ride())
        h.controller.refresh(ride(), "route_a", poll("t1", progress = 500.0), emptyMap())
        assertEquals(setOf("t1"), h.visibleTripIds())

        // Past the alighting stop: the ride is provably over.
        h.controller.setArrivals(groups(), ride())
        h.controller.refresh(ride(), "route_a", poll("t1", progress = 1500.0), emptyMap())
        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `a trip that stops running is forgotten`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        h.controller.setArrivals(groups(), ride())
        h.controller.refresh(ride(), "route_a", poll(), emptyMap())

        assertTrue(h.visibleTripIds().isEmpty())
    }

    // -- the guard that keeps this off the frame loop --

    @Test
    fun `re-running against the same poll and queue does no work`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride(continuation("route_b")))
        val samePoll = poll("t1")

        h.controller.refresh(ride(continuation("route_b")), "route_a", samePoll, emptyMap())
        val afterFirst = h.controller.visibleTrips
        val lookupsAfterFirst = h.neighbourLookups

        repeat(20) { h.controller.refresh(ride(continuation("route_b")), "route_a", samePoll, emptyMap()) }

        // Same instance, not merely an equal one: the guard returned before rebuilding anything.
        assertSame(afterFirst, h.controller.visibleTrips)
        assertEquals(lookupsAfterFirst, h.neighbourLookups)
    }

    @Test
    fun `tapping a pill admits it against the poll already in hand`() {
        // The pill tap seeds and then rebuilds the vehicle set in the same breath, with no new poll in
        // between. If the guard ignored the seed, the layer handed to the camera would not contain the
        // tapped vehicle, and the focus would drop silently (#1992) — the vehicle then appearing on its
        // own one poll later. Observed on device: an E Line pill with a "vehicle on map" pin whose tap
        // went nowhere and resolved itself unaided.
        val h = harness("t1" to ridden, "tapped" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        val landed = poll("t1", "tapped")
        h.controller.refresh(ride(), "route_a", landed, emptyMap())
        assertEquals(setOf("t1"), h.visibleTripIds())

        h.controller.seed("tapped")
        h.controller.refresh(ride(), "route_a", landed, emptyMap())

        assertTrue("tapped" in h.visibleTripIds())
    }

    @Test
    fun `abandoning the pill focus takes effect against the poll already in hand`() {
        val h = harness("tapped" to ridden)
        h.controller.start(focusTripId = "tapped")
        val landed = poll("tapped")
        h.controller.refresh(ride(), "route_a", landed, emptyMap())
        assertEquals(setOf("tapped"), h.visibleTripIds())

        h.controller.clearSeed()
        h.controller.refresh(ride(), "route_a", landed, emptyMap())

        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `a landed poll re-runs the selection`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())
        val afterFirst = h.controller.visibleTrips

        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        assertEquals(setOf("t1"), h.visibleTripIds())
        assertTrue(afterFirst !== h.controller.visibleTrips)
    }

    // -- the continuation walk --

    @Test
    fun `a stay-aboard ride admits the continuation its vehicle rolls onto`() = runTest {
        val h = harness("t1" to schedule("board" to 100.0, nextTripId = "t2"))
        h.neighbourRoutes["t2"] = "route_b"
        val interlined = ride(continuation("route_b"))
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), interlined)

        h.controller.refresh(interlined, "route_a", poll("t1"), emptyMap())

        assertTrue("t2" in h.visibleTripIds() || h.republishes > 0)
        // The walk republishes so the newly-admitted continuation reaches the renderer.
        h.controller.refresh(interlined, "route_a", poll("t1", "t2"), emptyMap())
        assertTrue(h.visibleTripIds().containsAll(setOf("t1", "t2")))
    }

    @Test
    fun `a ride with no planned continuation never walks the block`() {
        // The regression guard: a block is a service day, so an unbounded walk would admit a vehicle's
        // whole remaining day.
        val h = harness("t1" to schedule("board" to 100.0, nextTripId = "t2"))
        h.neighbourRoutes["t2"] = "route_a"
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())

        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        assertEquals(0, h.neighbourLookups)
        assertEquals(setOf("t1"), h.visibleTripIds())
    }

    // -- focus transitions --

    @Test
    fun `moving to another ride drops what the old one admitted`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())
        assertEquals(setOf("t1"), h.visibleTripIds())

        h.controller.rideChanged()
        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        // Back to Pending with no seed: nothing is admitted until the new stop's arrivals land.
        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `a direction switch abandons the pill focus but keeps the ride`() {
        val h = harness("tapped" to ridden)
        h.controller.start(focusTripId = "tapped")
        h.controller.clearSeed()

        h.controller.refresh(ride(), "route_a", poll("tapped"), emptyMap())

        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `leaving route mode forgets the ride`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.controller.refresh(ride(), "route_a", poll("t1"), emptyMap())

        h.controller.stop()

        assertEquals(RideVisibility.All, h.controller.visibility)
        assertTrue(h.controller.visibleTrips.isEmpty())
    }

    // -- what the drawn approach reads --

    @Test
    fun `visibleTrips carries the selected trips' cached schedules`() {
        val h = harness("t1" to ridden, "t2" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())

        h.controller.refresh(ride(), "route_a", poll("t1", "t2"), emptyMap())

        assertEquals(listOf("t1"), h.controller.visibleTrips.map { it.tripId })
        assertSame(ridden, h.controller.visibleTrips.single().schedule)
    }
}

/** A trips-for-route response reporting [tripIds] on [routeId], each with the same progress. */
private class FakeRouteTrips(
    tripIds: List<String>,
    private val routeId: String,
    progress: Double?
) : RouteTrips {
    override val trips: List<ObaTripDetails> = tripIds.map { tripId ->
        FakeTripDetails(tripId, testTripStatus(activeTripId = tripId, distanceAlongTrip = progress))
    }

    override fun trip(tripId: String?): ObaTrip? = tripId?.let { FakeTrip(it, routeId) }

    override fun route(routeId: String): ObaRoute? = null

    override val currentTimeMs: Long = 0L
}

private class FakeTripDetails(
    override val id: String,
    override val status: ObaTripStatus?
) : ObaTripDetails {
    override val schedule: ObaTripSchedule? = null
}

private class FakeTrip(override val id: String, override val routeId: String) : ObaTrip {
    override val shortName: String? = null
    override val shapeId: String = "shape-$id"
    override val directionId: Int = 0
    override val serviceId: String? = null
    override val headsign: String? = null
    override val timezone: String? = null
    override val blockId: String? = null
}
