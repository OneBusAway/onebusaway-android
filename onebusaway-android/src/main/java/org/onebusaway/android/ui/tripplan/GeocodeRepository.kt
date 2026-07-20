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
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.usf.cutr.pelias.AutocompleteRequest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.directions.util.CustomAddress
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.span
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.RegionUtils
import org.onebusaway.android.util.locationOf
import org.onebusaway.android.util.runCatchingCancellable

/** Address-autocomplete suggestions for the trip-plan endpoints. */
interface GeocodeRepository {
    suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>>
}

/**
 * Address suggestions for the trip-plan endpoints. Prefers Pelias (real autocomplete, with
 * `transport:public` results flagged as transit), but falls back to the on-device
 * [Geocoder] when no Pelias API key is configured — so key-free dev builds still geocode. Both paths
 * bias/limit results to the current region's bounding box; the Geocoder fallback has no
 * typeahead/transit categories, so it's degraded only. Runs the blocking work on the IO thread and
 * projects onto the JVM-pure [TripEndpoint.Geocoded].
 *
 * This is the sole caller of what used to be `LocationUtils.processPeliasGeocoding` /
 * `processGeocoding`; it always passes a bare user-typed query (no reference lat/lng, not
 * "geocoding for a marker"), so the collapsed pipeline here drops the dead varargs/lat-lng,
 * closest-marker, and "current location" branches those methods carried.
 */
class DefaultGeocodeRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository
) : GeocodeRepository {

    override suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            if (query.isBlank()) return@runCatchingCancellable emptyList()
            val region = regionRepository.region.value
            val addresses = if (BuildFlavorUtils.isPeliasApiKeyDefined()) {
                peliasSuggestions(query, region)
            } else {
                platformSuggestions(query, region)
            }
            addresses.withinRegion(region).map { it.toGeocoded() }
        }
    }

    /** Pelias autocomplete, biased to the region's bounding box. Throws IOException on failure. */
    private fun peliasSuggestions(query: String, region: Region?): List<CustomAddress> {
        val requestBuilder = AutocompleteRequest.Builder(BuildConfig.PELIAS_API_KEY, query)
            .setApiEndpoint(context.getString(R.string.pelias_api_url))
        region?.span()?.let { requestBuilder.setBoundaryRect(it.minLat, it.minLon, it.maxLat, it.maxLon) }
        // Empty categories string still asks for categories, so transit results are flagged.
        requestBuilder.setCategories("")
        return requestBuilder.build().call().features.map { CustomAddress(it) }
    }

    /** On-device [Geocoder] fallback (no Pelias key), limited to the region's bounding box. */
    private fun platformSuggestions(query: String, region: Region?): List<CustomAddress> {
        val geocoder = Geocoder(context)

        // Sync getFromLocationName is deprecated in API 33, but its async GeocodeListener replacement
        // *requires* API 33 while minSdk is 23 — and minSdk reaching 33 isn't foreseeable — so the sync
        // call is retained deliberately (this key-free fallback path is already degraded/best-effort).
        @Suppress("DEPRECATION")
        val results = region?.span()?.let {
            geocoder.getFromLocationName(query, GEOCODER_MAX_RESULTS, it.minLat, it.minLon, it.maxLat, it.maxLon)
        } ?: geocoder.getFromLocationName(query, GEOCODER_MAX_RESULTS)
        return results.orEmpty().map { CustomAddress(it) }
    }

    /** Drops results outside the region's server limits (empty region = no filtering). */
    private fun List<CustomAddress>.withinRegion(region: Region?): List<CustomAddress> = if (region == null) {
        this
    } else {
        filter { RegionUtils.isLocationWithinRegion(locationOf(it.latitude, it.longitude), region) }
    }

    private companion object {
        const val GEOCODER_MAX_RESULTS = 5
    }
}

/** Mints the domain [TripEndpoint.Geocoded] from a geocoder [CustomAddress] result (the wire boundary). */
internal fun CustomAddress.toGeocoded(): TripEndpoint.Geocoded = TripEndpoint.Geocoded(
    displayName = toString(),
    lat = if (isSet) latitude else null,
    lon = if (isSet) longitude else null,
    isTransit = isTransitCategory
)
