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
package org.onebusaway.android.demo

import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.ScheduleDeviation

/**
 * The demo transit simulator (#2164). It is a pure function of the clock, so all of this runs as a
 * plain JVM test with no Android, no fixture file and no network.
 *
 * What's worth pinning here isn't the exact numbers — those are free to be retuned — but the properties
 * the scripted tutorial *depends* on: that a bus's position and its arrival times are the same fact
 * (so the tour can't narrate a countdown that disagrees with the marker on the map), that a run keeps
 * its identity as time passes, and that the arrivals list reliably contains every deviation state the
 * legend step teaches.
 */
class DemoScenarioTest {

    private val routeId = DEMO_TEST_ROUTE_ID
    private val anchorStopId = DEMO_TEST_ANCHOR_STOP_ID

    /** The shared synthetic deployment — see [demoTestFixture]. */
    private val fixture = demoTestFixture()

    /** An arbitrary but fixed instant, so nothing here depends on when the suite runs. */
    private val now = 1_780_000_000_000L

    @Test
    fun `a run's position and its arrival times are the same fact`() {
        val run = DemoScenario.activeRuns(fixture, routeId, now).first()
        val stopDistance = fixture.routeStops.getValue(routeId).distanceTo(anchorStopId)!!
        val arrival = run.timeAtDistance(stopDistance)

        // At the moment the run is due at the stop, it must actually be at the stop — the invariant the
        // whole demo rests on, since the ETA pill and the map marker are drawn from these two calls.
        val distanceAtArrival = run.distanceAlongShapeAt(arrival)
        assertEquals(stopDistance, distanceAtArrival, 1.0)
    }

    @Test
    fun `deviation shifts a run later without changing its schedule`() {
        val late = DemoScenario.runById(fixture, DemoScenario.tripIdFor(routeId, LATE_RUN_INDEX))
        assertNotNull(late)
        requireNotNull(late)
        assertTrue("expected the late slot of the deviation cycle", late.deviationSeconds > 0)

        val distance = 3000.0
        val delay = late.timeAtDistance(distance) - late.scheduledTimeAtDistance(distance)
        assertEquals(late.deviationSeconds * 1000L, delay)
    }

    @Test
    fun `a run keeps its identity as the clock advances`() {
        val run = DemoScenario.activeRuns(fixture, routeId, now).first()
        val laterRuns = DemoScenario.activeRuns(fixture, routeId, now + 60_000L)

        val same = laterRuns.firstOrNull { it.index == run.index }
        assertNotNull("the run should still be on the road a minute later", same)
        assertEquals(run.tripId, same?.tripId)
        assertEquals(run.vehicleId, same?.vehicleId)
        // …and it should have moved on, not sat still.
        assertTrue(same!!.distanceAlongShapeAt(now + 60_000L) > run.distanceAlongShapeAt(now))
    }

    @Test
    fun `a trip id round-trips back to its run`() {
        val run = DemoScenario.activeRuns(fixture, routeId, now).first()
        val resolved = DemoScenario.runById(fixture, run.tripId)
        assertEquals(run.index, resolved?.index)
        assertEquals(run.routeId, resolved?.routeId)
    }

    @Test
    fun `an unknown trip id resolves to nothing rather than throwing`() {
        assertNull(DemoScenario.runById(fixture, "1_not_a_demo_trip"))
        assertNull(DemoScenario.runById(fixture, "${routeId}_demo_notanumber"))
    }

    @Test
    fun `arrivals are in ascending order and none has already gone`() {
        val calls = DemoScenario.arrivalsAt(fixture, anchorStopId, now)
        assertTrue("the demo stop should have upcoming arrivals", calls.isNotEmpty())
        assertTrue("arrivals must be sorted", calls.zipWithNext().all { (a, b) -> a.arrivalTimeMs <= b.arrivalTimeMs })
        assertTrue("a passed arrival must not be listed", calls.all { it.arrivalTimeMs >= now })
    }

    @Test
    fun `a stop the route does not serve has no arrivals`() {
        assertTrue(DemoScenario.arrivalsAt(fixture, "stop_not_on_route", now).isEmpty())
    }

    /**
     * The legend step teaches four deviation states, so the arrivals list has to show them. Sampled
     * across a whole headway cycle because which runs are in the window slides with the clock — the
     * guarantee is that the states are always *available*, not that a given row always has one.
     */
    @Test
    fun `every deviation state the legend teaches appears in the arrivals list`() {
        val seen = mutableSetOf<ScheduleDeviation.Status>()
        for (offsetMinutes in 0 until 20) {
            val at = now + offsetMinutes * 60_000L
            DemoScenario.arrivalsAt(fixture, anchorStopId, at).forEach { call ->
                seen += ScheduleDeviation.status(
                    isRealtime = call.run.hasPrediction(at),
                    deviation = call.run.deviationSeconds.seconds
                )
            }
        }
        assertEquals(ScheduleDeviation.Status.entries.toSet(), seen)
    }

