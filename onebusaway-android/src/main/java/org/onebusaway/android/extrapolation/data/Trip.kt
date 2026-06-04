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
package org.onebusaway.android.extrapolation.data

import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.Extrapolator
import org.onebusaway.android.extrapolation.GammaExtrapolator
import org.onebusaway.android.extrapolation.ScheduleReplayExtrapolator
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.isLocationRealtime
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.util.Polyline

private const val MAX_ENTRIES = 100
private const val MAX_HORIZON_MS = 15 * 60 * 1000L
private const val PRE_DEPARTURE_DISTANCE_THRESHOLD = 50.0
private const val TRIP_END_DISTANCE_THRESHOLD = 50.0

/**
 * All data for a single tracked trip: vehicle history, extrapolation anchor, schedule, polyline,
 * and route metadata. Provides [extrapolate] which selects the appropriate strategy (gamma,
 * schedule replay, or schedule-only fallback) based on the data available.
 *
 * Instances are permanent: the trip store (TripStore.kt) guarantees one instance per tripId for
 * the life of the process, so references may be held freely. Payload may be cleared ([clearData])
 * when the trip goes cold, after which the instance simply reports no data until it is recorded
 * again.
 *
 * Thread safety: main thread only — see TripStore.kt's threading contract.
 */
class Trip(val tripId: String) {

    // --- Vehicle history (raw log — everything, for debugging) ---
    val history = mutableListOf<ObaTripStatus>()
    val fetchTimes = mutableListOf<Long>()
    val localFetchTimes = mutableListOf<Long>()

    /** The most recent status by timestamp, with GPS winning ties. */
    var anchor: ObaTripStatus? = null
        private set
    /**
     * Effective timestamp of the anchor in the **server** clock domain (lastUpdateTime, or
     * serverTimeMs as fallback). Used for plotting against other server-clock values such as
     * [ObaTripStatus.lastUpdateTime] and service-date-based schedule times.
     */
    var anchorTimeMs: Long = 0L
        private set
    /**
     * Same instant as [anchorTimeMs], in the local device clock domain. Used for extrapolation —
     * comparing `System.currentTimeMillis()` against a server timestamp would silently classify
     * fresh data as stale under client/server clock skew.
     */
    var anchorLocalTimeMs: Long = 0L
        private set

    // --- Caches ---
    var schedule: ObaTripSchedule? = null
    /** Service date in ms since the epoch, or 0 when not yet known. */
    var serviceDate: Long = 0
    var polyline: Polyline? = null
    var routeType: Int? = null
    /**
     * The trip the vehicle serving this trip most recently reported as active — equal to [tripId]
     * while the vehicle is still on this run, and the successor run's trip ID once the vehicle
     * rolls onto its next trip. Null until a trip details response is recorded.
     */
    var vehicleActiveTripId: String? = null
    var tripDetailsResponse: ObaTripDetailsResponse? = null

    // --- Extrapolation ---

    private var extrapolator: Extrapolator? = null

    // --- Recording ---

    /**
     * Records a status snapshot. Skips entries without valid distance data.
     * @param serverTimeMs the server's currentTime from the API response, or 0 to use local clock
     * @param localTimeMs local device clock time when the response was received
     * @return true if the entry was actually recorded, false on dedup skip or missing data
     */
    fun recordStatus(status: ObaTripStatus, serverTimeMs: Long, localTimeMs: Long): Boolean {
        if (status.distanceAlongTrip == null || serverTimeMs <= 0) return false

        // Skip true duplicates (same distance and time as previous entry)
        val prev = history.lastOrNull()
        if (prev != null &&
                        status.distanceAlongTrip == prev.distanceAlongTrip &&
                        status.lastUpdateTime == prev.lastUpdateTime
        ) {
            return false
        }

        history.add(status)
        fetchTimes.add(serverTimeMs)
        localFetchTimes.add(localTimeMs)

        // Update anchor: newest timestamp wins; GPS wins ties
        val effectiveTime = if (status.lastUpdateTime > 0) status.lastUpdateTime else serverTimeMs
        val serverLocalOffset = serverTimeMs - localTimeMs
        if (effectiveTime > anchorTimeMs ||
                        (effectiveTime == anchorTimeMs &&
                                status.isLocationRealtime &&
                                anchor?.isLocationRealtime != true)
        ) {
            anchor = status
            anchorTimeMs = effectiveTime
            anchorLocalTimeMs = effectiveTime - serverLocalOffset
        }

        if (history.size > MAX_ENTRIES) {
            history.subList(0, history.size - MAX_ENTRIES).clear()
            fetchTimes.subList(0, fetchTimes.size - MAX_ENTRIES).clear()
            localFetchTimes.subList(0, localFetchTimes.size - MAX_ENTRIES).clear()
        }
        return true
    }

    // --- Extrapolation ---

    fun extrapolate(queryTimeMs: Long): ExtrapolationResult {
        val currentAnchor = anchor ?: return ExtrapolationResult.NoData
        val lastDist = currentAnchor.distanceAlongTrip ?: return ExtrapolationResult.NoData
        if (anchorLocalTimeMs <= 0) return ExtrapolationResult.NoData

        val dtMs = queryTimeMs - anchorLocalTimeMs
        if (dtMs < 0 || dtMs > MAX_HORIZON_MS) return ExtrapolationResult.Stale
        if (lastDist <= PRE_DEPARTURE_DISTANCE_THRESHOLD) return ExtrapolationResult.TripNotStarted
        val totalDist = currentAnchor.totalDistanceAlongTrip
        if (totalDist != null && totalDist > 0 && totalDist - lastDist < TRIP_END_DISTANCE_THRESHOLD
        ) {
            return ExtrapolationResult.TripEnded
        }

        return getOrCreateExtrapolator().doExtrapolate(lastDist, anchorLocalTimeMs, queryTimeMs)
    }

    // --- Eviction ---

    /**
     * Drops all payload, returning this trip to a fresh-shell state. Called by the trip store
     * when the trip is evicted from the warm set. The instance itself is permanent, so holders
     * simply observe empty data until the trip is recorded again.
     */
    fun clearData() {
        history.clear()
        fetchTimes.clear()
        localFetchTimes.clear()
        anchor = null
        anchorTimeMs = 0L
        anchorLocalTimeMs = 0L
        schedule = null
        serviceDate = 0
        polyline = null
        routeType = null
        vehicleActiveTripId = null
        tripDetailsResponse = null
        extrapolator = null
    }

    private fun getOrCreateExtrapolator(): Extrapolator {
        extrapolator?.let {
            return it
        }
        val ext =
                if (routeType != null && ObaRoute.isGradeSeparated(routeType!!))
                        ScheduleReplayExtrapolator(this)
                else GammaExtrapolator(this)
        extrapolator = ext
        return ext
    }
}
