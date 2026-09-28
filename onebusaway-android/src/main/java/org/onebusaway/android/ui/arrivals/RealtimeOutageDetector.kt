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
package org.onebusaway.android.ui.arrivals

/**
 * Minimum number of arrivals for an agency at a stop before an absence of realtime predictions
 * is flagged as an outage (issue #2301). Prevents false alarms on stops with only 1–2 scheduled
 * trips in the window.
 */
const val MIN_ARRIVALS_FOR_REALTIME_OUTAGE = 3

/**
 * An operating transit agency identified by its unique ID and display name.
 *
 * @param id the unique agency identifier from GTFS / OBA references
 * @param name the human-readable display name of the agency
 */
data class OperatingAgency(
    val id: String,
    val name: String
)

/**
 * An agency whose realtime predictions appear to be offline at the stop being viewed.
 *
 * @param agencyId the unique identifier of the affected transit agency
 * @param agencyName the display name of the affected transit agency
 */
data class RealtimeOutage(
    val agencyId: String,
    val agencyName: String
) {
    /**
     * Secondary constructor allowing creation with only [agencyName] for testing convenience.
     *
     * @param agencyName the display name of the affected transit agency
     */
    constructor(agencyName: String) : this(agencyId = agencyName, agencyName = agencyName)
}

/** Identity of one trip instance whose realtime prediction was present in a prior response. */
internal data class RealtimeTripInstanceKey(
    val agencyId: String,
    val tripId: String,
    val serviceDate: Long
)

/**
 * Detects whether any transit agency serving the given [arrivals] is experiencing a realtime data
 * outage (issue #2301).
 *
 * An agency is flagged as having an outage if:
 * 1. It has at least [MIN_ARRIVALS_FOR_REALTIME_OUTAGE] arrivals at this stop within the time window.
 * 2. None of its arrivals have realtime predictions ([ArrivalInfo.predicted] is false for all).
 * 3. At least [MIN_ARRIVALS_FOR_REALTIME_OUTAGE] of the same trip instances had realtime predictions
 *    in the preceding successful response for this stop and region, or an outage was already
 *    established and predictions have not returned.
 *
 * Arrivals are grouped by their unique [OperatingAgency.id] so that per-agency thresholds and
 * prediction checks remain strictly independent, even if two distinct agencies share the same display
 * name. A first response containing scheduled-only arrivals is not evidence of an outage; prior
 * realtime observations establish that the affected trip instances were expected to be predicted.
 * If an agency has even a single predicted arrival, no outage is flagged for it. Agencies with
 * fewer than [MIN_ARRIVALS_FOR_REALTIME_OUTAGE] arrivals or missing/blank ID/name are ignored.
 * This remains client-side inference: an outage already active on first observation is unknown, and
 * ordinary prediction removals for multiple previously-predicted trips can still resemble an outage.
 *
 * @param arrivals the list of arrivals loaded for the stop
 * @param agencyOf resolves an arrival's operating agency (ID and display name) from route references
 * @return a list of [RealtimeOutage] objects for affected agencies, ordered alphabetically by agency name
 */
internal fun detectRealtimeOutages(
    arrivals: List<ArrivalInfo>,
    previouslyRealtimeTripKeys: Set<RealtimeTripInstanceKey> = emptySet(),
    previouslyOutagedAgencyIds: Set<String> = emptySet(),
    agencyOf: (ArrivalInfo) -> OperatingAgency?
): List<RealtimeOutage> {
    if (arrivals.isEmpty()) return emptyList()

    return arrivals
        .mapNotNull { arrival ->
            val agency = agencyOf(arrival)
            if (agency == null || agency.id.isBlank() || agency.name.isBlank()) {
                null
            } else {
                agency to arrival
            }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapNotNull { (agency, agencyArrivals) ->
            val lostPreviouslyPredictedTrips = agencyArrivals.count { arrival ->
                RealtimeTripInstanceKey(agency.id, arrival.tripId, arrival.serviceDate) in previouslyRealtimeTripKeys
            }
            val outageEstablished = agency.id in previouslyOutagedAgencyIds ||
                lostPreviouslyPredictedTrips >= MIN_ARRIVALS_FOR_REALTIME_OUTAGE
            if (
                agencyArrivals.size >= MIN_ARRIVALS_FOR_REALTIME_OUTAGE &&
                agencyArrivals.none { it.predicted } &&
                outageEstablished
            ) {
                RealtimeOutage(agencyId = agency.id, agencyName = agency.name)
            } else {
                null
            }
        }
        .sortedWith(compareBy({ it.agencyName }, { it.agencyId }))
}
