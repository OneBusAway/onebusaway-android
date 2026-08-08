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
package org.onebusaway.android.map.rental

import android.location.Location
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.toRentalPlaces
import org.onebusaway.android.api.contract.BikeWebService
import org.onebusaway.android.api.net.Otp2RentalsClient
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.tripplan.otp2GraphQlEndpoint
import org.onebusaway.android.util.RegionUtils
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Loads rental vehicles and stations from OpenTripPlanner for a map bounding box.
 *
 * Two protocols, one result type (#2168). Where the region publishes an OTP2 GraphQL endpoint that
 * supports bikeshare, this reads `vehicleRentalsByBbox` — which returns free-floating vehicles and
 * docks as distinct things, with form factor, charge, operator and per-type occupancy. Everywhere
 * else it falls back to the OTP1 REST `/bike_rental` call this layer has always made, whose stations
 * carry none of that. Both land on [RentalPlace], so nothing downstream branches on the protocol.
 *
 * The corners are passed in Google-Maps terms (southWest / northEast); each request maps them to its
 * own vocabulary (OTP1's lowerLeft/upperRight, OTP2's minimum/maximum latitude+longitude).
 */
interface RentalPlacesRepository {
    suspend fun getRentals(southWest: Location, northEast: Location): Result<List<RentalPlace>>
}

class DefaultRentalPlacesRepository @Inject constructor(
    private val bikeService: BikeWebService,
    private val otp2RentalsClient: Otp2RentalsClient,
    private val regionRepository: RegionRepository,
    private val prefs: PreferencesRepository
) : RentalPlacesRepository {

    override suspend fun getRentals(southWest: Location, northEast: Location): Result<List<RentalPlace>> = runCatchingCancellable {
        val source = rentalSource(
            customOtpApiUrl = prefs.getString(R.string.preference_key_otp_api_url, null),
            customUrlUsesGraphQl = prefs.getBoolean(R.string.preference_key_otp_api_url_is_graphql, false),
            region = regionRepository.region.value
        ) ?: error("No OTP base URL for the current region")

        when (source) {
            is RentalSource.Otp2 -> otp2RentalsClient.rentalsByBbox(
                otp2GraphQlEndpoint(source.baseUrl),
                southWest.latitude,
                southWest.longitude,
                northEast.latitude,
                northEast.longitude
            )

            is RentalSource.Otp1 -> fetchOtp1(source.baseUrl, southWest, northEast)
        }
    }

    /**
     * The OTP1 REST path, with the URL-structure retry it has always had.
     *
     * OTP servers differ in how their base URL is rooted (see TripPlanRepository): the "new" structure
     * roots it at the OTP server and needs the /routers/default prefix; the "old" structure roots it
     * at the router itself. Some regions' otpBaseUrl already includes /routers/default (e.g. Puget
     * Sound), so blindly prepending it doubles the path. Try the recorded structure first, then fall
     * back to the old one and record it (shared with trip planning via the same flag, reset on region
     * change).
     */
    private suspend fun fetchOtp1(
        base: String,
        southWest: Location,
        northEast: Location
    ): List<RentalPlace> {
        val useOld = prefs.getBoolean(R.string.preference_key_otp_api_url_version, false)
        return try {
            fetchOtp1(base, useOld, southWest, northEast)
        } catch (e: CancellationException) {
            // Don't treat a cancelled fetch as a "wrong URL structure" and retry it on the other path.
            throw e
        } catch (e: Exception) {
            if (useOld) throw e
            fetchOtp1(base, useOldUrlStructure = true, southWest, northEast)
                .also { prefs.setBoolean(R.string.preference_key_otp_api_url_version, true) }
        }
    }

    private suspend fun fetchOtp1(
        base: String,
        useOldUrlStructure: Boolean,
        southWest: Location,
        northEast: Location
    ): List<RentalPlace> = bikeService.getBikeStations(
        bikeRentalUrl(
            base,
            useOldUrlStructure,
            southWest.latitude,
            southWest.longitude,
            northEast.latitude,
            northEast.longitude
        )
    ).toRentalPlaces()
}

/** Which OTP server the rental layer talks to, and how. */
sealed interface RentalSource {
    /** OTP2 GraphQL `vehicleRentalsByBbox` against this OTP mount base. */
    data class Otp2(val baseUrl: String) : RentalSource

    /** OTP1 REST `/bike_rental` against this (already trailing-slash-stripped) base. */
    data class Otp1(val baseUrl: String) : RentalSource
}

/**
 * Which server this device should ask for rentals, or null when it has none to ask.
 *
 * Mirrors [org.onebusaway.android.directions.util.OtpTarget.resolve]'s branch exactly — a custom
 * server wins over the region, and the protocol is always an explicit setting (the custom URL's own
 * `..._is_graphql` preference, or a region publishing an `otpBaseGraphqlUrl`) rather than anything
 * sniffed from the URL. It is a separate function rather than a call into `OtpTarget` because that
 * one resolves through `Context` EntryPoints, while this repository already holds the injected region
 * and preference seams — and because the rental layer asks a question trip planning doesn't: a region
 * can speak OTP2 while publishing **no** rental data over it, in which case there is still an OTP1
 * server with rentals to fall back to.
 *
 * Pure, so `RentalSourceTest` can cover the grid without a device.
 */
fun rentalSource(
    customOtpApiUrl: String?,
    customUrlUsesGraphQl: Boolean,
    region: Region?
): RentalSource? {
    val custom = customOtpApiUrl?.trim()?.takeUnless { it.isEmpty() }
    if (custom != null) {
        // The advanced-setting hatch answers for itself: whatever protocol the user says their server
        // speaks, with no capability flag to consult (there is no Region behind a hand-entered URL).
        return if (customUrlUsesGraphQl) RentalSource.Otp2(custom) else RentalSource.Otp1(RegionUtils.formatOtpBaseUrl(custom))
    }
    if (region == null) return null
    // OTP2 first, but only when the region says that server actually serves rentals — the two OTP
    // servers publish bikeshare support separately (see Region.supportsOtpGraphqlBikeshare), and a
    // region that speaks OTP2 for planning but only serves rentals from its OTP1 host must keep
    // drawing the layer it has always drawn.
    val graphqlBase = region.otpBaseGraphqlUrl?.takeIf { it.isNotBlank() && region.supportsOtpGraphqlBikeshare }
    if (graphqlBase != null) return RentalSource.Otp2(graphqlBase)
    // isNotBlank, not isNotEmpty: these values come from the regions directory, and a whitespace-only
    // otpBaseUrl would otherwise resolve to a source whose every request URL is malformed rather than
    // reporting no source at all. Matches the GraphQL branch above.
    val otp1Base = region.otpBaseUrl?.takeIf { it.isNotBlank() && region.supportsOtpBikeshare } ?: return null
    return RentalSource.Otp1(RegionUtils.formatOtpBaseUrl(otp1Base))
}

/**
 * Builds the OTP1 bike-rental URL for a viewport bbox. [formattedBase] is the OTP base with its
 * trailing slash stripped; [useOldUrlStructure] selects whether the base is already rooted at the
 * router (old: append just `/bike_rental`) or at the OTP server (new: insert `/routers/default`
 * first). The bbox corners are emitted in OTP's lowerLeft/upperRight terms.
 */
internal fun bikeRentalUrl(
    formattedBase: String,
    useOldUrlStructure: Boolean,
    swLat: Double,
    swLon: Double,
    neLat: Double,
    neLon: Double
): String {
    val path = if (useOldUrlStructure) "/bike_rental" else "/routers/default/bike_rental"
    return "$formattedBase$path?lowerLeft=$swLat,$swLon&upperRight=$neLat,$neLon"
}
