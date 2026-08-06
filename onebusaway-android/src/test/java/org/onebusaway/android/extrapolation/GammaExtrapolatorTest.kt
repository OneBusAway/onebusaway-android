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
import org.onebusaway.android.extrapolation.math.prob.AffineTransformDistribution
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.testing.testTripStatus
import org.onebusaway.android.time.ScheduleTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

private const val ANCHOR_TIME = 1_000_000L
private const val ANCHOR_DIST = 500.0

/** The stop where every fixture below changes speed. */
private const val BOUNDARY_DIST = 1000.0

class GammaExtrapolatorTest {

    // 10 m/s to the stop at 1000m, then 10 m/s again — no speed change anywhere.
    private val uniform =
        makeSchedule(
            Triple(0.0, 0L, 0L),
            Triple(1000.0, 100L, 100L),
            Triple(3000.0, 300L, 300L)
        )

    // 10 m/s, then an express segment at 20 m/s — the RapidRide C case from #2137.
    private val speedUp =
        makeSchedule(
            Triple(0.0, 0L, 0L),
            Triple(1000.0, 100L, 100L),
            Triple(3000.0, 200L, 200L)
        )

    // The converse: 10 m/s, then city streets at 5 m/s.
    private val slowDown =
        makeSchedule(
            Triple(0.0, 0L, 0L),
            Triple(1000.0, 100L, 100L),
            Triple(3000.0, 500L, 500L)
        )

    // --- Regression: an unchanging schedule still extrapolates in a straight line ---

    @Test
    fun `a uniform schedule reproduces the plain constant-speed extrapolation`() {
        val dtSec = 60.0
        val bent = extrapolate(uniform, dtSec)
        val straight =
            AffineTransformDistribution(
                buildH34SpeedDistribution(10.0),
                ANCHOR_DIST,
                dtSec / MPS_TO_MPH
            )

        for (p in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
            // Guard the pin: a quantile that ran off the end of the schedule would be clamped, and
            // would agree for the wrong reason.
            assertTrue("p=$p should stay inside the schedule", straight.quantile(p) < 3000.0)
            assertEquals("quantile at p=$p", straight.quantile(p), bent.quantile(p), 1e-6)
        }
    }

    // --- The fix: quantiles bend at the speed change ---

    @Test
    fun `crossing into a faster segment carries the whole distribution further`() {
        val dtSec = 120.0
        val bent = extrapolate(speedUp, dtSec)
        val straight = straightLine(dtSec)

        var crossings = 0
        for (p in listOf(0.25, 0.5, 0.75, 0.9)) {
            if (straight.quantile(p) <= BOUNDARY_DIST) continue
            crossings++
            assertTrue(
                "p=$p should gain from the express segment",
                bent.quantile(p) > straight.quantile(p) + 1.0
            )
        }
        assertTrue("fixture should push quantiles past the boundary", crossings >= 2)
    }

    @Test
    fun `crossing into a slower segment holds the whole distribution back`() {
        val dtSec = 120.0
        val bent = extrapolate(slowDown, dtSec)
        val straight = straightLine(dtSec)

        var crossings = 0
        for (p in listOf(0.25, 0.5, 0.75, 0.9)) {
            if (straight.quantile(p) <= BOUNDARY_DIST) continue
            crossings++
            assertTrue(
                "p=$p should lose to the slow segment",
                bent.quantile(p) < straight.quantile(p) - 1.0
            )
        }
        assertTrue("fixture should push quantiles past the boundary", crossings >= 2)
    }

    @Test
    fun `each quantile lands where the schedule puts its own pace`() {
        val dtSec = 120.0
        val bent = extrapolate(speedUp, dtSec)
        for (p in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
            assertEquals("quantile at p=$p", expectedOnSpeedUp(p, dtSec), bent.quantile(p), 1e-6)
        }
    }

    // --- Bounds and continuity ---

    @Test
    fun `extrapolation never runs past the end of the schedule`() {
        val bent = extrapolate(speedUp, dtSec = 600.0)
        assertEquals(3000.0, bent.quantile(0.99), 0.0)
        assertTrue(bent.median() <= 3000.0)
    }

    @Test
    fun `quantiles only ever advance as time passes`() {
        for (p in listOf(0.1, 0.5, 0.9)) {
            var previous = Double.NEGATIVE_INFINITY
            for (dtSec in listOf(0.0, 15.0, 30.0, 60.0, 90.0, 120.0, 180.0, 300.0, 600.0)) {
                val q = extrapolate(speedUp, dtSec).quantile(p)
                assertTrue("p=$p went backwards at ${dtSec}s", q >= previous - 1e-9)
                previous = q
            }
        }
    }

