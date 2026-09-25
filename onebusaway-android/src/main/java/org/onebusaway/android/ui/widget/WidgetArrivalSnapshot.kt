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
package org.onebusaway.android.ui.widget

import kotlinx.serialization.Serializable

/**
 * Arrival data fetched from the OBA API, persisted (as JSON, via [WidgetPrefs]) so the widget can
 * update its relative-time labels every minute ("5 min" -> "4 min" -> "3 min") without an API call.
 *
 * The instants are raw epoch/monotonic millis, not the app's typed [org.onebusaway.android.time.ServerTime]/
 * [org.onebusaway.android.time.ElapsedTime] — this class is the storage boundary (like
 * `StopArrivals.currentTime` itself), so a plain `Long` is the sanctioned shape here. Callers mint them
 * back into the typed domain immediately on read (see `StopTimesWidget.updateArrivalsFromCache`) rather
 * than ever comparing these raw fields against another clock directly.
 *
 * @param serverTimeAtFetchMs the OBA server's `currentTime` when this snapshot was fetched.
 * @param receivedAtElapsedMs the device's monotonic clock reading ([ElapsedClock]) at the same moment,
 * pairing the server time with a local receipt time so the server clock can be projected forward by
 * elapsed device time on each per-minute tick (#1612 — never compared against a fresh device wall time).
 */
@Serializable
data class WidgetArrivalSnapshot(
    val serverTimeAtFetchMs: Long,
    val receivedAtElapsedMs: Long,
    val routes: List<Route>
) {

    /** A single configured route with its upcoming arrivals (empty when the route has none). */
    @Serializable
    data class Route(val shortName: String, val arrivals: List<Arrival>)

    /**
     * A single upcoming arrival. [isPredicted] is the already-resolved "does this trip have a usable
     * real-time prediction" decision (mirrors `ArrivalInfo`'s `hasPrediction`) — not re-derived from a
     * sentinel value here, since a real-time prediction can legitimately be absent without the instant
     * being some magic "unset" number (#1687).
     */
    @Serializable
    data class Arrival(val scheduledTimeMs: Long, val displayTimeMs: Long, val isPredicted: Boolean)
}
