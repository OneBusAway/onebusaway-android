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

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.junit.Test

class UncertaintyBandTest {

    /** Uniform on [0, [width]]: constant PDF, linear quantile — every band slice should be full alpha. */
    private class UniformDist(private val width: Double) : ProbDistribution {
        override val mean = width / 2
        override fun pdf(x: Double) = if (x in 0.0..width) 1.0 / width else 0.0
        override fun cdf(x: Double) = (x / width).coerceIn(0.0, 1.0)
        override fun quantile(p: Double) = p * width
    }

    /** Symmetric triangular on [0, 2c] peaking at c: closed-form PDF/quantile, so alphas are checkable. */
    private class TriangularDist(private val c: Double) : ProbDistribution {
        override val mean = c
        override fun pdf(x: Double) = when {
            x < 0 || x > 2 * c -> 0.0
            x <= c -> x / (c * c)
            else -> (2 * c - x) / (c * c)
        }
        override fun cdf(x: Double) = when {
            x <= 0 -> 0.0
            x <= c -> x * x / (2 * c * c)
            x < 2 * c -> 1 - (2 * c - x) * (2 * c - x) / (2 * c * c)
            else -> 1.0
        }
        override fun quantile(p: Double) =
            if (p <= 0.5) c * sqrt(2 * p) else 2 * c - c * sqrt(2 * (1 - p))
    }

    /** A distribution with no finite quantile (the degenerate case the contract warns about). */
    private object NaNQuantileDist : ProbDistribution {
        override val mean = Double.NaN
        override fun pdf(x: Double) = 0.0
        override fun cdf(x: Double) = Double.NaN
        override fun quantile(p: Double) = Double.NaN
    }

    @Test
    fun `uniform distribution yields the requested count of contiguous full-alpha slices`() {
        val slices = uncertaintyBandSlices(UniformDist(1500.0))

        assertEquals(BAND_SEGMENT_COUNT, slices.size)
        // [0.01, 0.99] of Uniform[0,1500] = [15, 1485], split into 15 slices of width 98.
        assertEquals(15.0, slices.first().startDist, 1e-9)
        assertEquals(1485.0, slices.last().endDist, 1e-9)
        slices.zipWithNext { a, b -> assertEquals(a.endDist, b.startDist, 1e-9) } // contiguous
        slices.forEach { assertEquals("constant PDF -> uniform alpha", 1.0f, it.alpha, 1e-6f) }
    }

    @Test
    fun `alpha is the per-slice PDF normalized to the band peak`() {
        val slices = uncertaintyBandSlices(TriangularDist(500.0))

        assertEquals(BAND_SEGMENT_COUNT, slices.size)
        // The middle slice's midpoint lands exactly on the peak (500m), so it normalizes to 1.0.
        assertEquals(1.0f, slices[7].alpha, 1e-6f)
        assertEquals("peak is the max", 1.0f, slices.maxOf { it.alpha }, 1e-6f)
        // Symmetric distribution -> symmetric alphas, fading toward the tails.
        assertEquals(slices[0].alpha, slices[14].alpha, 1e-6f)
        assertTrue("tails fade below the peak", slices[0].alpha < slices[7].alpha)
        assertTrue(slices[0].alpha > 0f)
    }

    @Test
    fun `the segment count is configurable`() {
        assertEquals(5, uncertaintyBandSlices(UniformDist(1000.0), count = 5).size)
    }

    @Test
    fun `a non-finite quantile yields no band`() {
        assertTrue(uncertaintyBandSlices(NaNQuantileDist).isEmpty())
    }

    @Test
    fun `a zero-width range yields no band`() {
        // low == high quantile -> nothing to draw.
        assertTrue(uncertaintyBandSlices(UniformDist(1500.0), lowQuantile = 0.5, highQuantile = 0.5).isEmpty())
    }

    @Test
    fun `a non-positive segment count yields no band`() {
        assertTrue(uncertaintyBandSlices(UniformDist(1500.0), count = 0).isEmpty())
    }
}
