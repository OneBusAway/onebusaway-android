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
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.okHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * shape: throws a classified [TripPlanException] on any failure (see [otp2ErrorFor]), so
 * [DefaultTripPlanRepository]'s existing `runCatching` wrapping in both `plan()` and `planBlocking()`
 * handles this path the same way as OTP1.
 */
class Otp2Planner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Otp2HttpClient private val okHttpClient: OkHttpClient,
) {

    /**
     * The last [ApolloClient] built, keyed by the endpoint URL it targets. `TripPlanMonitorService`
     * calls [plan] repeatedly (every 60s) against the *same* endpoint for the life of one monitored
     * trip, and only one URL is realistically in play per [Otp2Planner] instance — a single cached
     * slot avoids rebuilding the client (its own coroutine scope + interceptor chain) on every tick,
     * without needing a general-purpose cache.
     */
    @Volatile
    private var cachedClient: Pair<String, ApolloClient>? = null

    private fun apolloClientFor(endpointUrl: String): ApolloClient = synchronized(this) {
        cachedClient?.takeIf { it.first == endpointUrl }?.second
            ?: ApolloClient.Builder().serverUrl(endpointUrl).okHttpClient(okHttpClient).build()
                .also {
                    // Close the client this one replaces (e.g. a region/custom-URL switch) —
                    // ApolloClient owns a coroutine scope that otherwise leaks until this
                    // Otp2Planner itself is garbage collected.
                    cachedClient?.second?.close()
                    cachedClient = endpointUrl to it
                }
    }

    /**
     * Blocking: safe to call only from a background thread — [DefaultTripPlanRepository.plan] calls
     * it inside `Dispatchers.IO`, and [DefaultTripPlanRepository.planBlocking] is itself
     * `@WorkerThread` by contract.
     */
    fun plan(builder: TripRequestBuilder, baseUrl: String): List<TripItinerary> {
        val query = Otp2PlanRequestBuilder.build(builder, context)
        val apolloClient = apolloClientFor(otp2GraphQlEndpoint(baseUrl))
        val data = try {
            runBlocking { apolloClient.query(query).execute() }.dataOrThrow()
        } catch (e: ApolloNetworkException) {
            // Transport failure (connect/read timeout, DNS, etc.) — a connectivity problem.
            throw TripPlanException(
                TripPlanError(TripPlanError.Category.CONNECTIVITY, R.string.tripplanner_error_request_timeout),
                e,
            )
        } catch (e: ApolloException) {
            // A non-network ApolloException (HTTP status, parse failure, GraphQL-protocol error) isn't
            // a timeout — surface it as an unclassified request failure, not a connectivity one.
            throw TripPlanException(TripPlanError.Unknown, e)
        }

        return resolveOtp2Plan(data)
    }
}

/**
 * Resolves an OTP2 `planConnection` [PlanQuery.Data] into itineraries, or throws a classified
 * [TripPlanException] when there is genuinely nothing to show.
 *
 * **Itineraries win over routing errors.** A `routingErrors` entry can accompany a perfectly good
 * result, so we must return the result rather than surface the error. The motivating case (#1947) is
 * [RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT]: OTP2 always computes a direct WALK itinerary
 * alongside transit — the `planConnection.modes` default is documented as "all transit modes are
 * usable and WALK is used for direct street suggestions" — and when that walk's *generalized* cost
 * (which folds in wait/transfer/boarding penalties, not just distance) beats every transit option,
 * the filter chain deletes the *transit* itineraries and attaches this error while **keeping the
 * walk-only itinerary in `edges`**. Surfacing the error there would throw away a valid walk route and
 * show a "Try walking instead" advisory with no result — even for trips too long to actually walk.
 * Returning whatever itineraries came back shows that walk route as a normal option instead.
 *
 * This is safe for every *fatal* code — `LOCATION_NOT_FOUND`, `OUTSIDE_BOUNDS`,
 * `NO_TRANSIT_CONNECTION`, the same-location `WALKING_BETTER_THAN_TRANSIT` raised by OTP's
 * `SameEdgeAdjuster`, … — because those always come back with empty `edges`, so consulting
 * `routingErrors` only when there are no itineraries still classifies them exactly as before.
 *
 * Top-level and `internal` (no [Context] dependency) so it's JVM-unit-testable from a
 * [PlanQuery.Data] fixture without Apollo, like [otp2ErrorFor].
 */
