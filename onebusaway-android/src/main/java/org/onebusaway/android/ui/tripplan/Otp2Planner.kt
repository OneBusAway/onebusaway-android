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
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Error as GraphQlError
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
 * shape: throws a classified [TripPlanException] on any failure — from one of the three tiers a plan
 * can fail at, [otp2TransportErrorFor] (transport), [otp2GraphQlErrorFor] (GraphQL `errors`) and
 * [otp2ErrorFor] (`routingErrors`), resolved in that order by [resolveOtp2Response] — so
 * [DefaultTripPlanRepository]'s existing `runCatching` wrapping in both `plan()` and `planBlocking()`
 * handles this path the same way as OTP1.
 */
class Otp2Planner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Otp2HttpClient private val okHttpClient: OkHttpClient
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
        val response = try {
            runBlocking { apolloClient.query(query).execute() }
        } catch (e: ApolloException) {
            // Apollo 4+ reports failures on the response rather than throwing (see
            // [resolveOtp2Response]), so this only catches anything that escapes the interceptor
            // chain — belt-and-braces, classified identically either way.
            throw TripPlanException(otp2TransportErrorFor(e), e)
        }

        // #2023: a GraphQL `errors` array is the server telling us, in its own words, exactly what it
        // objected to — usually the offending argument, the rejected value, and the accepted range.
        // It's untranslated, developer-facing English naming schema internals, so it can't be shown to
        // the rider; this line is what actually surfaces it, since both repository call paths swallow
        // the thrown exception (see [TripPlanException]). Logged even when the response also carried
        // usable data, because GraphQL permits partial results and a plan we can still render is no
        // reason to discard the explanation of what was left out of it. The server quotes the argument
        // it rejected, so this can echo back request values including the trip's endpoints — logcat is
        // app-private from Android 4.1, so it goes no further than the device.
        otp2ServerWording(response.errors)?.let {
            Log.e(TAG, "OTP2 planConnection returned GraphQL errors: $it")
        }

        return resolveOtp2Response(response.data, response.errors, response.exception)
    }

    private companion object {
        const val TAG = "Otp2Planner"
    }
}

/**
 * Resolves an executed OTP2 response — its [data], GraphQL [errors] and transport [exception], the
 * three fields [com.apollographql.apollo.api.ApolloResponse] can report a plan through — into
 * itineraries, or throws the [TripPlanException] classified from whichever tier actually failed.
 *
 * Takes the fields rather than the response so it stays a pure JVM function, constructible in a test
 * without an Apollo `Query` instance, like its sibling classifiers.
 */
internal fun resolveOtp2Response(
    data: PlanQuery.Data?,
    errors: List<GraphQlError>?,
    exception: ApolloException?
): List<TripItinerary> {
    val serverWording = otp2ServerWording(errors)

    // What to raise when nothing resolves: the server's own account of the failure whenever it sent
    // one, and only otherwise the plain no-route result.
    val noResultFailure: () -> TripPlanException = if (serverWording != null) {
        { TripPlanException(otp2GraphQlErrorFor(errors.orEmpty()), message = serverWording) }
    } else {
        { TripPlanException(TripPlanError.NoRoute) }
    }

    if (data != null) {
        // `data` being non-null doesn't mean the query succeeded: a GraphQL error nulls only the field
        // it hit, so an execution-tier failure on `planConnection` arrives as `{"planConnection":
        // null}` *alongside* an `errors` entry. Handing [resolveOtp2Plan] the GraphQL failure keeps
        // that case from surfacing as its defensive "no route", which would tell the rider we looked
        // and found nothing when in fact we never got an answer.
        return resolveOtp2Plan(data, noResultFailure)
    }

    // Nothing at all to show. Apollo reports transport/HTTP/parse failures on `exception` rather than
    // throwing them (that is the point of its non-throwing `execute()`), so the transport tier has to
    // be read off the response, not caught. A GraphQL-errors-only response leaves `exception` null and
    // is classified from the errors themselves.
    throw when {
        exception != null -> TripPlanException(otp2TransportErrorFor(exception), exception)
        serverWording != null -> noResultFailure()
        else -> TripPlanException(TripPlanError.Unknown)
    }
}

/**
 * The server's own wording for a GraphQL `errors` array — every error rendered in full, so the
 * message, the `extensions` (including `classification`) and the response path all survive — or null
 * when the response carried no errors.
 */
internal fun otp2ServerWording(errors: List<GraphQlError>?): String? = errors?.takeIf { it.isNotEmpty() }?.joinToString("; ")

/**
 * Classifies the transport tier — everything below the GraphQL response itself. An
 * [ApolloNetworkException] is a connect/read timeout or DNS failure, i.e. a connectivity problem; any
 * other kind (HTTP status, parse failure, protocol error) isn't a timeout, so it surfaces as an
 * unclassified request failure rather than a connectivity one.
 *
 * Top-level and `internal`, and returning the [TripPlanError] rather than the exception, so it's
 * JVM-unit-testable from a constructed exception like its sibling tiers [otp2GraphQlErrorFor] and
 * [otp2ErrorFor].
 */
internal fun otp2TransportErrorFor(e: ApolloException): TripPlanError = if (e is ApolloNetworkException) {
    TripPlanError.Connectivity
} else {
    TripPlanError.Unknown
}

