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
 * An agency whose realtime predictions appear to be offline at the stop being viewed.
 *
 * @param agencyName the display name of the affected transit agency
 */
data class RealtimeOutage(
    val agencyName: String
)

/**
 * Detects whether any transit agency serving the given [arrivals] is experiencing a realtime data
 * outage (issue #2301).
 *
 * An agency is flagged as having an outage if:
 * 1. It has at least [MIN_ARRIVALS_FOR_REALTIME_OUTAGE] arrivals at this stop within the time window.
 * 2. None of its arrivals have realtime predictions ([ArrivalInfo.predicted] is false for all).
 *
 * If an agency has even a single predicted arrival, no outage is flagged for it (partial coverage
 * is normal). Agencies with fewer than [MIN_ARRIVALS_FOR_REALTIME_OUTAGE] arrivals or an empty/blank
 * name are ignored to avoid false positives on low-frequency services or incomplete metadata.
 *
 * @param arrivals the list of arrivals loaded for the stop
 * @param agencyNameOf resolves an arrival's agency display name from route references
 * @return a list of [RealtimeOutage] objects for affected agencies, ordered alphabetically by agency name
 */
fun detectRealtimeOutages(
    arrivals: List<ArrivalInfo>,
    agencyNameOf: (ArrivalInfo) -> String?
): List<RealtimeOutage> {
    if (arrivals.isEmpty()) return emptyList()

    return arrivals
        .groupBy { agencyNameOf(it) }
        .mapNotNull { (agencyName, agencyArrivals) ->
            if (agencyName.isNullOrBlank()) return@mapNotNull null
            if (agencyArrivals.size >= MIN_ARRIVALS_FOR_REALTIME_OUTAGE && agencyArrivals.none { it.predicted }) {
                RealtimeOutage(agencyName = agencyName)
            } else {
                null
            }
        }
        .sortedBy { it.agencyName }
}
