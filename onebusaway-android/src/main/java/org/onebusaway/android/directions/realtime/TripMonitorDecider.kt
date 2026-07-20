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

import kotlin.math.abs
import kotlin.time.Duration
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

/**
 * The outcome of comparing the itinerary the user is monitoring against a freshly-planned set of
 * results. Kept a pure sealed type (no Android or clock dependency) so [TripMonitorDecider.decide] is
 * JVM-unit-testable — the branchy decision logic that used to live inline in
 * `RealtimeChecker.checkForItineraryChange`.
 */
sealed interface MonitorResult {
    /**
     * The monitored itinerary still exists but its end time moved by more than the threshold.
     * [delaySeconds] > 0 means later than planned (delayed); < 0 means earlier.
     */
    data class Deviation(val delaySeconds: Long) : MonitorResult

    /** No matching itinerary in the new results — the recommended plan has changed. */
    data object ItineraryChanged : MonitorResult

    /** The itinerary still matches within the threshold — keep polling. */
    data object KeepMonitoring : MonitorResult

    /** Monitoring should stop without notifying: the plan returned no results (legacy failure path). */
    data object Stop : MonitorResult
}

/**
 * Pure decision function for the trip-plan-change monitor. Mirrors the branch structure of the legacy
 * `RealtimeChecker.checkForItineraryChange`, factored out so it can be exercised without a running
 * service or Android framework. Expiry/departure bounds are the service loop's job (they need the
 * clock), so this stays clock-free.
 */
object TripMonitorDecider {

    /**
     * Decide what the monitor should do given the [current] itinerary being watched and the [results]
     * of a fresh plan for the same origin/destination/time.
     *
     * @param thresholdSeconds absolute end-time deviation (seconds) that counts as a notable change
     *   (see [org.onebusaway.android.directions.util.OTPConstants.REALTIME_SERVICE_DELAY_THRESHOLD]).
     */
    @JvmStatic
    fun decide(
        current: ItineraryDescription,
        results: List<TripItinerary>,
        thresholdSeconds: Long
    ): MonitorResult {
        if (results.isEmpty()) {
            // Matches the legacy failure path: no results returned -> stop listening.
            return MonitorResult.Stop
        }

        for (itinerary in results) {
            val other = ItineraryDescription(itinerary)
            if (!current.itineraryMatches(other)) {
                continue
            }
            // Found the itinerary the user is on. getDelay is null when either end date is unparseable;
            // treat that as "no notable change" and keep polling.
            val delay = current.getDelay(other)
            if (delay != null && abs(delay) > thresholdSeconds) {
                return MonitorResult.Deviation(delay)
            }
            return MonitorResult.KeepMonitoring
        }

        // The monitored itinerary is gone from the results.
        return MonitorResult.ItineraryChanged
    }
}

/** Pure timing helper for the monitor's start/stop boundaries (extracted for JVM testing). */
object TripMonitorWindow {

    /**
     * Whether monitoring should start polling immediately rather than being deferred. True once we're
     * inside the pre-departure query [window] (or the departure has already passed); false for a trip
     * far enough out that the foreground service should be scheduled to start later. [departure] (the
     * user-picked request time) and [now] are both on the device wall clock, so this is a same-domain
     * comparison ([window] = [org.onebusaway.android.directions.util.OTPConstants.REALTIME_SERVICE_QUERY_WINDOW]).
     */
    @JvmStatic
    fun shouldStartNow(departure: WallTime, now: WallTime, window: Duration): Boolean = departure - now <= window

    /**
     * Whether the monitored trip has already departed, so warning the user is moot and the foreground
     * service should stop. A null [departure] means the itinerary's start time was unknown (couldn't be
     * parsed), in which case we don't bound on it and fall back to the trip-end guard.
     *
     * This is the monitor's one deliberate server-clock ([departure], the itinerary start) vs
     * device-clock ([now]) crossing, done on raw `epochMs` — it's a coarse foreground-service lifecycle
     * bound ("has this moment passed?"), not a user-facing duration, so device-clock skew is immaterial.
     */
    // Sanctioned server↔device crossing (see KDoc): the typed API forbids ServerTime vs WallTime by
    // design; here the coarse "has this moment passed?" lifecycle bound accepts the device-clock skew.
    @Suppress("PrematureUnwrap")
    @JvmStatic
    fun hasDeparted(departure: ServerTime?, now: WallTime): Boolean = departure != null && now.epochMs >= departure.epochMs
}