    /**
     * The three pill states the legend step points at, all present at once: a bus on the road (map
     * pin), a predicted run with no bus out yet (broadcast glyph), and a run too far out to predict
     * (schedule grey). Sampled across a headway because which runs are in the window slides.
     */
    @Test
    fun `on-road, predicted-only and scheduled arrivals coexist at the demo stop`() {
        var sawOnRoad = false
        var sawPredictedOnly = false
        var sawScheduled = false
        for (offsetSeconds in 0 until 600 step 30) {
            val at = now + offsetSeconds * 1000L
            DemoScenario.arrivalsAt(fixture, anchorStopId, at).forEach { call ->
                when {
                    call.run.isOnRoad(at) -> sawOnRoad = true
                    call.run.hasPrediction(at) -> sawPredictedOnly = true
                    else -> sawScheduled = true
                }
            }
        }
        assertTrue("expected a bus on the road (map pin)", sawOnRoad)
        assertTrue("expected a predicted run with no bus out (broadcast glyph)", sawPredictedOnly)
        assertTrue("expected a schedule-only row (grey)", sawScheduled)
    }

    /**
     * The soonest arrival at the tour's stop must always have a bus already reporting a position, so
     * its ETA pill shows the map pin rather than the broadcast glyph. That is the whole reason a run's
     * bus appears at the terminus before it departs — without the pull-out window, a stop early in its
     * route only had drawable vehicles for arrivals a couple of minutes away.
     */
    @Test
    fun `the next arrival always has a bus that can be drawn`() {
        for (offsetSeconds in 0 until 900 step 15) {
            val at = now + offsetSeconds * 1000L
            val next = DemoScenario.arrivalsAt(fixture, anchorStopId, at).firstOrNull()
            assertNotNull("no arrivals at +${offsetSeconds}s", next)
            assertTrue(
                "the soonest arrival at +${offsetSeconds}s has no drawable bus",
                next!!.run.isOnRoad(at)
            )
            assertNotNull(next.run.positionAt(at))
        }
    }

    @Test
    fun `a bus waiting at the terminus sits at the start of the line`() {
        val run = DemoScenario.activeRuns(fixture, routeId, now).first()
        // A minute before this run pulls out — inside the window where its bus is assigned and
        // reporting, but hasn't started down the line. Derived from the run rather than sampled at a
        // fixed clock, so the case is exercised whatever time the suite runs at.
        val waiting = run.actualDepartureMs - 60_000L
        assertTrue("expected to be before departure", waiting < run.actualDepartureMs)
        assertTrue("a waiting bus is drawable", run.isOnRoad(waiting))
        assertTrue("…but has not left yet", run.distanceAlongShapeAt(waiting) < 0.0)
        assertEquals("held at the start of the line", 0.0, run.progressAlongShapeAt(waiting), 0.0)
        assertNotNull("a waiting bus still reports a position", run.positionAt(waiting))
    }

    @Test
    fun `a predicted run with no bus out yet has no position to draw`() {
        val geometry = fixture.routeStops.getValue(routeId)
        val service = DemoRouteService(headwaySeconds = 600, phaseSeconds = 420, speedMetersPerSecond = 5.2)
        // Find a run that is predicted but hasn't departed — the broadcast-glyph state.
        val pending = (0L..40L)
            .map { DemoScenario.runById(fixture, DemoScenario.tripIdFor(routeId, service.indexAt(now) + it))!! }
            .first { it.hasPrediction(now) && !it.isOnRoad(now) }
        assertNull("a run with no bus assigned yet has nowhere to be drawn", pending.positionAt(now))
        assertTrue(pending.distanceAlongShapeAt(now) < 0.0)
        assertTrue(geometry.totalDistance > 0.0)
    }

    @Test
    fun `a run that has not left yet reports no prediction and no position`() {
        val service = DemoRouteService(headwaySeconds = 600, phaseSeconds = 0, speedMetersPerSecond = 5.0)
        // The run that departs one full headway from now cannot be on the road yet.
        val future = DemoScenario.runById(
            fixture,
            // Far enough out to be past the prediction lead entirely.
            DemoScenario.tripIdFor(routeId, service.indexAt(now) + 10)
        )
        requireNotNull(future)
        assertTrue("a far-out run must be scheduled-only", !future.hasPrediction(now))
        assertNull(future.positionAt(now))
    }

    @Test
    fun `every active run is somewhere on its shape`() {
        val total = fixture.routeStops.getValue(routeId).totalDistance
        DemoScenario.activeRuns(fixture, routeId, now).forEach { run ->
            val distance = run.distanceAlongShapeAt(now)
            assertTrue("$distance is off the shape", distance in 0.0..total)
            assertNotNull("an active run must have a position", run.positionAt(now))
        }
    }

