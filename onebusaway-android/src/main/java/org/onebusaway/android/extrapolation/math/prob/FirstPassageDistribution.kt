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

/** Central-difference step for [FirstPassageDistribution.pdf], as a fraction of the profile's extent. */
private const val PDF_STEP_FRACTION = 1e-4

/** Quadrature samples for [FirstPassageDistribution.mean]. */
private const val MEAN_SAMPLES = 64

/**
 * Where a vehicle is after [elapsedSeconds], modelled through the time it takes to get places
 * rather than the speed it travels at.
 *
 * The identity that makes this cheap is
 *
 *     P(D >= d) = P(S(d) <= dt)
 *
 * — a vehicle has passed `d` exactly when its travel time to `d` is within the elapsed time. So
 * the position CDF is one minus a *first-passage-time* CDF, and no distribution over position is
 * ever convolved.
 *
 * Travel time accumulates as a gamma process: the time to cover ground the schedule budgets `tau`
 * seconds for is `Gamma(shape = k*tau/theta, scale = theta)`, mean `k*tau`, variance `k*tau*theta`,
 * where `k` is how much longer a vehicle really takes than the schedule allows.
 * Independent gammas sharing a scale add exactly — shapes sum — so a whole trip stays one gamma
 * however many segments it spans, and one evaluation answers any query.
 *
 * Independence of the increments is the substantive claim, and it is the one measured: on a day of
 * King County Metro AVL the spread of ground covered grows as `dt^0.477`, against the `dt^1.0` a
 * single held pace implies, and a persistent per-trip pace factor fits to zero. That is why the
 * band here widens with the *square root* of elapsed time.
 *
 * @param elapsedSeconds seconds since the anchoring fix
 * @param scheduleSeconds scheduled seconds from the anchor to each knot; non-decreasing. Usually
 *   starts at 0; a pace-warped profile starts above it, an atom of probability at the anchor
 * @param distances distance in meters at each knot; non-decreasing, starts at the anchor's position
 * @param theta gamma-process dispersion in seconds; the travel time to `tau` has variance `tau*theta`
 * @param meanTravelMultiplier how long a vehicle really takes relative to the schedule, in the mean
 */
