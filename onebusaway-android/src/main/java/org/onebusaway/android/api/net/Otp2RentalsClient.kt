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
package org.onebusaway.android.api.net

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.onebusaway.android.api.adapters.toRentalPlaces
import org.onebusaway.android.api.graphql.RentalsByBboxQuery
import org.onebusaway.android.app.di.Otp2HttpClient
import org.onebusaway.android.map.rental.RentalPlace

/**
 * Runs the OTP2 `vehicleRentalsByBbox` query for a viewport (#2168) and adapts it onto [RentalPlace].
 *
 * Mirrors [org.onebusaway.android.ui.tripplan.Otp2Planner]'s seam and for the same reason: Apollo
 * fixes `serverUrl` at client-construction time, but the endpoint is region-dependent and resolved
 * per call, so the client is built per distinct URL over the shared [Otp2HttpClient] OkHttp instance
 * and cached in a single slot — the map pans repeatedly against one endpoint for the life of a
 * session, and only a region (or custom-URL) switch changes it.
 *
 * Unlike the planner this is `suspend` rather than blocking: the rental layer is driven from the map
 * host's camera flow, which is already a coroutine.
 */
@Singleton
class Otp2RentalsClient @Inject constructor(
    @param:Otp2HttpClient private val okHttpClient: OkHttpClient
) {

    @Volatile
    private var cachedClient: Pair<String, ApolloClient>? = null

    private fun apolloClientFor(endpointUrl: String): ApolloClient = synchronized(this) {
        cachedClient?.takeIf { it.first == endpointUrl }?.second
            ?: ApolloClient.Builder().serverUrl(endpointUrl).okHttpClient(okHttpClient).build()
                .also {
                    // Close the client this one replaces — ApolloClient owns a coroutine scope that
                    // otherwise leaks until this object is garbage collected.
                    cachedClient?.second?.close()
                    cachedClient = endpointUrl to it
                }
    }

    /**
     * Every rental vehicle and station in the bbox. Throws on any failure — transport, GraphQL errors,
     * or a null result — so the caller's `runCatchingCancellable` classifies this path exactly as it
     * classifies the OTP1 one.
     */
    suspend fun rentalsByBbox(
        endpointUrl: String,
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double
    ): List<RentalPlace> {
        val response = apolloClientFor(endpointUrl).query(
            RentalsByBboxQuery(
                minimumLatitude = swLat,
                minimumLongitude = swLon,
                maximumLatitude = neLat,
                maximumLongitude = neLon
            )
        ).execute()

        // Apollo 4+ reports transport/HTTP/parse failures on the response rather than throwing (see
        // Otp2Planner.resolveOtp2Response), so the failure has to be read off it. A GraphQL `errors`
        // array with no data means the server refused the query; either way there is nothing to draw,
        // and raising it lets the caller leave the previous markers alone instead of clearing the
        // layer as though the viewport were genuinely empty.
        response.exception?.let { throw it }
        val data = response.data
            ?: error("OTP2 vehicleRentalsByBbox returned no data (${response.errors?.size ?: 0} GraphQL errors)")
        return data.toRentalPlaces()
    }
}
