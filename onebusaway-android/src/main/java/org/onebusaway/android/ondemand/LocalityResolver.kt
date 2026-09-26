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
package org.onebusaway.android.ondemand

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.runCatchingCancellable

/** Spec §2.8: the locality is used only when it arrives within a second; otherwise the copy omits it. */
const val LOCALITY_TIMEOUT_MS = 1_000L

/** The reverse-geocoded locality ("Boyne City") of a point, or null when unknown or too slow. */
interface LocalityResolver {
    suspend fun locality(point: GeoPoint): String?
}

/** The on-device [Geocoder], on the IO thread, capped at [LOCALITY_TIMEOUT_MS]. Never throws. */
class DefaultLocalityResolver @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocalityResolver {

    override suspend fun locality(point: GeoPoint): String? = withTimeoutOrNull(LOCALITY_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                if (!Geocoder.isPresent()) return@runCatchingCancellable null
                // Sync getFromLocation is deprecated in API 33; its replacement needs API 33 while minSdk
                // is 23 — the same degraded path DefaultGeocodeRepository.platformReverse takes.
                // https://developer.android.com/reference/android/location/Geocoder#getFromLocation(double,%20double,%20int)
                // tracking issue: to be filed
                @Suppress("DEPRECATION")
                val address = Geocoder(context).getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                address?.locality ?: address?.subAdminArea
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }
}
