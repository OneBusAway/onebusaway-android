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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffineTransformDistributionTest {

    // Gamma(3, 2) has mean = 6
    private val base = GammaDistribution(3.0, 2.0)

    // --- Mean ---

    @Test
    fun `mean applies affine transform`() {
        val dist = AffineTransformDistribution(base, offset = 10.0, scale = 3.0)
        // offset + base.mean * scale = 10 + 6*3 = 28
        assertEquals(28.0, dist.mean, 1e-12)
    }

    @Test
    fun `mean with zero scale equals offset`() {
        val dist = AffineTransformDistribution(base, offset = 5.0, scale = 0.0)
        assertEquals(5.0, dist.mean, 1e-12)
    }

    @Test
    fun `mean with zero offset`() {
        val dist = AffineTransformDistribution(base, offset = 0.0, scale = 2.0)
        assertEquals(12.0, dist.mean, 1e-12)
    }

    // --- PDF with scale == 0 ---

    @Test
    fun `pdf with zero scale returns 0`() {
        val dist = AffineTransformDistribution(base, offset = 5.0, scale = 0.0)
        assertEquals(0.0, dist.pdf(5.0), 0.0)
        assertEquals(0.0, dist.pdf(0.0), 0.0)
        assertEquals(0.0, dist.pdf(100.0), 0.0)
    }

    @Test
    fun `pdf is non-negative`() {
        val dist = AffineTransformDistribution(base, offset = 10.0, scale = 3.0)
        for (x in listOf(-10.0, 0.0, 5.0, 10.0, 20.0, 50.0, 100.0)) {
            assertTrue("PDF at $x should be non-negative", dist.pdf(x) >= 0)
        }
    }

    // --- PDF integrates to 1 after transform ---

    @Test
    fun `pdf integrates to approximately 1`() {
        val dist = AffineTransformDistribution(base, offset = 100.0, scale = 5.0)
        val dx = 0.01
        var sum = 0.0
        var x = 100.0 + dx / 2
        while (x < 300.0) {
            sum += dist.pdf(x) * dx
            x += dx
        }
        assertEquals(1.0, sum, 0.01)
    }

    @Test
    fun `pdf integrates to approximately 1 with small scale`() {
        val dist = AffineTransformDistribution(base, offset = 0.0, scale = 0.5)
        val dx = 0.001
        var sum = 0.0
        var x = dx / 2
        while (x < 30.0) {
            sum += dist.pdf(x) * dx
            x += dx
        }
        assertEquals(1.0, sum, 0.01)
    }

    // --- CDF with scale == 0 ---

    @Test
    fun `cdf with zero scale is step at offset`() {
        val dist = AffineTransformDistribution(base, offset = 5.0, scale = 0.0)
        assertEquals(0.0, dist.cdf(4.99), 0.0)
        assertEquals(1.0, dist.cdf(5.0), 0.0)
        assertEquals(1.0, dist.cdf(5.01), 0.0)
    }

    @Test
    fun `cdf approaches 1 for large x`() {
        val dist = AffineTransformDistribution(base, offset = 100.0, scale = 5.0)
        assertTrue(dist.cdf(500.0) > 0.999)
    }

    // --- Quantile-CDF round-trip ---

    @Test
    fun `quantile and CDF are inverses`() {
        val dist = AffineTransformDistribution(base, offset = 10.0, scale = 3.0)
        for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)) {
            val x = dist.quantile(p)
            assertEquals("round-trip at p=$p", p, dist.cdf(x), 1e-6)
        }
    }

    // --- Identity transform ---

    @Test
    fun `identity transform preserves base distribution`() {
        val dist = AffineTransformDistribution(base, offset = 0.0, scale = 1.0)
        for (x in listOf(0.5, 1.0, 5.0, 10.0)) {
            assertEquals(base.pdf(x), dist.pdf(x), 1e-12)
            assertEquals(base.cdf(x), dist.cdf(x), 1e-12)
        }
        assertEquals(base.mean, dist.mean, 1e-12)
    }
}
