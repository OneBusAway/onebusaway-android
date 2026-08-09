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

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.colorArgb
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.contract.VehicleSearchWebService
import org.onebusaway.android.api.contract.activeOrOwnTripId
import org.onebusaway.android.api.isNotFound
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.runCatchingCancellable

/**
 * A vehicle the region's sidecar matched on its coach number, and what it is doing right now.
 *
 * @param vehicleId the agency-prefixed id (`1_4531`) — the OBA key, not what's painted on the bus
 * @param coachNumber the un-prefixed number as a rider reads it off the vehicle
 * @param agencyName the operating agency's display name
 * @param assignment the ride it is running, or why there isn't one to show — a match is reported
 *   either way, because a rider who typed a real coach number is better served by knowing the
 *   vehicle exists than by "no results"
 */
data class VehicleMatch(
    val vehicleId: String,
    val coachNumber: String,
    val agencyName: String,
    val assignment: VehicleAssignment
)

/**
 * What a matched vehicle is doing, as far as `trip-for-vehicle` could tell us. [NotInService] and
 * [Unknown] are deliberately separate: only the first is something the server actually told us, and
 * captioning a failed lookup "not in service" would state as fact something we never asked
 * successfully.
 */
sealed interface VehicleAssignment {

    /** Running [trip] right now — the ride the map can drill into. */
    data class OnTrip(val trip: VehicleTrip) : VehicleAssignment

    /**
     * The server answered that this vehicle has no trip: deadheading, in the yard, or its real-time
     * feed has gone quiet.
     */
    data object NotInService : VehicleAssignment

    /**
     * The lookup didn't produce an answer — a transport, server or decode failure, or a response that
     * names a trip its own references don't describe. The vehicle may well be in service; we don't know.
     */
    data object Unknown : VehicleAssignment
}

/** The live trip behind a [VehicleMatch] — enough to drill the map into the vehicle on its route. */
data class VehicleTrip(
    val tripId: String,
    val routeId: String,
    val routeShortName: String?,
    val routeColor: Int?,
    val headsign: String?
)

/** Resolves a rider-typed coach number to the live vehicle(s) it names. */
interface VehicleSearchDataSource {

    /**
     * Vehicles whose id matches [query], each carrying what it is running. Empty when nothing
     * matches (including any query the sidecar considers too short to index).
     */
    suspend fun vehiclesMatching(query: String): Result<List<VehicleMatch>>
}

/**
 * Two-hop implementation, because neither service can answer the question alone: the region's
 * sidecar ([VehicleSearchWebService]) turns the coach number into agency-prefixed vehicle ids, then
 * the OBA `trip-for-vehicle` endpoint turns each of those into the route + trip the map needs. The
 * second hop runs concurrently across the matches and never fails the search — a vehicle whose trip
 * can't be resolved is reported with an unknown assignment rather than dropped,
 * so one flaky lookup costs neither the match nor the other matches beside it.
 *
 * A region with no sidecar host configured has no vehicle index at all, so the search fails rather
 * than returning empty: "we can't look coach numbers up here" is not the same as "no such coach".
 */
class DefaultVehicleSearchDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val vehicleSearch: VehicleSearchWebService,
    private val api: ObaApiProvider
) : VehicleSearchDataSource {

    override suspend fun vehiclesMatching(query: String): Result<List<VehicleMatch>> = runCatchingCancellable {
        val region = regionRepository.region.value
        val base = region?.sidecarBaseUrl
            ?: throw IOException("No sidecar base URL for vehicle search")
        val url = base +
            context.getString(R.string.vehicles_api_endpoint)
                .replace("regionID", region.sidecarId.toString())

        val matched = vehicleSearch.searchVehicles(url, query)
            .mapNotNull { hit -> hit.vehicleId?.takeIf { it.isNotBlank() }?.let { it to hit } }
        // A short query can match a lot of the fleet, and each match costs a trip-for-vehicle round
        // trip. Cap the fan-out, and log the drop rather than let a truncated list read as the whole
        // answer.
        if (matched.size > MAX_MATCHES) {
            Log.i(TAG, "vehiclesMatching($query): ${matched.size} matches, keeping the first $MAX_MATCHES")
        }
        coroutineScope {
            matched.take(MAX_MATCHES)
                .map { (vehicleId, hit) ->
                    async {
                        VehicleMatch(
                            vehicleId = vehicleId,
                            coachNumber = coachNumberOf(vehicleId, hit.agencyId),
                            agencyName = hit.agencyName,
                            assignment = assignmentOf(vehicleId)
                        )
                    }
                }
                .awaitAll()
        }
    }.onFailure { Log.e(TAG, "vehiclesMatching($query) failed", it) }

    /**
     * What the vehicle is running, per `trip-for-vehicle` — see [vehicleAssignment] for the mapping.
     * A [VehicleAssignment.Unknown] is logged with what caused it, since it's the one outcome the row
     * can't explain to the rider.
     */
    private suspend fun assignmentOf(vehicleId: String): VehicleAssignment {
        val result = api.call { it.tripForVehicle(vehicleId).requireData().toVehicleTrip() }
        return vehicleAssignment(result).also { assignment ->
            if (assignment != VehicleAssignment.Unknown) return@also
            val cause = result.exceptionOrNull()
            if (cause != null) {
                Log.w(TAG, "trip-for-vehicle($vehicleId) failed", cause)
            } else {
                Log.w(TAG, "trip-for-vehicle($vehicleId) named a trip its own references don't describe")
            }
        }
    }

    private companion object {

        const val TAG = "VehicleSearchDataSource"

        /**
         * How many sidecar matches are kept (and given a trip-for-vehicle lookup); the rest are
         * dropped. Sized to OkHttp's default `maxRequestsPerHost` (5) so the fan-out is one wave
         * rather than two, and so it can't fill the shared client's whole budget for the OBA host —
         * the sibling route/stop searches go through the same client.
         */
        const val MAX_MATCHES = 5
    }
}

/**
 * Reads a `trip-for-vehicle` outcome as an assignment. The server answers an unassigned vehicle with
 * a 404 ([isNotFound]) — that, and only that, is [VehicleAssignment.NotInService]; every other failure
 * (transport, server error, decode) is [VehicleAssignment.Unknown], so a lookup we never got an answer
 * to is never captioned as one. A success with no usable trip (see [toVehicleTrip]) is unknown too:
 * the server said the vehicle *is* on a trip, just not one it described.
 *
 * Pure, so it's exercised directly in JVM tests.
 */
internal fun vehicleAssignment(lookup: Result<VehicleTrip?>): VehicleAssignment = lookup.fold(
    onSuccess = { trip -> trip?.let(VehicleAssignment::OnTrip) ?: VehicleAssignment.Unknown },
    onFailure = { if (it.isNotFound) VehicleAssignment.NotInService else VehicleAssignment.Unknown }
)

/**
 * Adapts a trip-for-vehicle payload to [VehicleTrip], or null when the response names a trip that
 * isn't in its own references (nothing to drill into). Pure, so it's exercised directly in JVM tests.
 */
internal fun EntryWithReferences<TripDetailsEntry>.toVehicleTrip(): VehicleTrip? {
    val tripId = entry.activeOrOwnTripId
    val trip = references.trip(tripId) ?: return null
    val route = references.route(trip.routeId)
    return VehicleTrip(
        tripId = tripId,
        routeId = trip.routeId,
        routeShortName = route?.shortName?.takeIf { it.isNotBlank() },
        routeColor = route?.colorArgb(),
        headsign = trip.tripHeadsign?.takeIf { it.isNotBlank() }
    )
}

/**
 * The coach number as painted on the vehicle: the OBA id minus its `{agencyId}_` prefix. The prefix is
 * built from the agency id the same response states rather than by splitting on the first underscore,
 * so a vehicle id that itself contains one survives. An id that doesn't carry the prefix (a feed that
 * doesn't follow the OBA id convention) is passed through unchanged.
 */
internal fun coachNumberOf(vehicleId: String, agencyId: String): String = vehicleId.removePrefix("${agencyId}_")
