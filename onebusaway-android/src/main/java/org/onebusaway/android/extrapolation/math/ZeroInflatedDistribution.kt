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
package org.onebusaway.android.extrapolation.math

/**
 * Zero-inflated distribution with point mass [p0] at zero.
 *
 * With probability [p0], the value is exactly zero. With probability (1 - [p0]), the value follows
 * the [base] distribution.
 */
open class ZeroInflatedDistribution(
        @JvmField val p0: Double,
        protected val base: SpeedDistribution
) : SpeedDistribution {

    override val mean: Double
        get() = (1 - p0) * base.mean

    // PDF at x=0 is technically a Dirac delta with weight p0; for x > 0, weighted base PDF
    override fun pdf(x: Double): Double = if (x <= 0) 0.0 else (1 - p0) * base.pdf(x)

    // CDF includes the point mass at zero
    override fun cdf(x: Double): Double =
            when {
                x < 0 -> 0.0
                x == 0.0 -> p0
                else -> p0 + (1 - p0) * base.cdf(x)
            }

    override fun quantile(p: Double): Double =
            when {
                p <= 0 -> 0.0
                p >= 1 -> Double.MAX_VALUE
                p <= p0 -> 0.0 // point mass at zero
                else -> base.quantile((p - p0) / (1 - p0))
            }
}
