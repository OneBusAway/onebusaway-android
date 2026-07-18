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
import android.os.Bundle
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.toTripItinerary
import org.onebusaway.android.api.contract.OtpErrorId
import org.onebusaway.android.api.contract.OtpPlanParser
import org.onebusaway.android.api.contract.OtpResponseDto
import org.onebusaway.android.api.contract.OtpWebService
import org.onebusaway.android.api.contract.TripPlanRequest
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.util.CustomAddress
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.util.runCatchingCancellable

/** Plans a trip against the region's OpenTripPlanner server. */
interface TripPlanRepository {
    suspend fun plan(params: TripPlanParams): Result<List<TripItinerary>>

    /**
     * Blocking OTP plan for a caller that is already on a background thread and has assembled its
     * own [TripRequestBuilder] — the trip-plan monitor background worker, which replaced the legacy
     * `TripRequest` AsyncTask. Returns the itineraries, or an empty list on any failure (mirroring
     * the old callback's failure path, which only logged and disabled updates). Kept non-suspend and
     * Result-free so it stays cleanly callable from the Java service.
     */
    @WorkerThread
    fun planBlocking(builder: TripRequestBuilder): List<TripItinerary>
}

/**
 * The coroutine replacement for the legacy `TripRequest` AsyncTask. Reuses [TripRequestBuilder] to
 * assemble the OTP request + base URL, then performs the (blocking) Retrofit call ([OtpWebService]) +
 * [OtpPlanParser] parse on the IO thread — including the old-URL-structure fallback. OTP error codes are
 * mapped to user-facing messages (ported from TripPlanActivity.getErrorMessage) and surfaced as
 * [Result.failure].
 */
class DefaultTripPlanRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val otpWebService: OtpWebService,
    private val otp2Planner: Otp2Planner,
) : TripPlanRepository {

    override suspend fun plan(params: TripPlanParams): Result<List<TripItinerary>> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable { planInternal(builderFor(params)) }
        }

    @WorkerThread
    override fun planBlocking(builder: TripRequestBuilder): List<TripItinerary> =
        runCatching { planInternal(builder) }.getOrDefault(emptyList())

    /** Assembles a [TripRequestBuilder] from the UI-supplied [params]. */
    private fun builderFor(params: TripPlanParams): TripRequestBuilder =
        params.toRequestBuilder(context)

    /**
     * Runs the OTP request for an already-assembled [builder] on the calling thread. Throws
     * [IOException] with a user-facing message when no server is selected or the plan is empty/errored.
     * Dispatches on [TripRequestBuilder.usesOtp2] (#1780) — explicit per [builder]'s resolved
     * region/custom-URL setting, never sniffed — to either the OTP2 GraphQL path ([Otp2Planner]) or
     * the OTP1 REST path below, unchanged.
     */
    private fun planInternal(builder: TripRequestBuilder): List<TripItinerary> {
        val baseUrl = builder.formattedOtpBaseUrl
            ?: throw TripPlanException(
                TripPlanError(TripPlanError.Category.REQUEST, R.string.tripplanner_no_server_selected_error)
            )

        if (builder.usesOtp2) {
            return otp2Planner.plan(builder, baseUrl)
        }

        val request = builder.buildRequest()
        val response = requestPlan(
            request,
            baseUrl,
            oldServer = prefs.getBoolean(R.string.preference_key_otp_api_url_version, false),
        )
        val itineraries = response.plan?.itineraries?.map { it.toTripItinerary() }
        if (itineraries.isNullOrEmpty()) {
            throw TripPlanException(otp1ErrorFor(response.error?.id ?: -1))
        }
        return itineraries
    }

    /**
     * Builds the OTP `/plan` URL, fetches, and parses. The endpoint OTP exposes is
     * `{server}/routers/{routerId}/plan`, but a region's otpBaseUrl is published rooted either at the
     * server or already at the router — and which one it is is readable from the URL, so we hit the
     * right endpoint on the first try instead of probing:
     *  - a base already rooted at a router (contains `/routers/`, e.g. Puget Sound's
     *    `…/otp/routers/default`) only needs `/plan` appended. Re-adding the segment doubled the path
     *    (`…/routers/default/routers/default/plan`), which the server answers with a 500 — that was
     *    why trip planning always failed for those regions.
     *  - a server-root base (`…/otp`) needs the `/routers/default` segment inserted first.
     *
     * The single distinction the URL can't make is a modern server-root vs a pre-1.0 OTP server that
     * never had `/routers/…` at all — both look like `…/otp`. For that case only, [oldServer] retries
     * the bare `/plan` path once and records the answer (the `otp_api_url_version` flag, shared with
     * the bike layer and reset on region change) so later plans skip the probe.
     */
    private fun requestPlan(
        request: TripPlanRequest,
        baseUrl: String,
        oldServer: Boolean
    ): OtpResponseDto {
        val routerRooted = baseUrl.contains(OTP_ROUTERS_SEGMENT)
        // A router-rooted base is unambiguously a modern server, so it can never be the pre-1.0 kind
        // the `oldServer` fallback exists for; force it modern even if a stale flag says otherwise.
        val ancientServer = oldServer && !routerRooted

        val query = buildString {
            var first = true
            for (entry in request.parameters.entries) {
                append(if (first) "?" else "&").append(entry)
                first = false
            }
        }.let { params ->
            // Read from the built mode string, not TripRequestBuilder.mModeId: mModeId is unset after
            // initFromBundleSimple (the trip-plan-monitor restore path never repopulates it), and even
            // on the direct path TRANSIT_AND_BIKE only actually requests bike rental when bikeshare is
            // enabled — the mode string is the one place both facts are already resolved.
            val bikeRentalToken = context.getString(R.string.traverse_mode_bicycle_rent)
            if (modeStringRequestsBikeRental(request.parameters["mode"], bikeRentalToken)) {
                // Pre-1.0 servers spell bike rental as "BICYCLE, WALK"; modern ones as "BICYCLE_RENT".
                val bicycle = TripMode.BICYCLE.name
                val rental = if (ancientServer) "$bicycle, ${TripMode.WALK.name}"
                             else bicycle + OTP_RENTAL_QUALIFIER
                params.replace(bicycle, rental)
            } else {
                params
            }
        }

        val url = otpPlanUrl(baseUrl, query, oldServer)

        val response = try {
            otpWebService.plan(url).execute()
        } catch (e: IOException) {
            // Transport failure / timeout (SocketTimeoutException is an IOException) — mirror the legacy
            // catch-all mapping to a connectivity failure.
            throw TripPlanException(otp1ErrorFor(OtpErrorId.REQUEST_TIMEOUT.id), e)
        }

        // A server-root base we assumed was modern but that failed at the HTTP layer is a pre-1.0
        // server: retry the bare `/plan` path once. OTP returns *planner* errors (no path, out of
        // bounds, …) as HTTP 200 with an `error` body, so a non-2xx here is always structural, never a
        // routing failure. Router-rooted bases already hit the exact endpoint, so there's nothing to
        // try. Retrofit has already buffered + closed the error body.
        if (!response.isSuccessful && !routerRooted && !oldServer) {
            return requestPlan(request, baseUrl, oldServer = true)
        }

        val parsed: OtpResponseDto = try {
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw IOException("OTP /plan returned HTTP ${response.code()}")
            }
            body.use { OtpPlanParser.parse(it.byteStream()) }
        } catch (e: IOException) {
            throw TripPlanException(otp1ErrorFor(OtpErrorId.REQUEST_TIMEOUT.id), e)
        }

        // Record a discovered pre-1.0 server so later plans (and the bike layer) skip the probe.
        if (ancientServer) {
            prefs.setBoolean(R.string.preference_key_otp_api_url_version, true)
        }
        return parsed
    }

}

