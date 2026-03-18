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
package org.onebusaway.android.extrapolation.math.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.extrapolation.math.GammaDistribution
import org.onebusaway.android.extrapolation.math.ZeroInflatedGammaDistribution

class GammaSpeedModelTest {

    // m/s test speeds (named for readability)
    private val mps5 = 2.235 // ~5 mph
    private val mps10 = 4.470 // ~10 mph
    private val mps15 = 6.706 // ~15 mph
    private val mps20 = 8.941 // ~20 mph
    private val mps30 = 13.411 // ~30 mph
    private val mps40 = 17.882 // ~40 mph
    private val mps60 = 26.822 // ~60 mph

    // --- fromSpeeds ---

    @Test(expected = IllegalArgumentException::class)
    fun `fromSpeeds throws when schedSpeed is zero`() {
        GammaSpeedModel.fromSpeeds(0.0, mps5, 60.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromSpeeds throws when schedSpeed is negative`() {
        GammaSpeedModel.fromSpeeds(-1.0, mps5, 60.0)
    }

    @Test
    fun `fromSpeeds falls back to schedSpeed when prevSpeed is zero`() {
        val withZero = GammaSpeedModel.fromSpeeds(mps20, 0.0, 60.0) as ZeroInflatedGammaDistribution
        val withEqual = GammaSpeedModel.fromSpeeds(mps20, mps20, 60.0) as ZeroInflatedGammaDistribution
        assertEquals(withEqual.alpha, withZero.alpha, 1e-9)
        assertEquals(withEqual.scale, withZero.scale, 1e-9)
    }

    @Test
    fun `fromSpeeds falls back to schedSpeed when prevSpeed is negative`() {
        val withNeg = GammaSpeedModel.fromSpeeds(mps20, -5.0, 60.0) as ZeroInflatedGammaDistribution
        val withEqual = GammaSpeedModel.fromSpeeds(mps20, mps20, 60.0) as ZeroInflatedGammaDistribution
        assertEquals(withEqual.alpha, withNeg.alpha, 1e-9)
        assertEquals(withEqual.scale, withNeg.scale, 1e-9)
    }

    @Test
    fun `fromSpeeds produces positive alpha and scale`() {
        for (sched in listOf(mps5, mps15, mps30, mps60)) {
            for (prev in listOf(mps5, mps15, mps30, mps60)) {
                val dist = GammaSpeedModel.fromSpeeds(sched, prev, 60.0) as ZeroInflatedGammaDistribution
                assertTrue("alpha <= 0", dist.alpha > 0)
                assertTrue("scale <= 0", dist.scale > 0)
            }
        }
    }

    @Test
    fun `fromSpeeds worked example at 20 and 10 mph`() {
        // mps20 = 8.941, mps10 = 4.470
        // vEff = 8.941 * 0.9127 + 4.470 * 0.0873 = 8.55 m/s (above KINK)
        // b0 = END_B0 = 0.3102
        // alpha = b0 * vEff = 0.3102 * 8.55 ≈ 2.65
        // scale = 1/b0 = 3.22
        val dist = GammaSpeedModel.fromSpeeds(mps20, mps10, 60.0) as ZeroInflatedGammaDistribution
        assertEquals(2.65, dist.alpha, 0.1)
        assertEquals(3.22, dist.scale, 0.1)
    }

    @Test
    fun `fromSpeeds at very low speed`() {
        val dist = GammaSpeedModel.fromSpeeds(0.447, 0.447, 60.0) as ZeroInflatedGammaDistribution
        assertTrue(dist.alpha > 0)
        assertTrue(dist.scale > 0)
    }

    @Test
    fun `fromSpeeds at highway speed`() {
        val dist = GammaSpeedModel.fromSpeeds(mps60, mps60, 60.0) as ZeroInflatedGammaDistribution
        assertTrue(dist.alpha > 0)
        assertTrue(dist.scale > 0)
    }

    // --- mean / median ---

    @Test
    fun `mean speed is alpha times scale`() {
        val dist = GammaDistribution(alpha = 3.0, scale = 5.0)
        assertEquals(15.0, dist.mean, 1e-9)
    }

    @Test
    fun `mean speed is close to input when schedSpeed equals prevSpeed`() {
        for (inputMps in listOf(mps10, mps20, mps40)) {
            val dist = GammaSpeedModel.fromSpeeds(inputMps, inputMps, 60.0)
            assertEquals("mean should be near $inputMps m/s", inputMps, dist.mean, inputMps * 0.2)
        }
    }

    @Test
    fun `median is less than mean for right-skewed gamma`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        val median = dist.quantile(0.5)
        assertTrue("median ($median) should be < mean (${dist.mean})", median < dist.mean)
    }

    // --- pdf ---

    @Test
    fun `pdf is zero at zero and negative`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        assertEquals(0.0, dist.pdf(0.0), 1e-12)
        assertEquals(0.0, dist.pdf(-5.0), 1e-12)
    }

    @Test
    fun `pdf is positive for reasonable speeds`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        for (speed in listOf(mps5, mps10, mps15, mps20)) {
            assertTrue("pdf should be > 0 at $speed m/s", dist.pdf(speed) > 0)
        }
    }

    @Test
    fun `pdf continuous part integrates to approximately (1 - p0)`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0) as ZeroInflatedGammaDistribution
        val dx = 0.005
        var sum = 0.0
        var x = dx
        while (x <= 90.0) {
            sum += dist.pdf(x) * dx
            x += dx
        }
        // Continuous part integrates to (1 - p0), point mass at 0 accounts for p0
        assertEquals("pdf should integrate to ~(1-p0)", 1.0 - dist.p0, sum, 0.01)
    }

    // --- cdf ---

    @Test
    fun `cdf at zero equals p0 and is zero for negative`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0) as ZeroInflatedGammaDistribution
        assertEquals(dist.p0, dist.cdf(0.0), 1e-12)
        assertEquals(0.0, dist.cdf(-1.0), 1e-12)
    }

    @Test
    fun `cdf approaches 1 for large values`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        assertTrue(dist.cdf(45.0) > 0.99)
        assertTrue(dist.cdf(90.0) > 0.999)
    }

    @Test
    fun `cdf increases from low to high speeds`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        assertTrue("cdf(5mph) < cdf(15mph)", dist.cdf(mps5) < dist.cdf(mps15))
        assertTrue("cdf(15mph) < cdf(40mph)", dist.cdf(mps15) < dist.cdf(mps40))
    }

    @Test
    fun `cdf at median is approximately 0_5`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        val median = dist.quantile(0.5)
        assertEquals(0.5, dist.cdf(median), 0.01)
    }

    // --- quantile ---

    @Test
    fun `quantile at 0 returns 0`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        assertEquals(0.0, dist.quantile(0.0), 1e-12)
    }

    @Test
    fun `quantile at 1 returns MAX_VALUE`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        assertEquals(Double.MAX_VALUE, dist.quantile(1.0), 0.0)
    }

    @Test
    fun `quantile is monotonically non-decreasing`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        var prev = 0.0
        // Note: quantile returns 0 for p <= p0, then increases for p > p0
        for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)) {
            val q = dist.quantile(p)
            assertTrue("quantile($p) = $q should be >= $prev", q >= prev)
            prev = q
        }
    }

    @Test
    fun `cdf of quantile round-trips for percentiles above p0`() {
        val dist = GammaSpeedModel.fromSpeeds(mps15, mps15, 60.0)
        // Only test percentiles > p0 where quantile returns positive values
        for (p in doubleArrayOf(0.25, 0.50, 0.75, 0.90, 0.95)) {
            val q = dist.quantile(p)
            assertEquals("CDF(quantile($p)) should ≈ $p", p, dist.cdf(q), 0.01)
        }
    }

    @Test
    fun `cdf of quantile round-trips across different speed regimes`() {
        for (sched in listOf(mps5, mps15, mps40)) {
            for (prev in listOf(mps5, mps15, mps40)) {
                val dist = GammaSpeedModel.fromSpeeds(sched, prev, 60.0)
                val q50 = dist.quantile(0.5)
                assertEquals(
                        "round-trip failed for sched=$sched prev=$prev",
                        0.5,
                        dist.cdf(q50),
                        0.01
                )
            }
        }
    }

    // --- GammaDistribution ---

    @Test
    fun `GammaDistribution fields accessible`() {
        val d = GammaDistribution(2.0, 7.5)
        assertEquals(2.0, d.alpha, 0.0)
        assertEquals(7.5, d.scale, 0.0)
    }
}
