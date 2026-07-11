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
package org.onebusaway.android.ui.tripplan

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.network.okHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.toTripItineraries
import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.type.InputField
import org.onebusaway.android.api.graphql.type.RoutingErrorCode
import org.onebusaway.android.app.di.Otp2HttpClient
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.util.Otp2PlanRequestBuilder
import org.onebusaway.android.directions.util.TripRequestBuilder

/**
 * Executes the OTP 2.x GraphQL trip-plan path (#1780): builds the `PlanQuery` variables via
 * [Otp2PlanRequestBuilder], runs the query through an [ApolloClient] built for the resolved OTP
 * base URL (the URL is region-dependent and resolved per call — same reason
 * [org.onebusaway.android.api.contract.OtpWebService] uses a throwaway Retrofit base + `@Url`;
 * Apollo's `serverUrl` is fixed at client-construction time, so building a lightweight client per
 * distinct URL, reusing the shared [OkHttpClient], is the equivalent seam here), and adapts the
 * response onto the shared [TripItinerary] domain model. Mirrors [DefaultTripPlanRepository]'s OTP1
 * `requestPlan`/`errorMessage` shape: throws [IOException] with a user-facing message on any
 * failure, so [DefaultTripPlanRepository]'s existing `runCatching` wrapping in both `plan()` and
 * `planBlocking()` handles this path the same way as OTP1.
 */
class Otp2Planner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Otp2HttpClient private val okHttpClient: OkHttpClient,
) {

    /**
     * The last [ApolloClient] built, keyed by the base URL it targets. `TripPlanMonitorService`
     * calls [plan] repeatedly (every 60s) against the *same* base URL for the life of one monitored
     * trip, and only one URL is realistically in play per [Otp2Planner] instance — a single cached
     * slot avoids rebuilding the client (its own coroutine scope + interceptor chain) on every tick,
     * without needing a general-purpose cache.
     */
    @Volatile
    private var cachedClient: Pair<String, ApolloClient>? = null

    private fun apolloClientFor(baseUrl: String): ApolloClient = synchronized(this) {
        cachedClient?.takeIf { it.first == baseUrl }?.second
            ?: ApolloClient.Builder().serverUrl(baseUrl).okHttpClient(okHttpClient).build()
                .also { cachedClient = baseUrl to it }
    }

    /**
     * Blocking: safe to call only from a background thread — [DefaultTripPlanRepository.plan] calls
     * it inside `Dispatchers.IO`, and [DefaultTripPlanRepository.planBlocking] is itself
     * `@WorkerThread` by contract.
     */
    fun plan(builder: TripRequestBuilder, baseUrl: String): List<TripItinerary> {
        val query = Otp2PlanRequestBuilder.build(builder, context)
        val apolloClient = apolloClientFor(baseUrl)
        val data = try {
            runBlocking { apolloClient.query(query).execute() }.dataOrThrow()
        } catch (e: ApolloException) {
            throw IOException(context.getString(R.string.tripplanner_error_request_timeout), e)
        }

        data.planConnection?.routingErrors?.firstOrNull()?.let {
            throw IOException(errorMessage(it))
        }

        val itineraries = data.toTripItineraries()
        if (itineraries.isEmpty()) {
            throw IOException(context.getString(R.string.tripplanner_error_path_not_found))
        }
        return itineraries
    }

    /**
     * Maps a `routingErrors` entry to a user-facing message: OTP1's existing string resources where
     * `RoutingErrorCode` names a genuinely equivalent failure, and an OTP2-specific string where it
     * doesn't — [RoutingErrorCode.OUTSIDE_SERVICE_PERIOD] and
     * [RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT] have no OTP1-era concept, so folding them into
     * the generic fallback would silently discard information OTP2 is actually telling the user.
     */
    private fun errorMessage(error: PlanQuery.RoutingError): String = when (error.code) {
        RoutingErrorCode.OUTSIDE_BOUNDS ->
            context.getString(R.string.tripplanner_error_outside_bounds)

        RoutingErrorCode.NO_TRANSIT_CONNECTION, RoutingErrorCode.NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW ->
            context.getString(R.string.tripplanner_error_no_transit_times)

        RoutingErrorCode.NO_STOPS_IN_RANGE ->
            context.getString(R.string.tripplanner_error_path_not_found)

        RoutingErrorCode.LOCATION_NOT_FOUND -> when (error.inputField) {
            InputField.FROM -> context.getString(R.string.tripplanner_error_geocode_from_not_found)
            InputField.TO -> context.getString(R.string.tripplanner_error_geocode_to_not_found)
            else -> context.getString(R.string.tripplanner_error_not_defined)
        }

        RoutingErrorCode.OUTSIDE_SERVICE_PERIOD ->
            context.getString(R.string.tripplanner_error_outside_service_period)

        RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT ->
            context.getString(R.string.tripplanner_error_walking_better_than_transit)

        RoutingErrorCode.UNKNOWN__ -> context.getString(R.string.tripplanner_error_not_defined)
    }
}
