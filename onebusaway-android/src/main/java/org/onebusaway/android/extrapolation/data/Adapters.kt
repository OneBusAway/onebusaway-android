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

import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.time.ServerTime

/*
 * The adapter between a trips-for-route / trip-details fetch and the trip store's standard
 * vocabulary: a pure toObservations extension that distills what the poll says about each trip's
 * vehicle into TripObservations — the store (TripStateCache.kt) never sees wire shapes, and the
 * adapters never touch the cache. The hydrating call sites that compose the two live in
 * TripObservationRepository.kt.
 */

/**
 * What one API response said about one trip's vehicle: the standard object the trip store
 * records. [serviceDate] is 0 and [routeType] null when the response doesn't carry them.
 */
data class TripObservation(
        val tripId: String,
        val status: ObaTripStatus,
        /** The server's currentTime when the status was fetched. */
        val serverTimeMs: ServerTime,
        val serviceDate: Long,
        val routeType: Int?
)

/** One observation per active trip in the route's vehicles (one for a single trip-details poll). */
fun RouteTrips.toObservations(): List<TripObservation> = buildList {
    forEachActiveTrip { tripId, status, _ ->
        // currentTimeMs is the server's currentTime, already epoch millis.
        add(TripObservation(tripId, status, ServerTime(currentTimeMs), status.serviceDate, routeTypeForTrip(tripId)))
    }
}

/** Resolves [tripId]'s route type from the [RouteTrips] refs, or null when they don't include it. */
private fun RouteTrips.routeTypeForTrip(tripId: String): Int? =
        trip(tripId)?.routeId?.let { route(it) }?.type

/**
 * Iterates the active trips in a [RouteTrips], skipping entries without a status, an active trip ID,
 * or a matching trip reference. Shared by [toObservations] and the route poller's backfill so both
 * walk the vehicles identically.
 */
internal inline fun RouteTrips.forEachActiveTrip(
        block: (tripId: String, status: ObaTripStatus, activeTrip: ObaTrip) -> Unit
) {
    for (tripDetails in trips) {
        val status = tripDetails.status ?: continue
        val tripId = status.activeTripId ?: continue
        val activeTrip = trip(tripId) ?: continue
        block(tripId, status, activeTrip)
    }
}
