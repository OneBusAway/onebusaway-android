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
package org.onebusaway.android.extrapolation.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroInflatedGammaDistributionTest {

    // Standard test distribution: 20% zero-inflation, Gamma(3, 5) for positive values
    private val dist = ZeroInflatedGammaDistribution(p0 = 0.2, alpha = 3.0, scale = 5.0)

    // --- Constructor and fields ---

    @Test
    fun `fields are accessible`() {
        assertEquals(0.2, dist.p0, 0.0)
        assertEquals(3.0, dist.alpha, 0.0)
        assertEquals(5.0, dist.scale, 0.0)
    }

    // --- mean ---

    @Test
    fun `mean is weighted by (1 - p0)`() {
        // Gamma(3, 5) has mean = 15, so zero-inflated mean = 0.8 * 15 = 12
        assertEquals(12.0, dist.mean, 1e-9)
    }

    @Test
    fun `mean is zero when p0 is 1`() {
        val allZero = ZeroInflatedGammaDistribution(p0 = 1.0, alpha = 3.0, scale = 5.0)
        assertEquals(0.0, allZero.mean, 1e-9)
    }

    @Test
    fun `mean equals gamma mean when p0 is 0`() {
        val noZero = ZeroInflatedGammaDistribution(p0 = 0.0, alpha = 3.0, scale = 5.0)
        assertEquals(15.0, noZero.mean, 1e-9)
    }

    // --- pdf ---

    @Test
    fun `pdf is zero at zero and negative values`() {
        assertEquals(0.0, dist.pdf(0.0), 1e-12)
        assertEquals(0.0, dist.pdf(-1.0), 1e-12)
    }

    @Test
    fun `pdf is scaled by (1 - p0) for positive values`() {
        val gamma = GammaDistribution(3.0, 5.0)
        val x = 10.0
        assertEquals(0.8 * gamma.pdf(x), dist.pdf(x), 1e-12)
    }

    @Test
    fun `pdf integrates to approximately (1 - p0)`() {
        // The continuous part should integrate to (1 - p0) = 0.8
        val dx = 0.01
        var sum = 0.0
        var x = dx
        while (x <= 100.0) {
            sum += dist.pdf(x) * dx
            x += dx
        }
        assertEquals(0.8, sum, 0.01)
    }

    // --- cdf ---

    @Test
    fun `cdf is zero for negative values`() {
        assertEquals(0.0, dist.cdf(-1.0), 1e-12)
    }

    @Test
    fun `cdf at zero equals p0`() {
        assertEquals(0.2, dist.cdf(0.0), 1e-12)
    }

    @Test
    fun `cdf just above zero is slightly greater than p0`() {
        assertTrue(dist.cdf(0.001) > 0.2)
    }

    @Test
    fun `cdf approaches 1 for large values`() {
        assertTrue(dist.cdf(100.0) > 0.999)
    }

    @Test
    fun `cdf is monotonically increasing`() {
        var prev = 0.0
        for (x in listOf(0.0, 1.0, 5.0, 10.0, 15.0, 20.0, 30.0)) {
            val c = dist.cdf(x)
            assertTrue("cdf($x) = $c should be >= $prev", c >= prev)
            prev = c
        }
    }

    @Test
    fun `cdf formula is p0 + (1-p0) * gammaCdf`() {
        val gamma = GammaDistribution(3.0, 5.0)
        val x = 10.0
        val expected = 0.2 + 0.8 * gamma.cdf(x)
        assertEquals(expected, dist.cdf(x), 1e-12)
    }

    // --- quantile ---

    @Test
    fun `quantile at 0 returns 0`() {
        assertEquals(0.0, dist.quantile(0.0), 1e-12)
    }

    @Test
    fun `quantile at 1 returns MAX_VALUE`() {
        assertEquals(Double.MAX_VALUE, dist.quantile(1.0), 0.0)
    }

    @Test
    fun `quantile returns 0 for p less than or equal to p0`() {
        assertEquals(0.0, dist.quantile(0.1), 1e-12)
        assertEquals(0.0, dist.quantile(0.2), 1e-12)
    }

    @Test
    fun `quantile returns positive value for p greater than p0`() {
        assertTrue(dist.quantile(0.21) > 0)
        assertTrue(dist.quantile(0.5) > 0)
        assertTrue(dist.quantile(0.9) > 0)
    }

    @Test
    fun `quantile maps correctly into gamma portion`() {
        val gamma = GammaDistribution(3.0, 5.0)
        // For p > p0: quantile(p) = gamma.quantile((p - p0) / (1 - p0))
        // e.g., p = 0.6 -> pGamma = (0.6 - 0.2) / 0.8 = 0.5
        val expected = gamma.quantile(0.5)
        assertEquals(expected, dist.quantile(0.6), 1e-9)
    }

    @Test
    fun `quantile is monotonically increasing`() {
        var prev = 0.0
        for (p in listOf(0.0, 0.1, 0.2, 0.3, 0.5, 0.7, 0.9, 0.99)) {
            val q = dist.quantile(p)
            assertTrue("quantile($p) = $q should be >= $prev", q >= prev)
            prev = q
        }
    }

    @Test
    fun `cdf of quantile round-trips for percentiles above p0`() {
        for (p in listOf(0.3, 0.5, 0.7, 0.9, 0.95)) {
            val q = dist.quantile(p)
            assertEquals("CDF(quantile($p)) should ≈ $p", p, dist.cdf(q), 0.01)
        }
    }

    // --- median ---

    @Test
    fun `median returns 0 when p0 is greater than 0_5`() {
        val highZero = ZeroInflatedGammaDistribution(p0 = 0.6, alpha = 3.0, scale = 5.0)
        assertEquals(0.0, highZero.median(), 1e-12)
    }

    @Test
    fun `median is positive when p0 is less than 0_5`() {
        assertTrue(dist.median() > 0)
    }

    // --- edge cases ---

    @Test
    fun `works with p0 equals 0 (pure gamma)`() {
        val pureGamma = ZeroInflatedGammaDistribution(p0 = 0.0, alpha = 3.0, scale = 5.0)
        val gamma = GammaDistribution(3.0, 5.0)

        assertEquals(gamma.mean, pureGamma.mean, 1e-12)
        assertEquals(gamma.cdf(0.0), pureGamma.cdf(0.0), 1e-12)
        assertEquals(gamma.cdf(10.0), pureGamma.cdf(10.0), 1e-12)
        assertEquals(gamma.quantile(0.5), pureGamma.quantile(0.5), 1e-12)
    }

    @Test
    fun `works with p0 equals 1 (always zero)`() {
        val allZero = ZeroInflatedGammaDistribution(p0 = 1.0, alpha = 3.0, scale = 5.0)

        assertEquals(0.0, allZero.mean, 1e-12)
        assertEquals(1.0, allZero.cdf(0.0), 1e-12)
        assertEquals(1.0, allZero.cdf(10.0), 1e-12)
        assertEquals(0.0, allZero.quantile(0.5), 1e-12)
        assertEquals(0.0, allZero.quantile(0.99), 1e-12)
    }
}
