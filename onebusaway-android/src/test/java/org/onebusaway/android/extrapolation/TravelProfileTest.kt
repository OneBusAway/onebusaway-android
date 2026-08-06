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
package org.onebusaway.android.extrapolation

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.time.ScheduleTime
import org.onebusaway.android.time.WallTime

class TravelProfileTest {

    // An express-style schedule that changes speed at the middle stop — the shape #2137 is about.
    //   Stop 0: dist=0m,    arrive=0s,   depart=0s
    //   Stop 1: dist=1000m, arrive=100s, depart=100s   → segment 0: 1000m / 100s = 10 m/s
    //   Stop 2: dist=3000m, arrive=200s, depart=200s   → segment 1: 2000m / 100s = 20 m/s
    private val speedUp =
        makeSchedule(
            Triple(0.0, 0L, 0L),
            Triple(1000.0, 100L, 100L),
            Triple(3000.0, 200L, 200L)
        )

    // --- Shape ---

    @Test
    fun `profile runs from the anchor to the last stop, bending at each one`() {
        val profile = speedUp.travelProfileFrom(500.0)!!

        // 500m left to stop 1 at 10 m/s = 50s, then the whole 2000m express segment in 100s.
        assertArrayEquals(doubleArrayOf(0.0, 50.0, 150.0), profile.travelSeconds, 1e-9)
        assertArrayEquals(doubleArrayOf(500.0, 1000.0, 3000.0), profile.distances, 1e-9)
        assertEquals(10.0, profile.anchorSpeedMps, 1e-9)
    }

    @Test
    fun `anchor speed is the segment the vehicle is actually in`() {
        // Past the boundary, the express segment governs.
        val profile = speedUp.travelProfileFrom(2000.0)!!
        assertEquals(20.0, profile.anchorSpeedMps, 1e-9)
        assertArrayEquals(doubleArrayOf(0.0, 50.0), profile.travelSeconds, 1e-9)
        assertArrayEquals(doubleArrayOf(2000.0, 3000.0), profile.distances, 1e-9)
    }

    @Test
    fun `starting exactly on a stop starts the profile there`() {
        val profile = speedUp.travelProfileFrom(1000.0)!!
        assertArrayEquals(doubleArrayOf(0.0, 100.0), profile.travelSeconds, 1e-9)
        assertArrayEquals(doubleArrayOf(1000.0, 3000.0), profile.distances, 1e-9)
        assertEquals(20.0, profile.anchorSpeedMps, 1e-9)
    }

    // --- Agreement with the schedule-replay path ---

    @Test
    fun `without dwells the profile traces the same path as schedule replay`() {
        // Cross-check against ScheduleReplayExtrapolator's independent stop-walking implementation.
        val profile = speedUp.travelProfileFrom(500.0)!!
        for (sec in listOf(0.0, 10.0, 49.0, 50.0, 51.0, 90.0, 149.0, 150.0, 400.0)) {
            val replayed = replaySchedule(speedUp, 500.0, WallTime(0L), WallTime((sec * 1000).toLong()))
            assertEquals("at ${sec}s", replayed!!, profile.distanceAt(sec), 1e-9)
        }
    }

    @Test
    fun `dwells are left out of the profile`() {
        // Same geometry, but stop 1 now holds for 60s before departing. Schedule replay waits it
        // out; the profile deliberately does not, because the gamma speed model this feeds already
        // accounts for stopping in its slow component.
        val dwelling =
            makeSchedule(
                Triple(0.0, 0L, 0L),
                Triple(1000.0, 100L, 160L),
                Triple(3000.0, 260L, 260L)
            )
        val profile = dwelling.travelProfileFrom(500.0)!!
        assertArrayEquals(doubleArrayOf(0.0, 50.0, 150.0), profile.travelSeconds, 1e-9)

        val replayed = replaySchedule(dwelling, 500.0, WallTime(0L), WallTime(80_000L))
        assertEquals("replay dwells at the stop", 1000.0, replayed!!, 1e-9)
        assertNotEquals(replayed, profile.distanceAt(80.0), 1e-9)
    }