    /**
     * The index windows both queries scan are an optimisation, not the answer — so they must never be
     * narrower than the truth. Sweeping a whole headway second by second is what catches the one-bus
     * drop at each edge that a mid-headway sample never sees: an early run is already on the road
     * before its scheduled departure, and a late one is still short of a stop after its scheduled
     * arrival.
     */
    @Test
    fun `no run on the road is missing from the active list`() {
        for (offsetSeconds in 0 until 600) {
            val at = now + offsetSeconds * 1000L
            val listed = DemoScenario.activeRuns(fixture, routeId, at).map { it.index }.toSet()
            // Check a generous band of indices by brute force, independent of the window under test.
            val brute = (-20L..20L).map { DemoScenario.runById(fixture, DemoScenario.tripIdFor(routeId, indexNear(at) + it))!! }
                .filter { it.isOnRoad(at) }
                .map { it.index }
                .toSet()
            assertEquals("at +${offsetSeconds}s", brute, listed)
        }
    }

    @Test
    fun `no pending arrival is missing from a stop's list`() {
        val geometry = fixture.routeStops.getValue(routeId)
        val stopDistance = geometry.distanceTo(anchorStopId)!!
        val horizonMs = 45 * 60 * 1000L
        for (offsetSeconds in 0 until 600) {
            val at = now + offsetSeconds * 1000L
            val listed = DemoScenario.arrivalsAt(fixture, anchorStopId, at).map { it.run.index }.toSet()
            val brute = (-20L..20L).map { DemoScenario.runById(fixture, DemoScenario.tripIdFor(routeId, indexNear(at) + it))!! }
                .filter { it.timeAtDistance(stopDistance) in at..(at + horizonMs) }
                .map { it.index }
                .toSet()
            assertEquals("at +${offsetSeconds}s", brute, listed)
        }
    }

    /** A run index in the right neighbourhood of [at], for the brute-force sweeps above to centre on. */
    private fun indexNear(at: Long): Long = DemoRouteService(headwaySeconds = 600, phaseSeconds = 420, speedMetersPerSecond = 5.2).indexAt(at)

    @Test
    fun `active runs are ordered by progress along the route`() {
        val distances = DemoScenario.activeRuns(fixture, routeId, now).map { it.distanceAlongShapeAt(now) }
        assertTrue("farthest first", distances.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun `the service date is local midnight in the agency's zone`() {
        // The agency's zone, not the JVM's and not UTC. `now` is 2026-05-28T13:26:40-07:00, so the
        // service date is that day's midnight in America/Los_Angeles — written out rather than
        // recomputed, since recomputing it the same way the implementation does would assert nothing.
        // A UTC-day implementation would answer 2026-05-28T00:00Z, seven hours early.
        assertEquals(1_779_951_600_000L, DemoScenario.serviceDateMs(fixture, now))
    }

    @Test
    fun `the next stop is the first one the run has not reached`() {
        val run = DemoScenario.activeRuns(fixture, routeId, now).first()
        val geometry = fixture.routeStops.getValue(routeId)
        val index = run.nextStopIndexAt(now)
        assertNotNull(index)
        requireNotNull(index)
        assertTrue(geometry.stopDistances[index] >= run.distanceAlongShapeAt(now))
        if (index > 0) {
            assertTrue(geometry.stopDistances[index - 1] < run.distanceAlongShapeAt(now))
        }
    }

    @Test
    fun `stop distance lookups answer only for stops on the route`() {
        val geometry = fixture.routeStops.getValue(routeId)
        assertEquals(3000.0, geometry.distanceTo(anchorStopId))
        assertEquals(3, geometry.indexOf(anchorStopId))
        assertNull(geometry.distanceTo("stop_not_on_route"))
        assertNull(geometry.indexOf("stop_not_on_route"))
    }

    @Test
    fun `run indices are anchored on the epoch, not on when the tour started`() {
        val service = DemoRouteService(headwaySeconds = 600, phaseSeconds = 0, speedMetersPerSecond = 5.0)
        // Two readings a headway apart differ by exactly one index, whatever the absolute time.
        assertEquals(service.indexAt(now) + 1, service.indexAt(now + 600_000L))
        // And an index maps back to the departure that defines it.
        val index = service.indexAt(now)
        assertTrue(abs(service.departureMs(index) - now) <= 600_000L)
        assertTrue(service.departureMs(index) <= now)
    }

    private companion object {
        /**
         * An index landing on the cycle's "clearly late" slot. The cycle is
         * `[0, +330, -240, +45]`, so any index ≡ 1 (mod 4) is late.
         */
        const val LATE_RUN_INDEX = 4_000_001L
    }
}
