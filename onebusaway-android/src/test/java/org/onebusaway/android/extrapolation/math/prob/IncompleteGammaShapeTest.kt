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
import kotlin.math.exp
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quantile levels the app draws, plus the extremes of the trajectory view's density window. */
private val LEVELS = doubleArrayOf(0.001, 0.01, 0.1, 0.5, 0.9, 0.99, 0.999)

/**
 * How far the tabulated inverse may sit from a direct solve, relatively. This is deliberately close
 * to the interpolation's measured worst case — 3.4e-7, at level 0.999 where the curve is steepest —
 * so that it pins the figure `IncompleteGammaShape` documents to justify its node count, rather than
 * merely catching a table that had stopped being interpolated at all.
 */
private const val TABLE_TOLERANCE = 1e-6

class IncompleteGammaShapeTest {

    /**
     * The same geometric bisection the production solver runs, but to convergence rather than to a
     * fixed 60 steps. Sharing the kernel is the point: it pins the *interpolation* and nothing else,
     * leaving the incomplete gamma itself to [GammaDistributionTest].
     */
    private fun reference(level: Double, x: Double): Double {
        var lo = ln(1e-12)
        var hi = ln(1e4)
        repeat(100) {
            val mid = (lo + hi) / 2
            if (GammaDistribution.regularizedGammaP(exp(mid), x) > level) lo = mid else hi = mid
        }
        return exp((lo + hi) / 2)
    }

    /** Log-spaced arguments, deliberately offset so they land between table nodes. */
    private fun sampleArguments(count: Int, min: Double = 1e-4, max: Double = 256.0) = (0 until count).map { exp(ln(min) + (ln(max) - ln(min)) * (it + 0.5) / count) }

    /** Every level, against [reference], at each of [xs]. */
    private fun assertMatchesReference(xs: Iterable<Double>) {
        for (level in LEVELS) {
            for (x in xs) {
                val expected = reference(level, x)
                val actual = IncompleteGammaShape.shapeFor(level, x)
                assertTrue(
                    "level=$level x=$x expected $expected but got $actual",
                    abs(actual - expected) <= TABLE_TOLERANCE * expected
                )
            }
        }
    }

    // --- Agreement with a direct solve ---

    @Test
    fun `matches a direct solve across the tabulated range`() = assertMatchesReference(sampleArguments(200))

    @Test
    fun `matches a direct solve at the edges of the tabulated range`() {
        // Where the interpolation stencil is most nearly one-sided, and (at the low end) where the
        // curve is steepest in the argument.
        assertMatchesReference(listOf(1e-4, 1.02e-4, 1.1e-4, 2e-4, 200.0, 255.0, 255.99, 256.0))
    }

    @Test
    fun `an argument outside the tabulated range is solved rather than clamped`() {
        // Below X_MIN and above X_MAX there is no table to read, so these exercise the direct path
        // and pin that it stays exact instead of pinning the ends of the table.
        assertMatchesReference(listOf(1e-7, 1e-5, 9.9e-5, 300.0, 1000.0, 5000.0))
    }

    // --- Against closed forms ---

    @Test
    fun `recovers the shapes whose incomplete gamma is elementary`() {
        // P(1, x) = 1 - exp(-x), and P(2, x) = 1 - exp(-x) * (1 + x). Asking for the level those
        // produce must give back the shape that produced it. Held to moderate x: past x ~ 36 the
        // level rounds to 1.0 and the test would be measuring double precision, not the inverse.
        for (x in sampleArguments(100, min = 1e-2, max = 10.0)) {
            assertEquals("shape 1 at x=$x", 1.0, IncompleteGammaShape.shapeFor(1 - exp(-x), x), 1e-5)
            assertEquals("shape 2 at x=$x", 2.0, IncompleteGammaShape.shapeFor(1 - exp(-x) * (1 + x), x), 2e-5)
        }
    }

    @Test
    fun `the median shape approaches the classic gamma-median result`() {
        // The median of Gamma(a, 1) is a - 1/3 + O(1/a), so inverting at level 0.5 gives x + 1/3.
        // The tolerance is that remainder — 0.004 at x = 5, shrinking as 1/x from there — not the
        // table's error, which is nine orders of magnitude smaller.
        for (x in listOf(5.0, 10.0, 20.0, 41.0, 100.0)) {
            assertEquals("x=$x", x + 1.0 / 3.0, IncompleteGammaShape.shapeFor(0.5, x), 5e-3)
        }
    }

    // --- Structure ---

    @Test
    fun `the shape round-trips through the incomplete gamma`() {
        for (level in LEVELS) {
            for (x in sampleArguments(100)) {
                val shape = IncompleteGammaShape.shapeFor(level, x)
                assertEquals("level=$level x=$x", level, GammaDistribution.regularizedGammaP(shape, x), 1e-5)
            }
        }
    }

    @Test
    fun `the shape rises with the argument and falls with the level`() {
        for (level in LEVELS) {
            var previous = Double.NEGATIVE_INFINITY
            for (x in sampleArguments(600)) {
                val shape = IncompleteGammaShape.shapeFor(level, x)
                assertTrue("level=$level went backwards at x=$x", shape >= previous)
                previous = shape
            }
        }
        for (x in listOf(0.01, 1.0, 10.0, 41.0)) {
            var previous = Double.POSITIVE_INFINITY
            for (level in LEVELS) {
                val shape = IncompleteGammaShape.shapeFor(level, x)
                assertTrue("x=$x rose at level=$level", shape <= previous)
                previous = shape
            }
        }
    }

    // --- Degenerate arguments ---

    @Test
    fun `a zero argument has no shape at all`() {
        // P(a, 0) is 0 for every shape, so nothing has been covered and the limiting shape is 0.
        assertEquals(0.0, IncompleteGammaShape.shapeFor(0.5, 0.0), 0.0)
        assertEquals(0.0, IncompleteGammaShape.shapeFor(0.999, 0.0), 0.0)
    }

    @Test
    fun `unattainable levels resolve to the limiting shape`() {
        // P never reaches 1 or 0, so these have no solution. Certainty bottoms out at a shape of
        // nothing; impossibility runs out to a shape with no mass at x at all.
        assertTrue(IncompleteGammaShape.shapeFor(1.0, 10.0) <= 1e-11)
        val unreachable = IncompleteGammaShape.shapeFor(0.0, 10.0)
        assertTrue("got $unreachable", unreachable > 100.0)
        assertTrue(GammaDistribution.regularizedGammaP(unreachable, 10.0) < 1e-300)
    }

    // --- Cache behaviour ---

    @Test
    fun `the uncached solve agrees with the tabulated one`() {
        for (level in LEVELS) {
            for (x in listOf(0.01, 1.0, 12.5, 41.0)) {
                val tabulated = IncompleteGammaShape.shapeFor(level, x)
                val solved = IncompleteGammaShape.solveShapeFor(level, x)
                assertTrue(
                    "level=$level x=$x tabulated $tabulated but solved $solved",
                    abs(solved - tabulated) <= TABLE_TOLERANCE * tabulated
                )
            }
        }
        assertEquals(0.0, IncompleteGammaShape.solveShapeFor(0.5, 0.0), 0.0)
    }

    @Test
    fun `answers survive being evicted from the table cache`() {
        val before = IncompleteGammaShape.shapeFor(0.5, 7.5)
        // More distinct levels than the cache holds, so 0.5's table is certainly evicted.
        for (i in 1..64) {
            IncompleteGammaShape.shapeFor(i / 65.0, 7.5)
        }
        assertEquals(before, IncompleteGammaShape.shapeFor(0.5, 7.5), 0.0)
    }
}