class FirstPassageDistribution(
    private val elapsedSeconds: Double,
    private val scheduleSeconds: DoubleArray,
    private val distances: DoubleArray,
    private val theta: Double,
    private val meanTravelMultiplier: Double = 1.0
) : ProbDistribution {

    init {
        require(scheduleSeconds.size == distances.size) {
            "knot arrays must be the same length, were ${scheduleSeconds.size} and ${distances.size}"
        }
        require(scheduleSeconds.size >= 2) { "at least 2 knots are required" }
        // An infinite knot stays non-decreasing, so it survives the check below and reaches
        // interpolate() as a span it cannot divide by. NaN would fail that check, but as a knot
        // that "steps back" rather than as what it is.
        for (i in scheduleSeconds.indices) {
            require(scheduleSeconds[i].isFinite() && distances[i].isFinite()) {
                "knots must be finite, but knot $i is (${scheduleSeconds[i]}, ${distances[i]})"
            }
        }
        // Reading the profile -- in either direction -- assumes it only ever goes forward. A knot
        // that steps back reads as a plausible position rather than as an error, so check it here
        // instead of letting it out silently.
        for (i in 1 until scheduleSeconds.size) {
            require(scheduleSeconds[i] >= scheduleSeconds[i - 1] && distances[i] >= distances[i - 1]) {
                "knots must be non-decreasing, but knot $i steps back"
            }
        }
        require(theta > 0 && theta.isFinite()) { "theta must be positive, was $theta" }
        require(meanTravelMultiplier > 0 && meanTravelMultiplier.isFinite()) {
            "meanTravelMultiplier must be positive, was $meanTravelMultiplier"
        }
        require(elapsedSeconds >= 0 && elapsedSeconds.isFinite()) {
            "elapsedSeconds must be non-negative, was $elapsedSeconds"
        }
    }

    /** [elapsedSeconds] in units of [theta] — the incomplete gamma's x, fixed across every query. */
    private val elapsedOverTheta = elapsedSeconds / theta

    /**
     * P(the vehicle has already covered [tau] seconds' worth of schedule). Decreasing in [tau] —
     * the further ahead you look, the less likely it has got there.
     *
     * Only the *shape* varies with [tau], so this calls the incomplete-gamma kernel directly rather
     * than building a [GammaDistribution] per evaluation: the map's uncertainty band and the
     * trajectory view's density read it through [pdf] tens to hundreds of times per frame.
     */
    private fun reached(tau: Double): Double {
        // No ground takes no time, so it is certainly covered. (A zero gamma shape is not a
        // distribution, which is the same statement.)
        if (tau <= 0.0) return 1.0
        return GammaDistribution.regularizedGammaP(meanTravelMultiplier * tau / theta, elapsedOverTheta)
    }

    override fun cdf(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x <= distances.first()) return 0.0
        if (x >= distances.last()) return 1.0
        return (1.0 - reached(interpolate(distances, scheduleSeconds, x))).coerceIn(0.0, 1.0)
    }

    /**
     * Solved rather than searched for. `1 - reached(tau) = p` is `P(m*tau/theta, dt/theta) = 1 - p`,
     * so the answer in schedule time is
     *
     *     tau = A(1 - p, dt/theta) * theta / m
     *
     * where `A` inverts the incomplete gamma in its *shape* — a curve that knows nothing about
     * theta, the travel multiplier or the schedule, and is therefore shared across every vehicle
     * (see [IncompleteGammaShape]). Answering in schedule time rather than distance keeps it
     * well-behaved across dwell plateaus, where a whole range of schedule times maps to one
     * distance; [interpolate] then clamps `tau` past the end of the profile to its last knot.
     */
    override fun quantile(p: Double): Double {
        if (p.isNaN()) return Double.NaN
        if (p <= 0.0) return distances.first()
        if (p >= 1.0) return distances.last()
        return positionAtShape(IncompleteGammaShape.shapeFor(1.0 - p, elapsedOverTheta))
    }

    /** Where a gamma [shape] worth of schedule time puts the vehicle, clamped to the profile. */
    private fun positionAtShape(shape: Double) = interpolate(scheduleSeconds, distances, shape * theta / meanTravelMultiplier)

    /**
     * Density at [x], by central difference on the CDF.
     *
     * The exact derivative would need the derivative of the incomplete gamma with respect to its
     * *shape*, which has no elementary form. The step is a fixed fraction of the profile's own
     * extent, so it scales with the distribution rather than being an absolute guess, and the
     * density is only ever used for relative shading — the uncertainty band's alphas and the
     * trajectory histogram — never as a probability.
     */
    override fun pdf(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x <= distances.first() || x >= distances.last()) return 0.0
        val step = (distances.last() - distances.first()) * PDF_STEP_FRACTION
        val lo = (x - step).coerceAtLeast(distances.first())
        val hi = (x + step).coerceAtMost(distances.last())
        if (hi <= lo) return 0.0
        return ((cdf(hi) - cdf(lo)) / (hi - lo)).coerceAtLeast(0.0)
    }

    /**
     * Mean position, by midpoint quadrature over the quantile function. No consumer of an
     * extrapolated distance reads the mean — the map, the band and the trajectory view all work in
     * quantiles — so the approximation buys simplicity at no cost. It is `lazy` because the sweep is
     * genuinely expensive; nothing currently triggers it.
     *
     * The samples go through [IncompleteGammaShape.solveShapeFor] rather than [quantile]: 64 distinct
     * levels through the shared tables would evict every level the map draws, so a mean here would
     * slow the next frame down there.
     */
    override val mean: Double by lazy(LazyThreadSafetyMode.NONE) {
        var sum = 0.0
        for (i in 0 until MEAN_SAMPLES) {
            val level = 1.0 - (i + 0.5) / MEAN_SAMPLES
            sum += positionAtShape(IncompleteGammaShape.solveShapeFor(level, elapsedOverTheta))
        }
        sum / MEAN_SAMPLES
    }
}

/**
 * Reads the piecewise-linear curve ([from], [to]) at [value], clamped to its ends. On a plateau —
 * several knots sharing a [from] value — this takes the first, which for a dwell is the moment the
 * vehicle arrives rather than when it leaves.
 */
private fun interpolate(from: DoubleArray, to: DoubleArray, value: Double): Double {
    if (value <= from.first()) return to.first()
    if (value >= from.last()) return to.last()
    var lo = 0
    var hi = from.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (from[mid] < value) lo = mid + 1 else hi = mid
    }
    val span = from[lo] - from[lo - 1]
    if (span <= 0.0) return to[lo]
    val fraction = (value - from[lo - 1]) / span
    return to[lo - 1] + fraction * (to[lo] - to[lo - 1])
}
