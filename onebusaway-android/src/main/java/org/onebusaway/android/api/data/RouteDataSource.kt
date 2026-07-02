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

import org.onebusaway.android.api.adapters.toRouteDetails
import org.onebusaway.android.api.requireData

import org.onebusaway.android.api.net.ObaApiProvider

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.models.RouteDetails

/** Fetches route details from the modernized OBA REST client. */
interface RouteDataSource {

    /**
     * route-details for [routeId]. Returns [Result.failure] (with the IO / HTTP / non-OK-OBA-code
     * error) rather than throwing, so callers can branch without a try/catch.
     */
    suspend fun getRoute(routeId: String): Result<RouteDetails>
}

/**
 * Default implementation backed by [ObaWebService]. Retrofit suspend functions are already
 * main-safe (they dispatch onto OkHttp's executor), so unlike the legacy blocking `.call()`
 * repositories this needs no manual `withContext(Dispatchers.IO)` wrapper.
 */
class DefaultRouteDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : RouteDataSource {

    override suspend fun getRoute(routeId: String): Result<RouteDetails> = api.call {
        it.route(routeId).requireData().toRouteDetails()
    }.onFailure { Log.e(TAG, "getRoute($routeId) failed", it) }

    private companion object {
        const val TAG = "RouteDataSource"
    }
}
