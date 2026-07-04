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

import org.onebusaway.android.api.data.LocationSearchDataSource

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.database.oba.StopUserInfo
import org.onebusaway.android.database.oba.stopDisplayName
import org.onebusaway.android.database.oba.toStopUserInfoMap
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
 * Default implementation over the api [LocationSearchDataSource] (constructor-injected,
 * resolved at the Compose call site), decorated with the user's stop favorites and custom names from
 * the ContentProvider (the same query the legacy UIUtils.StopUserInfoMap ran). [context] is still
 * needed for the location lookup and the provider query.
 *
 * Stays on [Dispatchers.IO]: unlike the route search, the [loadStopUserInfo] ContentProvider query
 * is blocking. As with the route search, a transport/parse failure surfaces as [Result.failure]
 * while a server error code yields no results (via [LocationSearchDataSource.stopsNearOrEmpty]).
 */
class DefaultStopSearchRepository(
    private val context: Context,
    private val search: LocationSearchDataSource,
) : StopSearchRepository {

    override suspend fun search(query: String): Result<List<StopSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val center = LocationUtils.getSearchCenter(context)
                val stops = search.stopsNearOrEmpty(center.latitude, center.longitude, query, null)
                    .getOrThrow()
                val db = DatabaseEntryPoint.get(context)
                db.importGate().awaitReady()
                val userInfo = db.stopDao().userInfoMap().toStopUserInfoMap()
                stops.map { it.toStopSearchResult(userInfo[it.id]) }
            }.onFailure { Log.e(TAG, "stop search failed", it) }
        }

    private companion object {
        const val TAG = "StopSearchRepository"
    }
}

/** Maps a stop to its display result, applying the user's custom name / favorite. */
internal fun ObaStop.toStopSearchResult(userInfo: StopUserInfo?) = StopSearchResult(
    id = id,
    name = stopDisplayName(name, userInfo),
    serverName = name,
    direction = direction.orEmpty(),
    isFavorite = userInfo?.isFavorite == true,
    latitude = latitude,
    longitude = longitude
)
