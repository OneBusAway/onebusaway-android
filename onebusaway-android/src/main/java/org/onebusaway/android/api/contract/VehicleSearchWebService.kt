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
package org.onebusaway.android.api.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The coach-number (vehicle-ID) search client — separate from [ObaWebService] because the OBA `where`
 * API can only look a vehicle up by its *full*, agency-prefixed id (`trip-for-vehicle/1_4531`), and a
 * rider types the number painted on the bus (`4531`) with no idea which agency prefix it carries. The
 * region's sidecar host indexes the region's live vehicles and does that partial match, returning the
 * agency-qualified id the OBA API needs.
 *
 * Like weather/surveys the caller passes the resolved sidecar URL via [Url], so this service runs
 * WITHOUT [ApiParamsInterceptor] and the Retrofit base URL is a throwaway. Mirrors [WeatherWebService].
 */
interface VehicleSearchWebService {

    @GET
    suspend fun searchVehicles(
        @Url url: String,
        @Query("query") query: String
    ): List<AgencyVehicleResponse>
}

/**
 * One partial-match hit from the sidecar's vehicle index: the operating agency plus the fully
 * qualified vehicle id. The sidecar returns a bare JSON array (no [ObaEnvelope]), and [vehicleId] is
 * nullable on the wire, so a hit that carries no id is dropped at the adapter.
 *
 * Matches the shape OBAKit's `AgencyVehicle` decodes (`id`/`name`/`vehicle_id`) — the same endpoint
 * backs the iOS app's vehicle search.
 */
@Serializable
data class AgencyVehicleResponse(
    @SerialName("id") val agencyId: String = "",
    @SerialName("name") val agencyName: String = "",
    @SerialName("vehicle_id") val vehicleId: String? = null
)
