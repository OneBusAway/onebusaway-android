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
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaShapeRequest
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/**
 * Registry of [Trip] objects with background fetch support. All per-trip data lives on Trip; this
 * singleton manages the registry, eviction, and background fetches for schedules and shapes.
 *
 * Thread safety: **main thread only.** All public methods must be called from the main thread.
 * Background work (network fetches) lives in the pollers and the private fetch helpers below;
 * results are posted back to the main thread before they touch any registry state. This invariant
 * lets [Trip] hold plain (non-volatile, non-locked) mutable fields and lets the per-frame
 * extrapolation loop read them directly.
 */
object TripDataManager {

    private const val TAG = "TripDataManager"
    private const val MAX_TRACKED_TRIPS = 100

    private val fetchExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingScheduleFetches: MutableSet<String> = HashSet()
    private val pendingShapeFetches: MutableSet<String> = HashSet()

    // Access-ordered: each get/getOrPut promotes the entry to the tail, so the head is the
    // least-recently-used trip. evictOldTripsIfNeeded() removes from the head, which means a
    // trip that's currently being polled (via recordStatus / getOrCreateTrip / isScheduleCached)
    // stays alive even after MAX_TRACKED_TRIPS distinct trips have been observed.
    private val trips = LinkedHashMap<String, Trip>(16, 0.75f, /*accessOrder=*/ true)

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

    fun getOrCreateTrip(tripId: String): Trip = trips.getOrPut(tripId) { Trip(tripId) }

    fun getTrip(tripId: String?): Trip? = if (tripId != null) trips[tripId] else null

    // --- Recording ---

