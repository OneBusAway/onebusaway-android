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
import java.net.HttpURLConnection
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.OtpPlanParser
import org.onebusaway.android.api.contract.OtpWebService
import org.onebusaway.android.directions.util.CustomAddress
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.opentripplanner.api.model.Itinerary
import org.opentripplanner.api.ws.Message
import org.opentripplanner.api.ws.Request
import org.opentripplanner.api.ws.Response
import org.opentripplanner.routing.core.TraverseMode
import org.onebusaway.android.preferences.PreferencesRepository

/** Plans a trip against the region's OpenTripPlanner server. */
interface TripPlanRepository {
    suspend fun plan(params: TripPlanParams): Result<List<Itinerary>>

    /**
     * Blocking OTP plan for a caller that is already on a background thread and has assembled its
     * own [TripRequestBuilder] — the RealtimeService IntentService worker, which replaced the legacy
     * `TripRequest` AsyncTask. Returns the itineraries, or an empty list on any failure (mirroring
     * the old callback's failure path, which only logged and disabled updates). Kept non-suspend and
     * Result-free so it stays cleanly callable from the Java service.
     */
    @WorkerThread
    fun planBlocking(builder: TripRequestBuilder): List<Itinerary>
}

/**
 * The coroutine replacement for the legacy `TripRequest` AsyncTask. Reuses [TripRequestBuilder] to
 * assemble the OTP request + base URL, then performs the (blocking) Retrofit call ([OtpWebService]) +
 * [OtpPlanParser] parse on the IO thread — including the old-URL-structure fallback. OTP error codes are
 * mapped to user-facing messages (ported from TripPlanActivity.getErrorMessage) and surfaced as
 * [Result.failure].
 */
class DefaultTripPlanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val otpWebService: OtpWebService,
) : TripPlanRepository {

    override suspend fun plan(params: TripPlanParams): Result<List<Itinerary>> =
        withContext(Dispatchers.IO) {
            runCatching { planInternal(builderFor(params)) }
        }

    @WorkerThread
    override fun planBlocking(builder: TripRequestBuilder): List<Itinerary> =
        runCatching { planInternal(builder) }.getOrDefault(emptyList())

    /** Assembles a [TripRequestBuilder] from the UI-supplied [params]. */
    private fun builderFor(params: TripPlanParams): TripRequestBuilder =
        TripRequestBuilder(context, Bundle()).apply {
            setFrom(params.from.toCustomAddress())
            setTo(params.to.toCustomAddress())
            val calendar = Calendar.getInstance().apply { timeInMillis = params.dateTimeMillis }
            if (params.arriving) setArrivalTime(calendar) else setDepartureTime(calendar)
            setModeSetById(params.modeId)
            setWheelchairAccessible(params.wheelchair)
            setOptimizeTransfers(params.optimizeTransfers)
            params.maxWalkMeters?.let { setMaxWalkDistance(it) }
        }

    /**
     * Runs the OTP request for an already-assembled [builder] on the calling thread. Throws
     * [IOException] with a user-facing message when no server is selected or the plan is empty/errored.
     */
    private fun planInternal(builder: TripRequestBuilder): List<Itinerary> {
        val request = builder.buildRequest()
        val baseUrl = builder.formattedOtpBaseUrl
            ?: throw IOException(context.getString(R.string.tripplanner_no_server_selected_error))

        val response = requestPlan(
            request,
            baseUrl,
            prefs.getBoolean(R.string.preference_key_otp_api_url_version, false),
        )
        val itineraries = response.plan?.itinerary
        if (itineraries.isNullOrEmpty()) {
            throw IOException(errorMessage(response.error?.id ?: -1))
        }
        return itineraries
    }

    /**
     * Ports TripRequest.requestPlan: build the URL, fetch + parse, fall back to the old structure.
     * Returns the response paired with the final request URL (for travel-behavior logging).
     */
    private fun requestPlan(
        request: Request,
        baseUrl: String,
        useOldUrlStructure: Boolean
    ): Response {
        val query = buildString {
            var first = true
            for (entry in request.parameters.entries) {
                append(if (first) "?" else "&").append(entry)
                first = false
            }
        }.let { params ->
            if (request.bikeRental) {
                if (useOldUrlStructure) {
                    params.replace(
                        TraverseMode.BICYCLE.toString(),
                        TraverseMode.BICYCLE.toString() + ", " + TraverseMode.WALK.toString()
                    )
                } else {
                    params.replace(
                        TraverseMode.BICYCLE.toString(),
                        TraverseMode.BICYCLE.toString() + OTP_RENTAL_QUALIFIER
                    )
                }
            } else {
                params
            }
        }

        val url = if (useOldUrlStructure) {
            baseUrl + PLAN_LOCATION + query
        } else {
            baseUrl + PREFIX_NEW + PLAN_LOCATION + query
        }

        val response = try {
            otpWebService.plan(url).execute()
        } catch (e: IOException) {
            // Transport failure / timeout (SocketTimeoutException is an IOException) — mirror the legacy
            // catch-all mapping to the request-timeout message.
            throw IOException(errorMessage(Message.REQUEST_TIMEOUT.id), e)
        }

        // A 404 on the new /routers/default structure means an older OTP server; retry the old /plan
        // structure once (Retrofit has already buffered + closed the error body). On success below the
        // preference is flipped so subsequent plans skip straight to the old structure.
        if (response.code() == HttpURLConnection.HTTP_NOT_FOUND && !useOldUrlStructure) {
            return requestPlan(request, baseUrl, useOldUrlStructure = true)
        }

        val parsed: Response = try {
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw IOException("OTP /plan returned HTTP ${response.code()}")
            }
            body.use { OtpPlanParser.parse(it.byteStream()) }
        } catch (e: IOException) {
            throw IOException(errorMessage(Message.REQUEST_TIMEOUT.id), e)
        }

        if (useOldUrlStructure) {
            prefs.setBoolean(R.string.preference_key_otp_api_url_version, true)
        }
        return parsed
    }

    private fun TripEndpoint.toCustomAddress(): CustomAddress {
        val address = CustomAddress.getEmptyAddress()
        val lat = lat
        val lon = lon
        if (lat != null && lon != null) {
            address.latitude = lat
            address.longitude = lon
        }
        address.setAddressLine(0, addressLine())
        return address
    }

    /** The string the OTP server geocodes (or just labels the request); fixed kinds resolve a resource. */
    private fun TripEndpoint.addressLine(): String = displayText ?: when (this) {
        is TripEndpoint.MapPoint -> context.getString(R.string.trip_plan_map_location)
        // Only the fixed-label kinds (CurrentLocation/MapPoint) have a null displayText.
        else -> context.getString(R.string.tripplanner_current_location)
    }

    private fun errorMessage(errorCode: Int): String = when (errorCode) {
        Message.SYSTEM_ERROR.id -> context.getString(R.string.tripplanner_error_system)
        Message.OUTSIDE_BOUNDS.id -> context.getString(R.string.tripplanner_error_outside_bounds)
        Message.PATH_NOT_FOUND.id -> context.getString(R.string.tripplanner_error_path_not_found)
        Message.NO_TRANSIT_TIMES.id -> context.getString(R.string.tripplanner_error_no_transit_times)
        Message.REQUEST_TIMEOUT.id -> context.getString(R.string.tripplanner_error_request_timeout)
        Message.BOGUS_PARAMETER.id -> context.getString(R.string.tripplanner_error_bogus_parameter)
        Message.GEOCODE_FROM_NOT_FOUND.id ->
            context.getString(R.string.tripplanner_error_geocode_from_not_found)
        Message.GEOCODE_TO_NOT_FOUND.id ->
            context.getString(R.string.tripplanner_error_geocode_to_not_found)
        Message.GEOCODE_FROM_TO_NOT_FOUND.id ->
            context.getString(R.string.tripplanner_error_geocode_from_to_not_found)
        Message.TOO_CLOSE.id -> context.getString(R.string.tripplanner_error_too_close)
        Message.LOCATION_NOT_ACCESSIBLE.id ->
            context.getString(R.string.tripplanner_error_location_not_accessible)
        Message.GEOCODE_FROM_AMBIGUOUS.id ->
            context.getString(R.string.tripplanner_error_geocode_from_ambiguous)
        Message.GEOCODE_TO_AMBIGUOUS.id ->
            context.getString(R.string.tripplanner_error_geocode_to_ambiguous)
        Message.GEOCODE_FROM_TO_AMBIGUOUS.id ->
            context.getString(R.string.tripplanner_error_geocode_from_to_ambiguous)
        Message.UNDERSPECIFIED_TRIANGLE.id, Message.TRIANGLE_NOT_AFFINE.id,
        Message.TRIANGLE_OPTIMIZE_TYPE_NOT_SET.id, Message.TRIANGLE_VALUES_NOT_SET.id ->
            context.getString(R.string.tripplanner_error_triangle)
        else -> context.getString(R.string.tripplanner_error_not_defined)
    }

    private companion object {
        const val PREFIX_NEW = "/routers/default"
        const val PLAN_LOCATION = "/plan"
        const val OTP_RENTAL_QUALIFIER = "_RENT"
    }
}
