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

import org.onebusaway.android.models.ObaTripSchedule

/**
 * Every offset along the trip at which it serves [stopId], in travel order — empty when it doesn't,
 * and more than one entry for a loop or out-and-back that passes the stop again. Callers decide what
 * an ambiguous stop means to them; none of them may guess a visit (see [ObaTripSchedule.soleOffsetOf]
 * and the map's ride-eligibility filter, which refuse in different ways).
 */
fun ObaTripSchedule.offsetsOf(stopId: String): List<Double> = stopTimes.filter { it.stopId == stopId }.map { it.distanceAlongTrip }

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
