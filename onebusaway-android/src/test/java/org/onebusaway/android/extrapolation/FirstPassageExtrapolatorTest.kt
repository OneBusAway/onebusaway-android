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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.FirstPassageDistribution
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.testing.testTripStatus
import org.onebusaway.android.time.ScheduleTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

/**
 * The pace wiring: a vehicle with a sustained slow window extrapolates differently from a cold
 * start, and a cold start is exactly the deviation-only model. The pace maths itself is pinned in
 * [PaceModelTest]; this covers the path from a [TripState]'s history to the distribution.
 */
class FirstPassageExtrapolatorTest {

    /** Stops every 1000 m, 100 s apart, no dwell: schedule speed exactly 10 m/s. */
    private val schedule: ObaTripSchedule = TripScheduleData(
        Array(11) { i ->
            StopTimeData(
                stopId = "stop_$i",
                arrivalTime = ScheduleTime((i * 100).seconds),
                departureTime = ScheduleTime((i * 100).seconds),
                distanceAlongTrip = i * 1000.0
            )
        }
    )

    private val t0 = 1_700_000_000_000L

    /** A state whose history is the given (distance, fix-time) observations, oldest first. */
    private fun state(vararg fixes: Pair<Double, Long>): TripState {
        var state = TripState.empty("trip").withSchedule(schedule)
        for ((distance, fixMs) in fixes) {
            state = state.withStatus(
                testTripStatus(
                    distanceAlongTrip = distance,
                    lastUpdateTime = fixMs,
                    lastLocationUpdateTime = fixMs,
                    phase = "in_progress"
                ),
                serverTimeMs = ServerTime(fixMs),
                localTimeMs = WallTime(fixMs)
            )
        }
        return state
    }

    private fun distribution(state: TripState, dtSeconds: Long): ProbDistribution {
        val anchorLocal = state.anchorLocalTimeMs!!
        val result = state.extrapolate(anchorLocal + dtSeconds.seconds)
        return (result as ExtrapolationResult.Success).distribution
    }

    @Test
    fun `cold start is exactly the deviation-only model`() {
        val actual = distribution(state(5000.0 to t0), dtSeconds = 60)
        val profile = schedule.passageProfileFrom(5000.0)!!
        val expected = FirstPassageDistribution(
            60.0,
            profile.scheduleSeconds,
            profile.distances,
            DeviationModel.dispersionFor(kotlin.time.Duration.ZERO),
            DeviationModel.travelMultiplierFor(kotlin.time.Duration.ZERO)
        )
        for (x in listOf(5100.0, 5400.0, 5800.0, 6500.0)) {
            assertEquals("cdf at $x", expected.cdf(x), actual.cdf(x), 1e-12)
        }
    }

    @Test
    fun `a sustained slow window shifts the distribution, a short one does not`() {
        // 600 m covered in 500 s against a 10 m/s schedule: deep in the slow band.
        val slow = distribution(state(4400.0 to t0 - 500_000, 5000.0 to t0), dtSeconds = 60)
        val cold = distribution(state(5000.0 to t0), dtSeconds = 60)
        // The same slow pace observed over less than the gate: identity, equal to cold.
        val brief = distribution(state(4520.0 to t0 - 400_000, 5000.0 to t0), dtSeconds = 60)

        var slowDiffers = false
        for (x in listOf(5100.0, 5400.0, 5800.0)) {
            assertEquals("gated cdf at $x", cold.cdf(x), brief.cdf(x), 1e-12)
            // A vehicle likely mid-lump has covered less ground: P(D < x) rises.
            if (slow.cdf(x) > cold.cdf(x) + 1e-6) slowDiffers = true
        }
        assertTrue("slow window should shift probability toward the anchor", slowDiffers)
    }
}
