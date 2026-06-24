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
package org.onebusaway.android.ui.search

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.io.request.ObaStopsForLocationRequest
import org.onebusaway.android.provider.StopUserInfo
import org.onebusaway.android.provider.loadStopUserInfo
import org.onebusaway.android.provider.stopDisplayName
import org.onebusaway.android.util.LocationUtils

/**
 * A stop search match.
 *
 * @param name display name — the user's custom name for the stop if they renamed it,
 * otherwise the formatted server name
 * @param serverName the raw server name, used for arrivals intents and shortcuts (legacy parity)
 * @param direction raw compass direction code ("N", "SW", ...), empty when unknown
 */
data class StopSearchResult(
    val id: String,
    val name: String,
    val serverName: String?,
    val direction: String,
    val isFavorite: Boolean,
    val latitude: Double,
    val longitude: Double
)

/** Searches stops by stop number/name near the user. */
interface StopSearchRepository {

    suspend fun search(query: String): Result<List<StopSearchResult>>
}

/**
 * Default implementation wrapping the blocking OBA stops-for-location request, decorated with
 * the user's stop favorites and custom names from the ContentProvider (the same query the
 * legacy UIUtils.StopUserInfoMap ran).
 */
class DefaultStopSearchRepository(private val context: Context) : StopSearchRepository {

    override suspend fun search(query: String): Result<List<StopSearchResult>> =
        withContext(Dispatchers.IO) {
            val response = ObaStopsForLocationRequest.Builder(context, LocationUtils.getSearchCenter(context))
                .setQuery(query)
                .build()
                .call()
            when {
                // Never reached the server (or got an unparseable reply)
                response == null || response.code == 0 ->
                    Result.failure(IOException("Stop search failed"))
                // Server replied with an error code — legacy screens show "no results"
                response.code != ObaApi.OBA_OK -> Result.success(emptyList())
                else -> {
                    val userInfo = loadStopUserInfo(context)
                    Result.success(response.stops.map { toResult(it, userInfo[it.id]) })
                }
            }
        }

    private fun toResult(stop: ObaStop, userInfo: StopUserInfo?) = StopSearchResult(
        id = stop.id,
        name = stopDisplayName(stop, userInfo),
        serverName = stop.name,
        direction = stop.direction.orEmpty(),
        isFavorite = userInfo?.isFavorite == true,
        latitude = stop.latitude,
        longitude = stop.longitude
    )
}
