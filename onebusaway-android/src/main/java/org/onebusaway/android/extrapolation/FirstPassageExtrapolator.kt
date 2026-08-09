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
 * On top of that, a vehicle observed holding an unusual pace over a sustained recent window — the
 * death-spiral bus of issue #2202 — gets [PaceModel]'s adjustment: a lump of scheduled time and a
 * dispersion factor, baked into the profile and theta once per snapshot. Without a long enough
 * window the adjustment is identity, so this changes nothing for a freshly opened trip.
 *
 * Known residual, measured and left in deliberately: the left tail is a few points heavier than the
 * model at every horizon — trips that fall badly behind are more common than a single gamma process
 * allows. It is not simply stalled buses, whose frequency decays with the horizon as expected, and
 * conditioning on deviation does not absorb it either, so it is a genuinely separate defect.
 */
class FirstPassageExtrapolator(state: TripState) : Extrapolator(state) {

    /**
     * The schedule ahead of the vehicle, how far off schedule it is, and the pace it was recently
     * observed holding — all fixed for the life of this extrapolator, since [Extrapolator] is built
     * per immutable snapshot, so the pace lookback is read from the history once, never per frame.
     *
     * The profile starts from where the vehicle **actually is**, not its `scheduledDistanceAlongTrip`:
     * the model asks how long the road ahead should take, which is a property of that road, so a late
     * bus is still governed by the stretch it currently occupies. The pace adjustment's mean effects
     * are baked into the profile here ([warpedBy]); its dispersion factor rides on theta below.
     */
    private val fit: Fit? by lazy(LazyThreadSafetyMode.NONE) {
        val anchor = state.anchor ?: return@lazy null
        val profile =
            state.schedule?.passageProfileFrom(anchor.distanceAlongTrip ?: return@lazy null)
                ?: return@lazy null
        val pace = PaceModel.adjustmentFor(PaceModel.lookbackFor(state))
        Fit(profile.warpedBy(pace), anchor.scheduleDeviation, pace.dispersionMultiplier)
    }

    private class Fit(
        val profile: PassageProfile,
        val deviation: Duration,
        val dispersionMultiplier: Double
    )

    override fun doExtrapolate(
        lastDist: Double,
        lastTime: WallTime,
        queryTime: WallTime
    ): ExtrapolationResult {
        val current = fit ?: return ExtrapolationResult.MissingSchedule
        val dtSec = (queryTime - lastTime).toDouble(DurationUnit.SECONDS)
        // Deviation is read once, from the fix the extrapolation starts at, rather than tracked as
        // it evolves. Letting it evolve would be more faithful -- the drift measurement is exactly
        // that equation of motion -- but it would cost the closed form this approach is built on.
        return ExtrapolationResult.Success(
            FirstPassageDistribution(
                dtSec,
                current.profile.scheduleSeconds,
                current.profile.distances,
                DeviationModel.dispersionFor(current.deviation) * current.dispersionMultiplier,
                DeviationModel.travelMultiplierFor(current.deviation)
            )
        )
    }
}
