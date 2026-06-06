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
@file:JvmName("Adapters")

package org.onebusaway.android.extrapolation.data

import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse

/*
 * The adapters between OBA API response shapes and the trip store's standard vocabulary. Each
 * response type gets a pure toObservations extension that distills what the response says about
 * each trip's vehicle into TripObservations — the store (TripStore.kt) never sees response
 * shapes, and the adapters never touch the cache. The hydrating call sites that compose the two
 * live in Pollers.kt.
 */

/**
 * What one API response said about one trip's vehicle: the standard object the trip store
 * records. [serviceDate] is 0 and [routeType] null when the response doesn't carry them.
 */
data class TripObservation(
        val tripId: String,
        val status: ObaTripStatus,
        /** The server's currentTime when the status was fetched. */
        val serverTimeMs: Long,
        val serviceDate: Long = 0,
        val routeType: Int? = null
)

/** The observation of the vehicle's active trip, or empty when the response carries no status. */
fun ObaTripDetailsResponse.toObservations(): List<TripObservation> {
    val status = status ?: return emptyList()
    val tripId = status.activeTripId ?: return emptyList()
    val route = getTrip(tripId)?.routeId?.let { getRoute(it) }
    return listOf(TripObservation(tripId, status, currentTime, status.serviceDate, route?.type))
}

/** One observation per active trip in the response. */
fun ObaTripsForRouteResponse.toObservations(): List<TripObservation> = buildList {
    forEachActiveTrip { tripId, status, activeTrip ->
        val route = activeTrip.routeId?.let { getRoute(it) }
        add(TripObservation(tripId, status, currentTime, status.serviceDate, route?.type))
    }
}

/**
 * Iterates the active trips in a trips-for-route response, skipping entries without a status, an
 * active trip ID, or a matching trip reference. Shared by [toObservations] and the route poller's
 * backfill so both walk the response identically.
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