/**
 * Classifies an OTP1 error code ([OtpErrorId.id], or `-1` when the server sent no code) into a
 * [TripPlanError] — the coarse [TripPlanError.Category] plus the specific, already-mapped detail
 * string. Top-level and `internal` (not a `Context`-bound method) so it's exhaustively
 * JVM-unit-testable and shares the ontology with the OTP2 path ([otp2ErrorFor]).
 */
internal fun otp1ErrorFor(errorCode: Int): TripPlanError = when (errorCode) {
    OtpErrorId.SYSTEM_ERROR.id ->
        TripPlanError(TripPlanError.Category.REQUEST, R.string.tripplanner_error_system)
    OtpErrorId.OUTSIDE_BOUNDS.id ->
        TripPlanError(TripPlanError.Category.NO_ROUTE, R.string.tripplanner_error_outside_bounds)
    OtpErrorId.PATH_NOT_FOUND.id -> TripPlanError.NoRoute
    OtpErrorId.NO_TRANSIT_TIMES.id ->
        TripPlanError(TripPlanError.Category.SCHEDULE, R.string.tripplanner_error_no_transit_times)
    OtpErrorId.REQUEST_TIMEOUT.id ->
        TripPlanError(TripPlanError.Category.CONNECTIVITY, R.string.tripplanner_error_request_timeout)
    OtpErrorId.BOGUS_PARAMETER.id ->
        TripPlanError(TripPlanError.Category.REQUEST, R.string.tripplanner_error_bogus_parameter)
    OtpErrorId.GEOCODE_FROM_NOT_FOUND.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_from_not_found)
    OtpErrorId.GEOCODE_TO_NOT_FOUND.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_to_not_found)
    OtpErrorId.GEOCODE_FROM_TO_NOT_FOUND.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_from_to_not_found)
    OtpErrorId.TOO_CLOSE.id ->
        TripPlanError(TripPlanError.Category.NO_ROUTE, R.string.tripplanner_error_too_close)
    OtpErrorId.LOCATION_NOT_ACCESSIBLE.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_location_not_accessible)
    OtpErrorId.GEOCODE_FROM_AMBIGUOUS.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_from_ambiguous)
    OtpErrorId.GEOCODE_TO_AMBIGUOUS.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_to_ambiguous)
    OtpErrorId.GEOCODE_FROM_TO_AMBIGUOUS.id ->
        TripPlanError(TripPlanError.Category.LOCATION, R.string.tripplanner_error_geocode_from_to_ambiguous)
    OtpErrorId.UNDERSPECIFIED_TRIANGLE.id, OtpErrorId.TRIANGLE_NOT_AFFINE.id,
    OtpErrorId.TRIANGLE_OPTIMIZE_TYPE_NOT_SET.id, OtpErrorId.TRIANGLE_VALUES_NOT_SET.id ->
        TripPlanError(TripPlanError.Category.REQUEST, R.string.tripplanner_error_triangle)
    else -> TripPlanError.Unknown
}

