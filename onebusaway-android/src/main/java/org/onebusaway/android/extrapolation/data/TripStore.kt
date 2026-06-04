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
@file:JvmName("TripStore")

package org.onebusaway.android.extrapolation.data

import androidx.annotation.VisibleForTesting
import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.ShellRegistry

/*
 * The registry of Trip objects: trip identity, payload retention, recording, and fetch-writeback —
 * and nothing else. All trip data is read directly off a Trip: acquire one via
 * getOrCreateTrip/lookupTrip and read its fields. Network I/O lives in the pollers (Pollers.kt)
 * and the pure fetchers (Fetchers.kt); hydrating call sites write results back in here.
 *
 * Identity vs retention are deliberately separate (see ShellRegistry). A Trip instance is
 * permanent: the same tripId always resolves to the same object, so holding a Trip reference (in
 * a fragment, a marker state, a frame loop) is always safe — new data recorded for that tripId
 * lands in the instance the holder is reading. What is bounded is the payload: once more than
 * MAX_TRACKED_TRIPS trips hold data, the least-recently-used trip's data is cleared
 * (Trip.clearData), leaving a shell of a few dozen bytes that refills if the trip is ever
 * recorded again.
 *
 * Retention contract: LRU promotion happens at acquisition (getOrCreateTrip/lookupTrip) and on
 * every record and put call. Direct field reads on a held Trip don't promote, but every screen
 * that
 * reads a trip also runs a poller that records into it every few seconds, re-warming it — so a
 * trip being actively displayed is never the eviction victim. Holding a Trip past eviction is
 * still safe: the instance simply reports empty payload until it is recorded again.
 *
 * Threading: main thread only. All public functions must be called from the main thread.
 * Background work (network fetches) lives in Pollers.kt and Fetchers.kt; results are posted back
 * to the main thread before they touch any state here. This invariant lets Trip hold plain
 * (non-volatile, non-locked) mutable fields and lets the per-frame extrapolation loop read them
 * directly.
 */

private const val MAX_TRACKED_TRIPS = 100

private val tripRegistry = ShellRegistry(MAX_TRACKED_TRIPS, ::Trip, Trip::clearData)

// --- Trip registry ---

/**
 * Acquires the permanent [Trip] for [tripId], creating it if needed, and promotes it in the
 * retention order. Use this when acquiring a reference to hold or write to; for transient reads
 * prefer [lookupTrip], which doesn't create a shell as a side effect.
 */
fun getOrCreateTrip(tripId: String): Trip = tripRegistry.acquire(tripId)

/**
 * Transient-read lookup: returns the [Trip] if it has ever been observed (promoting it in the
 * retention order), or null without creating a shell.
 */
fun lookupTrip(tripId: String?): Trip? = tripRegistry.lookup(tripId)

// --- Recording ---

/**
 * Records a trip status snapshot. Deduplicates history by distance — only adds an entry when the
 * vehicle has moved.
 */
fun recordStatus(status: ObaTripStatus?, serverTimeMs: Long, localTimeMs: Long) {
    if (status == null) return
    val tripId = status.activeTripId ?: return
    tripRegistry.retain(tripId).recordStatus(status, serverTimeMs, localTimeMs)
}

fun recordTripDetailsResponse(
        polledTripId: String?,
        response: ObaTripDetailsResponse?,
        localTimeMs: Long
) {
    if (response == null) return
    val status = response.status ?: return
    if (polledTripId != null) {
        val polledTrip = tripRegistry.retain(polledTripId)
        polledTrip.vehicleActiveTripId = status.activeTripId
        polledTrip.tripDetailsResponse = response
    }
    val activeTripId = status.activeTripId ?: return
    val trip = tripRegistry.retain(activeTripId)
    trip.recordStatus(status, response.currentTime, localTimeMs)
    if (status.serviceDate > 0) {
        trip.serviceDate = status.serviceDate
    }
}

fun recordTripsForRouteResponse(response: ObaTripsForRouteResponse, localTimeMs: Long) {
    val serverTime = response.currentTime
    response.forEachActiveTrip { tripId, status, activeTrip ->
        val trip = tripRegistry.retain(tripId)
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
        tripRegistry.retain(tripId).schedule = schedule
    }
}

fun putServiceDate(tripId: String?, serviceDate: Long) {
    if (tripId != null && serviceDate > 0) {
        tripRegistry.retain(tripId).serviceDate = serviceDate
    }
}

fun putPolyline(tripId: String, polyline: Polyline) {
    tripRegistry.retain(tripId).polyline = polyline
}

// --- Introspection and cleanup ---

/** IDs of trips currently retaining payload (the eviction working set). */
fun getTrackedTripIds(): Set<String> = tripRegistry.warmKeys()

/**
 * Full reset, including identity — any Trip references held by callers are orphaned. For tests
 * only; production code relies on Trip identity being permanent.
 */
@VisibleForTesting
fun clearAllTrips() {
    tripRegistry.clear()
}

/**
 * Iterates the active trips in a trips-for-route response, skipping entries without a status, an
 * active trip ID, or a matching trip reference. Shared by [recordTripsForRouteResponse] and the
 * route poller's backfill so both walk the response identically.
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
