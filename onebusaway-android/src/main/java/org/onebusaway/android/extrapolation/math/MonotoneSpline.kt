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

import kotlin.math.abs
import kotlin.math.sign

/**
 * Shape-preserving cubic interpolation through fixed knots (PCHIP), clamped flat outside them.
 *
 * "Shape-preserving" is the point: between two knots the curve never overshoots the values at
 * those knots, and it cannot invent a wiggle the knots don't imply. An ordinary cubic through
 * the same points can do both, and past the outermost knot a polynomial accelerates away
 * without limit — which is exactly how a curve fitted to a region with little data ends up
 * making absurd claims about it. Clamping outside the knot range says the honest thing
 * instead: beyond here we stop distinguishing.
 *
 * This is the same construction `scipy.interpolate.PchipInterpolator` uses — tangents from the
 * weighted harmonic mean of neighbouring secants, zeroed at local extrema — so a curve fitted
 * in Python evaluates identically here.
 *
 * @param knotX knot positions; strictly increasing, at least two
 * @param knotY knot values
 */
class MonotoneSpline(private val knotX: DoubleArray, private val knotY: DoubleArray) {

    private val tangents: DoubleArray

    init {
        require(knotX.size == knotY.size) {
            "knot arrays must be the same length, were ${knotX.size} and ${knotY.size}"
        }
        require(knotX.size >= 2) { "at least 2 knots are required, got ${knotX.size}" }
        for (i in 0 until knotX.size - 1) {
            require(knotX[i] < knotX[i + 1]) {
                "knotX must be strictly increasing, got ${knotX[i]} then ${knotX[i + 1]} at $i"
            }
        }
        tangents = computeTangents()
    }

    /** The curve at [x], clamped to the end knot values outside the knot range. */
    fun valueAt(x: Double): Double {
        if (x <= knotX.first()) return knotY.first()
        if (x >= knotX.last()) return knotY.last()

        var lo = 0
        var hi = knotX.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (knotX[mid] <= x) lo = mid else hi = mid - 1
        }

        val h = knotX[lo + 1] - knotX[lo]
        val t = (x - knotX[lo]) / h
        val t2 = t * t
        val t3 = t2 * t
        // Cubic Hermite basis.
        return knotY[lo] *
            (2 * t3 - 3 * t2 + 1) +
            tangents[lo] *
            h *
            (t3 - 2 * t2 + t) +
            knotY[lo + 1] *
            (-2 * t3 + 3 * t2) +
            tangents[lo + 1] *
            h *
            (t3 - t2)
    }

    /**
     * Tangents that keep each interval monotone: the weighted harmonic mean of the neighbouring
     * secants, forced to zero wherever they disagree in sign (a local extremum, where any
     * non-zero tangent would overshoot).
     */
    private fun computeTangents(): DoubleArray {
        val n = knotX.size
        val widths = DoubleArray(n - 1) { knotX[it + 1] - knotX[it] }
        val secants = DoubleArray(n - 1) { (knotY[it + 1] - knotY[it]) / widths[it] }
        val out = DoubleArray(n)

        // Two knots is a straight line; the three-point end estimate has nothing to work with.
        if (n == 2) return DoubleArray(2) { secants[0] }

        for (i in 1 until n - 1) {
            out[i] = if (secants[i - 1] * secants[i] <= 0.0) {
                0.0
            } else {
                val w1 = 2 * widths[i] + widths[i - 1]
                val w2 = widths[i] + 2 * widths[i - 1]
                (w1 + w2) / (w1 / secants[i - 1] + w2 / secants[i])
            }
        }
        out[0] = endTangent(widths[0], widths[1], secants[0], secants[1])
        out[n - 1] = endTangent(widths[n - 2], widths[n - 3], secants[n - 2], secants[n - 3])
        return out
    }

    /**
     * One-sided end tangent: a three-point estimate, held back so the end interval cannot
     * overshoot — zeroed if it would turn the curve around, and capped at three times the
     * adjacent secant if that secant is itself a turning point.
     */
    private fun endTangent(h0: Double, h1: Double, s0: Double, s1: Double): Double {
        val d = ((2 * h0 + h1) * s0 - h0 * s1) / (h0 + h1)
        if (sign(d) != sign(s0)) return 0.0
        if (sign(s0) != sign(s1) && abs(d) > abs(3 * s0)) return 3 * s0
        return d
    }
}
