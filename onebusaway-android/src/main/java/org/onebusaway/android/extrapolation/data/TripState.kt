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

/** One recorded vehicle status snapshot with the clocks it arrived under. */
data class HistoryEntry(
        val status: ObaTripStatus,
        /** The server's currentTime when the status was fetched. */
        val serverTimeMs: Long,
        /** Local device clock when the response was received. */
        val localTimeMs: Long
)

/**
 * Immutable snapshot of everything known about a single tracked trip: vehicle history,
 * extrapolation anchor, schedule, polyline, and route metadata. Stored keyed by [tripId] in the
 * trip store (TripStore.kt); consumers look up the current snapshot per frame/tick — data
 * updates produce new instances via [withObservation], the other `with*` folds, and `copy`.
 *
 * Provides [extrapolate], which selects the appropriate strategy (gamma or schedule replay) based
 * on [routeType] and the data available. The strategy and its fitted model are lazily derived per
 * instance, so snapshot identity is the cache-invalidation signal: new data → new snapshot → fresh
 * fit.
 *
 * The [anchor] instance is carried across snapshots until newer data supersedes it, so consumers
 * may use reference equality on it to detect fresh data.
 */
data class TripState(
        val tripId: String,
        /** Raw vehicle status log (everything, for debugging), oldest first, capped at 100. */
        val history: List<HistoryEntry> = emptyList(),
        /** The most recent status by timestamp, with GPS winning ties. */
        val anchor: ObaTripStatus? = null,
        /**
         * Effective timestamp of the anchor in the **server** clock domain (lastUpdateTime, or
         * serverTimeMs as fallback). Used for plotting against other server-clock values.
         */
        val anchorTimeMs: Long = 0L,
        /**
         * Same instant as [anchorTimeMs], in the local device clock domain. Used for
         * extrapolation — comparing `System.currentTimeMillis()` against a server timestamp would
         * silently classify fresh data as stale under client/server clock skew.
         */
        val anchorLocalTimeMs: Long = 0L,
        val schedule: ObaTripSchedule? = null,
        /** Service date in ms since the epoch, or 0 when not yet known. */
        val serviceDate: Long = 0L,
        val polyline: Polyline? = null,
        val routeType: Int? = null,
        /**
         * The trip the vehicle serving this trip most recently reported as active — equal to
         * [tripId] while the vehicle is still on this run, and the successor run's trip ID once
         * the vehicle rolls onto its next trip. Null until a trip details response is recorded,
         * or when the latest one carried no vehicle status.
         */
        val vehicleActiveTripId: String? = null,
        val tripDetailsResponse: ObaTripDetailsResponse? = null
) {

    /** [history]'s raw statuses, projected once per snapshot. */
    val statuses: List<ObaTripStatus> by
            lazy(LazyThreadSafetyMode.NONE) { history.map { it.status } }

    // Body property: excluded from equals/hashCode/copy. Fresh per instance, so snapshot
    // identity invalidates the fitted model without any explicit cache key.
    private val extrapolator: Extrapolator by
            lazy(LazyThreadSafetyMode.NONE) {
                if (routeType != null && ObaRoute.isGradeSeparated(routeType)) {
                    ScheduleReplayExtrapolator(this)
                } else {
                    GammaExtrapolator(this)
                }
            }

    /**
     * Returns the state with [status] recorded, or `this` when the entry is skipped
     * (missing distance data or an exact duplicate of the previous entry). Returning `this`
     * keeps the snapshot — and with it the lazily fitted extrapolator and the anchor
     * instance — intact.
     *
     * @param serverTimeMs the server's currentTime from the API response, or 0 to use local clock
     * @param localTimeMs local device clock time when the response was received
     */
    fun withStatus(status: ObaTripStatus, serverTimeMs: Long, localTimeMs: Long): TripState {
        if (status.distanceAlongTrip == null || serverTimeMs <= 0) return this

        // Skip true duplicates (same distance and time as previous entry)
        val prev = history.lastOrNull()?.status
        if (prev != null &&
                        status.distanceAlongTrip == prev.distanceAlongTrip &&
                        status.lastUpdateTime == prev.lastUpdateTime
        ) {
            return this
        }

        // Update anchor: newest timestamp wins; GPS wins ties
        val effectiveTime = if (status.lastUpdateTime > 0) status.lastUpdateTime else serverTimeMs
        val serverLocalOffset = serverTimeMs - localTimeMs
        val anchorAdvances =
                effectiveTime > anchorTimeMs ||
                        (effectiveTime == anchorTimeMs &&
                                status.isLocationRealtime &&
                                anchor?.isLocationRealtime != true)

        val appended = history + HistoryEntry(status, serverTimeMs, localTimeMs)
        return copy(
                history = if (appended.size > MAX_ENTRIES) appended.takeLast(MAX_ENTRIES)
                else appended,
                anchor = if (anchorAdvances) status else anchor,
                anchorTimeMs = if (anchorAdvances) effectiveTime else anchorTimeMs,
                anchorLocalTimeMs =
                        if (anchorAdvances) effectiveTime - serverLocalOffset
                        else anchorLocalTimeMs
        )
    }

    /**
     * Returns the state with everything [observation] carries applied — the status
     * recorded, plus serviceDate and routeType when the observation has them.
     */
    fun withObservation(observation: TripObservation, localTimeMs: Long): TripState =
            withStatus(observation.status, observation.serverTimeMs, localTimeMs)
                    .withServiceDate(observation.serviceDate)
                    .withRouteType(observation.routeType)

    /** Returns the state with [serviceDate] applied, or `this` when it is unknown or unchanged. */
    fun withServiceDate(serviceDate: Long): TripState =
            if (serviceDate > 0 && serviceDate != this.serviceDate) copy(serviceDate = serviceDate)
            else this

    /**
     * Returns the state with [routeType] filled in, or `this` when it is already known or
     * [routeType] is null — route type is static per trip, so the first writer wins.
     */
    fun withRouteType(routeType: Int?): TripState =
            if (this.routeType == null && routeType != null) copy(routeType = routeType) else this

    /**
     * Returns the state with [schedule] filled in, or `this` when it is already known — the
     * schedule is an immutable resource (Fetchers.kt), so the first writer wins and repeated
     * poll-tick writes don't churn the snapshot.
     */
    fun withSchedule(schedule: ObaTripSchedule): TripState =
            if (this.schedule == null) copy(schedule = schedule) else this

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

        return extrapolator.doExtrapolate(lastDist, anchorLocalTimeMs, queryTimeMs)
    }

    companion object {
        /** A fresh state for [tripId]: no data yet, [extrapolate] reports [ExtrapolationResult.NoData]. */
        fun empty(tripId: String) = TripState(tripId)
    }
}