internal fun resolveOtp2Plan(data: PlanQuery.Data): List<TripItinerary> {
    val itineraries = data.toTripItineraries()
    if (itineraries.isNotEmpty()) {
        return itineraries
    }
    data.planConnection?.routingErrors?.firstOrNull()?.let {
        throw TripPlanException(otp2ErrorFor(it.code, it.inputField))
    }
    throw TripPlanException(TripPlanError.NoRoute)
}

/**
 * Classifies an OTP2 `routingErrors` entry into a [TripPlanError]: reuses OTP1's existing detail
 * strings where [RoutingErrorCode] names a genuinely equivalent failure, and an OTP2-specific string
 * where it doesn't — [RoutingErrorCode.OUTSIDE_SERVICE_PERIOD] and
 * [RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT] have no OTP1-era concept, so folding them into the
 * generic fallback would silently discard information OTP2 is actually telling the user. Top-level and
 * `internal` (not a `Context`-bound method) so it's exhaustively JVM-unit-testable without Apollo.
 */
internal fun otp2ErrorFor(code: RoutingErrorCode, inputField: InputField?): TripPlanError = when (code) {
    RoutingErrorCode.OUTSIDE_BOUNDS ->
        TripPlanError(TripPlanError.Category.NO_ROUTE, R.string.tripplanner_error_outside_bounds)

    RoutingErrorCode.NO_TRANSIT_CONNECTION, RoutingErrorCode.NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW ->
        TripPlanError(TripPlanError.Category.SCHEDULE, R.string.tripplanner_error_no_transit_times)

    RoutingErrorCode.NO_STOPS_IN_RANGE -> TripPlanError.NoRoute

    RoutingErrorCode.LOCATION_NOT_FOUND -> when (inputField) {
        InputField.FROM ->
            TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_from_not_found)
        InputField.TO ->
            TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_to_not_found)
        else -> TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_not_defined)
    }

    RoutingErrorCode.OUTSIDE_SERVICE_PERIOD ->
        TripPlanError(TripPlanError.Category.SCHEDULE, R.string.tripplanner_error_outside_service_period)

    RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT ->
        TripPlanError(TripPlanError.Category.ADVISORY, R.string.tripplanner_error_walking_better_than_transit)

    RoutingErrorCode.UNKNOWN__ -> TripPlanError.Unknown
}

/** OTP2's standard gtfs GraphQL mount, relative to the OTP base (`…/otp`). */
private const val OTP2_GTFS_GRAPHQL_PATH = "/gtfs/v1"

/**
 * Resolves the OTP2 gtfs GraphQL endpoint from a region/custom OTP base URL. `otpBaseGraphqlUrl`
 * (and the custom-URL setting) is the OTP mount base — e.g. `https://…/prod/otp` — mirroring the
 * OTP1 REST path where the base is `…/otp/routers/default` and the client appends `/plan`; here the
 * fixed gtfs GraphQL mount `/gtfs/v1` is appended. Not a heuristic: `/gtfs/v1` is OTP2's standard
 * gtfs API path, identical across OTP2 servers (verified against the live endpoint). Trailing slash
 * on the base is tolerated so `…/otp` and `…/otp/` both resolve to `…/otp/gtfs/v1`.
 */
internal fun otp2GraphQlEndpoint(otpBaseUrl: String): String =
    otpBaseUrl.trimEnd('/') + OTP2_GTFS_GRAPHQL_PATH
