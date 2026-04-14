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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.extrapolation.math.prob.GammaDistribution

class GammaSpeedModelTest {

    private fun h34Dist(schedMps: Double) = buildH34SpeedDistribution(schedMps)

    // m/s test speeds (named for readability)
    private val mps5 = 2.235 // ~5 mph
    private val mps10 = 4.470 // ~10 mph
    private val mps15 = 6.706 // ~15 mph
    private val mps20 = 8.941 // ~20 mph
    private val mps30 = 13.411 // ~30 mph
    private val mps40 = 17.882 // ~40 mph
    private val mps60 = 26.822 // ~60 mph

    // --- buildH34SpeedDistribution ---

    @Test(expected = IllegalArgumentException::class)
    fun `throws when schedSpeed is zero`() {
        h34Dist(0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when schedSpeed is negative`() {
        h34Dist(-1.0)
    }

    @Test
    fun `produces positive mean and median across speed range`() {
        for (sched in listOf(mps5, mps10, mps15, mps20, mps30, mps40, mps60)) {
            val dist = h34Dist(sched)
            assertTrue("mean <= 0 at $sched m/s", dist.mean > 0)
            assertTrue("median <= 0 at $sched m/s", dist.median() > 0)
        }
    }

    @Test
    fun `ensemble mean equals scheduled speed in mph`() {
        // H34 is ensemble-mean-locked: mixture mean should equal v_sched in mph
        for (sched in listOf(mps5, mps15, mps30, mps60)) {
            val dist = h34Dist(sched)
            val expectedMph = sched * MPS_TO_MPH
            assertEquals(
                    "mean should equal sched speed at $sched m/s",
                    expectedMph,
                    dist.mean,
                    expectedMph * 0.01
            )
        }
    }

    @Test
    fun `worked example at 15 mph`() {
        // At v=15 mph (6.706 m/s), H34 with constant slow shape:
        //   alpha1 = exp(-1.3227) ≈ 0.266, mixture mean = 15.0 mph
        val dist = h34Dist(mps15)
        assertEquals(15.0, dist.mean, 0.05)
    }

    @Test
    fun `at very low speed`() {
        val dist = h34Dist(0.447) // ~1 mph
        assertTrue(dist.mean > 0)
        assertTrue(dist.median() > 0)
    }

    @Test
    fun `at highway speed`() {
        val dist = h34Dist(mps60)
        assertTrue(dist.mean > 0)
        assertTrue(dist.median() > 0)
    }

    // --- pdf ---

    @Test
    fun `pdf is zero at zero and negative`() {
        val dist = h34Dist(mps15)
        assertEquals(0.0, dist.pdf(0.0), 1e-12)
        assertEquals(0.0, dist.pdf(-5.0), 1e-12)
    }

    @Test
    fun `pdf is positive for reasonable speeds`() {
        val dist = h34Dist(mps15)
        // Test in mph (the distribution output is in mph)
        for (speed in listOf(5.0, 10.0, 15.0, 20.0)) {
            assertTrue("pdf should be > 0 at $speed mph", dist.pdf(speed) > 0)
        }
    }

    @Test
    fun `pdf integrates to approximately 1`() {
        val dist = h34Dist(mps15)
        val dx = 0.01
        var sum = 0.0
        var x = dx
        while (x <= 100.0) {
            sum += dist.pdf(x) * dx
            x += dx
        }
        assertEquals("pdf should integrate to ~1.0", 1.0, sum, 0.02)
    }

    // --- cdf ---

    @Test
    fun `cdf at zero is zero`() {
        val dist = h34Dist(mps15)
        assertEquals(0.0, dist.cdf(0.0), 1e-12)
    }

    @Test
    fun `cdf approaches 1 for large values`() {
        val dist = h34Dist(mps15)
        assertTrue(dist.cdf(60.0) > 0.99)
        assertTrue(dist.cdf(120.0) > 0.999)
    }

    @Test
    fun `cdf increases from low to high speeds`() {
        val dist = h34Dist(mps15)
        assertTrue("cdf(5) < cdf(15)", dist.cdf(5.0) < dist.cdf(15.0))
        assertTrue("cdf(15) < cdf(30)", dist.cdf(15.0) < dist.cdf(30.0))
    }

    @Test
    fun `cdf at median is approximately 0_5`() {
        val dist = h34Dist(mps15)
        val median = dist.quantile(0.5)
        assertEquals(0.5, dist.cdf(median), 0.01)
    }

    // --- quantile ---

    @Test
    fun `quantile at 0 returns 0`() {
        val dist = h34Dist(mps15)
        assertEquals(0.0, dist.quantile(0.0), 1e-12)
    }

    @Test
    fun `quantile at 1 returns large value`() {
        // FrozenDistribution returns last table entry (not MAX_VALUE)
        val dist = h34Dist(mps15)
        assertTrue(dist.quantile(1.0) > dist.mean * 3)
    }

    @Test
    fun `quantile is monotonically non-decreasing`() {
        val dist = h34Dist(mps15)
        var prev = 0.0
        for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)) {
            val q = dist.quantile(p)
            assertTrue("quantile($p) = $q should be >= $prev", q >= prev)
            prev = q
        }
    }

    @Test
    fun `cdf of quantile round-trips`() {
        val dist = h34Dist(mps15)
        for (p in doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90, 0.95)) {
            val q = dist.quantile(p)
            assertEquals("CDF(quantile($p)) should ~= $p", p, dist.cdf(q), 0.01)
        }
    }

    @Test
    fun `cdf of quantile round-trips across speed regimes`() {
        for (sched in listOf(mps5, mps15, mps40)) {
            val dist = h34Dist(sched)
            val q50 = dist.quantile(0.5)
            assertEquals("round-trip failed for sched=$sched", 0.5, dist.cdf(q50), 0.01)
        }
    }

    // --- Degenerate / extreme schedule speeds ---

    @Test
    fun `extremely low speed does not crash`() {
        // 0.01 m/s ≈ 0.02 mph — pushes m very close to 1.0,
        // which can overflow the fast component scale
        val dist = h34Dist(0.01)
        assertTrue("mean should be finite and positive", dist.mean.isFinite() && dist.mean > 0)
        assertTrue(
                "median should be finite and positive",
                dist.median().isFinite() && dist.median() > 0
        )
    }

    @Test
    fun `very small speed produces valid distribution`() {
        // 0.001 m/s — extreme case, sigmoid(MW_INTERCEPT + MW_SLOPE * v) ≈ sigmoid(-1.35) ≈ 0.79
        // but as v → 0, scale1 → 0, so we hit the slow-only fallback
        val dist = h34Dist(0.001)
        assertTrue("mean should be finite", dist.mean.isFinite())
        assertTrue("median should be finite", dist.median().isFinite())
    }

    @Test
    fun `extremely high speed does not crash`() {
        // 100 m/s ≈ 224 mph — extreme high end
        val dist = h34Dist(100.0)
        assertTrue("mean should be finite and positive", dist.mean.isFinite() && dist.mean > 0)
        assertTrue(
                "median should be finite and positive",
                dist.median().isFinite() && dist.median() > 0
        )
    }

    @Test
    fun `speed near epsilon does not crash`() {
        // Just above zero — smallest reasonable positive value
        val dist = h34Dist(1e-10)
        assertTrue("mean should be finite", dist.mean.isFinite())
        assertTrue("median should be finite", dist.median().isFinite())
    }

    @Test
    fun `distribution is valid across wide speed range`() {
        // Sweep from very slow to very fast, verify no crashes or NaN
        val speeds = listOf(0.01, 0.1, 0.5, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0)
        for (s in speeds) {
            val dist = h34Dist(s)
            assertTrue("mean NaN at $s m/s", dist.mean.isFinite())
            assertTrue("median NaN at $s m/s", dist.median().isFinite())
            assertTrue("pdf(mean) NaN at $s m/s", dist.pdf(dist.mean).isFinite())
            assertTrue("cdf(mean) NaN at $s m/s", dist.cdf(dist.mean).isFinite())
        }
    }

    // --- GammaDistribution ---

    @Test
    fun `GammaDistribution fields accessible`() {
        val d = GammaDistribution(2.0, 7.5)
        assertEquals(2.0, d.alpha, 0.0)
        assertEquals(7.5, d.scale, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `GammaDistribution rejects negative alpha`() {
        GammaDistribution(-1.0, 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `GammaDistribution rejects zero scale`() {
        GammaDistribution(1.0, 0.0)
    }
}
