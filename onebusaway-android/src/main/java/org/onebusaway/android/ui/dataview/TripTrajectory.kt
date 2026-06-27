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
package org.onebusaway.android.ui.dataview

import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution

/** A plotted point: distance along the trip (meters) at a server-clock time (ms since epoch). */
data class TrajectoryPoint(val distanceMeters: Double, val timeMs: Long)

/** One scheduled stop: its distance, plus arrival/departure server-clock times (the dwell bar). */
data class ScheduleStop(
    val distanceMeters: Double,
    val arrivalMs: Long,
    val departureMs: Long,
    val stopId: String?,
)

/** One position-PDF histogram bin: a distance and its density normalized to the band peak (0..1). */
data class PdfBin(val distanceMeters: Double, val normalizedHeight: Double)

/**
 * The extrapolation overlay at [nowMs]: the median estimate and the 80% CI bounds projected from the
 * [anchor], plus the position PDF histogram. Distances in meters, times in server-clock ms.
 *
 * [scheduleAtMedianMs] is the server-clock time the schedule says the vehicle should reach the
 * median distance — the schedule-deviation reference. 0 when the median falls outside the schedule's
 * interpolatable span (no deviation to draw).
 */
data class ExtrapolationSeries(
    val anchor: TrajectoryPoint,
    val nowMs: Long,
    val medianMeters: Double,
    val lowMeters: Double,
    val highMeters: Double,
    val pdf: List<PdfBin>,
    val scheduleAtMedianMs: Long = 0L,
)

/** The full data extent of a trajectory, for the viewport's initial fit. */
data class DataBounds(val minDist: Double, val maxDist: Double, val minTime: Long, val maxTime: Long)

/** Everything the trajectory graph plots for one trip at one instant. All distance-vs-time. */
data class TripTrajectory(
    val observations: List<TrajectoryPoint> = emptyList(),
    val schedule: List<ScheduleStop> = emptyList(),
    val extrapolation: ExtrapolationSeries? = null,
    val bounds: DataBounds = DataBounds(0.0, 0.0, 0L, 0L),
    /**
     * "Now" the graph was built at — the horizontal now line, rising over time. NOTE: the caller
     * passes a local clock (System.currentTimeMillis), but the time axis is laid out in server-clock
     * values, so client/server skew shifts the now line off the latest observation. (Carried over
     * from the upstream view's TODO; fix by offsetting via a history entry's server/local time pair.)
     */
    val nowMs: Long = 0L,
)

/** Lower bound of the position-PDF histogram window (quantile). */
const val PDF_BIN_LOW_QUANTILE = 0.0

/** Upper bound of the position-PDF histogram window (quantile) — the long tail is clipped. */
const val PDF_BIN_HIGH_QUANTILE = 0.95

/** Number of bins across the position-PDF histogram. */
const val PDF_BIN_COUNT = 160

/** The 80% credible interval bounds drawn from the anchor. */
const val CI_LOW_QUANTILE = 0.10
const val CI_HIGH_QUANTILE = 0.90

/**
 * The position-PDF histogram: [binCount] equal-width bins spanning the distribution's
 * [lowQuantile, highQuantile] range, each normalized to the peak density (0..1). Pure (distribution
 * only), so it is unit-testable. Empty when the quantile bounds are non-finite, the range is
 * non-positive, or the density is everywhere zero.
 */
fun pdfBins(
    distribution: ProbDistribution,
    binCount: Int = PDF_BIN_COUNT,
    lowQuantile: Double = PDF_BIN_LOW_QUANTILE,
    highQuantile: Double = PDF_BIN_HIGH_QUANTILE,
): List<PdfBin> {
    if (binCount <= 0) return emptyList()
    val lo = distribution.quantile(lowQuantile)
    val hi = distribution.quantile(highQuantile)
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) return emptyList()

    val width = (hi - lo) / binCount
    val centers = DoubleArray(binCount) { lo + width * (it + 0.5) }
    val densities = centers.map { distribution.pdf(it) }
    val peak = densities.maxOrNull() ?: 0.0
    if (peak <= 0.0) return emptyList()

    return centers.indices.map { PdfBin(centers[it], densities[it] / peak) }
}

/**
 * The bounding box of the supplied distances and times, expanded by [padFraction] on every side so
 * the outermost points aren't drawn on the axes. Pure. A degenerate (empty or zero-extent) input
 * yields a unit-extent box so the viewport never divides by zero.
 */
fun dataBounds(distances: List<Double>, times: List<Long>, padFraction: Double = 0.05): DataBounds {
    val minD = distances.minOrNull() ?: 0.0
    val maxD = distances.maxOrNull() ?: 0.0
    val minT = times.minOrNull() ?: 0L
    val maxT = times.maxOrNull() ?: 0L
    val distPad = ((maxD - minD) * padFraction).coerceAtLeast(1.0)
    val timePad = ((maxT - minT) * padFraction).toLong().coerceAtLeast(1_000L)
    return DataBounds(minD - distPad, maxD + distPad, minT - timePad, maxT + timePad)
}

