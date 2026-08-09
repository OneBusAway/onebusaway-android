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

import kotlin.math.ln
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.testing.testTripStatus
import org.onebusaway.android.time.ScheduleTime

class PaceModelTest {

    // ================================================================
    // adjustmentFor: the gate and the shrunken ratio
    // ================================================================

    @Test
    fun `no lookback is identity`() {
        assertSame(PaceModel.IDENTITY, PaceModel.adjustmentFor(null))
    }

    @Test
    fun `window shorter than the gate is identity`() {
        val lookback = PaceLookback(achievedSeconds = 100.0, elapsedSeconds = 419.9)
        assertSame(PaceModel.IDENTITY, PaceModel.adjustmentFor(lookback))
    }

    @Test
    fun `on-pace lookback is exactly identity — the rho = 1 knot is pinned`() {
        val adjustment = PaceModel.adjustmentFor(PaceLookback(600.0, 600.0))
        assertEquals(1.0, adjustment.paceMultiplier, 0.0)
        assertEquals(1.0, adjustment.dispersionMultiplier, 0.0)
        assertEquals(0.0, adjustment.extraSeconds, 0.0)
    }

    @Test
    fun `sustained slow pace costs a lump, wider dispersion, near-unit rate`() {
        // rho = (120 + c) / (600 + c): deep in the slow band.
        val adjustment = PaceModel.adjustmentFor(PaceLookback(120.0, 600.0))
        assertTrue("extra seconds, was ${adjustment.extraSeconds}", adjustment.extraSeconds > 10.0)
        assertTrue("dispersion, was ${adjustment.dispersionMultiplier}", adjustment.dispersionMultiplier > 1.3)
        assertEquals(1.0, adjustment.paceMultiplier, 0.15)
    }

    @Test
    fun `sustained fast pace also costs a lump — the U shape`() {
        val adjustment = PaceModel.adjustmentFor(PaceLookback(1200.0, 600.0))
        assertTrue("extra seconds, was ${adjustment.extraSeconds}", adjustment.extraSeconds > 10.0)
    }

    @Test
    fun `negative achieved schedule is floored, not extrapolated`() {
        val floored = PaceModel.adjustmentFor(PaceLookback(-50.0, 600.0))
        val zero = PaceModel.adjustmentFor(PaceLookback(0.0, 600.0))
        assertEquals(zero, floored)
    }

    @Test
    fun `adjustment matches the scipy fit for a deep-slow lookback — clamped at the first knot`() {
        // rho = (120 + c)/(600 + c) = 0.204, below the 0.55 knot, so the curves read their
        // first-knot values exactly. References generated with scipy from h39_params_shipped.json.
        val adjustment = PaceModel.adjustmentFor(PaceLookback(120.0, 600.0))
        assertEquals(0.9612270622735628, adjustment.paceMultiplier, 1e-12)
        assertEquals(1.6947184458381417, adjustment.dispersionMultiplier, 1e-12)
        assertEquals(21.973541744065756, adjustment.extraSeconds, 1e-12)
    }

    @Test
    fun `spline interior matches scipy PCHIP — generated, not fabricated`() {
        // rho values chosen between knots; expected values from scipy.interpolate.PchipInterpolator
        // on the same knots (see extrapolation-science h39_params_shipped.json).
        val cases = mapOf(
            0.62 to Triple(-0.064795225, 0.280828724, 17.898343016),
            0.70 to Triple(-0.077656811, 0.062421023, 13.534721639),
            0.90 to Triple(-0.040302335, -0.025745956, 3.08388969),
            1.20 to Triple(-0.008443109, -0.000935796, 4.213797938)
        )
        for ((rho, expected) in cases) {
            // Invert the shrinkage so adjustmentFor sees exactly this rho: with elapsed fixed at
            // 600s, achieved = rho*(600 + c) - c.
            val c = 3.005739011224008
            val adjustment = PaceModel.adjustmentFor(PaceLookback(rho * (600.0 + c) - c, 600.0))
            assertEquals("log gk at $rho", expected.first, ln(adjustment.paceMultiplier), 1e-8)
            assertEquals("log gtheta at $rho", expected.second, ln(adjustment.dispersionMultiplier), 1e-8)
            assertEquals("delta at $rho", expected.third, adjustment.extraSeconds, 1e-8)
        }
    }

    // ================================================================
    // lookbackFor: choosing the window from the history
    // ================================================================

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

    private fun status(distance: Double?, fixMs: Long, phase: String? = "in_progress") = testTripStatus(
        distanceAlongTrip = distance,
        lastLocationUpdateTime = fixMs,
        phase = phase
    )

