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

import org.onebusaway.android.extrapolation.data.Trip
import org.onebusaway.android.extrapolation.math.prob.DiracDistribution
import org.onebusaway.android.io.elements.ObaTripSchedule

/**
 * Per-trip extrapolator for grade-separated transit (rail, subway) that replays the schedule
 * trajectory forward from the vehicle's current position, including dwell times at stops.
 */
class ScheduleReplayExtrapolator(trip: Trip) : Extrapolator(trip) {

    override fun doExtrapolate(
            lastDist: Double,
            lastTimeMs: Long,
            queryTimeMs: Long
    ): ExtrapolationResult {
        val schedule = trip.schedule ?: return ExtrapolationResult.MissingSchedule
        val distance =
                replaySchedule(schedule, lastDist, lastTimeMs, queryTimeMs)
                        ?: return ExtrapolationResult.MissingSchedule
        return ExtrapolationResult.Success(DiracDistribution(distance))
    }
}

/**
 * Replays the schedule forward from [startDist] by the elapsed time between [lastTimeMs] and
 * [queryTimeMs].
 *
 * Finds the schedule segment bracketing startDist, computes the corresponding schedule time, adds
 * the elapsed time, then walks forward through stops — traveling at segment speeds between stops
 * and dwelling at stops for scheduled dwell times.
 *
 * @param schedule the trip schedule containing stop times
 * @param startDist the starting distance along the trip in meters
 * @param lastTimeMs the timestamp of the starting position in milliseconds
 * @param queryTimeMs the target time in milliseconds
 * @return the extrapolated distance in meters, or null if out of bounds
 */
fun replaySchedule(
        schedule: ObaTripSchedule,
        startDist: Double,
        lastTimeMs: Long,
        queryTimeMs: Long
): Double? {
    val dtSec = (queryTimeMs - lastTimeMs) / 1000.0
    val stopTimes = schedule.stopTimes ?: return null
    if (stopTimes.size < 2 || dtSec < 0) return null

    val segIdx =
            try {
                schedule.findSegmentStartIndex(startDist)
            } catch (e: IndexOutOfBoundsException) {
                return null
            }
    val segStart = stopTimes[segIdx]
    val segEnd = stopTimes[segIdx + 1]

    val segDist = segEnd.distanceAlongTrip - segStart.distanceAlongTrip
    val travelTimeSec = (segEnd.arrivalTime - segStart.departureTime).toDouble()
    if (segDist <= 0 || travelTimeSec <= 0) return null

    // Interpolate the schedule time at startDist within this segment
    val fraction = (startDist - segStart.distanceAlongTrip) / segDist
    val schedTimeAtStart = segStart.departureTime + fraction * travelTimeSec
    val targetSchedTime = schedTimeAtStart + dtSec

    return walkForward(stopTimes, segIdx, targetSchedTime)
}

/**
 * Walks the schedule timeline forward from segment [startSegIdx] to find the distance corresponding
 * to [targetTime] (in seconds since service date).
 */
private fun walkForward(
        stopTimes: Array<ObaTripSchedule.StopTime>,
        startSegIdx: Int,
        targetTime: Double
): Double {
    // Check if still within the starting segment's travel phase
    val segEnd = stopTimes[startSegIdx + 1]
    if (targetTime <= segEnd.arrivalTime) {
        return interpolateInSegment(stopTimes[startSegIdx], segEnd, targetTime)
    }

    // Walk through subsequent stops
    for (i in (startSegIdx + 1) until stopTimes.size) {
        val stop = stopTimes[i]

        // Dwelling at this stop?
        if (targetTime <= stop.departureTime) {
            return stop.distanceAlongTrip
        }

        // Traveling to next stop?
        if (i + 1 < stopTimes.size) {
            val nextStop = stopTimes[i + 1]
            if (targetTime <= nextStop.arrivalTime) {
                return interpolateInSegment(stop, nextStop, targetTime)
            }
        }
    }

    // Past the last stop: clamp to end of trip
    return stopTimes.last().distanceAlongTrip
}

/** Linearly interpolates distance within a travel segment given a schedule time. */
private fun interpolateInSegment(
        from: ObaTripSchedule.StopTime,
        to: ObaTripSchedule.StopTime,
        targetTime: Double
): Double {
    val travelTime = to.arrivalTime - from.departureTime
    if (travelTime <= 0) return to.distanceAlongTrip
    val elapsed = targetTime - from.departureTime
    val fraction = (elapsed / travelTime).coerceIn(0.0, 1.0)
    val segDist = to.distanceAlongTrip - from.distanceAlongTrip
    return from.distanceAlongTrip + fraction * segDist
}
