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
 * Finds the index of the first stop in the segment bracketing [distanceAlongTrip]. The segment
 * spans `stopTimes[result]`..`stopTimes[result + 1]`. Lives here because schedule geometry is only
 * needed by the extrapolators ([GammaExtrapolator], [replaySchedule]).
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
