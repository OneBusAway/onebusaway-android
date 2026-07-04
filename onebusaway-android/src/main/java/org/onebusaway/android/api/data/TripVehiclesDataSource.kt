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
package org.onebusaway.android.api.data

import org.onebusaway.android.api.adapters.toObaTripSchedule
import org.onebusaway.android.api.adapters.DtoTripDetails
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoTrip
import org.onebusaway.android.api.requireData

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.TripDetailsEntry

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripDetails
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.PolylineDecoder

/** Presents trip-details entries + their references as [RouteTrips] (DTOs as the model interfaces). */
private fun routeTripsOf(
    entries: List<TripDetailsEntry>,
    references: References,
    serverTimeMs: Long,
): RouteTrips = object : RouteTrips {
    override val trips: List<ObaTripDetails> =
        entries.dedupeByActiveTripKeepingBestFix().map { DtoTripDetails(it) }
    override fun trip(tripId: String?): ObaTrip? = tripId?.let { references.trip(it) }?.let { DtoTrip(it) }
    override fun route(routeId: String): ObaRoute? = references.route(routeId)?.let { DtoRoute(it) }
    override val currentTimeMs: Long = serverNowOrDeviceClock(serverTimeMs)
}

/**
 * Collapses trip-details entries that share an active trip, keeping the one whose status is the best
 * live fix.
 *
 * OBA trips-for-route can carry the same active trip more than once — the same trip listed twice (a
 * real-time GPS entry plus a schedule-only entry, at different `distanceAlongTrip`), or, during a block
 * rollover, two *distinct* trips both reporting the successor as active. Both forms collide downstream
 * on `activeTripId`: the render layer keys its vehicle markers by it (the marker's position then flips
 * between the two entries every frame — upstream #1667 / origin #50), and the store keys its
 * observations by it ([RouteTrips.toObservations] via `forEachActiveTrip` — a conflicting
 * double-record). Keying the dedup on `activeTripId` at this one wire→model seam fixes every consumer
 * at once; fall back to `tripId` when a status is absent, and preserve first-seen order.
 */
private fun List<TripDetailsEntry>.dedupeByActiveTripKeepingBestFix(): List<TripDetailsEntry> {
    if (size < 2) return this
    val byKey = LinkedHashMap<String, TripDetailsEntry>(size)
    for (entry in this) {
        val key = entry.status?.activeTripId?.ifBlank { null } ?: entry.tripId
        val kept = byKey[key]
        if (kept == null || entry.fixRank() > kept.fixRank()) byKey[key] = entry
    }
    return if (byKey.size == size) this else byKey.values.toList()
}

/**
 * How good a live fix an entry's status is, higher = better: a raw GPS `lastKnownLocation` is the
 * measured vehicle position and outranks a schedule-only entry; `predicted` breaks the no-fix tie.
 * This is a "which record is the better fix" tiebreak for de-duplication — deliberately not the #1621
 * realtime *classifier* `isLocationRealtime`, which would tie a GPS entry with a position-only one and
 * so couldn't pick the fix. Reads the wire DTO directly, so it never materializes an `android.Location`.
 */
private fun TripDetailsEntry.fixRank(): Int {
    val s = status ?: return 0
    return (if (s.lastKnownLocation != null) 2 else 0) + (if (s.predicted) 1 else 0)
}

/** Adapts a modernized trips-for-route envelope (a list of vehicles) to [RouteTrips]. */
@JvmName("listAsRouteTrips")
internal fun ObaEnvelope<ListWithReferences<TripDetailsEntry>>.asRouteTrips(): RouteTrips {
    val data = requireData()
    return routeTripsOf(data.list, data.references, currentTime)
}

/** Adapts a modernized trip-details envelope (a single trip) to [RouteTrips]. */
@JvmName("entryAsRouteTrips")
internal fun ObaEnvelope<EntryWithReferences<TripDetailsEntry>>.asRouteTrips(): RouteTrips {
    val data = requireData()
    return routeTripsOf(listOf(data.entry), data.references, currentTime)
}

/**
 * The wire-fetch seam for the speed-estimation/vehicle layer: each OBA call the trip data layer
 * needs, adapting the wire response to the model types (so the extrapolation fetcher never touches a
 * DTO). These do the fetch+adapt only; a non-OK code / transport failure maps to [Result.failure]
 * (consistent with the other api data sources). The caller (`DefaultTripObservationFetcher`)
 * owns the null-on-failure + de-duplication policy.
 */
interface TripVehiclesDataSource {

    suspend fun tripsForRoute(routeId: String): Result<RouteTrips>

    suspend fun tripDetails(tripId: String): Result<RouteTrips>

    /** The trip's schedule from a trip-details fetch, or null when the response carries none. */
    suspend fun tripSchedule(tripId: String): Result<ObaTripSchedule?>

    /** The decoded shape polyline, or null when the response carries no usable points. */
    suspend fun shape(shapeId: String): Result<Polyline?>
}

class DefaultTripVehiclesDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : TripVehiclesDataSource {

    override suspend fun tripsForRoute(routeId: String): Result<RouteTrips> = api.call {
        it.tripsForRoute(routeId).asRouteTrips()
    }.onFailure { Log.e(TAG, "tripsForRoute($routeId) failed", it) }

    override suspend fun tripDetails(tripId: String): Result<RouteTrips> = api.call {
        it.tripDetails(tripId).asRouteTrips()
    }.onFailure { Log.e(TAG, "tripDetails($tripId) failed", it) }

    override suspend fun tripSchedule(tripId: String): Result<ObaTripSchedule?> = api.call {
        it.tripDetails(tripId).requireData().entry.schedule?.toObaTripSchedule()
    }.onFailure { Log.e(TAG, "tripSchedule($tripId) failed", it) }

    override suspend fun shape(shapeId: String): Result<Polyline?> = api.call {
        val entry = it.shape(shapeId).requireData().entry
        PolylineDecoder.decodeLine(entry.points, entry.length)
            .takeIf { it.isNotEmpty() }
            ?.let { Polyline(it) }
    }.onFailure { Log.e(TAG, "shape($shapeId) failed", it) }

    private companion object {
        const val TAG = "TripVehiclesDataSource"
    }
}
