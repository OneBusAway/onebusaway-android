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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiecewiseLinearTransformDistributionTest {

    // Gamma(3, 2) has mean 6; its mass is effectively spent by x = 60.
    private val base = GammaDistribution(3.0, 2.0)

    // --- Construction ---

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched knot array lengths are rejected`() {
        transform(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a single knot is rejected`() {
        transform(doubleArrayOf(0.0), doubleArrayOf(0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-increasing knotX is rejected`() {
        transform(doubleArrayOf(0.0, 1.0, 1.0), doubleArrayOf(0.0, 1.0, 2.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decreasing knotY is rejected`() {
        transform(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 5.0, 4.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-finite knots are rejected`() {
        transform(doubleArrayOf(0.0, Double.POSITIVE_INFINITY), doubleArrayOf(0.0, 1.0))
    }

    // --- One piece is exactly the affine transform ---

    @Test
    fun `a single piece reproduces the equivalent affine transform`() {
        // Y = 100 + 5X, expressed as one piece running well past the base's support so the
        // terminal clamp never bites.
        val piecewise = transform(doubleArrayOf(0.0, 60.0), doubleArrayOf(100.0, 400.0))
        val affine = AffineTransformDistribution(base, offset = 100.0, scale = 5.0)

        for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)) {
            assertEquals("quantile at p=$p", affine.quantile(p), piecewise.quantile(p), 1e-9)
        }
        for (x in listOf(100.0, 105.0, 120.0, 150.0, 200.0, 300.0)) {
            assertEquals("pdf at $x", affine.pdf(x), piecewise.pdf(x), 1e-12)
            assertEquals("cdf at $x", affine.cdf(x), piecewise.cdf(x), 1e-12)
        }
        // The affine's mean is exact where this one is quadrature, so they agree only to the
        // midpoint rule's discretization error — a few hundredths of a percent on a gamma tail.
        assertEquals(affine.mean, piecewise.mean, 0.05)
    }

    // --- The kink: two pieces with different slopes ---

    // Y = 10X below X = 10, then Y = 100 + 5(X - 10) up to X = 60.
    private val kinked = transform(doubleArrayOf(0.0, 10.0, 60.0), doubleArrayOf(0.0, 100.0, 350.0))

    @Test
    fun `each piece applies its own slope`() {
        // Tolerance is the base quantile's own root-finding precision — the round trip through
        // cdf/quantile is what carries the input in, and it lands within ~1e-8.
        assertEquals(50.0, kinked.quantileOfWarpedInput(5.0), 1e-6)
        assertEquals(100.0, kinked.quantileOfWarpedInput(10.0), 1e-6)
        assertEquals(150.0, kinked.quantileOfWarpedInput(20.0), 1e-6)
    }

    @Test
    fun `quantiles stay monotone and continuous across the kink`() {
        var previous = Double.NEGATIVE_INFINITY
        var p = 0.001
        while (p < 0.999) {
            val q = kinked.quantile(p)
            assertTrue("quantile decreased at p=$p", q >= previous)
            previous = q
            p += 0.001
        }
        // The knot itself is not a jump: approaching 100 m from either side converges on it.
        assertEquals(kinked.quantile(kinked.cdf(100.0) - 1e-7), 100.0, 0.01)
        assertEquals(kinked.quantile(kinked.cdf(100.0) + 1e-7), 100.0, 0.01)
    }

    @Test
    fun `quantile and CDF are inverses across the kink`() {
        for (p in listOf(0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95)) {
            val x = kinked.quantile(p)
            assertEquals("round-trip at p=$p", p, kinked.cdf(x), 1e-6)
        }
    }

    @Test
    fun `density jumps by the slope ratio at the kink`() {
        // Halving the slope at the knot doubles the density just past it: the same probability
        // mass is spread over half as much ground.
        val below = kinked.pdf(100.0 - 1e-6)
        val above = kinked.pdf(100.0 + 1e-6)
        assertEquals(2.0, above / below, 1e-4)
    }

    @Test
    fun `pdf integrates to approximately 1 across the kink`() {
        val dx = 0.01
        var sum = 0.0
        var x = dx / 2
        while (x < 350.0) {
            sum += kinked.pdf(x) * dx
            x += dx
        }
        assertEquals(1.0, sum, 0.01)
    }

    // --- Terminal clamp ---

    // Y = 10X, but only out to X = 10: everything faster piles up at Y = 100.
    private val clamped = transform(doubleArrayOf(0.0, 10.0), doubleArrayOf(0.0, 100.0))

    @Test
    fun `mass beyond the last knot piles up as an atom at the end`() {
        val clampMass = 1.0 - base.cdf(10.0)
        assertTrue("fixture should leave real mass past the knot", clampMass > 0.1)

        assertEquals(1.0, clamped.cdf(100.0), 0.0)
        assertEquals(1.0, clamped.cdf(1e9), 0.0)
        assertEquals(base.cdf(10.0), clamped.cdf(100.0 - 1e-9), 1e-6)

        // The atom carries no density, and no quantile ever runs past the end.
        assertEquals(0.0, clamped.pdf(100.0), 0.0)
        assertEquals(0.0, clamped.pdf(200.0), 0.0)
        assertEquals(100.0, clamped.quantile(1.0 - clampMass / 2), 0.0)
        assertEquals(100.0, clamped.quantile(1.0), 0.0)
    }

    @Test
    fun `below the first knot the transform is flat too`() {
        val shifted = transform(doubleArrayOf(5.0, 15.0), doubleArrayOf(50.0, 150.0))
        assertEquals(50.0, shifted.quantile(0.0), 0.0)
        assertEquals(0.0, shifted.cdf(49.0), 0.0)
        assertEquals(0.0, shifted.pdf(49.0), 0.0)
        // Everything the base puts below X = 5 lands on the first knot.
        assertEquals(base.cdf(5.0), shifted.cdf(50.0 + 1e-9), 1e-6)
    }

    // --- Interior plateau ---

    @Test
    fun `a plateau is an atom whose mass is the base mass it spans`() {
        // Y climbs to 50, holds there from X = 10 to X = 20, then climbs again.
        val plateau =
            transform(doubleArrayOf(0.0, 10.0, 20.0, 30.0), doubleArrayOf(0.0, 50.0, 50.0, 100.0))

        // Right-edge inverse: cdf at the plateau value counts everything up to its far end.
        assertEquals(base.cdf(20.0), plateau.cdf(50.0), 1e-6)
        assertEquals(base.cdf(10.0), plateau.cdf(50.0 - 1e-9), 1e-6)

        val spanned = base.cdf(20.0) - base.cdf(10.0)
        assertTrue("fixture should span real mass", spanned > 0.01)
        for (p in listOf(base.cdf(10.0) + spanned / 4, base.cdf(10.0) + spanned * 3 / 4)) {
            assertEquals("quantile at p=$p", 50.0, plateau.quantile(p), 1e-6)
        }
    }

    // --- Mean ---

    @Test
    fun `mean matches direct numeric integration`() {
        val dx = 0.001
        var expected = 0.0
        var x = dx / 2
        while (x < 100.0) {
            expected += kinkedWarp(x) * base.pdf(x) * dx
            x += dx
        }
        assertEquals(expected, kinked.mean, 0.05)
    }

    // --- Helpers ---

    private fun transform(knotX: DoubleArray, knotY: DoubleArray) = PiecewiseLinearTransformDistribution(base, knotX, knotY)

    /** [kinked]'s transform, written out longhand as the oracle for its own shape. */
    private fun kinkedWarp(x: Double): Double = when {
        x <= 0.0 -> 0.0
        x <= 10.0 -> 10.0 * x
        x <= 60.0 -> 100.0 + 5.0 * (x - 10.0)
        else -> 350.0
    }

    /**
     * The transformed value of a specific base input, reached the only way the public surface
     * allows: through the quantile the base assigns it.
     */
    private fun PiecewiseLinearTransformDistribution.quantileOfWarpedInput(x: Double): Double = quantile(base.cdf(x))
}
