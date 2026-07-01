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
package org.onebusaway.android.io.elements

/**
 * For display we always use [ObaTripStatus.getPosition] (the server-extrapolated location) because
 * it aligns with the polyline distance fraction (PDF) calculations exactly. Do NOT substitute
 * lastKnownLocation here — it has slightly different semantics (raw GPS fix) and will disagree with
 * the PDF overlay.
 */

/**
 * True if the server provided a real-time location for this vehicle — i.e. it has a last-known
 * location *and* the trip is predicted.
 */
val ObaTripStatus.isLocationRealtime: Boolean
    get() = lastKnownLocation != null && isPredicted

/**
 * A key over a situation's rider-visible content (summary, description, advice, reason, url,
 * severity). Two situations that present identically to the rider share a key even if their id,
 * creation time, or active windows differ — which is how republished-duplicate alerts are collapsed
 * (see #1593). Fields are joined with a NUL separator so adjacent values can't run together and
 * collide across field boundaries.
 */
val ObaSituation.contentKey: String
    get() = listOf(summary, description, advice, reason, url, severity)
        .joinToString("\u0000") { it.orEmpty() }

/**
 * Computes the scheduled segment speed (m/s) at a given distance along the trip. Returns null if
 * the schedule has too few stops or the distance is out of bounds.
 */
fun ObaTripSchedule.speedAtDistance(distanceAlongTrip: Double): Double? {
    val stopTimes = stopTimes ?: return null
    if (stopTimes.size < 2) return null

    val segmentStart =
            try {
                findSegmentStartIndex(distanceAlongTrip)
            } catch (e: IndexOutOfBoundsException) {
                return null
            }

    val distDelta =
            stopTimes[segmentStart + 1].distanceAlongTrip -
                    stopTimes[segmentStart].distanceAlongTrip
    val timeDelta = stopTimes[segmentStart + 1].arrivalTime - stopTimes[segmentStart].departureTime

    return if (distDelta > 0 && timeDelta > 0) distDelta / timeDelta else null
}
