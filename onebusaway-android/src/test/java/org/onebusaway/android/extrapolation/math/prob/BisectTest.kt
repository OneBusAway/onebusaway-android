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

/**
 * Tests for the [bisect] root-finding utility. These guard against infinite loops and NaN
 * propagation from degenerate inputs.
 */
class BisectTest {

    // --- Normal operation ---

    @Test
    fun `finds root of linear CDF`() {
        // f(x) = x/10, target = 0.5 => x = 5
        val result = bisect({ x -> x / 10.0 }, 0.5, 10.0)
        assertEquals(5.0, result, 1e-6)
    }

    @Test
    fun `finds root when initialHi needs bracketing`() {
        // f(x) = x/100, target = 0.5 => x = 50. initialHi = 1.0 needs doubling.
        val result = bisect({ x -> x / 100.0 }, 0.5, 1.0)
        assertEquals(50.0, result, 1e-6)
    }

    // --- Degenerate initialHi values ---

    @Test(timeout = 1000)
    fun `zero initialHi does not loop forever`() {
        val result = bisect({ x -> x / 10.0 }, 0.5, 0.0)
        // Should fall back to 1.0 and find the root normally
        assertEquals(5.0, result, 1e-6)
    }

    @Test(timeout = 1000)
    fun `negative initialHi does not loop forever`() {
        val result = bisect({ x -> x / 10.0 }, 0.5, -5.0)
        assertEquals(5.0, result, 1e-6)
    }

    @Test(timeout = 1000)
    fun `NaN initialHi does not loop forever`() {
        val result = bisect({ x -> x / 10.0 }, 0.5, Double.NaN)
        assertEquals(5.0, result, 1e-6)
    }

    @Test(timeout = 1000)
    fun `positive infinity initialHi does not loop forever`() {
        val result = bisect({ x -> x / 10.0 }, 0.5, Double.POSITIVE_INFINITY)
        // Falls back to 1.0
        assertEquals(5.0, result, 1e-6)
    }

    @Test(timeout = 1000)
    fun `negative infinity initialHi does not loop forever`() {
        val result = bisect({ x -> x / 10.0 }, 0.5, Double.NEGATIVE_INFINITY)
        assertEquals(5.0, result, 1e-6)
    }

    // --- Pathological CDF functions ---

    @Test(timeout = 1000)
    fun `CDF that always returns zero reports NaN`() {
        // f(x) = 0 for all x. bisect can never bracket — the bracket loop hits its
        // iteration cap (or hi overflows to +Infinity) and returns NaN as the
        // explicit "no valid answer" sentinel. Callers (DistanceEstimateOverlay,
        // TripExtrapolationController) check for NaN and degrade gracefully.
        val result = bisect({ 0.0 }, 0.5, 1.0)
        assertTrue("Non-convergent bracket should report NaN", result.isNaN())
    }

    @Test(timeout = 1000)
    fun `CDF that returns NaN terminates with a finite value`() {
        // NaN < target is always false, so the bracket loop exits immediately and
        // the refine loop halves hi toward zero without lo ever moving up. After
        // the refine iter cap fires, the midpoint of the still-tiny bracket is a
        // finite (astronomically small) value — the same shape as a legitimately
        // hyperconcentrated distribution, which bisect is allowed to return.
        val result = bisect({ Double.NaN }, 0.5, 1.0)
        assertTrue(
                "Result should be finite (refine cap returns best-effort midpoint)",
                result.isFinite()
        )
    }

    // --- Integration with real distributions ---

    @Test(timeout = 2000)
    fun `GammaDistribution quantile does not hang for very small alpha`() {
        val dist = GammaDistribution(0.01, 1.0)
        val q = dist.quantile(0.5)
        assertTrue("Quantile should be finite and positive", q.isFinite() && q > 0)
    }

    @Test(timeout = 2000)
    fun `GammaDistribution quantile does not hang for very large alpha`() {
        val dist = GammaDistribution(1000.0, 0.001)
        val q = dist.quantile(0.5)
        assertTrue("Quantile should be finite and positive", q.isFinite() && q > 0)
    }

    @Test(timeout = 2000)
    fun `GammaMixtureDistribution quantile does not hang with extreme weight`() {
        // weight very close to 1.0 — the denominator (1-m) in GammaExtrapolator
        // approaches zero, similar to what happens with extreme schedule speeds
        val slow = GammaDistribution(0.27, 1.0)
        val fast = GammaDistribution(0.1, 100.0)
        val mix = GammaMixtureDistribution(0.999, slow, fast)
        val q = mix.quantile(0.5)
        assertTrue("Quantile should be finite", q.isFinite())
    }

    @Test(timeout = 2000)
    fun `GammaMixtureDistribution quantile does not produce NaN`() {
        // Components with very different scales can cause floating-point
        // precision loss in the variance calculation
        val comp1 = GammaDistribution(0.5, 0.001)
        val comp2 = GammaDistribution(100.0, 100.0)
        val mix = GammaMixtureDistribution(0.5, comp1, comp2)
        val q = mix.quantile(0.5)
        assertTrue("Quantile should be finite", q.isFinite())
        assertTrue("Quantile should be non-negative", q >= 0)
    }

    @Test(timeout = 2000)
    fun `GammaMixtureDistribution with identical components has valid quantile`() {
        // When both components are the same, variance should be exactly that
        // of the single component — no precision issues
        val comp = GammaDistribution(3.0, 2.0)
        val mix = GammaMixtureDistribution(0.5, comp, comp)
        val q = mix.quantile(0.5)
        val expected = comp.quantile(0.5)
        assertEquals(expected, q, 1e-6)
    }
}
