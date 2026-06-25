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
import org.onebusaway.android.util.LocationUtils

/** Address-autocomplete suggestions for the trip-plan endpoints. */
interface GeocodeRepository {
    suspend fun suggest(query: String): Result<List<PlaceItem>>
}

/**
 * Pelias-backed geocoding. All four product flavors set `USE_PELIAS_GEOCODING = true`, so the
 * legacy Google-Places intent path is not carried over. Wraps the blocking
 * [LocationUtils.processPeliasGeocoding] on the IO thread and projects [CustomAddress] onto the
 * JVM-pure [PlaceItem] so the ViewModel stays testable.
 */
class DefaultGeocodeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
) : GeocodeRepository {

    override suspend fun suggest(query: String): Result<List<PlaceItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val region = regionRepository.region.value
                LocationUtils.processPeliasGeocoding(context, region, query).map { it.toPlaceItem() }
            }
        }

    private fun CustomAddress.toPlaceItem(): PlaceItem = PlaceItem(
        displayName = toString(),
        lat = if (isSet) latitude else null,
        lon = if (isSet) longitude else null,
        isTransit = isTransitCategory
    )
}
