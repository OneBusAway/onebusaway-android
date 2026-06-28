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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.directions.util.CustomAddress
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.LocationUtils

/** Address-autocomplete suggestions for the trip-plan endpoints. */
interface GeocodeRepository {
    suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>>
}

/**
 * Address suggestions for the trip-plan endpoints. Prefers Pelias (real autocomplete, with
 * `transport:public` results flagged as transit), but falls back to the on-device
 * [android.location.Geocoder] when no Pelias API key is configured — so key-free dev builds still
 * geocode. Both paths reuse the existing [LocationUtils] geocoders (which handle the region bbox
 * biasing/filtering); the Geocoder fallback has no typeahead/transit categories, so it's degraded only.
 * Runs the blocking work on the IO thread and projects onto the JVM-pure [TripEndpoint.Geocoded].
 */
class DefaultGeocodeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
) : GeocodeRepository {

    override suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val region = regionRepository.region.value
                val addresses = if (BuildFlavorUtils.isPeliasApiKeyDefined()) {
                    LocationUtils.processPeliasGeocoding(context, region, query)
                } else {
                    // No-key fallback: the platform Geocoder, region-biased + filtered — the same helper
                    // the legacy trip planner already geocodes its endpoint addresses with.
                    LocationUtils.processGeocoding(context, region, false, query)
                }
                addresses.orEmpty().map { it.toGeocoded() }
            }
        }
}

/** Mints the domain [TripEndpoint.Geocoded] from a geocoder [CustomAddress] result (the wire boundary). */
internal fun CustomAddress.toGeocoded(): TripEndpoint.Geocoded = TripEndpoint.Geocoded(
    displayName = toString(),
    lat = if (isSet) latitude else null,
    lon = if (isSet) longitude else null,
    isTransit = isTransitCategory
)
