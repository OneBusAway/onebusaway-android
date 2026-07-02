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

import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.listOrEmpty
import org.onebusaway.android.api.requireData

import org.onebusaway.android.api.net.ObaApiProvider

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop

/**
 * Fetches routes/stops near a location from the modernized OBA REST client, adapting the wire
 * references to the [ObaRoute]/[ObaStop] model interfaces so callers never see the DTOs.
 *
 * Two error policies: the plain `…Near` methods treat a non-OK OBA code as [Result.failure]
 * (the combined-search screen surfaces it); the `…NearOrEmpty` methods treat a non-OK code as an
 * empty list (the My-Lists search screens render it as "no results"). Both map a transport/parse
 * failure to [Result.failure].
 */
interface LocationSearchDataSource {

    suspend fun routesNear(lat: Double, lon: Double, query: String?, radius: Int?): Result<List<ObaRoute>>

    suspend fun stopsNear(lat: Double, lon: Double, query: String?, radius: Int?): Result<List<ObaStop>>

    suspend fun routesNearOrEmpty(lat: Double, lon: Double, query: String?, radius: Int?): Result<List<ObaRoute>>

    suspend fun stopsNearOrEmpty(lat: Double, lon: Double, query: String?, radius: Int?): Result<List<ObaStop>>
}

/** Default implementation backed by [ObaWebService]; adapts each reference via [DtoRoute]/[DtoStop]. */
class DefaultLocationSearchDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : LocationSearchDataSource {

    override suspend fun routesNear(
        lat: Double, lon: Double, query: String?, radius: Int?,
    ): Result<List<ObaRoute>> = api.call {
        it.routesForLocation(lat, lon, query, radius).requireData().list.map(::DtoRoute)
    }.onFailure { Log.e(TAG, "routesNear failed", it) }

    override suspend fun stopsNear(
        lat: Double, lon: Double, query: String?, radius: Int?,
    ): Result<List<ObaStop>> = api.call {
        it.stopsForLocation(lat, lon, query, radius).requireData().list.map(::DtoStop)
    }.onFailure { Log.e(TAG, "stopsNear failed", it) }

    override suspend fun routesNearOrEmpty(
        lat: Double, lon: Double, query: String?, radius: Int?,
    ): Result<List<ObaRoute>> = api.call {
        it.routesForLocation(lat, lon, query, radius).listOrEmpty().map(::DtoRoute)
    }.onFailure { Log.e(TAG, "routesNearOrEmpty failed", it) }

    override suspend fun stopsNearOrEmpty(
        lat: Double, lon: Double, query: String?, radius: Int?,
    ): Result<List<ObaStop>> = api.call {
        it.stopsForLocation(lat, lon, query, radius).listOrEmpty().map(::DtoStop)
    }.onFailure { Log.e(TAG, "stopsNearOrEmpty failed", it) }

    private companion object {
        const val TAG = "LocationSearchDataSource"
    }
}
