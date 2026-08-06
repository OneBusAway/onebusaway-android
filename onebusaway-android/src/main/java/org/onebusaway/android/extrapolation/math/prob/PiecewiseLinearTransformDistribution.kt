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

/** Samples used by the [PiecewiseLinearTransformDistribution.mean] quadrature. */
private const val MEAN_QUADRATURE_SAMPLES = 200

/**
 * Wraps a base distribution with a monotone piecewise-linear transform: Y = W(X), where W is the
 * polyline through the knots ([knotX], [knotY]) and is **flat outside** their range. The
 * generalization of [AffineTransformDistribution] from one line to many: with two knots the two are
 * the same map (below the terminal clamp).
 *
 * Because W is monotone it is quantile-preserving — the p-th quantile of Y is W applied to the p-th
 * quantile of X — so bending W bends every quantile together rather than only the median. That is
 * what lets the extrapolator carry a *whole confidence interval* through a scheduled speed change
 * (#2137): each piece is one segment's speed, and the band compresses or stretches as it crosses a
 * boundary.
 *
 * The flat ends are genuine atoms, not a modelling shortcut: the mass of X beyond the last knot
 * piles up at `knotY.last()` (the vehicle has reached the end of the schedule and stops). Following
 * [DiracDistribution] and [AffineTransformDistribution]'s zero-scale case, an atom's [pdf] is 0
 * while its mass shows up as a jump in the [cdf].
 *
 * Interior plateaus (equal consecutive [knotY] with advancing [knotX]) are allowed and behave the
 * same way — an atom at that distance. Where the transform is invertible the inverse takes the
 * **right** edge of a plateau, which is what makes the cdf right-continuous.
 *
 * The knot arrays are held by reference, not copied, so the caller must not mutate them afterwards
 * — this type is built on a per-frame path and its producer shares one cached profile across
 * frames.
 *
 * @param knotX transform inputs; strictly increasing, all finite
 * @param knotY transform outputs; non-decreasing, all finite, same length as [knotX]
 */
class PiecewiseLinearTransformDistribution(
    private val base: ProbDistribution,
    private val knotX: DoubleArray,
    private val knotY: DoubleArray
) : ProbDistribution {

    init {
        require(knotX.size == knotY.size) {
            "knot arrays must be the same length, were ${knotX.size} and ${knotY.size}"
        }
        require(knotX.size >= 2) { "at least 2 knots are required, got ${knotX.size}" }
        for (i in knotX.indices) {
            require(knotX[i].isFinite() && knotY[i].isFinite()) {
                "knots must be finite, got (${knotX[i]}, ${knotY[i]}) at $i"
            }
        }
        for (i in 0 until knotX.size - 1) {
            require(knotX[i] < knotX[i + 1]) {
                "knotX must be strictly increasing, got ${knotX[i]} then ${knotX[i + 1]} at $i"
            }
            require(knotY[i] <= knotY[i + 1]) {
                "knotY must be non-decreasing, got ${knotY[i]} then ${knotY[i + 1]} at $i"
            }
        }
    }

    /**
     * Midpoint quadrature in quantile space, at the same resolution [FrozenDistribution] tabulates
     * at. Exact evaluation would need partial expectations of the base over each piece, which the
     * [ProbDistribution] interface does not expose; no consumer of an extrapolated distance reads
     * the mean (the map, the band and the trajectory view all work in quantiles), so the
     * approximation buys simplicity at no cost. Computed on demand, and only once.
     */
    override val mean: Double by lazy(LazyThreadSafetyMode.NONE) {
        var sum = 0.0
        for (i in 0 until MEAN_QUADRATURE_SAMPLES) {
            sum += warp(base.quantile((i + 0.5) / MEAN_QUADRATURE_SAMPLES))
        }
        sum / MEAN_QUADRATURE_SAMPLES
    }

    override fun quantile(p: Double): Double = warp(base.quantile(p))

    override fun cdf(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x < knotY.first()) return 0.0
        // Includes the terminal atom: everything the base puts beyond the last knot lands here.
        if (x >= knotY.last()) return 1.0
        val i = lastIndexAtOrBelow(knotY, x)
        return base.cdf(invertInPiece(i, x))
    }

    override fun pdf(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        // Atoms (the two clamps) and everything outside the range carry no density.
        if (x < knotY.first() || x >= knotY.last()) return 0.0
        val i = lastIndexAtOrBelow(knotY, x)
        // Change of variables: dividing by the piece's slope dy/dx.
        val slope = (knotY[i + 1] - knotY[i]) / (knotX[i + 1] - knotX[i])
        return base.pdf(invertInPiece(i, x)) / slope
    }

    /** W applied to [x], clamped flat outside the knot range. */
    private fun warp(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x <= knotX.first()) return knotY.first()
        if (x >= knotX.last()) return knotY.last()
        val i = lastIndexAtOrBelow(knotX, x)
        val fraction = (x - knotX[i]) / (knotX[i + 1] - knotX[i])
        return knotY[i] + fraction * (knotY[i + 1] - knotY[i])
    }

    /**
     * The x such that W(x) = [y], where piece [i] is the one [lastIndexAtOrBelow] found for [y].
     * That piece always has positive slope: `knotY[i] <= y` while `knotY[i + 1] > y`, so a plateau
     * can never be the bracketing piece and the division is safe.
     */
    private fun invertInPiece(i: Int, y: Double): Double {
        val fraction = (y - knotY[i]) / (knotY[i + 1] - knotY[i])
        return knotX[i] + fraction * (knotX[i + 1] - knotX[i])
    }
}

/**
 * The largest index `i` with `sorted[i] <= value`, by binary search. Callers must have established
 * that `sorted.first() <= value < sorted.last()`, which bounds the result to a valid piece start.
 */
private fun lastIndexAtOrBelow(sorted: DoubleArray, value: Double): Int {
    var lo = 0
    var hi = sorted.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (sorted[mid] <= value) lo = mid else hi = mid - 1
    }
    return lo
}
