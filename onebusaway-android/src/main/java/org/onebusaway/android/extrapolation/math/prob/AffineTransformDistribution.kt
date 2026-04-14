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

/**
 * Wraps a base distribution with an affine transform: Y = offset + scale * X. Used to convert a
 * speed distribution (m/s) into a distance distribution (m) via offset = lastDist, scale = dt.
 */
class AffineTransformDistribution(
        private val base: ProbDistribution,
        private val offset: Double,
        private val scale: Double
) : ProbDistribution {

    override val mean: Double
        get() = offset + base.mean * scale

    override fun pdf(x: Double): Double =
            if (scale > 0) base.pdf((x - offset) / scale) / scale else 0.0

    override fun cdf(x: Double): Double =
            if (scale > 0) base.cdf((x - offset) / scale) else if (x >= offset) 1.0 else 0.0

    override fun quantile(p: Double): Double = offset + base.quantile(p) * scale
}
