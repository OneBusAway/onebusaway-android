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

import kotlin.math.exp
import org.onebusaway.android.extrapolation.data.Trip
import org.onebusaway.android.extrapolation.math.prob.AffineTransformDistribution
import org.onebusaway.android.extrapolation.math.prob.FrozenDistribution
import org.onebusaway.android.extrapolation.math.prob.GammaDistribution
import org.onebusaway.android.extrapolation.math.prob.GammaMixtureDistribution
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.io.elements.speedAtDistance

// H34 two-gamma mixture parameters, fitted on span-weighted King County Metro data (in mph).
private const val START_B0 = 0.571381 // 1/mph
private const val END_B0 = 0.124442 // 1/mph
private const val KINK = 21.918 // mph
private const val R_INTERCEPT = 0.006024
private const val R_SLOPE = 0.026456
private val SLOW_ALPHA = exp(-1.322671) // constant slow shape ≈ 0.266
private const val MW_INTERCEPT = -1.347435
private const val MW_SLOPE = -0.018838

internal const val MPS_TO_MPH = 2.23694

/**
 * Per-trip extrapolator for bus-like routes using the H34 two-gamma mixture speed distribution
 * model. Conditioned on scheduled speed only; the slow component (constant shape) captures
 * delayed/stopped vehicles while the fast component is ensemble-mean-locked to the schedule speed.
 */
class GammaExtrapolator(trip: Trip) : Extrapolator(trip) {

    private var cachedDistribution: Pair<Long, ProbDistribution>? = null

    override fun doExtrapolate(
            lastDist: Double,
            lastTimeMs: Long,
            queryTimeMs: Long
    ): ExtrapolationResult {
        val dtSec = (queryTimeMs - lastTimeMs) / 1000.0
        val speedDist =
                resolveDistribution(lastTimeMs) ?: return ExtrapolationResult.MissingSchedule
        return ExtrapolationResult.Success(
                AffineTransformDistribution(speedDist, lastDist, dtSec / MPS_TO_MPH)
        )
    }

    private fun resolveDistribution(lastFixTime: Long): ProbDistribution? {
        cachedDistribution?.let { (cachedTime, dist) -> if (cachedTime == lastFixTime) return dist }

        val lastState = trip.history.lastOrNull() ?: return null
        val schedule = trip.schedule
        val scheduleSpeed =
                schedule?.speedAtDistance(lastState.scheduledDistanceAlongTrip ?: return null)
                        ?: return null

        return buildH34SpeedDistribution(scheduleSpeed).also {
            cachedDistribution = lastFixTime to it
        }
    }
}

// --- H34 two-gamma mixture speed model ---

/**
 * Builds a frozen two-gamma mixture speed distribution from H34 parameters. The returned
 * distribution is over speed in mph.
 *
 * Guards against degenerate parameters: when the mixture weight [m] is very close to 1.0, the
 * fast-component scale can overflow to Infinity. In that regime the slow component dominates, so we
 * fall back to a single-component Gamma distribution.
 *
 * @param schedSpeedMps scheduled speed in m/s (must be positive)
 */
internal fun buildH34SpeedDistribution(schedSpeedMps: Double): ProbDistribution {
    require(schedSpeedMps > 0) { "schedSpeedMps must be positive" }

    val v = schedSpeedMps * MPS_TO_MPH

    // Mixture weight
    val m = sigmoid(MW_INTERCEPT + MW_SLOPE * v)

    // Slow component (constant shape, ratio-based mean)
    val r = sigmoid(R_INTERCEPT + R_SLOPE * v)
    val meanSlow = r * v
    val scale1 = meanSlow / SLOW_ALPHA

    // Fast component (ensemble-mean locked to v_sched)
    val b0 = beta0(v)
    val alpha2 = b0 * v
    val denom = 1.0 - m
    val c = if (denom > 1e-12) (1.0 - m * r) / denom else 1.0
    val scale2 = c / b0

    val slow = GammaDistribution(SLOW_ALPHA, scale1)

    // When m ≈ 1 the fast component parameters can overflow; fall back to
    // the slow component alone which dominates in this regime anyway.
    if (!alpha2.isFinite() || alpha2 <= 0 || !scale2.isFinite() || scale2 <= 0) {
        return FrozenDistribution(slow)
    }

    val fast = GammaDistribution(alpha2, scale2)
    return FrozenDistribution(GammaMixtureDistribution(m, slow, fast))
}

private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

/** Piecewise linear ramp from START_B0 to END_B0, flat after KINK. */
private fun beta0(v: Double): Double =
        when {
            v >= KINK -> END_B0
            v <= 0 -> START_B0
            else -> START_B0 + (END_B0 - START_B0) * (v / KINK)
        }
