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
package org.onebusaway.android.extrapolation.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotoneSplineTest {

    // --- Construction ---

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched knot arrays are rejected`() {
        MonotoneSpline(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a single knot is rejected`() {
        MonotoneSpline(doubleArrayOf(0.0), doubleArrayOf(1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-increasing knots are rejected`() {
        MonotoneSpline(doubleArrayOf(0.0, 1.0, 1.0), doubleArrayOf(0.0, 1.0, 2.0))
    }

    // --- Agreement with scipy ---

    @Test
    fun `matches scipy PchipInterpolator on the shipped deviation curve`() {
        // The dispersion curve from h37_penalized_params.json, with reference values produced by
        // scipy.interpolate.PchipInterpolator. If the tangent construction here ever drifts from
        // scipy's, the shipped model stops matching the one that was fitted and validated.
        val spline = MonotoneSpline(
            doubleArrayOf(-250.0, -110.0, 60.0, 300.0, 600.0, 900.0),
            doubleArrayOf(4.982657, 3.540953, 3.094457, 3.268313, 3.356563, 3.786251)
        )
        val xs = doubleArrayOf(-250.0, -200.0, -110.0, -20.0, 60.0, 150.0, 300.0, 450.0, 600.0, 750.0, 900.0)
        val expected = doubleArrayOf(
            4.982657, 4.326944, 3.540953, 3.212982, 3.094457, 3.140500,
            3.268313, 3.310075, 3.356563, 3.514659, 3.786251
        )
        for (i in xs.indices) {
            assertEquals("at x=${xs[i]}", expected[i], spline.valueAt(xs[i]), 1e-5)
        }
    }

    // --- Shape preservation ---

    @Test
    fun `never overshoots the knots it passes between`() {
        // A step-like set of knots is where an ordinary cubic rings; this must not.
        val spline = MonotoneSpline(
            doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0),
            doubleArrayOf(0.0, 0.0, 1.0, 1.0, 1.0)
        )
        var x = 0.0
        while (x <= 4.0) {
            val v = spline.valueAt(x)
            assertTrue("overshot below at $x: $v", v >= -1e-12)
            assertTrue("overshot above at $x: $v", v <= 1.0 + 1e-12)
            x += 0.01
        }
    }

    @Test
    fun `preserves monotone data`() {
        val spline = MonotoneSpline(
            doubleArrayOf(0.0, 1.0, 2.0, 3.0),
            doubleArrayOf(0.0, 2.0, 2.5, 10.0)
        )
        var previous = Double.NEGATIVE_INFINITY
        var x = 0.0
        while (x <= 3.0) {
            val v = spline.valueAt(x)
            assertTrue("decreased at $x", v >= previous - 1e-12)
            previous = v
            x += 0.005
        }
    }

    // --- Clamping ---

    @Test
    fun `is flat outside the knot range`() {
        val spline = MonotoneSpline(doubleArrayOf(-250.0, 0.0, 900.0), doubleArrayOf(5.0, 3.0, 4.0))
        assertEquals(5.0, spline.valueAt(-250.0), 0.0)
        assertEquals(5.0, spline.valueAt(-1000.0), 0.0)
        assertEquals(5.0, spline.valueAt(-1e9), 0.0)
        assertEquals(4.0, spline.valueAt(900.0), 0.0)
        assertEquals(4.0, spline.valueAt(5000.0), 0.0)
    }

    @Test
    fun `passes exactly through every knot`() {
        val xs = doubleArrayOf(-250.0, -110.0, 60.0, 300.0, 600.0, 900.0)
        val ys = doubleArrayOf(0.298932, 0.115495, 0.041843, 0.019873, 0.094518, 0.044967)
        val spline = MonotoneSpline(xs, ys)
        for (i in xs.indices) {
            assertEquals("knot $i", ys[i], spline.valueAt(xs[i]), 1e-12)
        }
    }

    @Test
    fun `two knots interpolate linearly`() {
        val spline = MonotoneSpline(doubleArrayOf(0.0, 10.0), doubleArrayOf(2.0, 12.0))
        assertEquals(2.0, spline.valueAt(0.0), 1e-12)
        assertEquals(7.0, spline.valueAt(5.0), 1e-12)
        assertEquals(12.0, spline.valueAt(10.0), 1e-12)
    }
}
