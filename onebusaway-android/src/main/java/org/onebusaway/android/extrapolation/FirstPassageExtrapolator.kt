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

import kotlin.time.Duration
import kotlin.time.DurationUnit
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.FirstPassageDistribution
import org.onebusaway.android.time.WallTime

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
 * A held-pace model tuned on the same data covers 0.41 of its claimed 80% at 30 seconds and 0.97 at
 * 12 minutes, because a constant relative spread cannot be right at more than one horizon.
 *
 * Both model parameters are read off the vehicle's schedule deviation rather than being constants —
 * see [DeviationModel] for what deviation predicts and why. Held out from the fit, that takes the
 * worst (horizon x deviation) cell's coverage error from 0.278 to 0.089, and the early-vehicle band
 * from 0.602 to 0.799 against a nominal 0.800.
 *
 * Known residual, measured and left in deliberately: the left tail is a few points heavier than the
 * model at every horizon — trips that fall badly behind are more common than a single gamma process
 * allows. It is not simply stalled buses, whose frequency decays with the horizon as expected, and
 * conditioning on deviation does not absorb it either, so it is a genuinely separate defect.
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
        // The anchor's deviation, not a running estimate: the model reads it once, from the same
        // fix the extrapolation starts at. Letting it evolve over the horizon would be more
        // faithful -- the drift measurement is exactly that equation of motion -- but it would
        // cost the closed form this whole approach is built on.
        val deviation = state.anchor?.scheduleDeviation ?: Duration.ZERO
        return ExtrapolationResult.Success(
            FirstPassageDistribution(
                dtSec,
                profile.scheduleSeconds,
                profile.distances,
                DeviationModel.dispersionFor(deviation),
                DeviationModel.travelMultiplierFor(deviation)
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