    /**
     * Records a trip status snapshot. Deduplicates history by distance — only adds an entry when
     * the vehicle has moved.
     */
    fun recordStatus(status: ObaTripStatus?, serverTimeMs: Long, localTimeMs: Long) {
        if (status == null) return
        val tripId = status.activeTripId ?: return
        val changed = getOrCreateTrip(tripId).recordStatus(status, serverTimeMs, localTimeMs)
        evictOldTripsIfNeeded()
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
            val polledTrip = getOrCreateTrip(polledTripId)
            if (polledTrip.lastActiveTripId != status.activeTripId) {
                polledTrip.lastActiveTripId = status.activeTripId
                changed = true
            }
            polledTrip.tripDetailsResponse = response
        }
        val activeTripId = status.activeTripId ?: return
        val trip = getOrCreateTrip(activeTripId)
        if (trip.recordStatus(status, response.currentTime, localTimeMs)) changed = true
        if (status.serviceDate > 0 && trip.serviceDate != status.serviceDate) {
            trip.serviceDate = status.serviceDate
            changed = true
        }
        evictOldTripsIfNeeded()
        if (changed) notifyChanged()
    }

    fun recordTripsForRouteResponse(response: ObaTripsForRouteResponse, localTimeMs: Long) {
        val serverTime = response.currentTime
        var changed = false
        for (tripDetails in response.trips) {
            val status = tripDetails.status ?: continue
            val activeTrip = response.getTrip(status.activeTripId) ?: continue
            val tripId = status.activeTripId ?: continue

            val trip = getOrCreateTrip(tripId)
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
            evictOldTripsIfNeeded()

            ensureSchedule(tripId)
            val shapeId = activeTrip.shapeId
            if (shapeId != null) {
                ensureShape(tripId, shapeId)
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

    fun getTrackedTripIds(): Set<String> = trips.keys.toSet()

    // --- Trip details response cache ---

    fun getTripDetails(tripId: String): ObaTripDetailsResponse? =
            getTrip(tripId)?.tripDetailsResponse

    // --- Schedule ---

    fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
        if (tripId != null && schedule != null) {
            getOrCreateTrip(tripId).schedule = schedule
            notifyChanged()
        }
    }

    fun getSchedule(tripId: String): ObaTripSchedule? = getTrip(tripId)?.schedule

    fun isScheduleCached(tripId: String?): Boolean =
            tripId != null && getTrip(tripId)?.schedule != null

    fun ensureSchedule(tripId: String) {
        ensureFetched(
                tripId = tripId,
                pending = pendingScheduleFetches,
                isCached = { isScheduleCached(tripId) },
                fetch = {
                    val ctx = Application.get().applicationContext
                    ObaTripDetailsRequest.Builder(ctx, tripId)
                            .setIncludeSchedule(true)
                            .setIncludeStatus(false)
                            .setIncludeTrip(false)
                            .build()
                            .call()
                            ?.schedule
                },
                onSuccess = { schedule -> putSchedule(tripId, schedule) }
        )
    }

    // --- Service date ---

    fun putServiceDate(tripId: String?, serviceDate: Long) {
        if (tripId != null && serviceDate > 0) {
            getOrCreateTrip(tripId).serviceDate = serviceDate
            notifyChanged()
        }
    }

    fun getServiceDate(tripId: String?): Long? =
            getTrip(tripId)?.serviceDate?.let { if (it > 0) it else null }

    // --- Shape ---

    fun putShape(tripId: String?, points: List<Location>?) {
        if (tripId != null && points != null && points.isNotEmpty()) {
            getOrCreateTrip(tripId).polyline = Polyline(points)
            notifyChanged()
        }
    }

    fun getShape(tripId: String?): List<Location>? = getTrip(tripId)?.polyline?.points

    fun getPolyline(tripId: String): Polyline? = getTrip(tripId)?.polyline

    @JvmOverloads
    fun ensureShape(
            tripId: String,
            shapeId: String,
            onReady: ((Polyline) -> Unit)? = null,
            onError: (() -> Unit)? = null
    ) {
        // Cached-hit fast path: still hops through mainHandler.post so callers always
        // observe the callback asynchronously, regardless of cache state.
        val cached = getPolyline(tripId)
        if (cached != null) {
            if (onReady != null) mainHandler.post { onReady(cached) }
            return
        }
        ensureFetched(
                tripId = tripId,
                pending = pendingShapeFetches,
                isCached = { getPolyline(tripId) != null },
                fetch = {
                    val ctx = Application.get().applicationContext
                    val response = ObaShapeRequest.newRequest(ctx, shapeId).call()
                    val points = response?.points
                    if (points != null && points.isNotEmpty()) Polyline(points) else null
                },
                onSuccess = { polyline ->
                    getOrCreateTrip(tripId).polyline = polyline
                    notifyChanged()
                    if (onReady != null) onReady(polyline)
                },
                onError = onError
        )
    }

    /**
     * Fetches a value off the main thread and applies the result on the main thread, with
     * pending-fetch deduplication. Failed fetches are retried naturally on the next call (typically
     * the next poll tick), which is already rate-limited by the polling interval — no extra
     * failure-cap or backoff logic is needed.
     *
     * [fetch] runs on the fetch executor; [onSuccess] and [onError] run on the main thread.
     */
    private fun <T : Any> ensureFetched(
            tripId: String,
            pending: MutableSet<String>,
            isCached: () -> Boolean,
            fetch: () -> T?,
            onSuccess: (T) -> Unit,
            onError: (() -> Unit)? = null
    ) {
        if (isCached()) return
        if (!pending.add(tripId)) return
        fetchExecutor.execute {
            val result: T? =
                    try {
                        fetch()
                    } catch (e: Exception) {
                        Log.e(TAG, "Fetch failed for $tripId", e)
                        null
                    }
            mainHandler.post {
                pending.remove(tripId)
                if (result != null) {
                    onSuccess(result)
                } else {
                    Log.d(TAG, "Fetch for $tripId yielded no data")
                    if (onError != null) onError()
                }
            }
        }
    }

    // --- Route type ---

    fun putRouteType(tripId: String, type: Int) {
        getOrCreateTrip(tripId).routeType = type
        notifyChanged()
    }

    fun getRouteType(tripId: String): Int? = getTrip(tripId)?.routeType

    // --- Active trip ID tracking ---

    fun putLastActiveTripId(polledTripId: String?, activeTripId: String?) {
        if (polledTripId != null) {
            getOrCreateTrip(polledTripId).lastActiveTripId = activeTripId
            notifyChanged()
        }
    }

    // TODO: Rename — "get trip id for trip id returning a different trip id" is confusing.
    //  This tracks when a vehicle switches trips (e.g. finishes one run, starts the next).
    //  The parameter is the trip we're watching; the return is what the vehicle is actually
    // running.
    fun getLastActiveTripId(polledTripId: String): String? = getTrip(polledTripId)?.lastActiveTripId

    // --- Eviction and cleanup ---

    private fun evictOldTripsIfNeeded() {
        while (trips.size > MAX_TRACKED_TRIPS) {
            val oldest = trips.keys.iterator().next()
            trips.remove(oldest)
            pendingScheduleFetches.remove(oldest)
            pendingShapeFetches.remove(oldest)
        }
    }

    fun clearAll() {
        trips.clear()
        pendingScheduleFetches.clear()
        pendingShapeFetches.clear()
        notifyChanged()
    }
}