    @Test
    fun `at the anchor instant the vehicle is exactly where it was seen`() {
        val bent = extrapolate(speedUp, dtSec = 0.0)
        assertEquals(ANCHOR_DIST, bent.quantile(0.5), 0.0)
        assertEquals(ANCHOR_DIST, bent.quantile(0.99), 0.0)
        assertEquals(1.0, bent.cdf(ANCHOR_DIST), 0.0)
        assertEquals(0.0, bent.cdf(ANCHOR_DIST - 1e-9), 0.0)
    }

    // --- Guards ---

    @Test
    fun `no schedule means no extrapolation`() {
        assertTrue(gammaResult(schedule = null, dtSec = 60.0) is ExtrapolationResult.MissingSchedule)
    }

    @Test
    fun `a vehicle past the last scheduled stop has nothing to follow`() {
        assertTrue(
            gammaResult(speedUp, dtSec = 60.0, distanceAlongTrip = 3500.0)
                is ExtrapolationResult.MissingSchedule
        )
    }

    @Test
    fun `a degenerate segment under the vehicle means no extrapolation`() {
        val degenerate =
            makeSchedule(
                Triple(0.0, 0L, 100L),
                Triple(1000.0, 100L, 100L),
                Triple(3000.0, 300L, 300L)
            )
        assertTrue(gammaResult(degenerate, dtSec = 60.0) is ExtrapolationResult.MissingSchedule)
    }

    // --- Helpers ---

    /**
     * Where [speedUp] puts a vehicle holding the p-th percentile pace for [dtSec], worked longhand:
     * 500m of 10 m/s running to the stop at 1000m, then 20 m/s out to 3000m. The speed model itself
     * is pinned by GammaSpeedModelTest; this only redoes the carry-forward.
     */
    private fun expectedOnSpeedUp(p: Double, dtSec: Double): Double {
        val speedMph = buildH34SpeedDistribution(10.0).quantile(p)
        val scheduleSeconds = dtSec * speedMph / (10.0 * MPS_TO_MPH)
        return when {
            scheduleSeconds <= 50.0 -> 500.0 + scheduleSeconds * 10.0
            scheduleSeconds <= 150.0 -> 1000.0 + (scheduleSeconds - 50.0) * 20.0
            else -> 3000.0
        }
    }

    /** The pre-#2137 extrapolation: one speed, held for the whole interval. */
    private fun straightLine(dtSec: Double): ProbDistribution = AffineTransformDistribution(buildH34SpeedDistribution(10.0), ANCHOR_DIST, dtSec / MPS_TO_MPH)

    // Driven straight at GammaExtrapolator rather than through TripState.extrapolate, which
    // now selects the first-passage model for buses. TripState's own guards are covered by
    // TripStateTest; what these tests are about is the speed model's own behaviour.
    private fun extrapolate(schedule: ObaTripSchedule, dtSec: Double): ProbDistribution {
        val result = gammaResult(schedule, dtSec)
        assertTrue("expected Success, got $result", result is ExtrapolationResult.Success)
        return (result as ExtrapolationResult.Success).distribution
    }

    private fun gammaResult(
        schedule: ObaTripSchedule?,
        dtSec: Double,
        distanceAlongTrip: Double = ANCHOR_DIST
    ): ExtrapolationResult = GammaExtrapolator(busState(schedule, distanceAlongTrip)).doExtrapolate(
        distanceAlongTrip,
        WallTime(ANCHOR_TIME),
        WallTime(ANCHOR_TIME + (dtSec * 1000).toLong())
    )

    private fun busState(schedule: ObaTripSchedule?, distanceAlongTrip: Double = ANCHOR_DIST): TripState {
        val state =
            TripState.empty("trip1")
                .withStatus(
                    testTripStatus(
                        distanceAlongTrip = distanceAlongTrip,
                        lastUpdateTime = ANCHOR_TIME
                    ),
                    serverTimeMs = ServerTime(ANCHOR_TIME),
                    localTimeMs = WallTime(ANCHOR_TIME)
                )
                .withRouteType(ObaRoute.TYPE_BUS)
        return if (schedule != null) state.withSchedule(schedule) else state
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
