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

import kotlin.math.sqrt

/**
 * Two-component gamma mixture: f(x) = w * Gamma(a1,s1) + (1-w) * Gamma(a2,s2).
 *
 * @param weight mixture weight for the first component (must be in [0, 1])
 * @param comp1 first gamma component
 * @param comp2 second gamma component
 */
class GammaMixtureDistribution(
        private val weight: Double,
        private val comp1: GammaDistribution,
        private val comp2: GammaDistribution
) : ProbDistribution {

    init {
        require(weight in 0.0..1.0) { "weight must be in [0, 1], got $weight" }
    }

    override val mean: Double = weight * comp1.mean + (1 - weight) * comp2.mean

    private val quantileHi: Double = run {
        fun secondMoment(g: GammaDistribution) = g.alpha * g.scale * g.scale + g.mean * g.mean
        val variance =
                (weight * secondMoment(comp1) + (1 - weight) * secondMoment(comp2) - mean * mean)
                        .coerceAtLeast(0.0)
        mean + 10 * sqrt(variance)
    }

    override fun pdf(x: Double): Double = weight * comp1.pdf(x) + (1 - weight) * comp2.pdf(x)

    override fun cdf(x: Double): Double = weight * comp1.cdf(x) + (1 - weight) * comp2.cdf(x)

    override fun quantile(p: Double): Double {
        if (p <= 0.0) return 0.0
        if (p >= 1.0) return Double.MAX_VALUE
        return bisect(::cdf, p, quantileHi)
    }
}
