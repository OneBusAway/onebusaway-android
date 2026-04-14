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
 * Pre-computes a quantile lookup table from a [ProbDistribution] so that subsequent quantile
 * evaluations are O(1) via linear interpolation instead of iterative root-finding.
 *
 * The table covers probabilities from 0 to 1 at [resolution] evenly spaced points. Quantile lookups
 * between table entries are linearly interpolated.
 *
 * @param source the distribution to freeze
 * @param resolution number of table entries (higher = more accurate, default 200)
 */
class FrozenDistribution(private val source: ProbDistribution, resolution: Int = 200) :
        ProbDistribution {

    private val table: DoubleArray
    private val step: Double
    override val mean: Double = source.mean

    init {
        require(resolution >= 2) { "resolution must be >= 2, got $resolution" }
        step = 1.0 / (resolution - 1)
        table =
                DoubleArray(resolution) { i ->
                    val p = (i * step).coerceAtMost(1.0 - 1e-9)
                    source.quantile(p)
                }
    }

    override fun quantile(p: Double): Double {
        if (p <= 0.0) return 0.0
        if (p >= 1.0) return table.last()

        val idx = p / step
        val lo = idx.toInt().coerceIn(0, table.size - 2)
        val frac = idx - lo
        return table[lo] + frac * (table[lo + 1] - table[lo])
    }

    override fun pdf(x: Double): Double = source.pdf(x)

    override fun cdf(x: Double): Double = source.cdf(x)
}
