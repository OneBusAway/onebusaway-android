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
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.request.ObaRoutesForLocationRequest
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.routeDisplayNames

/**
 * A route search match.
 *
 * @param shortName primary display name, with the legacy fallbacks already applied
 * (short name, falling back to the long name)
 * @param longName secondary line (long name or description), or null when there is none
 */
data class RouteSearchResult(
    val id: String,
    val shortName: String,
    val longName: String?,
    val url: String?
)

/** Searches routes by name/number near the user. */
interface RouteSearchRepository {

    suspend fun search(query: String): Result<List<RouteSearchResult>>
}

/**
 * Default implementation wrapping the blocking OBA routes-for-location request: queries around
 * the user's location first and falls back to a wide-radius search around the region's default
 * center when that returns nothing usable (the legacy route-search behavior).
 */
class DefaultRouteSearchRepository(private val context: Context) : RouteSearchRepository {

    override suspend fun search(query: String): Result<List<RouteSearchResult>> =
        withContext(Dispatchers.IO) {
            var response = ObaRoutesForLocationRequest.Builder(context, LocationUtils.getSearchCenter(context))
                .setQuery(query)
                .build()
                .call()
            if (response == null || response.code != ObaApi.OBA_OK
                || response.routesForLocation.isEmpty()
            ) {
                val defaultCenter = LocationUtils.getDefaultSearchCenter(context)
                if (defaultCenter != null) {
                    response = ObaRoutesForLocationRequest.Builder(context, defaultCenter)
                        .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
                        .setQuery(query)
                        .build()
                        .call()
                }
            }
            when {
                // Never reached the server (or got an unparseable reply)
                response == null || response.code == 0 ->
                    Result.failure(IOException("Route search failed"))
                // Server replied with an error code — legacy screens show "no results"
                response.code != ObaApi.OBA_OK -> Result.success(emptyList())
                else -> Result.success(response.routesForLocation.map(::toResult))
            }
        }

    private fun toResult(route: ObaRoute): RouteSearchResult {
        val names = routeDisplayNames(route)
        return RouteSearchResult(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            url = route.url?.takeIf { it.isNotEmpty() }
        )
    }
}
