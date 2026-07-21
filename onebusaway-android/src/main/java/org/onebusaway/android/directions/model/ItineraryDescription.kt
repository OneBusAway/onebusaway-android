/*
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 * Copyright (C) 2026 Open Transit Software Foundation
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

import java.time.Duration
import java.time.Instant
import org.onebusaway.android.directions.gtfsEntitySuffix

/**
 * The identity of a monitored itinerary: the ordered trip ids of its transit legs plus its end time.
 * The trip-plan-change monitor compares the itinerary the user is watching against a fresh re-plan by
 * matching these (see [org.onebusaway.android.directions.realtime.TripMonitorDecider]).
 *
 * **Identity is compared on the normalized entity suffix of each trip id, not the raw id.** OTP1 and
 * OTP2 label the same GTFS trip differently — OTP2 prefixes a feed id (`1:trip_5`), OTP1 does not
 * (`trip_5`) — so raw-string equality would report an unchanged trip as "changed" after the OTP1→OTP2
 * migration. [gtfsEntitySuffix] strips the feed prefix (the one sanctioned normalization), collapsing
 * both to the same underlying id so a monitor armed under either planner compares correctly. The raw
 * [tripIds] are retained as-is (that is what the monitor persists across process death); normalization
 * happens only at comparison time via [normalizedTripIds].
 */
class ItineraryDescription(val tripIds: List<String>, val endDate: Instant?) {

    /** The [tripIds] with each feed prefix stripped — the scheme-independent match key. */
    val normalizedTripIds: List<String> = tripIds.map { gtfsEntitySuffix(it) ?: it }

    constructor(itinerary: TripItinerary) : this(
        tripIds = itinerary.legs
            .filter { it.mode?.isTransit == true }
            .mapNotNull { it.tripId },
        endDate = itinerary.legs.lastOrNull()?.endTime?.let { Instant.ofEpochMilli(it.epochMs) }
    )

    /**
     * Whether this itinerary is the same one described by [other], comparing the normalized
     * ([normalizedTripIds]) trip ids as an ordered structural equality — so the same trips in the same
     * order match regardless of which planner (OTP1/OTP2) labeled them.
     */
    fun itineraryMatches(other: ItineraryDescription): Boolean = normalizedTripIds == other.normalizedTripIds

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
        // Both are server-provided instants; measure the span with java.time rather than raw epoch millis.
        return Duration.between(thisEnd, otherEnd).toMillis() / 1000
    }

    /**
     * @param now the current time, supplied by the caller (keep the clock out of this helper).
     * @return true if the itinerary's end date has passed
     */
    fun isExpired(now: Instant): Boolean = endDate?.isBefore(now) ?: false
}
