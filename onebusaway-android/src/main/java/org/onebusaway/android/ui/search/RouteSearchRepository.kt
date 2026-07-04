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
import org.onebusaway.android.models.ObaRoute
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
 * Default implementation over the api [LocationSearchDataSource]: queries around the user's
 * location first and falls back to a wide-radius search around the region's default center when that
 * returns nothing usable (the legacy route-search behavior). [context] is still needed for the
 * in-memory location lookups; [search] is constructor-injected (resolved at the Compose call site)
 * so this repository declares its dependency and is swappable in tests.
 *
 * A transport/parse failure surfaces as [Result.failure] (so the UI can show an error); a server
 * error *code* is treated as no results (via [LocationSearchDataSource.routesNearOrEmpty]).
 */
class DefaultRouteSearchRepository(
    private val context: Context,
    private val search: LocationSearchDataSource,
) : RouteSearchRepository {

    override suspend fun search(query: String): Result<List<RouteSearchResult>> = runCatching {
        val center = LocationUtils.getSearchCenter(context)
        var routes = search.routesNearOrEmpty(center.latitude, center.longitude, query, null)
            .getOrThrow()
        if (routes.isEmpty()) {
            LocationUtils.getDefaultSearchCenter(context)?.let { fallback ->
                routes = search.routesNearOrEmpty(
                    fallback.latitude, fallback.longitude,
                    query, LocationUtils.DEFAULT_SEARCH_RADIUS
                ).getOrThrow()
            }
        }
        routes.map(::toResult)
    }.onFailure { Log.e(TAG, "route search failed", it) }

    private fun toResult(route: ObaRoute): RouteSearchResult {
        val names = routeDisplayNames(route.shortName, route.longName, route.description)
        return RouteSearchResult(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            url = route.url?.takeIf { it.isNotEmpty() }
        )
    }

    private companion object {
        const val TAG = "RouteSearchRepository"
    }
}
