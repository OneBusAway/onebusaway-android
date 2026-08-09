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
import org.onebusaway.android.time.ScheduleTime

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
 * The schedule clock read at a single distance along the trip, interpolated across the travel
 * segment the distance sits in (`departure[i]` → `arrival[i+1]`, so crossing a stop picks up its
 * whole dwell) — or null when the schedule cannot place it: fewer than two stops, a distance
 * outside the scheduled range, or a degenerate segment. The scheduled interval between two
 * distances is then a same-domain [ScheduleTime] subtraction; [PaceModel]'s lookback reads the
 * ground a vehicle actually covered in schedule terms this way.
 */
fun ObaTripSchedule.scheduleTimeAt(distanceAlongTrip: Double): ScheduleTime? {
    if (stopTimes.size < 2) return null
    val segIdx =
        try {
            findSegmentStartIndex(distanceAlongTrip)
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
    val segStart = stopTimes[segIdx]
    val segEnd = stopTimes[segIdx + 1]
    val segDist = segEnd.distanceAlongTrip - segStart.distanceAlongTrip
    val segTravel: Duration = segEnd.arrivalTime - segStart.departureTime
    if (segDist <= 0 || segTravel <= Duration.ZERO) return null
    val fraction = (distanceAlongTrip - segStart.distanceAlongTrip) / segDist
    return segStart.departureTime + segTravel * fraction
}

/**
 * A trip's scheduled clock, read forward from a vehicle's current position: how long the schedule
 * says it should take to *first reach* each point ahead.
 *
 * Counts scheduled dwells, because it is compared against wall-clock elapsed time and a vehicle
 * really does spend the dwell. A dwell shows up as a plateau: schedule time advances while distance
 * does not.
 *
 * Deliberately just the two arrays. Reading the curve is [FirstPassageDistribution]'s job, and it
 * needs the mapping in both directions with its own plateau conventions, so a lookup here would be
 * a second copy of the same interpolation rather than a service to anyone.
 *
 * @property scheduleSeconds cumulative scheduled seconds from the anchor; starts at 0, non-decreasing
 * @property distances distance along the trip in meters at each knot; starts at the anchor's own
 *   distance, non-decreasing
 */
class PassageProfile
internal constructor(
    val scheduleSeconds: DoubleArray,
    val distances: DoubleArray
)

/**
 * Builds the [PassageProfile] from [startDist] to the last scheduled stop, or null when the schedule
 * cannot support one: fewer than two stops, a distance outside the scheduled range, a degenerate
 * segment under the vehicle, or nothing left ahead of it.
 */
fun ObaTripSchedule.passageProfileFrom(startDist: Double): PassageProfile? {
    // The anchor's own place on the schedule clock — the same construction replaySchedule uses to
    // put a distance on the schedule timeline. Also rejects everything a profile can't be built
    // from: too few stops, a distance out of range, a degenerate segment under the vehicle.
    val origin: ScheduleTime = scheduleTimeAt(startDist) ?: return null
    val segIdx = findSegmentStartIndex(startDist)

    val seconds = ArrayList<Double>(2 * (stopTimes.size - segIdx))
    val distances = ArrayList<Double>(2 * (stopTimes.size - segIdx))
    seconds.add(0.0)
    distances.add(startDist)

    fun addKnot(t: Double, d: Double) {
        // Schedule time must advance for the curve to stay invertible; a stop the schedule
        // reaches no later than the previous knot folds into it, carrying its distance forward.
        if (t <= seconds.last()) {
            if (d > distances.last()) distances[distances.size - 1] = d
            return
        }
        seconds.add(t)
        distances.add(maxOf(d, distances.last()))
    }

    for (i in (segIdx + 1) until stopTimes.size) {
        val stop = stopTimes[i]
        addKnot((stop.arrivalTime - origin).toDouble(DurationUnit.SECONDS), stop.distanceAlongTrip)
        addKnot((stop.departureTime - origin).toDouble(DurationUnit.SECONDS), stop.distanceAlongTrip)
    }

    if (seconds.size < 2) return null
    return PassageProfile(seconds.toDoubleArray(), distances.toDoubleArray())
}
