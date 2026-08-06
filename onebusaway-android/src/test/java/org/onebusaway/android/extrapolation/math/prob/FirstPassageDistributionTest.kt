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
package org.onebusaway.android.extrapolation.math.prob

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val THETA = 25.236

/**
 * Quantiles are found by bisecting the remaining schedule, so they carry that search's residue.
 * A centimetre is far below anything drawable and comfortably above the residue the iteration
 * count guarantees — asserting tighter would be pinning the constant, not the behaviour.
 */
private const val SEARCH_TOLERANCE_M = 0.01

class FirstPassageDistributionTest {

    // A schedule running 2000m in 400s at a steady 5 m/s, one knot every 500m.
    private val scheduleSeconds = doubleArrayOf(0.0, 100.0, 200.0, 300.0, 400.0)
    private val distances = doubleArrayOf(0.0, 500.0, 1000.0, 1500.0, 2000.0)

    private fun at(dtSec: Double) = FirstPassageDistribution(dtSec, scheduleSeconds, distances, THETA)

    // --- Construction ---

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched knot arrays are rejected`() {
        FirstPassageDistribution(60.0, doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0), THETA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive theta is rejected`() {
        FirstPassageDistribution(60.0, scheduleSeconds, distances, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative elapsed time is rejected`() {
        FirstPassageDistribution(-1.0, scheduleSeconds, distances, THETA)
    }

    // --- The centre tracks the schedule ---

    @Test
    fun `the median sits near where the schedule says the vehicle should be`() {
        // First-passage time is right-skewed, so the median trip is a little quicker than the
        // mean and the median position runs slightly ahead of schedule. The mean-travel multiplier
        // is fitted to hold that within a few percent from a couple of minutes out; at 60s the
        // gamma shape is near 1 and the skew is at its worst.
        for (dtSec in listOf(60.0, 120.0, 240.0)) {
            val scheduled = dtSec * 5.0
            val median = at(dtSec).median()
            val tolerance = if (dtSec < 90.0) 0.25 else 0.10
            assertTrue(
                "at ${dtSec}s median $median should be within ${tolerance * 100}% of $scheduled",
                abs(median - scheduled) < tolerance * scheduled
            )
        }
    }

    // --- Spread grows with the square root of elapsed time, not linearly ---

    @Test
    fun `the band widens with the square root of elapsed time`() {
        // This is the whole point of the model. Quadrupling the elapsed time should roughly
        // double the spread, where a held-pace model would quadruple it.
        val narrow = at(60.0)
        val wide = at(240.0)
        val narrowWidth = narrow.quantile(0.9) - narrow.quantile(0.1)
        val wideWidth = wide.quantile(0.9) - wide.quantile(0.1)
        val ratio = wideWidth / narrowWidth
        assertTrue("expected roughly 2x, got ${"%.3f".format(ratio)}", ratio in 1.7..2.4)
    }

    // --- Distribution mechanics ---

    @Test
    fun `quantiles increase with p and the cdf inverts them`() {
        val dist = at(120.0)
        var previous = Double.NEGATIVE_INFINITY
        for (p in listOf(0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99)) {
            val q = dist.quantile(p)
            assertTrue("quantile decreased at p=$p", q >= previous)
            previous = q
            if (q > distances.first() && q < distances.last()) {
                assertEquals("round-trip at p=$p", p, dist.cdf(q), 1e-3)
            }
        }
    }

    @Test
    fun `quantiles advance as time passes`() {
        for (p in listOf(0.1, 0.5, 0.9)) {
            var previous = Double.NEGATIVE_INFINITY
            for (dtSec in listOf(0.0, 30.0, 60.0, 120.0, 240.0, 480.0)) {
                val q = at(dtSec).quantile(p)
                assertTrue("p=$p went backwards at ${dtSec}s", q >= previous - 1e-9)
                previous = q
            }
        }
    }

    @Test
    fun `the vehicle never runs past the end of the schedule`() {
        val dist = at(3600.0)
        assertEquals(2000.0, dist.quantile(0.999), SEARCH_TOLERANCE_M)
        assertEquals(1.0, dist.cdf(2000.0), 0.0)
        assertEquals(0.0, dist.pdf(2000.0), 0.0)
    }

    @Test
    fun `at the anchor instant the vehicle is exactly where it was seen`() {
        val dist = at(0.0)
        assertEquals(0.0, dist.quantile(0.5), SEARCH_TOLERANCE_M)
        assertEquals(0.0, dist.quantile(0.99), SEARCH_TOLERANCE_M)
        assertEquals(0.0, dist.cdf(-1e-9), 0.0)
    }

    @Test
    fun `pdf is non-negative and integrates to approximately 1`() {
        val dist = at(120.0)
        val dx = 0.5
        var sum = 0.0
        var x = dx / 2
        while (x < 2000.0) {
            val d = dist.pdf(x)
            assertTrue("pdf negative at $x", d >= 0.0)
            sum += d * dx
            x += dx
        }
        // The tail beyond the last knot is an atom carrying the rest of the mass.
        val atom = 1.0 - dist.cdf(2000.0 - 1e-6)
        assertEquals(1.0, sum + atom, 0.02)
    }

    // --- Dwells ---

    @Test
    fun `a dwell plateau resolves to the moment the vehicle arrives`() {
        // 500m in 100s, then a 60s dwell, then 500m more.
        val dwelling =
            FirstPassageDistribution(
                120.0,
                doubleArrayOf(0.0, 100.0, 160.0, 260.0),
                doubleArrayOf(0.0, 500.0, 500.0, 1000.0),
                THETA
            )
        // Reaching 500m takes 100s of schedule whether or not the vehicle then waits, so the
        // probability of having passed it must not count the dwell against the vehicle.
        val reachedBy = 1.0 - dwelling.cdf(500.0 - 1e-6)
        assertTrue("expected a realistic chance of being past 500m, got $reachedBy", reachedBy > 0.5)
        assertTrue(dwelling.quantile(0.5) >= 500.0)
    }
}
