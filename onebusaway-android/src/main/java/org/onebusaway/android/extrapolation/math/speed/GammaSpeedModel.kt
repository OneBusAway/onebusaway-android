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
package org.onebusaway.android.extrapolation.math.speed

import kotlin.math.exp
import org.onebusaway.android.extrapolation.math.DiracDistribution
import org.onebusaway.android.extrapolation.math.SpeedDistribution
import org.onebusaway.android.extrapolation.math.ZeroInflatedGammaDistribution

/**
 * Implements the gamma speed model: a zero-inflated gamma distribution whose parameters are a
 * function of schedule speed, previous observed speed, and time since last observation.
 */
object GammaSpeedModel {

    // Fitted parameters expressed in m/s; fit on a single day of King County Metro
    // data from early March 2026. TODO: get more data.
    // START_B0 and END_B0 were converted from 1/mph to 1/(m/s).
    private const val START_B0 = 0.9455 // s/m
    private const val END_B0 = 0.3102 // s/m
    private const val KINK = 6.087 // m/s
    private const val D = 0.9127 // unitless
    private const val A = 0.1732 // unitless
    private const val LAMBDA = 0.00462 // 1/s

    /**
     * Computes a gamma speed distribution from schedule and previous observed speeds.
     *
     * @param schedSpeedMps scheduled speed in m/s
     * @param prevSpeedMps previous observed speed in m/s
     * @param dt time since last observation in seconds
     * @return ZeroInflatedGammaDistribution (in m/s)
     * @throws IllegalArgumentException if schedSpeedMps is non-positive
     */
    @JvmStatic
    fun fromSpeeds(
            schedSpeedMps: Double,
            prevSpeedMps: Double?,
            dt: Double
    ): SpeedDistribution {
        var vPrev = prevSpeedMps ?: 0.0
        if (vPrev <= 0) vPrev = schedSpeedMps
        require(schedSpeedMps > 0) { "schedSpeedMps must be positive" }

        // Effective speed is a blend of schedule and previous speed
        val vEff = schedSpeedMps * D + (1 - D) * vPrev

        // If vEff is 0 because both the schedule and previous speeds are 0, return a degenerate
        // distribution at 0
        if (vEff <= 0) return DiracDistribution(0.0)

        // Shape parameter is an empirical function of effective speed
        // More spread at lower speeds, tighter at higher speeds
        val b0 = beta0(vEff)

        require(b0 > 0) { "Computed b0 must be positive" }

        // Scale is 1/b0 to make E[X] = alpha*scale = vEff
        val alpha = b0 * vEff
        val scale = 1.0 / b0

        // Probability mass at zero speed decays exponentially with time since last observation
        val p0 = A * exp(-LAMBDA * dt)

        return ZeroInflatedGammaDistribution(p0, alpha, scale)
    }

    /** Piecewise linear ramp from START_B0 to END_B0, flat after KINK. */
    private fun beta0(vEff: Double): Double =
            when {
                vEff >= KINK -> END_B0
                vEff <= 0 -> START_B0
                else -> START_B0 + (END_B0 - START_B0) * (vEff / KINK)
            }
}
