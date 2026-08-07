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
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.colorArgb
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.contract.VehicleSearchWebService
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.runCatchingCancellable

/**
 * A vehicle the region's sidecar matched on its coach number, and the trip it is running right now.
 *
 * @param vehicleId the agency-prefixed id (`1_4531`) — the OBA key, not what's painted on the bus
 * @param coachNumber the un-prefixed number as a rider reads it off the vehicle
 * @param agencyName the operating agency's display name
 * @param trip what the vehicle is running, or null when it isn't running anything (deadheading, in
 *   the yard, or its real-time feed has gone quiet) — such a match is still reported, because a
 *   rider who typed a real coach number is better served by "not in service" than by "no results"
 */
data class VehicleMatch(
    val vehicleId: String,
    val coachNumber: String,
    val agencyName: String,
    val trip: VehicleTrip?
)

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
     * Vehicles whose id matches [query], each resolved to its current trip. Empty when nothing
     * matches (including any query the sidecar considers too short to index).
     */
    suspend fun vehiclesMatching(query: String): Result<List<VehicleMatch>>
}

/**
 * Two-hop implementation, because neither service can answer the question alone: the region's
 * sidecar ([VehicleSearchWebService]) turns the coach number into agency-prefixed vehicle ids, then
 * the OBA `trip-for-vehicle` endpoint turns each of those into the route + trip the map needs. The
 * second hop runs concurrently across the matches and never fails the search — a vehicle whose trip
 * can't be resolved is reported as not-in-service rather than dropped.
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

    override suspend fun vehiclesMatching(query: String): Result<List<VehicleMatch>> = coroutineScope {
        val region = regionRepository.region.value
        val base = region?.sidecarBaseUrl
            ?: return@coroutineScope Result.failure(IOException("No sidecar base URL for vehicle search"))
        val url = base +
            context.getString(R.string.vehicles_api_endpoint)
                .replace("regionID", region.sidecarId.toString())

        runCatchingCancellable {
            val matched = vehicleSearch.searchVehicles(url, query)
                .filter { !it.vehicleId.isNullOrBlank() }
            // A short query can match a lot of the fleet, and each match costs a trip-for-vehicle
            // round trip. Cap the fan-out, and log the drop rather than let a truncated list read as
            // the whole answer.
            val capped = if (matched.size > MAX_MATCHES) {
                Log.i(TAG, "vehiclesMatching($query): ${matched.size} matches, keeping the first $MAX_MATCHES")
                matched.take(MAX_MATCHES)
            } else {
                matched
            }
            capped
                .map { hit ->
                    // Non-null by the filter above; the wire type keeps it optional.
                    val vehicleId = hit.vehicleId.orEmpty()
                    async {
                        VehicleMatch(
                            vehicleId = vehicleId,
                            coachNumber = coachNumberOf(vehicleId, hit.agencyId),
                            agencyName = hit.agencyName,
                            trip = tripForVehicle(vehicleId)
                        )
                    }
                }
                .map { it.await() }
        }.onFailure { Log.e(TAG, "vehiclesMatching($query) failed", it) }
    }

    /**
     * The vehicle's current trip, or null when the lookup didn't produce one — a vehicle between
     * assignments answers with a non-OK code, which [requireData] turns into a failure here.
     */
    private suspend fun tripForVehicle(vehicleId: String): VehicleTrip? = api.call {
        it.tripForVehicle(vehicleId).requireData().toVehicleTrip()
    }.getOrNull()

    private companion object {

        const val TAG = "VehicleSearchDataSource"

        /** How many sidecar matches are kept (and given a trip-for-vehicle lookup); the rest are dropped. */
        const val MAX_MATCHES = 10
    }
}

/**
 * Adapts a trip-for-vehicle payload to [VehicleTrip], or null when the response names a trip that
 * isn't in its own references (nothing to drill into). Pure, so it's exercised directly in JVM tests.
 */
internal fun EntryWithReferences<TripDetailsEntry>.toVehicleTrip(): VehicleTrip? {
    // The active trip is the one the vehicle is serving now; on a block rollover the entry's own
    // tripId can still name the trip it just finished.
    val tripId = entry.status?.activeTripId?.ifBlank { null } ?: entry.tripId
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
