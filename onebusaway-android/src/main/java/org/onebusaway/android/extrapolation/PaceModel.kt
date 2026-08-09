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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.MonotoneSpline
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.time.ServerTime

/**
 * How a vehicle travels, given the pace it was just observed holding — the second conditioning
 * axis after [DeviationModel], built for the death-spiral bus of issue #2202 that runs far below
 * schedule speed for a whole route while the extrapolated marker sails ahead and snaps back on
 * every fix.
 *
 * Measured over the same day of King County Metro AVL as the deviation curves, a vehicle's pace
 * over the last ten minutes predicts its next few minutes *beyond* what deviation says, and the
 * signal is not what a "slow bus stays slow" multiplier would suggest. The median mostly recovers;
 * what distinguishes a slow-running vehicle is that it is disproportionately likely to be losing a
 * lump of time **right now** (mid-dwell, held at a timepoint, stuck at a light) and that a sizable
 * minority stay stuck. And the effect is U-shaped: a vehicle running well *ahead* of schedule pace
 * is about to pay its holds and padding back. So the model output is three factors:
 *
 *  - [PaceAdjustment.extraSeconds] — a fixed lump added to the scheduled time ahead (+22s for a
 *    sustained-slow vehicle, +19s for a sustained-fast one, 0 on pace);
 *  - [PaceAdjustment.dispersionMultiplier] — the "quarter stay stuck" fat tail, entering as
 *    dispersion (×1.7 for the slow band) rather than as a mean crawl;
 *  - [PaceAdjustment.paceMultiplier] — the ongoing rate, which barely moves (0.92–1.0).
 *
 * **The conditioning variable is deliberately hard to excite.** The pace ratio is shrunk toward 1
 * by a pseudo-count (`rho = (achieved + c) / (elapsed + c)`), and below [MIN_LOOKBACK] of observed
 * window it is exactly 1 — a vehicle without a sustained observation window reproduces the
 * deviation-only model to the bit, so cold start needs no special case. Both guards are structural
 * lessons from the fit: unconstrained, the optimiser turned short lookbacks into a
 * stopped-right-now detector keyed to the dataset's 30-second polling cadence (a different defect,
 * and one that would not survive a different polling schedule).
 *
 * Fitted and validated in the companion research repo (extrapolation-science, H39) against the
 * exact deviation curves this multiplies, per (horizon × deviation × pace) cell on held-out trips:
 * the slow band's 80% coverage rises from 0.61–0.69 to 0.72–0.88 across horizons, full-shape
 * calibration improves in every pace band, and the no-lookback population is unchanged by
 * construction.
 */
internal object PaceModel {

    /** Only ground covered within this window of the anchor fix counts as "recent". */
    private const val MAX_LOOKBACK_SECONDS = 600.0

    /** Below this much observed window the pace ratio is exactly 1 (identity adjustment). */
    private const val MIN_LOOKBACK_SECONDS = 420.0

    /**
     * Pseudo-count shrinking the observed ratio toward on-pace, in seconds. Weakly identified
     * once the [MIN_LOOKBACK_SECONDS] gate exists (the knot values compensate for its choice of
     * coordinates), so it carries the fitted value rather than a meaningful timescale.
     */
    private const val PSEUDO_COUNT_SECONDS = 3.005739011224008

    /** Shrunken pace ratio at each knot; 1.0 is on pace, below is slow. */
    private val KNOT_PACE = doubleArrayOf(0.55, 0.80, 1.00, 1.40)

    /** log of the ongoing-rate multiplier at each knot; the rate itself barely moves. */
    private val LOG_PACE_MULTIPLIER =
        doubleArrayOf(-0.0395446208412077, -0.08060466991102952, 0.0, -0.06754487223552258)

    /** log of the dispersion multiplier at each knot: the slow band's stuck minority. */
    private val LOG_DISPERSION_MULTIPLIER =
        doubleArrayOf(0.5275166183847799, -0.051491911099296225, 0.0, -0.007486369110162433)

    /** The lump of time a vehicle off its pace is likely mid-way through losing, in seconds. */
    private val EXTRA_SECONDS =
        doubleArrayOf(21.973541744065756, 8.536753997571957, 0.0, 19.32545235621823)

    private val paceMultiplierCurve = MonotoneSpline(KNOT_PACE, LOG_PACE_MULTIPLIER)
    private val dispersionMultiplierCurve = MonotoneSpline(KNOT_PACE, LOG_DISPERSION_MULTIPLIER)
    private val extraSecondsCurve = MonotoneSpline(KNOT_PACE, EXTRA_SECONDS)

    /** No adjustment: the deviation-only model, exactly. */
    val IDENTITY = PaceAdjustment(1.0, 1.0, 0.0)

    /**
     * The adjustment for a vehicle whose recent window covered [lookback], or [IDENTITY] when
     * there is no sufficiently long window to read a pace from.
     */
    fun adjustmentFor(lookback: PaceLookback?): PaceAdjustment {
        if (lookback == null || lookback.elapsedSeconds < MIN_LOOKBACK_SECONDS) return IDENTITY
        val rho = (lookback.achievedSeconds.coerceAtLeast(0.0) + PSEUDO_COUNT_SECONDS) /
            (lookback.elapsedSeconds + PSEUDO_COUNT_SECONDS)
        return PaceAdjustment(
            paceMultiplier = exp(paceMultiplierCurve.valueAt(rho)),
            dispersionMultiplier = exp(dispersionMultiplierCurve.valueAt(rho)),
            extraSeconds = extraSecondsCurve.valueAt(rho)
        )
    }

