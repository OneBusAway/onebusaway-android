/*
 * Copyright (C) 2016 University of South Florida,
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
package org.onebusaway.android.ui.report.infrastructure

import android.content.Context
import android.location.Geocoder
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A geocoded coordinate, decoupled from android.location.Location for the ViewModel. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Forward/reverse geocoding for the issue address field, replacing the legacy GeocoderTask and
 * InfrastructureIssueActivity.getLocationByAddress.
 */
interface GeocodeAddressRepository {

    /** A human-readable address for a coordinate, or an empty string when none is found. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String>

    /** The coordinate for a typed address; failure when the address can't be resolved. */
    suspend fun forwardGeocode(query: String): Result<GeoPoint>
}

class DefaultGeocodeAddressRepository(private val context: Context) : GeocodeAddressRepository {

    @Suppress("DEPRECATION") // Synchronous Geocoder API, run off the main thread.
    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull()
                ?: return@withContext Result.success("")

            // Join all but the final address line, matching the legacy GeocoderTask formatting.
            val lastIndex = address.maxAddressLineIndex
            if (lastIndex < 1) {
                return@withContext Result.success(address.getAddressLine(0).orEmpty())
            }
            val formatted = buildString {
                for (i in 0 until lastIndex - 1) {
                    append(address.getAddressLine(i)).append(", ")
                }
                append(address.getAddressLine(lastIndex - 1)).append(".")
            }
            Result.success(formatted)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun forwardGeocode(query: String): Result<GeoPoint> =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                val match = geocoder.getFromLocationName(query, 3)?.firstOrNull()
                    ?: return@withContext Result.failure(IOException("Address not found: $query"))
                Result.success(GeoPoint(match.latitude, match.longitude))
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
}
