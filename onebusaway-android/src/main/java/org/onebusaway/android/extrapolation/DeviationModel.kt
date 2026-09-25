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
package org.onebusaway.android.extrapolation

import kotlin.math.exp
import kotlin.time.Duration
import kotlin.time.DurationUnit
import org.onebusaway.android.extrapolation.math.MonotoneSpline

/**
 * How a vehicle travels, given how far off schedule it currently is.
 *
 * Schedule deviation predicts a vehicle's next few minutes, and not monotonically. Measured over
 * a day of King County Metro AVL, deviation change over five minutes runs: a vehicle 3-5 minutes
 * early loses a further 35 seconds (operators hold early vehicles at timepoints), one 2-6 minutes
 * late gains 1-2 seconds back against schedule slack, and past roughly 6-8 minutes late the sign
 * flips and grows — +6s at 10-15 minutes, +19s at 15-20, +45s at 30 or more. So the system has a
 * stable attractor a couple of minutes late, which is exactly where the fleet median sits, and an
 * unstable threshold past which delay compounds. Uncertainty widens in both directions away from
 * on-time, and most sharply for held vehicles, since holding is discretionary.
 *
 * Both model parameters therefore become curves over deviation rather than constants. They are
 * interpolated in log space (so they cannot go negative) through six knots, using a
 * shape-preserving spline that is flat outside the knot range.
 *
 * **Why the knots sit where they do.** Earlier fits used polynomials and wider knots, and both
 * misbehaved in exactly the places with the least data: a cubic extrapolated to a dispersion of
 * 1051s for a vehicle 10 minutes early and claimed a vehicle 25 minutes late runs *faster* than
 * the timetable, and an unregularized spline fitted 4106s at a knot supported by 0.18% of
 * observations. A roughness penalty was tried and made held-out calibration monotonically worse —
 * it smooths away curvature the data genuinely supports. Placing the outermost knots where about
 * a percent of observations sit, and clamping beyond them, is what fixed it.
 *
 * Fitted and validated in the companion research repo (extrapolation-science, H37); the fit
 * artifacts live there rather than in this tree, as with the speed models before it.
 */
internal object DeviationModel {

    /** Deviation, in seconds, at each knot. Positive is late. */
    private val KNOT_SECONDS = doubleArrayOf(-250.0, -110.0, 60.0, 300.0, 600.0, 900.0)

    /**
     * log of the mean travel multiplier at each knot: 1.35 for a vehicle held 4 minutes early,
     * 1.02 in the recovery band, rising again into the spiral.
     */
    private val LOG_TRAVEL_MULTIPLIER =
        doubleArrayOf(0.298932, 0.115495, 0.041843, 0.019873, 0.094518, 0.044967)

    /**
     * log of the gamma-process dispersion at each knot, in seconds. A clean U: 146 for a held
     * vehicle, 22 on time — the most predictable state on the network — and 44 once badly late.
     */
    private val LOG_DISPERSION =
        doubleArrayOf(4.982657, 3.540953, 3.094457, 3.268313, 3.356563, 3.786251)

    private val travelMultiplierCurve = MonotoneSpline(KNOT_SECONDS, LOG_TRAVEL_MULTIPLIER)
    private val dispersionCurve = MonotoneSpline(KNOT_SECONDS, LOG_DISPERSION)

    /** How much longer than scheduled this vehicle takes, in the mean. */
    fun travelMultiplierFor(deviation: Duration): Double = exp(travelMultiplierCurve.valueAt(deviation.toDouble(DurationUnit.SECONDS)))

    /** Gamma-process dispersion in seconds: travel time over `tau` has variance `k*tau*theta`. */
    fun dispersionFor(deviation: Duration): Double = exp(dispersionCurve.valueAt(deviation.toDouble(DurationUnit.SECONDS)))
}
