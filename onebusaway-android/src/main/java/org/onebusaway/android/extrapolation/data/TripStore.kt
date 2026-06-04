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

import android.location.Location
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/**
 * Registry of [Trip] objects. All per-trip data lives on Trip; this object owns trip identity,
 * payload retention, and change notifications. It is pure synchronous state — network fetches live
 * in [TripFetcher] and the pollers, which write their results back in here.
 *
 * Identity vs retention are deliberately separate. A [Trip] instance is **permanent**: the same
 * tripId always resolves to the same object, so holding a Trip reference (in a fragment, a marker
 * state, a frame loop) is always safe — new data recorded for that tripId lands in the instance
 * the holder is reading. What is bounded is the *payload*: once more than [MAX_TRACKED_TRIPS]
 * trips hold data, the least-recently-used trip's data is cleared ([Trip.clearData]), leaving a
 * shell of a few dozen bytes that refills if the trip is ever recorded again. Reads promote a warm
 * trip in the retention order, so a trip whose data is actively read or recorded is never the
 * eviction victim.
 *
 * Thread safety: **main thread only.** All public methods must be called from the main thread.
 * Background work (network fetches) lives in the pollers and [TripFetcher]; results are posted
 * back to the main thread before they touch any state here. This invariant lets [Trip] hold plain
 * (non-volatile, non-locked) mutable fields and lets the per-frame extrapolation loop read them
 * directly.
 */
object TripStore {

    private const val MAX_TRACKED_TRIPS = 100

    /** Permanent identity map: one [Trip] instance per tripId, never removed. */
    private val registry = HashMap<String, Trip>()

    /**
     * Retention policy: the trips currently holding payload, bounded at [MAX_TRACKED_TRIPS].
     * Capacity eviction clears the trip's payload ([Trip.clearData]) but never touches [registry]
     * — identity is permanent, only data is reclaimed. Writes insert/promote via [warmTrip];
     * reads promote via [promote].
     */
    private val warm =
            object : LruCache<String, Trip>(MAX_TRACKED_TRIPS) {
                override fun entryRemoved(
                        evicted: Boolean,
                        key: String,
                        oldValue: Trip,
                        newValue: Trip?
                ) {
                    // Only clear on capacity eviction — put() replacing an entry with the same
                    // Trip instance also lands here, with evicted == false.
                    if (evicted) oldValue.clearData()
                }
            }

    // --- Change notifications ---

    private val _changes =
            MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

    /** Emits Unit whenever any mutation method runs. Coalesces bursts via DROP_OLDEST. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    // --- Trip registry ---

    fun getOrCreateTrip(tripId: String): Trip {
        promote(tripId)
        return registry.getOrPut(tripId) { Trip(tripId) }
    }

    fun getTrip(tripId: String?): Trip? {
        if (tripId == null) return null
        promote(tripId)
        return registry[tripId]
    }

    /** Returns the trip as a retention-tracked write target, creating it if needed. */
    private fun warmTrip(tripId: String): Trip {
        val trip = registry.getOrPut(tripId) { Trip(tripId) }
        warm.put(tripId, trip)
        return trip
    }

    /** Marks the trip recently used so eviction prefers idle trips. No-op if not warm. */
    private fun promote(tripId: String) {
        // LruCache.get moves the entry to most-recently-used. Result intentionally unused.
        warm.get(tripId)
    }

    // --- Recording ---

    /**
     * Records a trip status snapshot. Deduplicates history by distance — only adds an entry when
     * the vehicle has moved.
     */
    fun recordStatus(status: ObaTripStatus?, serverTimeMs: Long, localTimeMs: Long) {
        if (status == null) return
        val tripId = status.activeTripId ?: return
        val changed = warmTrip(tripId).recordStatus(status, serverTimeMs, localTimeMs)
        if (changed) notifyChanged()
    }

    fun recordTripDetailsResponse(
            polledTripId: String?,
            response: ObaTripDetailsResponse?,
            localTimeMs: Long
    ) {
        if (response == null) return
        val status = response.status ?: return
        var changed = false
        if (polledTripId != null) {
            val polledTrip = warmTrip(polledTripId)
            if (polledTrip.vehicleActiveTripId != status.activeTripId) {
                polledTrip.vehicleActiveTripId = status.activeTripId
                changed = true
            }
            polledTrip.tripDetailsResponse = response
        }
        val activeTripId = status.activeTripId ?: return
        val trip = warmTrip(activeTripId)
        if (trip.recordStatus(status, response.currentTime, localTimeMs)) changed = true
        if (status.serviceDate > 0 && trip.serviceDate != status.serviceDate) {
            trip.serviceDate = status.serviceDate
            changed = true
        }
        if (changed) notifyChanged()
    }

    fun recordTripsForRouteResponse(response: ObaTripsForRouteResponse, localTimeMs: Long) {
        val serverTime = response.currentTime
        var changed = false
        response.forEachActiveTrip { tripId, status, activeTrip ->
            val trip = warmTrip(tripId)
            if (trip.recordStatus(status, serverTime, localTimeMs)) changed = true
            if (status.serviceDate > 0 && trip.serviceDate != status.serviceDate) {
                trip.serviceDate = status.serviceDate
                changed = true
            }
            if (trip.routeType == null) {
                val routeId = activeTrip.routeId
                val route = if (routeId != null) response.getRoute(routeId) else null
                if (route != null) {
                    trip.routeType = route.type
                    changed = true
                }
            }
        }
        if (changed) notifyChanged()
    }