    @Test
    fun `reads the oldest fix within the window`() {
        val anchor = status(6000.0, 700_000L)
        val history = listOf(
            status(1000.0, 100_000L), // 600s back: exactly at the window edge, still inside
            status(2000.0, 200_000L),
            status(5000.0, 600_000L),
            anchor
        )
        val lookback = PaceModel.lookbackFor(history, anchor, schedule)!!
        assertEquals(600.0, lookback.elapsedSeconds, 1e-9)
        // 1000 m -> 6000 m at 10 m/s of schedule = 500 scheduled seconds.
        assertEquals(500.0, lookback.achievedSeconds, 1e-9)
    }

    @Test
    fun `skips entries older than the window`() {
        val anchor = status(6000.0, 800_000L)
        val history = listOf(
            status(500.0, 100_000L), // 700s back: outside
            status(2000.0, 300_000L), // 500s back: the one
            anchor
        )
        val lookback = PaceModel.lookbackFor(history, anchor, schedule)!!
        assertEquals(500.0, lookback.elapsedSeconds, 1e-9)
        assertEquals(400.0, lookback.achievedSeconds, 1e-9)
    }

    @Test
    fun `skips entries without a GPS fix or a distance`() {
        val anchor = status(6000.0, 700_000L)
        val history = listOf(
            status(1000.0, 0L), // schedule-projected: no fix
            status(null, 150_000L), // no distance
            status(3000.0, 300_000L),
            anchor
        )
        val lookback = PaceModel.lookbackFor(history, anchor, schedule)!!
        assertEquals(400.0, lookback.elapsedSeconds, 1e-9)
        assertEquals(300.0, lookback.achievedSeconds, 1e-9)
    }

    @Test
    fun `null when the anchor has no GPS fix`() {
        val anchor = status(6000.0, 0L)
        assertNull(PaceModel.lookbackFor(listOf(status(1000.0, 100_000L), anchor), anchor, schedule))
    }

    @Test
    fun `null when no earlier fix exists`() {
        val anchor = status(6000.0, 700_000L)
        assertNull(PaceModel.lookbackFor(listOf(anchor), anchor, schedule))
    }

    @Test
    fun `an off-schedule lookback position falls through to the next candidate`() {
        val anchor = status(6000.0, 700_000L)
        val history = listOf(status(20_000.0, 200_000L), status(2000.0, 300_000L), anchor)
        val lookback = PaceModel.lookbackFor(history, anchor, schedule)!!
        assertEquals(400.0, lookback.elapsedSeconds, 1e-9)
    }

    @Test
    fun `skips fixes from a vehicle not running its trip — the fit never saw them`() {
        // Parked at the terminal (layover phase), then departs on time: without the phase filter
        // this window would read as deep-slow for the next ten minutes.
        val anchor = status(2000.0, 700_000L)
        val history = listOf(
            status(0.0, 100_000L, phase = "layover_before"),
            status(0.0, 500_000L, phase = "layover_before"),
            status(1000.0, 600_000L),
            anchor
        )
        val lookback = PaceModel.lookbackFor(history, anchor, schedule)!!
        assertEquals(100.0, lookback.elapsedSeconds, 1e-9)
    }

    @Test
    fun `skips in-progress fixes parked within the terminal margin`() {
        // phase says in_progress but the vehicle sits at the start, not late: terminal idling.
        val anchor = status(2000.0, 700_000L)
        val history = listOf(
            status(100.0, 200_000L),
            status(1000.0, 600_000L),
            anchor
        )
        val lookback = PaceModel.lookbackFor(history, anchor, schedule)!!
        assertEquals(100.0, lookback.elapsedSeconds, 1e-9)
    }

    @Test
    fun `null when the anchor itself is not running its trip`() {
        val anchor = status(6000.0, 700_000L, phase = "layover_during")
        assertNull(PaceModel.lookbackFor(listOf(status(1000.0, 100_000L), anchor), anchor, schedule))
    }

    @Test
    fun `null when the phase is unreported — identity beats conditioning on unvetted windows`() {
        val anchor = status(6000.0, 700_000L, phase = null)
        assertNull(PaceModel.lookbackFor(listOf(status(1000.0, 100_000L, phase = null), anchor), anchor, schedule))
    }

    // ================================================================
    // warpedBy: the profile carrying the mean effects
    // ================================================================

    @Test
    fun `identity warp returns the same profile instance`() {
        val profile = schedule.passageProfileFrom(500.0)!!
        assertSame(profile, profile.warpedBy(PaceModel.IDENTITY))
    }

    @Test
    fun `warp scales and shifts every schedule knot, distances untouched`() {
        val profile = schedule.passageProfileFrom(500.0)!!
        val warped = profile.warpedBy(PaceAdjustment(paceMultiplier = 0.9, dispersionMultiplier = 2.0, extraSeconds = 30.0))
        for (i in profile.scheduleSeconds.indices) {
            assertEquals(0.9 * profile.scheduleSeconds[i] + 30.0, warped.scheduleSeconds[i], 1e-9)
            assertEquals(profile.distances[i], warped.distances[i], 0.0)
        }
    }
}
