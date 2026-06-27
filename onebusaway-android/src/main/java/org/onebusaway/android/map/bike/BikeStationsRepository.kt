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
package org.onebusaway.android.map.bike

import javax.inject.Inject
import android.location.Location
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.api.contract.BikeWebService
import org.onebusaway.android.api.contract.toBikeRentalStations
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.RegionUtils
import org.opentripplanner.routing.bike_rental.BikeRentalStation

/**
 * Loads bike rental stations from OpenTripPlanner for a map bounding box. Replaces the
 * `BikeStationLoader` `AsyncTaskLoader` + `BikeLoaderCallbacks`; couriers the raw OTP
 * [BikeRentalStation] list for the bike overlay. The corners are passed in Google-Maps terms
 * (southWest / northEast); the request maps them to OTP's lowerLeft / upperRight.
 */
interface BikeStationsRepository {
    suspend fun getStations(southWest: Location, northEast: Location):
            Result<List<BikeRentalStation>>
}

class DefaultBikeStationsRepository @Inject constructor(
    private val bikeService: BikeWebService,
    private val regionRepository: RegionRepository,
    private val prefs: PreferencesRepository,
) : BikeStationsRepository {

    override suspend fun getStations(southWest: Location, northEast: Location):
            Result<List<BikeRentalStation>> = runCatching {
        val base = otpBaseUrl() ?: error("No OTP base URL for the current region")

        // OTP servers differ in how their base URL is rooted (see TripPlanRepository): the "new"
        // structure roots it at the OTP server and needs the /routers/default prefix; the "old"
        // structure roots it at the router itself. Some regions' otpBaseUrl already includes
        // /routers/default (e.g. Puget Sound), so blindly prepending it doubles the path. Try the
        // recorded structure first, then fall back to the old one and record it (shared with trip
        // planning via the same flag, reset on region change).
        val useOld = Application.get().useOldOtpApiUrlVersion
        try {
            fetch(base, useOld, southWest, northEast)
        } catch (e: Exception) {
            if (useOld) throw e
            fetch(base, useOldUrlStructure = true, southWest, northEast)
                .also { Application.get().useOldOtpApiUrlVersion = true }
        }
    }

    private suspend fun fetch(
        base: String,
        useOldUrlStructure: Boolean,
        southWest: Location,
        northEast: Location,
    ): List<BikeRentalStation> = bikeService.getBikeStations(
        bikeRentalUrl(base, useOldUrlStructure, southWest.latitude, southWest.longitude,
            northEast.latitude, northEast.longitude)
    ).toBikeRentalStations()

    /** The custom OTP url (advanced setting) or the region's `otpBaseUrl`, trailing slash stripped. */
    private fun otpBaseUrl(): String? {
        val custom = prefs.getString(R.string.preference_key_otp_api_url, null)
        val base = custom?.takeIf { it.isNotEmpty() }
            ?: regionRepository.region.value?.otpBaseUrl?.takeIf { it.isNotEmpty() }
            ?: return null
        return RegionUtils.formatOtpBaseUrl(base)
    }
}

/**
 * Builds the OTP bike-rental URL for a viewport bbox. [formattedBase] is the OTP base with its
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
    neLon: Double,
): String {
    val path = if (useOldUrlStructure) "/bike_rental" else "/routers/default/bike_rental"
    return "$formattedBase$path?lowerLeft=$swLat,$swLon&upperRight=$neLat,$neLon"
}

/**
 * Applies the directions-mode station filter, ported verbatim from
 * `BikeLoaderCallbacks.onLoadFinished`:
 *  - `null` filter → show all stations (returns [all])
 *  - empty filter → show nothing *at all* (returns `null` so the caller leaves the overlay
 *    untouched rather than clearing it — preserves the legacy quirk)
 *  - non-empty filter → only the stations whose id is in the filter
 */
internal fun filterStations(
    all: List<BikeRentalStation>,
    selectedIds: List<String>?,
): List<BikeRentalStation>? = when {
    selectedIds == null -> all
    selectedIds.isEmpty() -> null
    else -> all.filter { selectedIds.contains(it.id) }
}

/** What the bike loader should do with the overlay for the current viewport. */
internal enum class BikeAction {
    /** Load stations for the viewport and show the [filterStations] result. */
    SHOW,

    /** Clear the bike overlay (layer toggled off, and not directions-with-stations). */
    CLEAR,

    /** Leave the overlay untouched (directions mode before its station filter is known). */
    LEAVE,
}

/**
 * The pure layer/mode gate from the legacy `BikeshareMapController.updateData` + `showBikes`,
 * extracted so it can be unit-tested on the JVM (the bikeshare-enabled check stays at the call site
 * since it reads `Application`). Bikes always show in directions mode once the itinerary's stations
 * are known; otherwise they follow the layer toggle.
 *
 * @param isDirections whether the map is in directions mode
 * @param selectedIds the itinerary's bike-station filter: null = not a directions itinerary (show
 * all per the toggle), empty = a directions itinerary with no bike stations, non-empty = those ids
 * @param layerVisible the bikeshare layer toggle
 */
internal fun bikeAction(
    isDirections: Boolean,
    selectedIds: List<String>?,
    layerVisible: Boolean,
): BikeAction {
    val show = if (isDirections && !selectedIds.isNullOrEmpty()) true else layerVisible
    return when {
        !show -> BikeAction.CLEAR
        isDirections && selectedIds == null -> BikeAction.LEAVE
        else -> BikeAction.SHOW
    }
}