/**
 * Projects a trip snapshot into the graph's distance-vs-time series at [nowMs]: the recorded vehicle
 * observations, the schedule (with dwell), and — when [TripState.extrapolate] succeeds with a finite
 * estimate — the extrapolation overlay. Pure distance/time arithmetic (no geometry), so the screen
 * just draws it and the projection is testable.
 */
fun buildTrajectory(state: TripState, nowMs: Long): TripTrajectory {
    val observations = state.history.mapNotNull { entry ->
        val dist = entry.status.distanceAlongTrip ?: return@mapNotNull null
        val time = entry.status.lastUpdateTime.takeIf { it > 0 } ?: entry.serverTimeMs
        TrajectoryPoint(dist, time)
    }

    val schedule = state.schedule?.stopTimes.orEmpty().map { st ->
        ScheduleStop(
            distanceMeters = st.distanceAlongTrip,
            arrivalMs = state.serviceDate + st.arrivalTime * 1000L,
            departureMs = state.serviceDate + st.departureTime * 1000L,
            stopId = st.stopId,
        )
    }

    val extrapolation = extrapolationSeries(state, schedule, nowMs)

    val distances = buildList {
        observations.forEach { add(it.distanceMeters) }
        schedule.forEach { add(it.distanceMeters) }
        extrapolation?.let { add(it.anchor.distanceMeters); add(it.medianMeters); add(it.highMeters) }
    }
    val times = buildList {
        add(nowMs) // keep the now line in-bounds even before any extrapolation exists
        observations.forEach { add(it.timeMs) }
        schedule.forEach { add(it.arrivalMs); add(it.departureMs) }
        extrapolation?.let { add(it.anchor.timeMs); add(it.nowMs) }
    }

    return TripTrajectory(observations, schedule, extrapolation, dataBounds(distances, times), nowMs)
}

private fun extrapolationSeries(state: TripState, schedule: List<ScheduleStop>, nowMs: Long): ExtrapolationSeries? {
    val distribution = (state.extrapolate(nowMs) as? ExtrapolationResult.Success)?.distribution ?: return null
    val anchorDist = state.anchor?.distanceAlongTrip ?: return null
    val median = distribution.median()
    val low = distribution.quantile(CI_LOW_QUANTILE)
    val high = distribution.quantile(CI_HIGH_QUANTILE)
    if (!median.isFinite() || !low.isFinite() || !high.isFinite()) return null
    return ExtrapolationSeries(
        anchor = TrajectoryPoint(anchorDist, state.anchorTimeMs),
        nowMs = nowMs,
        medianMeters = median,
        lowMeters = low,
        highMeters = high,
        pdf = pdfBins(distribution),
        scheduleAtMedianMs = interpolateScheduleTime(schedule, median),
    )
}

/**
 * The server-clock time the [schedule] says the vehicle reaches [distanceMeters], linearly
 * interpolated within the bracketing stop pair (previous stop's departure to next stop's arrival).
 * Pure, so it is unit-testable. Returns 0 when fewer than two stops exist or the distance falls
 * outside every interpolatable segment.
 */
fun interpolateScheduleTime(schedule: List<ScheduleStop>, distanceMeters: Double): Long {
    if (schedule.size < 2) return 0L
    for (i in 1 until schedule.size) {
        val d0 = schedule[i - 1].distanceMeters
        val d1 = schedule[i].distanceMeters
        if (d1 <= d0) continue
        // Each segment owns the half-open span [d0, d1), so a distance exactly on a stop boundary
        // lands in a single segment (the next one) instead of double-counting into both. The final
        // segment is closed [d0, d1] so the trip's end distance still interpolates rather than
        // dropping to the 0 sentinel.
        val withinUpper = if (i == schedule.lastIndex) distanceMeters <= d1 else distanceMeters < d1
        if (distanceMeters >= d0 && withinUpper) {
            val fraction = (distanceMeters - d0) / (d1 - d0)
            val t0 = schedule[i - 1].departureMs
            val t1 = schedule[i].arrivalMs
            return t0 + (fraction * (t1 - t0)).toLong()
        }
    }
    return 0L
}

/**
 * Human-readable schedule deviation for [devSeconds] (now minus scheduled time): positive is "late",
 * negative "early", zero "on time". Minutes collapse the seconds when zero (e.g. "2m late",
 * "1m30s early"). Pure, so it is unit-testable.
 */
fun formatDeviationLabel(devSeconds: Long): String {
    if (devSeconds == 0L) return "on time"
    val absSeconds = kotlin.math.abs(devSeconds)
    val magnitude = if (absSeconds >= 60) {
        val mins = absSeconds / 60
        val secs = absSeconds % 60
        "${mins}m" + if (secs > 0) "${secs}s" else ""
    } else {
        "${absSeconds}s"
    }
    return magnitude + if (devSeconds > 0) " late" else " early"
}
