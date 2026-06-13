/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.io.elements.ObaTripSchedule

class ScheduleReplayExtrapolatorTest {

    // Helper: create StopTime with distance, arrivalTime, departureTime
    // Times are in seconds since service date.
    //
    // Test schedule:
    //   Stop 0: dist=0m,    arrive=0s,   depart=0s    (no dwell)
    //   Stop 1: dist=1000m, arrive=100s, depart=130s  (30s dwell)
    //   Stop 2: dist=3000m, arrive=330s, depart=330s  (no dwell)
    //
    // Segment 0→1: 1000m in 100s = 10 m/s
    // Segment 1→2: 2000m in 200s (depart@130 → arrive@330) = 10 m/s

    private val schedule =
            makeSchedule(
                    Triple(0.0, 0L, 0L),
                    Triple(1000.0, 100L, 130L),
                    Triple(3000.0, 330L, 330L)
            )

    // --- Basic travel within a single segment ---

    @Test
    fun `travel within first segment`() {
        // Start at 0m, advance 50s → 50s * 10m/s = 500m
        val result = replay(schedule, 0.0, 50.0)
        assertEquals(500.0, result!!, 1e-9)
    }

    @Test
    fun `travel within second segment`() {
        // Start at 1500m (mid second segment). Schedule time at 1500m:
        // departure@130s + (500/2000) * 200s = 130 + 50 = 180s
        // Advance 50s → target schedule time 230s
        // Distance at 230s: 1000 + (230-130)/200 * 2000 = 1000 + 1000 = 2000m
        val result = replay(schedule, 1500.0, 50.0)
        assertEquals(2000.0, result!!, 1e-9)
    }

    // --- Crossing a segment boundary ---

    @Test
    fun `cross from first segment through dwell into second segment`() {
        // Start at 500m. Schedule time = 50s.
        // Advance 150s → target = 200s
        // At 100s: arrive stop 1 (1000m). Dwell 30s (100→130s).
        // At 130s: depart stop 1. Remaining: 200-130=70s into second segment.
        // 70s * 10m/s = 700m past stop 1 → 1700m
        val result = replay(schedule, 500.0, 150.0)
        assertEquals(1700.0, result!!, 1e-9)
    }

    // --- Dwell at a stop ---

    @Test
    fun `target time falls during dwell at stop 1`() {
        // Start at 500m (schedule time 50s). Advance 60s → target = 110s.
        // Vehicle arrives at stop 1 at 100s and dwells until 130s.
        // At 110s, vehicle is dwelling → distance = 1000m (stop 1)
        val result = replay(schedule, 500.0, 60.0)
        assertEquals(1000.0, result!!, 1e-9)
    }

    @Test
    fun `start exactly at stop with dwell, advance moves into next segment`() {
        // Start at 1000m (stop 1). findSegmentIndex places this in segment 1→2.
        // Schedule time = departure[1] = 130s (dwell already happened).
        // Advance 20s → target = 150s. Travel in seg 1→2: (150-130)/200 * 2000 = 200m → 1200m.
        val result = replay(schedule, 1000.0, 20.0)
        assertEquals(1200.0, result!!, 1e-9)
    }

    // --- Past end of trip ---

    @Test
    fun `past last stop clamps to end`() {
        // Start at 0m, advance 500s. Schedule ends at 330s, dist=3000m.
        val result = replay(schedule, 0.0, 500.0)
        assertEquals(3000.0, result!!, 1e-9)
    }

    // --- Zero elapsed time ---

    @Test
    fun `zero dt returns start distance`() {
        val result = replay(schedule, 750.0, 0.0)
        assertEquals(750.0, result!!, 1e-9)
    }

    // --- Edge cases ---

    @Test
    fun `negative dt returns null`() {
        assertNull(replay(schedule, 500.0, -1.0))
    }

    @Test
    fun `fewer than 2 stops returns null`() {
        val single = makeSchedule(Triple(0.0, 0L, 0L))
        assertNull(replay(single, 0.0, 10.0))
    }

    @Test
    fun `distance before first stop returns null`() {
        assertNull(replay(schedule, -100.0, 10.0))
    }

    @Test
    fun `distance after last stop returns null`() {
        assertNull(replay(schedule, 5000.0, 10.0))
    }

    /** Convenience: replay from time 0 advancing by dtSec seconds. */
    private fun replay(schedule: ObaTripSchedule, startDist: Double, dtSec: Double): Double? =
            replaySchedule(schedule, startDist, 0L, (dtSec * 1000).toLong())

    // --- Helpers ---

    private fun makeSchedule(vararg stops: Triple<Double, Long, Long>): ObaTripSchedule {
        val stClass = ObaTripSchedule.StopTime::class.java
        val ctor = stClass.getDeclaredConstructor()
        ctor.isAccessible = true

        val stopTimesArray = java.lang.reflect.Array.newInstance(stClass, stops.size)
        for (i in stops.indices) {
            val (dist, arrive, depart) = stops[i]
            val st = ctor.newInstance()
            setField(st, "distanceAlongTrip", dist)
            setField(st, "arrivalTime", arrive)
            setField(st, "departureTime", depart)
            setField(st, "stopId", "stop_$i")
            java.lang.reflect.Array.set(stopTimesArray, i, st)
        }

        val schedCtor = ObaTripSchedule::class.java.getDeclaredConstructor()
        schedCtor.isAccessible = true
        val sched = schedCtor.newInstance()
        setField(sched, "stopTimes", stopTimesArray)
        return sched
    }

    private fun setField(obj: Any, fieldName: String, value: Any?) {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }
}
