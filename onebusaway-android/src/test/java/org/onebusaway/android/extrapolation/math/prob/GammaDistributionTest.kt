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
package org.onebusaway.android.extrapolation.math.prob

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GammaDistributionTest {

    // --- lnGamma reference values ---

    @Test
    fun `lnGamma of 1 is 0`() {
        assertEquals(0.0, GammaDistribution.lnGamma(1.0), 1e-12)
    }

    @Test
    fun `lnGamma of 2 is 0`() {
        // Gamma(2) = 1! = 1, ln(1) = 0
        assertEquals(0.0, GammaDistribution.lnGamma(2.0), 1e-12)
    }

    @Test
    fun `lnGamma of 5 is ln 24`() {
        // Gamma(5) = 4! = 24
        assertEquals(ln(24.0), GammaDistribution.lnGamma(5.0), 1e-10)
    }

    @Test
    fun `lnGamma of 0_5 is ln sqrt pi`() {
        // Gamma(0.5) = sqrt(pi)
        assertEquals(ln(Math.sqrt(Math.PI)), GammaDistribution.lnGamma(0.5), 1e-10)
    }

    // --- CDF known values ---

    @Test
    fun `exponential CDF at x=1 is 1 minus 1 over e`() {
        // Gamma(1, 1) is exponential(1). CDF(1) = 1 - 1/e ≈ 0.6321
        val dist = GammaDistribution(1.0, 1.0)
        assertEquals(1.0 - 1.0 / Math.E, dist.cdf(1.0), 1e-8)
    }

    @Test
    fun `CDF at 0 is 0`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertEquals(0.0, dist.cdf(0.0), 0.0)
    }

    @Test
    fun `CDF at negative is 0`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertEquals(0.0, dist.cdf(-5.0), 0.0)
    }

    @Test
    fun `CDF approaches 1 for large x`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertTrue(dist.cdf(100.0) > 0.999)
    }

    // --- Quantile-CDF round-trip ---

    @Test
    fun `quantile and CDF are inverses`() {
        val dist = GammaDistribution(3.0, 2.0)
        for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)) {
            val x = dist.quantile(p)
            assertEquals(p, dist.cdf(x), 1e-6)
        }
    }

    @Test
    fun `quantile CDF round-trip for small alpha`() {
        val dist = GammaDistribution(0.5, 1.0)
        for (p in listOf(0.1, 0.5, 0.9)) {
            val x = dist.quantile(p)
            assertEquals(p, dist.cdf(x), 1e-6)
        }
    }

    @Test
    fun `quantile CDF round-trip for large alpha`() {
        val dist = GammaDistribution(50.0, 1.0)
        for (p in listOf(0.1, 0.5, 0.9)) {
            val x = dist.quantile(p)
            assertEquals(p, dist.cdf(x), 1e-5)
        }
    }

    // --- Quantile boundary cases ---

    @Test
    fun `quantile of 0 returns 0`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertEquals(0.0, dist.quantile(0.0), 0.0)
    }

    @Test
    fun `quantile of 1 returns MAX_VALUE`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertEquals(Double.MAX_VALUE, dist.quantile(1.0), 0.0)
    }

    // --- Mean ---

    @Test
    fun `mean is alpha times scale`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertEquals(6.0, dist.mean, 1e-12)
    }

    // --- PDF ---

    @Test
    fun `PDF at 0 is 0`() {
        val dist = GammaDistribution(3.0, 2.0)
        assertEquals(0.0, dist.pdf(0.0), 0.0)
    }

    @Test
    fun `PDF is non-negative`() {
        val dist = GammaDistribution(3.0, 2.0)
        for (x in listOf(0.001, 0.1, 1.0, 5.0, 10.0, 50.0)) {
            assertTrue("PDF at $x should be non-negative", dist.pdf(x) >= 0)
        }
    }

    @Test
    fun `PDF integrates to approximately 1`() {
        val dist = GammaDistribution(3.0, 2.0)
        // Simple trapezoidal integration from 0 to 50 with small steps
        val dx = 0.01
        var sum = 0.0
        var x = dx / 2
        while (x < 50.0) {
            sum += dist.pdf(x) * dx
            x += dx
        }
        assertEquals(1.0, sum, 0.001)
    }

    // --- Constructor validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `alpha must be positive`() {
        GammaDistribution(0.0, 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scale must be positive`() {
        GammaDistribution(1.0, 0.0)
    }
}
