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

import android.util.LruCache
import androidx.annotation.VisibleForTesting
import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/**
 * Registry of [Trip] objects. This object owns trip identity, payload retention, recording, and
 * fetch-writeback — and nothing else. All trip data is read directly off a [Trip]: acquire one via
 * [getOrCreateTrip]/[getTrip] and read its fields. Network I/O lives in the pollers (Pollers.kt)
 * and the pure fetchers (Fetchers.kt); hydrating call sites write results back in here.
 *
 * Identity vs retention are deliberately separate. A [Trip] instance is **permanent**: the same
 * tripId always resolves to the same object, so holding a Trip reference (in a fragment, a marker
 * state, a frame loop) is always safe — new data recorded for that tripId lands in the instance
 * the holder is reading. What is bounded is the *payload*: once more than [MAX_TRACKED_TRIPS]
 * trips hold data, the least-recently-used trip's data is cleared ([Trip.clearData]), leaving a
 * shell of a few dozen bytes that refills if the trip is ever recorded again.
 *
 * Retention contract: LRU promotion happens at acquisition ([getOrCreateTrip]/[getTrip]) and on
 * every `record*`/`put*`. Direct field reads on a held Trip don't promote, but every screen that
 * reads a trip also runs a poller that records into it every few seconds, re-warming it — so a
 * trip being actively displayed is never the eviction victim. Holding a Trip past eviction is
 * still safe: the instance simply reports empty payload until it is recorded again.
 *
 * Thread safety: **main thread only.** All public methods must be called from the main thread.
 * Background work (network fetches) lives in Pollers.kt and Fetchers.kt; results are posted
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

    // --- Trip registry ---

    /**
     * Acquires the permanent [Trip] for [tripId], creating it if needed, and promotes it in the
     * retention order. Use this when acquiring a reference to hold or write to; for transient
     * reads prefer [getTrip], which doesn't create a shell as a side effect.
     */
    fun getOrCreateTrip(tripId: String): Trip {
        promote(tripId)
        return registry.getOrPut(tripId) { Trip(tripId) }
    }

    /**
     * Transient-read lookup: returns the [Trip] if it has ever been observed (promoting it in the
     * retention order), or null without creating a shell.
     */
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
        warmTrip(tripId).recordStatus(status, serverTimeMs, localTimeMs)
    }

    fun recordTripDetailsResponse(
            polledTripId: String?,
            response: ObaTripDetailsResponse?,
            localTimeMs: Long
    ) {
        if (response == null) return
        val status = response.status ?: return
        if (polledTripId != null) {
            val polledTrip = warmTrip(polledTripId)
            polledTrip.vehicleActiveTripId = status.activeTripId
            polledTrip.tripDetailsResponse = response
        }
        val activeTripId = status.activeTripId ?: return
        val trip = warmTrip(activeTripId)
        trip.recordStatus(status, response.currentTime, localTimeMs)
        if (status.serviceDate > 0) {
            trip.serviceDate = status.serviceDate
        }
    }

    fun recordTripsForRouteResponse(response: ObaTripsForRouteResponse, localTimeMs: Long) {
        val serverTime = response.currentTime
        response.forEachActiveTrip { tripId, status, activeTrip ->
            val trip = warmTrip(tripId)
            trip.recordStatus(status, serverTime, localTimeMs)
            if (status.serviceDate > 0) {
                trip.serviceDate = status.serviceDate
            }
            if (trip.routeType == null) {
                val routeId = activeTrip.routeId
                val route = if (routeId != null) response.getRoute(routeId) else null
                if (route != null) {
                    trip.routeType = route.type
                }
            }
        }
    }

    // --- Fetch-writeback ---

    fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
        if (tripId != null && schedule != null) {
            warmTrip(tripId).schedule = schedule
        }
    }

    fun putServiceDate(tripId: String?, serviceDate: Long) {
        if (tripId != null && serviceDate > 0) {
            warmTrip(tripId).serviceDate = serviceDate
        }
    }

    fun putPolyline(tripId: String, polyline: Polyline) {
        warmTrip(tripId).polyline = polyline
    }

    // --- Introspection and cleanup ---

    /** IDs of trips currently retaining payload (the eviction working set). */
    fun getTrackedTripIds(): Set<String> = warm.snapshot().keys

    /**
     * Full reset, including identity — any Trip references held by callers are orphaned. For tests
     * only; production code relies on Trip identity being permanent.
     */
    @VisibleForTesting
    fun clearAll() {
        registry.clear()
        warm.evictAll()
    }
}

/**
 * Iterates the active trips in a trips-for-route response, skipping entries without a status, an
 * active trip ID, or a matching trip reference. Shared by [TripStore] recording and the route
 * poller's backfill so both walk the response identically.
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
