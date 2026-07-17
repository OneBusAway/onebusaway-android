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

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.onebusaway.android.location.SearchCenter
import org.onebusaway.android.models.ObaRoute
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
    val url: String?,
    val routeColor: Int? = null
)

/** Searches routes by name/number near the user. */
interface RouteSearchRepository {

    suspend fun search(query: String): Result<List<RouteSearchResult>>
}

/**
 * Default implementation over the api [LocationSearchDataSource]: queries around the user's
 * location first and falls back to a wide-radius search around the region's default center when that
 * returns nothing usable (the legacy route-search behavior). [searchCenter] resolves the "near me"
 * origin; [search] is constructor-injected (resolved at the Compose call site) so this repository
 * declares its dependencies and is swappable in tests.
 *
 * A transport/parse failure surfaces as [Result.failure] (so the UI can show an error); a server
 * error *code* is treated as no results (via [LocationSearchDataSource.routesNearOrEmpty]).
 */
class DefaultRouteSearchRepository(
    private val searchCenter: SearchCenter,
    private val search: LocationSearchDataSource,
) : RouteSearchRepository {

    override suspend fun search(query: String): Result<List<RouteSearchResult>> = runCatching {
        val center = searchCenter.current()
            ?: throw IOException("No search location available")
        var routes = search.routesNearOrEmpty(center.latitude, center.longitude, query, null)
            .getOrThrow()
        if (routes.isEmpty()) {
            searchCenter.regionCenter()?.let { fallback ->
                routes = search.routesNearOrEmpty(
                    fallback.latitude, fallback.longitude,
                    query, SearchCenter.DEFAULT_SEARCH_RADIUS_METERS
                ).getOrThrow()
            }
        }
        routes.map(::toResult)
    }.onFailure {
        // transformLatest cancels the in-flight search on each keystroke; let that cancellation
        // propagate instead of reporting it to the UI as a search Error.
        if (it is CancellationException) throw it
        Log.e(TAG, "route search failed", it)
    }

    private fun toResult(route: ObaRoute): RouteSearchResult {
        val names = routeDisplayNames(route.shortName, route.longName, route.description)
        return RouteSearchResult(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            url = route.url?.takeIf { it.isNotEmpty() },
            routeColor = route.color
        )
    }

    private companion object {
        const val TAG = "RouteSearchRepository"
    }
}