    // --- Malformed schedules ---

    @Test
    fun `a downstream segment taking no time folds into the previous knot`() {
        // Stop 2 is scheduled to arrive the instant the vehicle leaves stop 1.
        val instant =
            makeSchedule(
                Triple(0.0, 0L, 0L),
                Triple(1000.0, 100L, 100L),
                Triple(3000.0, 100L, 100L),
                Triple(4000.0, 200L, 200L)
            )
        val profile = instant.travelProfileFrom(500.0)!!
        // The 3000m knot absorbs into the 50s one rather than duplicating its time.
        assertArrayEquals(doubleArrayOf(0.0, 50.0, 150.0), profile.travelSeconds, 1e-9)
        assertArrayEquals(doubleArrayOf(500.0, 3000.0, 4000.0), profile.distances, 1e-9)
    }

    @Test
    fun `a downstream segment covering no ground becomes a plateau`() {
        val stalled =
            makeSchedule(
                Triple(0.0, 0L, 0L),
                Triple(1000.0, 100L, 100L),
                Triple(1000.0, 160L, 160L),
                Triple(3000.0, 260L, 260L)
            )
        val profile = stalled.travelProfileFrom(500.0)!!
        assertArrayEquals(doubleArrayOf(0.0, 50.0, 110.0, 210.0), profile.travelSeconds, 1e-9)
        assertArrayEquals(doubleArrayOf(500.0, 1000.0, 1000.0, 3000.0), profile.distances, 1e-9)
    }

    // --- Guards ---

    @Test
    fun `fewer than 2 stops has no profile`() {
        assertNull(makeSchedule(Triple(0.0, 0L, 0L)).travelProfileFrom(0.0))
    }

    @Test
    fun `a distance outside the scheduled range has no profile`() {
        assertNull(speedUp.travelProfileFrom(-100.0))
        assertNull(speedUp.travelProfileFrom(3500.0))
    }

    @Test
    fun `sitting exactly on the last stop leaves no schedule ahead`() {
        assertNull(speedUp.travelProfileFrom(3000.0))
    }

    @Test
    fun `a degenerate segment under the vehicle has no profile`() {
        val noTravelTime =
            makeSchedule(
                Triple(0.0, 0L, 100L),
                Triple(1000.0, 100L, 100L),
                Triple(3000.0, 300L, 300L)
            )
        assertNull(noTravelTime.travelProfileFrom(500.0))

        val noDistance =
            makeSchedule(
                Triple(0.0, 0L, 0L),
                Triple(1000.0, 100L, 100L),
                Triple(1000.0, 200L, 200L)
            )
        assertNull(noDistance.travelProfileFrom(1000.0))
    }

    // --- Helpers ---

    /** Reads the profile the obvious way, so the tests above can compare whole trajectories. */
    private fun TravelProfile.distanceAt(seconds: Double): Double {
        if (seconds <= travelSeconds.first()) return distances.first()
        if (seconds >= travelSeconds.last()) return distances.last()
        val i = travelSeconds.indexOfLast { it <= seconds }
        val fraction = (seconds - travelSeconds[i]) / (travelSeconds[i + 1] - travelSeconds[i])
        return distances[i] + fraction * (distances[i + 1] - distances[i])
    }

    private fun makeSchedule(vararg stops: Triple<Double, Long, Long>): ObaTripSchedule {
        val stopTimes: Array<ObaTripSchedule.StopTime> = Array(stops.size) { i ->
            val (dist, arrive, depart) = stops[i]
            StopTimeData(
                stopId = "stop_$i",
                arrivalTime = ScheduleTime(arrive.seconds),
                departureTime = ScheduleTime(depart.seconds),
                distanceAlongTrip = dist
            )
        }
        return TripScheduleData(stopTimes)
    }
}
