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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.ShellRegistry

/*
 * The store of per-trip state: one StateFlow<TripState> per tripId, plus recording and
 * fetch-writeback — and nothing else. Consumers acquire a flow via tripFlow and read
 * `.value` (a single volatile read; cheap enough for per-frame loops), getting a consistent
 * immutable snapshot; transient readers use lookupTripState. Network I/O lives in the pollers
 * (Pollers.kt) and the pure fetchers (Fetchers.kt); hydrating call sites write results back in
 * here, where every record/put is a pure fold producing a new snapshot.
 *
 * Identity vs retention (see ShellRegistry): the StateFlow for a tripId is permanent — holding it
 * is always safe, and stale data is unrepresentable because readers see whatever snapshot is
 * current. What is bounded is the payload: once more than MAX_TRACKED_TRIPS trips hold data, the
 * least-recently-used trip's flow is reset to an empty TripState, which refills if the trip is
 * ever recorded again.
 *
 * Retention contract: LRU promotion happens at acquisition (tripFlow/lookupTripState) and on
 * every record and put call. Reads of a held flow don't promote, but every screen that reads a
 * trip also runs a poller that records into it every few seconds, re-warming it — so a trip being
 * actively displayed is never the eviction victim.
 *
 * Threading: main thread only. All public functions must be called from the main thread.
 * Background work (network fetches) lives in Pollers.kt and Fetchers.kt; results are posted back
 * to the main thread before they touch any state here.
 */

private const val MAX_TRACKED_TRIPS = 100

private val tripRegistry =
        ShellRegistry<String, MutableStateFlow<TripState>>(
                MAX_TRACKED_TRIPS,
                { tripId -> MutableStateFlow(TripState.empty(tripId)) },
                { it.value = TripState.empty(it.value.tripId) }
        )

// --- Trip registry ---

/**
 * The permanent state flow for [tripId], created on first use and promoted in the retention
 * order. Holding the returned flow is always safe; read `.value` for the current snapshot.
 */
fun tripFlow(tripId: String): StateFlow<TripState> = tripRegistry.acquire(tripId)

/**
 * Transient-read lookup: the current snapshot if [tripId] has ever been observed (promoting it in
 * the retention order), or null without creating a flow.
 */
fun lookupTripState(tripId: String?): TripState? = tripRegistry.lookup(tripId)?.value

// --- Recording ---

/**
 * Records a trip status snapshot. Deduplicates history by distance — only adds an entry when the
 * vehicle has moved.
 */
fun recordStatus(status: ObaTripStatus?, serverTimeMs: Long, localTimeMs: Long) {
    if (status == null) return
    val tripId = status.activeTripId ?: return
    val flow = tripRegistry.retain(tripId)
    flow.value = flow.value.recorded(status, serverTimeMs, localTimeMs)
}

fun recordTripDetailsResponse(
        polledTripId: String?,
        response: ObaTripDetailsResponse?,
        localTimeMs: Long
) {
    if (response == null) return
    val status = response.status ?: return
    if (polledTripId != null) {
        val polled = tripRegistry.retain(polledTripId)
        polled.value =
                polled.value.copy(
                        vehicleActiveTripId = status.activeTripId,
                        tripDetailsResponse = response
                )
    }
    val activeTripId = status.activeTripId ?: return
    val flow = tripRegistry.retain(activeTripId)
    flow.value =
            flow.value
                    .recorded(status, response.currentTime, localTimeMs)
                    .withServiceDate(status.serviceDate)
}

fun recordTripsForRouteResponse(response: ObaTripsForRouteResponse, localTimeMs: Long) {
    val serverTime = response.currentTime
    response.forEachActiveTrip { tripId, status, activeTrip ->
        val flow = tripRegistry.retain(tripId)
        val route = activeTrip.routeId?.let { response.getRoute(it) }
        flow.value =
                flow.value
                        .recorded(status, serverTime, localTimeMs)
                        .withServiceDate(status.serviceDate)
                        .withRouteType(route?.type)
    }
}

// --- Fetch-writeback ---

fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
    if (tripId != null && schedule != null) {
        val flow = tripRegistry.retain(tripId)
        flow.value = flow.value.copy(schedule = schedule)
    }
}

fun putServiceDate(tripId: String?, serviceDate: Long) {
    if (tripId != null && serviceDate > 0) {
        val flow = tripRegistry.retain(tripId)
        flow.value = flow.value.withServiceDate(serviceDate)
    }
}

fun putPolyline(tripId: String, polyline: Polyline) {
    val flow = tripRegistry.retain(tripId)
    flow.value = flow.value.copy(polyline = polyline)
}

// --- Introspection and cleanup ---

/** IDs of trips currently retaining payload (the eviction working set). */
fun getTrackedTripIds(): Set<String> = tripRegistry.warmKeys()

/**
 * Full reset, including the flows themselves — any flow held by callers is orphaned. For tests
 * only; production code relies on flow identity being permanent.
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
