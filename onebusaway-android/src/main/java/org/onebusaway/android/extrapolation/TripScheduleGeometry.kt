/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
import org.onebusaway.android.models.ObaTripSchedule

/**
 * This stop's one offset along the trip, or null when the trip does not serve it — or serves it more
 * than once, which nothing here can disambiguate from a stop id alone.
 *
 * A single indexed pass that gives up at the second match, rather than materializing every offset just
 * to ask whether there was exactly one: the callers run this per trip on each poll, over schedules of
 * a few dozen stop times.
 */
fun ObaTripSchedule.soleOffsetOf(stopId: String): Double? {
    var found = -1
    for (i in stopTimes.indices) {
        if (stopTimes[i].stopId != stopId) continue
        if (found >= 0) return null
        found = i
    }
    return if (found >= 0) stopTimes[found].distanceAlongTrip else null
}

/**
 * Finds the index of the first stop in the segment bracketing [distanceAlongTrip]. The segment
 * spans `stopTimes[result]`..`stopTimes[result + 1]`. Lives here with the rest of the
 * "search a schedule's stop times" logic, which `ObaTripSchedule`'s own doc places in this package.
 *
 * @throws IndexOutOfBoundsException if the distance is before the first stop, after the last stop,
 *         or there are fewer than 2 stops.
 */
fun ObaTripSchedule.findSegmentStartIndex(distanceAlongTrip: Double): Int {
    if (stopTimes.size < 2) {
        throw IndexOutOfBoundsException("Fewer than 2 stop times")
    }
    if (distanceAlongTrip < stopTimes[0].distanceAlongTrip) {
        throw IndexOutOfBoundsException("Distance is before first stop")
    }
    if (distanceAlongTrip > stopTimes[stopTimes.size - 1].distanceAlongTrip) {
        throw IndexOutOfBoundsException("Distance is after last stop")
    }
    for (i in 0 until stopTimes.size - 1) {
        if (stopTimes[i].distanceAlongTrip <= distanceAlongTrip &&
            distanceAlongTrip < stopTimes[i + 1].distanceAlongTrip
        ) {
            return i
        }
    }
    // At exactly the last stop's distance
    return stopTimes.size - 2
}

/**
 * A trip's scheduled distance-against-travel-time curve, measured forward from a vehicle's current
 * position to the end of the schedule. Piecewise linear with a knot at every remaining stop, so its
 * slope is that stop-to-stop segment's scheduled speed — the shape an extrapolation has to follow to
 * bend at a speed change instead of running on at the speed it started with (#2137).
 *
 * @property travelSeconds cumulative scheduled travel time from the anchor; starts at 0 and strictly
 *   increases
 * @property distances distance along the trip, in meters, at each of those times; starts at the
 *   anchor's own distance and never decreases
 * @property anchorSpeedMps scheduled speed, in m/s, of the segment the anchor sits in — the speed
 *   the trip is *currently* budgeted for, which conditions the speed model
 */
class TravelProfile
internal constructor(
    val travelSeconds: DoubleArray,
    val distances: DoubleArray,
    val anchorSpeedMps: Double
)

/**
 * Builds the [TravelProfile] running forward from [startDist] to the last scheduled stop, or null
 * when the schedule cannot support one: fewer than two stops, [startDist] outside the scheduled
 * distance range, a degenerate segment under the vehicle, or nothing left ahead of it.
 *
 * Scheduled dwells are deliberately excluded — each segment spans `departure[i]` to
 * `arrival[i + 1]`, travel time only. That matches the span the anchor speed itself is measured
 * over, and the gamma speed model this feeds already prices stopping into its slow component, so
 * replaying dwells here would count the same slowness twice. (Schedule replay for grade-separated
 * routes does include them — see [replaySchedule] — because it models one deterministic vehicle
 * rather than an ensemble.)
 */
fun ObaTripSchedule.travelProfileFrom(startDist: Double): TravelProfile? {
    if (stopTimes.size < 2) return null
    val segIdx =
        try {
            findSegmentStartIndex(startDist)
        } catch (e: IndexOutOfBoundsException) {
            return null
        }

    val segEnd = stopTimes[segIdx + 1]
    val segDist = segEnd.distanceAlongTrip - stopTimes[segIdx].distanceAlongTrip
    val segTime: Duration = segEnd.arrivalTime - stopTimes[segIdx].departureTime
    if (segDist <= 0 || segTime <= Duration.ZERO) return null
    val anchorSpeedMps = segDist / segTime.toDouble(DurationUnit.SECONDS)

    val remaining = stopTimes.size - segIdx
    val times = ArrayList<Double>(remaining)
    val distances = ArrayList<Double>(remaining)
    times.add(0.0)
    distances.add(startDist)

    /**
     * Adds the knot the schedule reaches [distance] at, [seconds] of travel from the anchor,
     * absorbing the two ways a malformed schedule can break the curve's shape. A segment that takes
     * no time (arrival at or before the previous departure) folds into the previous knot — the
     * schedule claims the ground is covered instantly — rather than duplicating a time, which the
     * transform needs to stay strictly increasing. A segment that covers no ground becomes a
     * plateau: time advances, distance does not, so the model waits there. Both mirror
     * `interpolateInSegment`'s conventions in [replaySchedule].
     */
    fun addKnot(seconds: Double, distance: Double) {
        val lastDistance = distances.last()
        if (seconds <= times.last()) {
            if (distance > lastDistance) distances[distances.size - 1] = distance
            return
        }
        times.add(seconds)
        distances.add(maxOf(distance, lastDistance))
    }

    // Partial first segment: the ground left between the vehicle and its next stop, at the speed
    // that segment is scheduled for.
    var cumulative = (segEnd.distanceAlongTrip - startDist) / anchorSpeedMps
    addKnot(cumulative, segEnd.distanceAlongTrip)

    for (i in (segIdx + 1) until stopTimes.size - 1) {
        val travel: Duration = stopTimes[i + 1].arrivalTime - stopTimes[i].departureTime
        cumulative += travel.toDouble(DurationUnit.SECONDS).coerceAtLeast(0.0)
        addKnot(cumulative, stopTimes[i + 1].distanceAlongTrip)
    }

    // Only reachable when the vehicle sits exactly on the last stop, leaving no schedule ahead.
    if (times.size < 2) return null

    return TravelProfile(times.toDoubleArray(), distances.toDoubleArray(), anchorSpeedMps)
}
