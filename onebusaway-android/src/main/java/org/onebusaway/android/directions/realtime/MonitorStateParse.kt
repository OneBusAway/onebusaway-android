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
package org.onebusaway.android.directions.realtime

import java.time.Instant
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.time.ServerTime

/**
 * The outcome of validating a persisted monitor bundle before (re)starting the loop. A pure sealed type
 * (no Android/`Bundle` dependency) so the version/validity gate is JVM-unit-testable — see
 * [parseMonitorState].
 */
sealed interface MonitorStateParse {
    /**
     * The bundle is usable: monitor the itinerary described by [description] (its server-clock
     * [departure] is null when unknown, i.e. fall back to the trip-end guard).
     */
    data class Valid(
        val description: ItineraryDescription,
        val departure: ServerTime?
    ) : MonitorStateParse

    /** Required state is absent/empty — there is nothing to monitor. Stop without notifying. */
    data object Missing : MonitorStateParse

    /**
     * The bundle was written by a newer, incompatible monitor format — a pending alarm or redelivered
     * `START_REDELIVER_INTENT` surviving an app update. We can't trust it, so stop **without** notifying
     * rather than risk a spurious "your trip changed" alert on state we can no longer interpret.
     */
    data object Incompatible : MonitorStateParse
}

/**
 * Validate the raw primitives read from a monitor bundle. Pure (no Android types) so it is unit-testable.
 *
 * @param version the persisted [TripPlanMonitor.MONITOR_STATE_VERSION]; null when the key is absent — a
 *   bundle written before versioning, treated as the compatible legacy v0 (its raw ids still normalize
 *   and compare correctly, so it is safe to honor).
 * @param tripIds the raw (un-normalized) transit-leg trip ids of the watched itinerary.
 * @param startDateMillis the itinerary's server-clock departure epoch millis; 0 = unknown.
 * @param endDateMillis the itinerary's server-clock end epoch millis; 0 = absent (unusable).
 */
fun parseMonitorState(
    version: Int?,
    tripIds: List<String>?,
    startDateMillis: Long,
    endDateMillis: Long
): MonitorStateParse {
    if (version != null && version > TripPlanMonitor.MONITOR_STATE_VERSION) {
        return MonitorStateParse.Incompatible
    }
    if (tripIds.isNullOrEmpty() || endDateMillis == 0L) {
        return MonitorStateParse.Missing
    }
    val description = ItineraryDescription(tripIds, Instant.ofEpochMilli(endDateMillis))
    val departure = if (startDateMillis != 0L) ServerTime(startDateMillis) else null
    return MonitorStateParse.Valid(description, departure)
}