/** OTP's default-router path segment: present in a router-rooted base, inserted for a server-root one. */
private const val OTP_ROUTERS_SEGMENT = "/routers/default"
private const val OTP_PLAN_LOCATION = "/plan"
private const val OTP_RENTAL_QUALIFIER = "_RENT"

/**
 * The OTP `/plan` URL for [baseUrl] with an already-built [query] (a leading-`?` query string).
 *
 * OTP's endpoint is `{server}/routers/{routerId}/plan`, but a region's otpBaseUrl is published rooted
 * either at the server or already at the router, and that's readable from the URL — so we build the
 * right endpoint directly instead of prepending `/routers/default` blindly (which doubled the path and
 * 500'd for router-rooted regions like Puget Sound). A server-root base gets the segment inserted; a
 * router-rooted base (or a [oldServer] pre-1.0 server established by the fallback) gets `/plan` alone.
 */
internal fun otpPlanUrl(baseUrl: String, query: String, oldServer: Boolean): String =
    if (baseUrl.contains(OTP_ROUTERS_SEGMENT) || oldServer) {
        "$baseUrl$OTP_PLAN_LOCATION$query"
    } else {
        "$baseUrl$OTP_ROUTERS_SEGMENT$OTP_PLAN_LOCATION$query"
    }

/**
 * True when [modeString] (the OTP `mode` query value built by
 * [org.onebusaway.android.directions.util.TripRequestBuilder.setModeSetById]) includes the bike-rental
 * wire token — pulled out as a standalone, `Context`-free function so this (previously never-true —
 * see #1778) derivation is unit-testable.
 */
internal fun modeStringRequestsBikeRental(modeString: String?, bikeRentalToken: String): Boolean =
    modeString?.contains(bikeRentalToken) == true

/**
 * Assembles a [TripRequestBuilder] from these [TripPlanParams]. Shared by the UI plan path
 * ([DefaultTripPlanRepository]) and the trip-plan-change monitor, so the request that produced the
 * results is re-planned identically (same modes / wheelchair / optimize / max-walk).
 */
internal fun TripPlanParams.toRequestBuilder(context: Context): TripRequestBuilder {
    val params = this
    // Inside the apply{} the receiver is the TripRequestBuilder (whose own `from`/`to` getters would
    // otherwise shadow the params), so read every field through `params`.
    return TripRequestBuilder(context, Bundle()).apply {
        setFrom(params.from.toCustomAddress(context))
        setTo(params.to.toCustomAddress(context))
        val instant = Instant.ofEpochMilli(params.dateTimeMillis)
        if (params.arriving) setArrivalTime(instant) else setDepartureTime(instant)
        setModeSetById(params.modeId)
        setWheelchairAccessible(params.wheelchair)
        setOptimizeTransfers(params.optimizeTransfers)
        params.maxWalkMeters?.let { setMaxWalkDistance(it) }
    }
}

private fun TripEndpoint.toCustomAddress(context: Context): CustomAddress {
    val address = CustomAddress.getEmptyAddress()
    val lat = lat
    val lon = lon
    if (lat != null && lon != null) {
        address.latitude = lat
        address.longitude = lon
    }
    address.setAddressLine(0, addressLine(context))
    return address
}

/** The string the OTP server geocodes (or just labels the request); fixed kinds resolve a resource. */
private fun TripEndpoint.addressLine(context: Context): String = displayText ?: when (this) {
    is TripEndpoint.MapPoint -> context.getString(R.string.trip_plan_map_location)
    // Only the fixed-label kinds (CurrentLocation/MapPoint) have a null displayText.
    else -> context.getString(R.string.tripplanner_current_location)
}
