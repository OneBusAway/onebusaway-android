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

import kotlin.time.DurationUnit
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.FirstPassageDistribution
import org.onebusaway.android.time.WallTime

/**
 * Gamma-process dispersion, in seconds: the time to cover ground the schedule budgets `tau` seconds
 * for has variance `tau * THETA_SECONDS`.
 *
 * Calibrated on a day of King County Metro AVL (1.3M polls, 11.6M same-trip fix pairs), on trips
 * held out from the fit. Chosen so the 80% band covers 80% *at every horizon* rather than on
 * average — pooling is actively misleading here, because a model too narrow at short horizons and
 * too wide at long ones has those errors cancel in the aggregate.
 *
 * See `extrapolation-science/h36_params.json` for the fit and its validation.
 */
private const val THETA_SECONDS = 25.236

/**
 * How long a vehicle really takes to cover its route relative to the schedule, in the mean: buses
 * run about 5% slower than the timetable allows.
 *
 * Fitted jointly with [THETA_SECONDS]. It is not cosmetic — first-passage times are right-skewed, so
 * a process whose *mean* matches the schedule has a *median* that runs ahead of it, and the median
 * is what positions the vehicle marker. Without this the marker sat 20-30% past the scheduled
 * position at short horizons.
 */
private const val MEAN_TRAVEL_MULTIPLIER = 1.0508

/**
 * Per-trip extrapolator for bus-like routes that models the time a vehicle takes to get places
 * rather than the speed it travels at.
 *
 * The vehicle's travel time accumulates as a gamma process along the schedule, so position follows
 * from `P(D >= d) = P(S(d) <= dt)` — it has passed `d` exactly when its travel time to `d` fits
 * inside the elapsed time. Because the increments are independent, uncertainty grows with the
 * **square root** of elapsed time; the speed models this replaces draw one pace and hold it, which
 * makes it grow linearly.
 *
 * That difference is measured, not assumed. Conditioned on elapsed time, the spread of ground
 * covered grows as `dt^0.477` in the AVL data, and a persistent per-trip pace factor fits to zero.
 * Held out from the fit, this model's 80% band covers 0.76–0.84 at every horizon from 30 seconds to
 * 12 minutes; a held-pace model tuned on the same data covers 0.41 at 30 seconds and 0.97 at 12
 * minutes, because a constant relative spread cannot be right at more than one horizon.
 *
 * Known residuals, measured and left in deliberately: the left tail is about 3 points heavier than
 * the model at every horizon — trips that fall badly behind are more common than a single gamma
 * process allows, and it is not simply stalled buses, whose frequency decays with the horizon as
 * expected. And at the shortest horizons the median still runs ~20% past the scheduled position,
 * because at a gamma shape near 1 no single parameter pair matches both centre and spread.
 */
class FirstPassageExtrapolator(state: TripState) : Extrapolator(state) {

    // One extrapolator per immutable TripState, whose anchor distance is likewise frozen, so this
    // is a per-instance memo; the distance key just stops a direct doExtrapolate caller from
    // reading a profile built for a different position. NaN never equals anything, so the first
    // call always computes, and a null profile is memoized too since it cannot start succeeding.
    private var cachedProfile: PassageProfile? = null
    private var cachedProfileDist = Double.NaN

    override fun doExtrapolate(
        lastDist: Double,
        lastTime: WallTime,
        queryTime: WallTime
    ): ExtrapolationResult {
        val profile = resolveProfile(lastDist) ?: return ExtrapolationResult.MissingSchedule
        val dtSec = (queryTime - lastTime).toDouble(DurationUnit.SECONDS)
        return ExtrapolationResult.Success(
            FirstPassageDistribution(
                dtSec,
                profile.scheduleSeconds,
                profile.distances,
                THETA_SECONDS,
                MEAN_TRAVEL_MULTIPLIER
            )
        )
    }

    /**
     * The schedule ahead of a vehicle at [lastDist], or null when there isn't one.
     *
     * Keyed off where the vehicle **actually is**, not its `scheduledDistanceAlongTrip`: the model
     * asks how long the road ahead should take, which is a property of that road, so a late bus is
     * still governed by the stretch it currently occupies.
     */
    private fun resolveProfile(lastDist: Double): PassageProfile? {
        if (cachedProfileDist == lastDist) return cachedProfile
        cachedProfileDist = lastDist
        cachedProfile = state.schedule?.passageProfileFrom(lastDist)
        return cachedProfile
    }
}