    /** The one phase in which a vehicle's movement says anything about its travel state. */
    private const val PHASE_IN_PROGRESS = "in_progress"

    /**
     * Distance from either end of the trip within which a stationary vehicle is assumed to be
     * waiting rather than running. Same margin as the research pipeline's `drop_terminal_idling`.
     */
    private const val TERMINAL_MARGIN_METERS = 200.0

    /**
     * Whether this fix is usable as a pace observation: the vehicle is actually running its trip.
     * The curves were fitted on fixes filtered exactly this way (`phase == "in_progress"`, terminal
     * idling dropped); without the same filter here, a bus that sat at its terminal with AVL live
     * and then departed on time would read as deep-slow for the next ten minutes — a population the
     * held-out validation never scored. A null phase also fails: better to leave the adjustment at
     * identity than to condition on windows the fit never saw.
     */
    private fun ObaTripStatus.isMidRoute(): Boolean {
        if (phase != PHASE_IN_PROGRESS) return false
        val dist = distanceAlongTrip ?: return false
        if (dist < TERMINAL_MARGIN_METERS && scheduleDeviation <= Duration.ZERO) return false
        val total = totalDistanceAlongTrip
        return total == null || total <= 0 || total - dist >= TERMINAL_MARGIN_METERS
    }

    /**
     * The ground the vehicle covered — in schedule terms — over the window ending at [anchor]'s
     * GPS fix, read from the oldest usable fix within [MAX_LOOKBACK_SECONDS] of it, or null when
     * no such window exists: no GPS fix on the anchor, the anchor not mid-route, no earlier usable
     * entry in range, or a position the schedule cannot place. Mirrors the research instrument:
     * fix times are `lastLocationUpdateTime` (GPS, so poll jitter doesn't leak into the elapsed
     * time), only real fixes participate, and only [isMidRoute] fixes count — with the one known
     * divergence that the anchor's distance is the poll-time (server-extrapolated) value the whole
     * extrapolation anchors on, where the research pipeline kept each fix's first report.
     */
    fun lookbackFor(
        history: List<ObaTripStatus>,
        anchor: ObaTripStatus,
        schedule: ObaTripSchedule
    ): PaceLookback? {
        if (anchor.lastLocationUpdateTime <= 0 || !anchor.isMidRoute()) return null
        val anchorDist = anchor.distanceAlongTrip ?: return null
        val anchorSchedule = schedule.scheduleTimeAt(anchorDist) ?: return null
        val anchorFix = ServerTime(anchor.lastLocationUpdateTime)
        val windowStart = anchorFix - MAX_LOOKBACK_SECONDS.seconds
        // Oldest-first, so the first usable entry inside the window is the longest available
        // lookback; an unusable one falls through to the next rather than giving up.
        for (entry in history) {
            if (entry.lastLocationUpdateTime <= 0 || !entry.isMidRoute()) continue
            val entryFix = ServerTime(entry.lastLocationUpdateTime)
            if (entryFix < windowStart || entryFix >= anchorFix) continue
            val entryDist = entry.distanceAlongTrip ?: continue
            val entrySchedule = schedule.scheduleTimeAt(entryDist) ?: continue
            return PaceLookback(
                achievedSeconds = (anchorSchedule - entrySchedule).toDouble(DurationUnit.SECONDS),
                elapsedSeconds = (anchorFix - entryFix).toDouble(DurationUnit.SECONDS)
            )
        }
        return null
    }

    /** [lookbackFor] read off a [TripState] snapshot's own history and schedule. */
    fun lookbackFor(state: TripState): PaceLookback? {
        val anchor = state.anchor ?: return null
        val schedule = state.schedule ?: return null
        return lookbackFor(state.statuses, anchor, schedule)
    }
}

/** A recent observation window: the vehicle covered [achievedSeconds] of schedule in [elapsedSeconds]. */
internal data class PaceLookback(val achievedSeconds: Double, val elapsedSeconds: Double)

/** The three pace factors [PaceModel] reads off a vehicle's recent window. */
internal data class PaceAdjustment(
    /** Multiplier on the scheduled time ahead — the ongoing rate. */
    val paceMultiplier: Double,
    /** Multiplier on the gamma-process dispersion theta. */
    val dispersionMultiplier: Double,
    /** Fixed lump added to the scheduled time ahead, in seconds. */
    val extraSeconds: Double
)

/**
 * Applies the adjustment's mean effects to the profile: every scheduled second ahead is scaled by
 * the rate and shifted by the lump, `tau → paceMultiplier*tau + extraSeconds`. A monotone
 * piecewise-linear warp, so the profile stays a valid profile and the closed-form quantile
 * machinery is untouched; identity returns the same instance so the no-lookback path costs
 * nothing. The dispersion multiplier rides separately, on theta.
 *
 * The first knot is deliberately lifted with the rest: a warped profile starts at `extraSeconds`
 * rather than 0, which is the model saying the vehicle may not have left the anchor yet — the
 * lump puts an atom of probability at the anchor position. [FirstPassageDistribution] handles a
 * non-zero first knot throughout (its cdf short-circuits at the anchor, and quantiles below the
 * atom clamp to it).
 */
internal fun PassageProfile.warpedBy(adjustment: PaceAdjustment): PassageProfile {
    if (adjustment == PaceModel.IDENTITY) return this
    return PassageProfile(
        DoubleArray(scheduleSeconds.size) {
            adjustment.paceMultiplier * scheduleSeconds[it] + adjustment.extraSeconds
        },
        distances
    )
}
