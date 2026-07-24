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
import edu.usf.cutr.pelias.PeliasRequest
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

/** Address geocoding for the trip-plan endpoints: autocomplete forward, coordinates back. */
interface GeocodeRepository {
    suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>>

    /**
     * The short name of the place at [lat]/[lon] ("Pike Place Market", "1717 Cascade Way"), or null
     * when the geocoder knows of nothing there. Used to label an endpoint the user chose as a bare
     * point — their current location or a map pick — which otherwise reaches the directions as OTP's
     * "Origin" placeholder (#2006).
     *
     * A plain `String?` rather than the [CustomAddress] the geocoders produce: the caller wants a
     * label, and keeping `android.location.Address` out of this interface keeps the ViewModel that
     * consumes it JVM-testable (as [suggest]'s [TripEndpoint.Geocoded] projection already does).
     */
    suspend fun reverse(lat: Double, lon: Double): Result<String?>
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

    /**
     * Reverse lookup, same backend split as [suggest]. Unlike a text query there is nothing to bias —
     * a reverse lookup has one answer, at a point the user has already chosen as an endpoint — so the
     * region's bounding box isn't applied here; filtering by it could only ever discard a correct name.
     */
    override suspend fun reverse(lat: Double, lon: Double): Result<String?> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            if (BuildFlavorUtils.isPeliasApiKeyDefined()) {
                peliasReverse(lat, lon)
            } else {
                platformReverse(lat, lon)
            }?.takeIf { it.isNotBlank() }
        }
    }

    /** Pelias `/reverse`: the nearest feature to the point. Throws IOException on failure. */
    private fun peliasReverse(lat: Double, lon: Double): String? {
        val url = peliasReverseUrl(
            endpoint = context.getString(R.string.pelias_reverse_api_url),
            apiKey = BuildConfig.PELIAS_API_KEY,
            lat = lat,
            lon = lon
        )
        val feature = PeliasReverseRequest(url).call().features.firstOrNull() ?: return null
        // Pelias carries the structured short `name` ("Pike Place Market", "1717 Cascade Way") beside the
        // fully-qualified `label` ("1717 Cascade Way, Springfield, MA, USA"); the request pins the layers
        // (see [peliasReverseUrl]) so this is a place's name and never a county's or a country's.
        return feature.properties[PELIAS_NAME_PROPERTY] as? String
    }

    /** On-device [Geocoder] fallback (no Pelias key). */
    private fun platformReverse(lat: Double, lon: Double): String? {
        // Sync getFromLocation is deprecated in API 33 — same reasoning (and same degraded, key-free
        // path) as platformSuggestions above: its replacement requires API 33 while minSdk is 23.
        @Suppress("DEPRECATION")
        val address = Geocoder(context).getFromLocation(lat, lon, REVERSE_MAX_RESULTS)?.firstOrNull()
            ?: return null
        // The street address — house number ([Address.getSubThoroughfare]) and street
        // ([Address.getThoroughfare]) — which is this geocoder's answer to Pelias's short `name` above.
        // Deliberately not CustomAddress.toString(), which tails off into the county and city a timeline
        // node doesn't want ("1717 Cascade Way", not "1717 Cascade Way, Snohomish County, Everett").
        //
        // Built from the two fields that *are* the street address rather than from `featureName`, which
        // the platform fills with the house number for a street address but with the place's name for a
        // named feature — telling those apart would mean comparing the strings. So `featureName` is used
        // only where there is no street for it to duplicate, and a named venue reads as its address
        // ("85 Pike Pl") rather than its name: unambiguous, at the cost of some colour.
        return address.thoroughfare?.let { street ->
            listOfNotNull(address.subThoroughfare, street).joinToString(" ")
        } ?: address.featureName ?: address.getAddressLine(0)
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

/** A reverse lookup wants the one place at the point, not a list. */
private const val REVERSE_MAX_RESULTS = 1

/** The Pelias feature property holding a place's own short name, beside the qualified `label`. */
private const val PELIAS_NAME_PROPERTY = "name"

/**
 * The kinds of Pelias result worth labelling a trip endpoint with, nearest first: the building, else the
 * venue standing on it, else the street.
 *
 * Pelias otherwise answers a reverse lookup with whatever is nearest across *every* layer, which in a
 * thinly-mapped area is a `county` or `region` — so the origin node would read "Snohomish County". Asking
 * for the place-like layers means an answer is either a real place or absent, and absent degrades to
 * OTP's own name rather than to something worse than it.
 */
private const val PELIAS_PLACE_LAYERS = "address,venue,street"

/**
 * The Pelias `/reverse` query for a point. Top-level and `internal` so the query shape is unit-testable
 * without a `Context` (as [otpPlanUrl][org.onebusaway.android.ui.tripplan.otpPlanUrl] is on the OTP side).
 *
 * The coordinates are interpolated through `Double.toString`, which is locale-independent — so the
 * decimal separator is always the `.` the API expects, whatever the device locale.
 */
internal fun peliasReverseUrl(endpoint: String, apiKey: String, lat: Double, lon: Double): String = "$endpoint?point.lat=$lat&point.lon=$lon&api_key=$apiKey" +
    "&size=$REVERSE_MAX_RESULTS&layers=$PELIAS_PLACE_LAYERS"

/**
 * A Pelias `/reverse` request: coordinates in, the place at them out.
 *
 * The vendored client library only models the text-query endpoints — every `PeliasRequest.Builder`
 * hard-codes `?text=` — so the reverse query string is assembled by the caller. The response is the
 * same GeoJSON feature collection, so the request itself rides the library's own [PeliasRequest.call]
 * (URL fetch + parse) rather than a second HTTP/JSON path.
 */
private class PeliasReverseRequest(url: String) : PeliasRequest(url)

/** Mints the domain [TripEndpoint.Geocoded] from a geocoder [CustomAddress] result (the wire boundary). */
internal fun CustomAddress.toGeocoded(): TripEndpoint.Geocoded = TripEndpoint.Geocoded(
    displayName = toString(),
    lat = if (isSet) latitude else null,
    lon = if (isSet) longitude else null,
    isTransit = isTransitCategory
)
