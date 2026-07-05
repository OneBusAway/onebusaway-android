/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.model

import org.onebusaway.android.directions.util.ConversionUtils
import org.opentripplanner.api.model.Itinerary
import org.opentripplanner.routing.core.TraverseMode
import java.time.Instant

/**
 * Itinerary desciption is a list of trips and a rank. This is for the Realtime service.
 */
class ItineraryDescription {

    val tripIds: List<String>

    val endDate: Instant?

    constructor(itinerary: Itinerary) {
        val ids = ArrayList<String>()
        for (leg in itinerary.legs) {
            val traverseMode = TraverseMode.valueOf(leg.mode)
            if (traverseMode.isTransit) {
                ids.add(leg.tripId)
            }
        }
        tripIds = ids

        val last = itinerary.legs[itinerary.legs.size - 1]
        endDate = ConversionUtils.parseOtpDate(last.endTime)
    }

    constructor(tripIds: List<String>, endDate: Instant?) {
        this.tripIds = tripIds
        this.endDate = endDate
    }

    /**
     * Check if this itinerary matches the itinerary of another ItineraryDescription
     *
     * @param other object to compare to
     * @return true if matches, false otherwise
     */
    fun itineraryMatches(other: ItineraryDescription): Boolean =
        // Ordered structural equality: same trip IDs in the same order.
        tripIds == other.tripIds

    /**
     * Check the delay on this itinerary relative to a newer one.
     * Positive indicates a delay, negative indicates running early.
     *
     * @param other Newer itinerary to use to calculate delay.
     * @return delay in seconds, or null if either itinerary has no parseable end date.
     */
    fun getDelay(other: ItineraryDescription): Long? {
        val otherEnd = other.endDate ?: return null
        val thisEnd = endDate ?: return null
        // Both are server-provided instants; same-domain subtraction is safe.
        return (otherEnd.toEpochMilli() - thisEnd.toEpochMilli()) / 1000
    }

    /**
     * An ID for this ItineraryDescription.
     * The notification requires an ID so it does not create duplicates. Right now, sending a
     * notification cancels out the RealtimeService, so we do not send multiple notifications,
     * but we may in future.
     * Use the hash code of the trips array. Not guaranteed to be unique.
     */
    val id: Int
        get() = if (tripIds.isEmpty()) -1 else tripIds.hashCode()

    /**
     * @param now the current time, supplied by the caller (keep the clock out of this helper).
     * @return true if the itinerary's end date has passed
     */
    fun isExpired(now: Instant): Boolean = endDate?.isBefore(now) ?: false
}
