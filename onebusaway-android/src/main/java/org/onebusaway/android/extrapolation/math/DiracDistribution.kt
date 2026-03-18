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

/** A degenerate (Dirac delta) distribution concentrated at a single [value]. */
class DiracDistribution(@JvmField val value: Double) : SpeedDistribution {
    override val mean: Double
        get() = value
    override fun pdf(x: Double): Double = 0.0
    override fun cdf(x: Double): Double = if (x >= value) 1.0 else 0.0
    override fun quantile(p: Double): Double =
            when {
                p <= 0.0 -> 0.0
                p >= 1.0 -> Double.MAX_VALUE
                else -> value
            }
}
