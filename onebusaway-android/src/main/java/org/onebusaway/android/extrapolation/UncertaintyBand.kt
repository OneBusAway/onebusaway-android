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
package org.onebusaway.android.extrapolation

import org.onebusaway.android.extrapolation.math.prob.ProbDistribution

/** Distance range, along the trip, covered by the uncertainty band's faded tails. */
const val BAND_LOW_QUANTILE = 0.01
const val BAND_HIGH_QUANTILE = 0.99

/** Number of equal-width slices the band is split into. */
const val BAND_SEGMENT_COUNT = 15

/** The optimistic "best case" position estimate — the vehicle is this far along with 90% confidence. */
const val FAST_ESTIMATE_QUANTILE = 0.90

/**
 * One slice of the uncertainty band in distance-along-trip space (no geometry yet): the slice spans
 * `[startDist, endDist)` meters and is drawn at [alpha], its mid-point probability density relative
 * to the band's peak (so the most-likely slice is 1.0 and the faded tails approach 0).
 */
data class BandSlice(val startDist: Double, val endDist: Double, val alpha: Float)

/**
 * Splits [distribution] into [count] equal-width distance slices spanning its [lowQuantile,
 * highQuantile] range and weights each by its mid-point PDF normalized to the band's peak. This is
 * the distance-space half of the trip map's uncertainty band — the renderer maps each slice onto the
 * route polyline and draws it at the returned alpha.
 *
 * Pure (no geometry, no Android), so the weighting is unit-testable in isolation. Returns an empty
 * list when there is no band to draw: a degenerate distribution whose quantiles aren't finite (see
 * [ProbDistribution.quantile]), a zero- or negative-width range, or an all-zero PDF.
 */
fun uncertaintyBandSlices(
    distribution: ProbDistribution,
    lowQuantile: Double = BAND_LOW_QUANTILE,
    highQuantile: Double = BAND_HIGH_QUANTILE,
    count: Int = BAND_SEGMENT_COUNT,
): List<BandSlice> {
    if (count <= 0) return emptyList()
    val lo = distribution.quantile(lowQuantile)
    val hi = distribution.quantile(highQuantile)
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) return emptyList()

    val width = (hi - lo) / count
    val densities = DoubleArray(count) { i -> distribution.pdf(lo + width * (i + 0.5)) }
    val peak = densities.maxOrNull() ?: 0.0
    if (peak <= 0.0) return emptyList()

    return (0 until count).map { i ->
        BandSlice(
            startDist = lo + width * i,
            endDist = lo + width * (i + 1),
            alpha = (densities[i] / peak).toFloat(),
        )
    }
}