/**
 * The `extensions.classification` values that mean the server rejected *this query as written*, before
 * ever routing anything: OTP2 runs on graphql-java, whose `graphql.ErrorType` enumerates exactly
 * `InvalidSyntax`, `ValidationError`, `DataFetchingException`, `NullValueInNonNullableField`,
 * `OperationNotSupported` and `ExecutionAborted`, and emits the constant's name verbatim under
 * `extensions.classification` (confirmed against a live OTP2 response — see #2023). The first two are
 * decided by the parser and the validator against the schema, so the same request will be refused
 * identically forever; the rest happen during execution and may well be transient.
 *
 * This is an exact match on an explicitly-stated field with a documented, closed value set — not an
 * inference from the message text — and an unrecognized or absent classification falls back to the
 * generic result, so a server that stops sending the key simply behaves as it does today.
 */
private val DETERMINISTIC_REJECTION_CLASSIFICATIONS = setOf("InvalidSyntax", "ValidationError")

/**
 * Classifies a GraphQL-level `errors` array — the tier *above* the `routingErrors` that [otp2ErrorFor]
 * handles, and the one that had no classification at all before #2023.
 *
 * A deterministic rejection ([DETERMINISTIC_REJECTION_CLASSIFICATIONS]) must not advise a retry:
 * resending the identical query fails identically, forever. So it maps to the shared
 * [TripPlanError.RequestRejected] — the same result OTP1's `BOGUS_PARAMETER` gets, because it means the
 * same thing, and in practice the trigger is a trip-preference value the rider can actually change.
 * Everything else keeps the generic try-again result, which is honest there: a server-side execution
 * failure may not recur.
 *
 * Top-level and `internal` so it's JVM-unit-testable from a constructed error list, like [otp2ErrorFor].
 */
internal fun otp2GraphQlErrorFor(errors: List<GraphQlError>): TripPlanError {
    val rejectedAsWritten = errors.any { it.classification() in DETERMINISTIC_REJECTION_CLASSIFICATIONS }
    return if (rejectedAsWritten) TripPlanError.RequestRejected else TripPlanError.Unknown
}

/**
 * The server-stated classification — `classification` being the GraphQL convention for it under
 * `extensions` — or null when the key is absent or doesn't hold a string.
 */
private fun GraphQlError.classification(): String? = extensions?.get("classification") as? String

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
 * show a no-result message — even for trips too long to actually walk. Returning whatever itineraries
 * came back shows that walk route as a normal option instead; the code only classifies as an error
 * (the same-location "too close" case) when there is genuinely nothing to show.
 *
 * This is safe for every *fatal* code — `LOCATION_NOT_FOUND`, `OUTSIDE_BOUNDS`,
 * `NO_TRANSIT_CONNECTION`, the same-location `WALKING_BETTER_THAN_TRANSIT` raised by OTP's
 * `SameEdgeAdjuster`, … — because those always come back with empty `edges`, so consulting
 * `routingErrors` only when there are no itineraries still classifies them exactly as before.
 *
 * [noResultFailure] is what to throw when the data explains nothing at all — no itineraries and no
 * `routingErrors`. It defaults to the plain no-route result, and [resolveOtp2Response] overrides it
 * with the GraphQL-tier failure when the response carried `errors`, because a response whose
 * `planConnection` was nulled by a field error isn't a search that came back empty. Lazy so the
 * success path never builds an exception it won't throw.
 *
 * Top-level and `internal` (no [Context] dependency) so it's JVM-unit-testable from a
 * [PlanQuery.Data] fixture without Apollo, like [otp2ErrorFor].
 */
internal fun resolveOtp2Plan(
    data: PlanQuery.Data,
    noResultFailure: () -> TripPlanException = { TripPlanException(TripPlanError.NoRoute) }
): List<TripItinerary> {
    val itineraries = data.toTripItineraries()
    if (itineraries.isNotEmpty()) {
        return itineraries
    }
    data.planConnection?.routingErrors?.firstOrNull()?.let {
        throw TripPlanException(otp2ErrorFor(it.code, it.inputField))
    }
    throw noResultFailure()
}

/**
 * Classifies an OTP2 `routingErrors` entry into a [TripPlanError]: reuses OTP1's existing detail
 * strings where [RoutingErrorCode] names a genuinely equivalent failure, and an OTP2-specific string
 * where it doesn't — [RoutingErrorCode.OUTSIDE_SERVICE_PERIOD] has no OTP1-era concept, so folding it
 * into the generic fallback would silently discard information OTP2 is actually telling the user.
 *
 * [RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT] reaches here only when there are no itineraries
 * (see [resolveOtp2Plan] — when a walk route survives we return it rather than classify an error),
 * i.e. only OTP's same-location `SameEdgeAdjuster` case, which is exactly OTP1's `TOO_CLOSE`. So it
 * maps to the same too-close result and never advises walking (#1947). Top-level and `internal` (not a
 * `Context`-bound method) so it's exhaustively JVM-unit-testable without Apollo.
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
        TripPlanError(TripPlanError.Category.NO_ROUTE, R.string.tripplanner_error_too_close)

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
internal fun otp2GraphQlEndpoint(otpBaseUrl: String): String = otpBaseUrl.trimEnd('/') + OTP2_GTFS_GRAPHQL_PATH
