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
 * How many times one poll may republish before the selection is declared not to settle. Any real pass
 * republishes at most a handful of times — the walk's seed can only grow while a poll stands — so this
 * is far above the useful range and exists only so a livelock reports instead of hanging the runner.
 */
private const val REPUBLISH_LIMIT = 50

/**
 * Unit tests for [RideSelectionController] — the wiring between the boarding stop's arrivals and the
 * vehicle set, which the pure rules in [RideQueueTest] don't reach: the identity guard that keeps the
 * decision off the 20 Hz sampler, the admitted set carried across polls, the continuation walk it
 * launches, and the resets each focus transition owes.
 *
 * The trip-observation cache enters as the two lambdas the controller calls, so no repository or map
 * host is needed. The continuation coroutine runs on an unconfined test dispatcher and [Harness.refresh]
 * drains it, so every assertion sees a settled state.
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

    /**
     * The controller wired the way `RouteMapController` wires it: `onSelectionChanged` is
     * `publishVehicleSet`, which re-samples and so lands back in [RideSelectionController.refresh]
     * against the poll already in hand (#2149). A counter-only callback cannot see a selection that
     * fails to *settle*, which is how #2206 — the continuation walk and the republish feeding each other
     * forever — shipped: the cycle closed across two files, and neither file's tests re-entered.
     *
     * So re-entry is the default here, and [refresh] bounds it: a selection that does not settle against
     * one poll fails the test that ran it instead of hanging the runner.
     */
    private class Harness {
        val states = mutableMapOf<String, TripState>()
        val neighbourRoutes = mutableMapOf<String, String>()
        var neighbourLookups = 0
        var republishes = 0
        val scope = TestScope(UnconfinedTestDispatcher())

        /** The pass a republish replays, the way `publishVehicleSet` re-samples the poll it holds. */
        private var lastPass: (() -> Unit)? = null
        private var passes = 0

        val controller = RideSelectionController(
            lookupTripState = { states[it] },
            neighbourRouteOf = {
                neighbourLookups++
                neighbourRoutes[it]
            },
            scope = scope,
            onSelectionChanged = {
                republishes++
                // Stop *inside* the loop rather than assert here: this runs on the walk's coroutine,
                // where a thrown AssertionError would be routed to the scope's handler instead of
                // failing the test. [refresh] asserts on the test thread once the pass has settled.
                if (++passes < REPUBLISH_LIMIT) lastPass?.invoke()
            }
        )

        /**
         * One selection pass, then settle the walk it launched: the walk hands its republish back to the
         * event loop before reporting, so the scheduler is drained here rather than leaving a test to
         * assert against a half-resolved state.
         */
        fun refresh(
            ride: RideFocus?,
            poll: VehiclePoll,
            extras: Map<String, VehiclePoll> = emptyMap()
        ) {
            val pass = { controller.refresh(ride, "route_a", poll, extras) }
            lastPass = pass
            passes = 0
            pass()
            scope.testScheduler.runCurrent()
            assertTrue(
                "the selection republished $passes times against one poll without settling",
                passes < REPUBLISH_LIMIT
            )
        }

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

        h.refresh(ride(), poll("t1", "t2"))

        assertEquals(setOf("t1"), h.visibleTripIds())
    }

    @Test
    fun `a plain route view selects nothing and draws every vehicle`() {
        // The null ride is what a non-directions route focus passes; without it the Pending branch
        // would render seed-only and hide the whole route.
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)

        h.refresh(ride = null, poll("t1", "t2"))

        assertEquals(RideVisibility.All, h.controller.visibility)
    }

    @Test
    fun `the tapped pill is drawn before the first arrivals load lands`() {
        val h = harness("tapped" to ridden)
        h.controller.start(focusTripId = "tapped")

        h.refresh(ride(), poll("tapped", "other"))

        assertEquals(setOf("tapped"), h.visibleTripIds())
    }

    @Test
    fun `a stop that serves none of the ride's routes draws everything`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(listOf(RideRouteGroup("route_z", "Elsewhere", listOf("zz"))), ride())

        h.refresh(ride(), poll("t1"))

        assertEquals(RideVisibility.All, h.controller.visibility)
    }

    // -- the admitted set across polls --

    @Test
    fun `a trip stays drawn after it drops out of the queue, until its trip ends`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.refresh(ride(), poll("t1"))

        // The vehicle passed the boarding stop, so its arrival left the list — but the rider is aboard.
        h.controller.setArrivals(groups(), ride())
        h.refresh(ride(), poll("t1", progress = 500.0))
        assertEquals(setOf("t1"), h.visibleTripIds())

        // Past the alighting stop: the ride is provably over.
        h.controller.setArrivals(groups(), ride())
        h.refresh(ride(), poll("t1", progress = 1500.0))
        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `a trip that stops running is forgotten`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.refresh(ride(), poll("t1"))

        h.controller.setArrivals(groups(), ride())
        h.refresh(ride(), poll())

        assertTrue(h.visibleTripIds().isEmpty())
    }

    // -- the guard that keeps this off the frame loop --

    @Test
    fun `re-running against the same poll and queue does no work`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride(continuation("route_b")))
        val samePoll = poll("t1")

        h.refresh(ride(continuation("route_b")), samePoll)
        val afterFirst = h.controller.visibleTrips
        val lookupsAfterFirst = h.neighbourLookups

        repeat(20) { h.refresh(ride(continuation("route_b")), samePoll) }

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
        h.refresh(ride(), landed)
        assertEquals(setOf("t1"), h.visibleTripIds())

        h.controller.seed("tapped")
        h.refresh(ride(), landed)

        assertTrue("tapped" in h.visibleTripIds())
    }

    @Test
    fun `abandoning the pill focus takes effect against the poll already in hand`() {
        val h = harness("tapped" to ridden)
        h.controller.start(focusTripId = "tapped")
        val landed = poll("tapped")
        h.refresh(ride(), landed)
        assertEquals(setOf("tapped"), h.visibleTripIds())

        h.controller.clearSeed()
        h.refresh(ride(), landed)

        assertTrue(h.visibleTripIds().isEmpty())
    }

    /**
     * A tripwire, not a behaviour test. Each input the selection reads has an invalidation test in this
     * section; adding a seventh without one is how the two shipped instances of this bug happened, so
     * a new field fails here until it is covered.
     */
    @Test
    fun `every guarded input has an invalidation test`() {
        // Compose's compiler adds a synthetic `$stable`; the declared inputs are the rest.
        val inputs = RideSelectionInputs::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .sorted()
        assertEquals(
            "A new selection input needs an invalidation test alongside the others in this section",
            listOf("continuations", "extras", "poll", "queue", "ride", "seed"),
            inputs
        )
    }

    @Test
    fun `a resolved continuation is admitted against the poll already in hand`() {
        // The continuation walk lands asynchronously and republishes; if the guard ignored it, the
        // interline vehicle would wait a whole poll to be drawn.
        val h = harness("t1" to schedule("board" to 100.0, nextTripId = "t2"), "t2" to ridden)
        h.neighbourRoutes["t2"] = "route_b"
        val interlined = ride(continuation("route_b"))
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), interlined)
        val landed = poll("t1", "t2")

        h.refresh(interlined, landed)

        // The walk ran during that pass and republished; the republish re-enters refresh against the
        // same poll, and must not be turned away.
        h.refresh(interlined, landed)
        assertTrue(h.visibleTripIds().containsAll(setOf("t1", "t2")))
    }

    @Test
    fun `a changed ride re-runs the selection against the poll already in hand`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        val landed = poll("t1")
        h.refresh(ride(), landed)
        assertEquals(setOf("t1"), h.visibleTripIds())

        // Same poll and queue, but the rider now alights where this trip has already passed.
        h.refresh(ride(alightStopId = "board"), poll("t1", progress = 500.0))

        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `a landed poll re-runs the selection`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.refresh(ride(), poll("t1"))
        val afterFirst = h.controller.visibleTrips

        h.refresh(ride(), poll("t1"))

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

        h.refresh(interlined, poll("t1"))

        assertTrue("t2" in h.visibleTripIds() || h.republishes > 0)
        // The walk republishes so the newly-admitted continuation reaches the renderer.
        h.refresh(interlined, poll("t1", "t2"))
        assertTrue(h.visibleTripIds().containsAll(setOf("t1", "t2")))
    }

    @Test
    fun `the walk settles against a poll the continuation has not entered yet`() {
        // The controller-side half of #2206 (`rideContinuations`' KDoc has the mechanism, and
        // RideQueueTest pins it): the walk is seeded from `admitted`, which carries its own previous
        // answer, so an answer that changed every pass republished back into the pass that launched it.
        // On device that spun the main thread to an ANR.
        val h = harness("t1" to schedule("board" to 100.0, nextTripId = "t2"), "t2" to ridden)
        h.neighbourRoutes["t2"] = "route_b"
        val interlined = ride(continuation("route_b"))
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), interlined)

        // The continuation is the trip the vehicle rolls onto *next*, so the poll in hand is silent
        // about it — the ordinary case, not an edge one.
        h.refresh(interlined, poll("t1"))

        // Settled (Harness.refresh would have failed otherwise), and settled on the right answer: the
        // continuation stays admitted rather than being walked back off by the next pass.
        assertEquals(setOf("t1", "t2"), h.visibleTripIds())
    }

    @Test
    fun `a ride with no planned continuation never walks the block`() {
        // The regression guard: a block is a service day, so an unbounded walk would admit a vehicle's
        // whole remaining day.
        val h = harness("t1" to schedule("board" to 100.0, nextTripId = "t2"))
        h.neighbourRoutes["t2"] = "route_a"
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())

        h.refresh(ride(), poll("t1"))

        assertEquals(0, h.neighbourLookups)
        assertEquals(setOf("t1"), h.visibleTripIds())
    }

    // -- focus transitions --

    @Test
    fun `moving to another ride drops what the old one admitted`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.refresh(ride(), poll("t1"))
        assertEquals(setOf("t1"), h.visibleTripIds())

        h.controller.rideChanged()
        // The route redraw happens before the next selection pass. Its approach must not read trips
        // derived from the ride that just ended during that window.
        assertTrue(h.controller.visibleTrips.isEmpty())
        h.refresh(ride(), poll("t1"))

        // Back to Pending with no seed: nothing is admitted until the new stop's arrivals land.
        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `a direction switch abandons the pill focus but keeps the ride`() {
        val h = harness("tapped" to ridden)
        h.controller.start(focusTripId = "tapped")
        h.controller.clearSeed()

        h.refresh(ride(), poll("tapped"))

        assertTrue(h.visibleTripIds().isEmpty())
    }

    @Test
    fun `leaving route mode forgets the ride`() {
        val h = harness("t1" to ridden)
        h.controller.start(focusTripId = null)
        h.controller.setArrivals(groups("t1"), ride())
        h.refresh(ride(), poll("t1"))

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

        h.refresh(ride(), poll("t1", "t2"))

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
