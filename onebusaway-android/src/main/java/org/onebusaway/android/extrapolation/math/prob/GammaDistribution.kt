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

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Gamma distribution parameterized by shape ([alpha]) and [scale]. Provides PDF, CDF, quantile
 * (inverse CDF), and mean.
 */
class GammaDistribution(val alpha: Double, val scale: Double) : ProbDistribution {

    init {
        require(alpha > 0) { "alpha must be positive, got $alpha" }
        require(scale > 0) { "scale must be positive, got $scale" }
    }

    override val mean: Double = alpha * scale

    override fun pdf(x: Double): Double {
        if (x <= 0) return 0.0
        val lnPdf = (alpha - 1) * ln(x) - x / scale - alpha * ln(scale) - lnGamma(alpha)
        return exp(lnPdf)
    }

    override fun cdf(x: Double): Double {
        if (x <= 0) return 0.0
        return regularizedGammaP(alpha, x / scale)
    }

    override fun quantile(p: Double): Double {
        if (p <= 0) return 0.0
        if (p >= 1) return Double.MAX_VALUE

        return bisect(::cdf, p, mean + 10 * sqrt(alpha) * scale)
    }

    companion object {
        private const val MAX_ITERATIONS = 200
        private const val EPSILON = 1e-10

        /** Stand-in for zero in the continued fraction, so a vanishing term can't divide by 0. */
        private const val TINY = 1e-30

        private fun regularizedGammaP(a: Double, x: Double): Double {
            if (x <= 0) return 0.0

            return if (x < a + 1) {
                var sum = 1.0 / a
                var term = 1.0 / a
                for (n in 1..MAX_ITERATIONS) {
                    term *= x / (a + n)
                    sum += term
                    if (abs(term) < EPSILON * abs(sum)) break
                }
                sum * exp(-x + a * ln(x) - lnGamma(a))
            } else {
                // Modified Lentz evaluation of the continued fraction for Q(a, x). The numerator
                // ratio starts at 1/TINY rather than 1: it stands in for a leading term of zero,
                // and seeding it with 1 instead makes the first iteration fold in a spurious
                // `an / 1` term the fraction never converges away from. That understated the CDF
                // across this whole branch — by 11 points at x = a + 1, still 5 points an order of
                // magnitude out — which is upper-tail territory, so it biased every extrapolated
                // confidence band inward.
                var c = 1.0 / TINY
                var d = 1.0 / (x - a + 1)
                var f = d

                for (n in 1..MAX_ITERATIONS) {
                    val an = -n * (n - a)
                    val bn = x - a + 1 + 2 * n

                    d = bn + an * d
                    if (abs(d) < TINY) d = TINY
                    d = 1.0 / d

                    c = bn + an / c
                    if (abs(c) < TINY) c = TINY

                    val delta = c * d
                    f *= delta

                    if (abs(delta - 1.0) < EPSILON) break
                }

                1.0 - exp(-x + a * ln(x) - lnGamma(a)) * f
            }
        }

        private val LN_GAMMA_COEF =
            doubleArrayOf(
                76.18009172947146,
                -86.50532032941677,
                24.01409824083091,
                -1.231739572450155,
                0.1208650973866179e-2,
                -0.5395239384953e-5
            )

        /** Lanczos approximation for ln(Gamma(x)), valid for x > 0. */
        fun lnGamma(x: Double): Double {
            var y = x
            var tmp = x + 5.5
            tmp -= (x + 0.5) * ln(tmp)
            var ser = 1.000000000190015
            for (c in LN_GAMMA_COEF) {
                y += 1.0
                ser += c / y
            }
            return -tmp + ln(2.5066282746310005 * ser / x)
        }
    }
}