    // --- Delegating accessors (for callers not yet using Trip directly) ---

    data class HistorySnapshot(
            val history: List<ObaTripStatus>,
            val fetchTimes: List<Long>,
            val localFetchTimes: List<Long>
    ) {
        companion object {
            val EMPTY = HistorySnapshot(emptyList(), emptyList(), emptyList())
        }
    }

    fun getHistorySnapshot(activeTripId: String?): HistorySnapshot {
        val trip = getTrip(activeTripId) ?: return HistorySnapshot.EMPTY
        return HistorySnapshot(
                trip.history.toList(),
                trip.fetchTimes.toList(),
                trip.localFetchTimes.toList()
        )
    }

    fun getHistory(activeTripId: String?): List<ObaTripStatus> =
            getTrip(activeTripId)?.history?.toList().orEmpty()

    fun getHistorySize(activeTripId: String?): Int = getTrip(activeTripId)?.history?.size ?: 0

    fun getFetchTimes(activeTripId: String?): List<Long> =
            getTrip(activeTripId)?.fetchTimes?.toList().orEmpty()

    fun getLocalFetchTimes(activeTripId: String?): List<Long> =
            getTrip(activeTripId)?.localFetchTimes?.toList().orEmpty()

    fun getLastState(activeTripId: String?): ObaTripStatus? =
            getTrip(activeTripId)?.history?.lastOrNull()

    /**
     * Returns the filtered extrapolation anchor — the newest-by-timestamp status with GPS winning
     * ties. Use this (not [getLastState]) when surfacing "the data the prediction is based on" to
     * the user; [getLastState] is for raw debug views.
     */
    fun getAnchor(activeTripId: String?): ObaTripStatus? = getTrip(activeTripId)?.anchor

    /** IDs of trips currently retaining payload (the eviction working set). */
    fun getTrackedTripIds(): Set<String> = warm.snapshot().keys

    // --- Trip details response cache ---

    fun getTripDetails(tripId: String): ObaTripDetailsResponse? =
            getTrip(tripId)?.tripDetailsResponse

    // --- Schedule ---

    fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
        if (tripId != null && schedule != null) {
            warmTrip(tripId).schedule = schedule
            notifyChanged()
        }
    }

    fun getSchedule(tripId: String): ObaTripSchedule? = getTrip(tripId)?.schedule

    fun isScheduleCached(tripId: String?): Boolean =
            tripId != null && getTrip(tripId)?.schedule != null

    // --- Service date ---

    fun putServiceDate(tripId: String?, serviceDate: Long) {
        if (tripId != null && serviceDate > 0) {
            warmTrip(tripId).serviceDate = serviceDate
            notifyChanged()
        }
    }

    fun getServiceDate(tripId: String?): Long? =
            getTrip(tripId)?.serviceDate?.let { if (it > 0) it else null }

    // --- Shape ---

    fun putShape(tripId: String?, points: List<Location>?) {
        if (tripId != null && points != null && points.isNotEmpty()) {
            putPolyline(tripId, Polyline(points))
        }
    }

    fun putPolyline(tripId: String, polyline: Polyline) {
        warmTrip(tripId).polyline = polyline
        notifyChanged()
    }

    fun getShape(tripId: String?): List<Location>? = getTrip(tripId)?.polyline?.points

    fun getPolyline(tripId: String): Polyline? = getTrip(tripId)?.polyline

    // --- Route type ---

    fun putRouteType(tripId: String, type: Int) {
        warmTrip(tripId).routeType = type
        notifyChanged()
    }

    fun getRouteType(tripId: String): Int? = getTrip(tripId)?.routeType

    // --- Vehicle active trip tracking ---

    /**
     * Returns the trip that the vehicle serving [watchedTripId] most recently reported as its
     * active trip — equal to [watchedTripId] while the vehicle is still on that run, and the
     * successor run's trip ID once the vehicle rolls onto its next trip. Null until a trip
     * details response has been recorded for [watchedTripId].
     */
    fun getVehicleActiveTripId(watchedTripId: String): String? =
            getTrip(watchedTripId)?.vehicleActiveTripId

    // --- Cleanup ---

    /**
     * Full reset, including identity — any Trip references held by callers are orphaned. For tests
     * only; production code relies on Trip identity being permanent.
     */
    @VisibleForTesting
    fun clearAll() {
        registry.clear()
        warm.evictAll()
        notifyChanged()
    }
}

/**
 * Iterates the active trips in a trips-for-route response, skipping entries without a status, an
 * active trip ID, or a matching trip reference. Shared by [TripStore] recording and [TripFetcher]
 * backfills so both walk the response identically.
 */
internal inline fun ObaTripsForRouteResponse.forEachActiveTrip(
        block: (tripId: String, status: ObaTripStatus, activeTrip: ObaTrip) -> Unit
) {
    for (tripDetails in trips) {
        val status = tripDetails.status ?: continue
        val tripId = status.activeTripId ?: continue
        val activeTrip = getTrip(tripId) ?: continue
        block(tripId, status, activeTrip)
    }
}
