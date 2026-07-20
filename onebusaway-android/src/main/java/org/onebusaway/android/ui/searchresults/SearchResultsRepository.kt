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
package org.onebusaway.android.ui.searchresults

import android.location.Location
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.api.data.LocationSearchDataSource
import org.onebusaway.android.api.data.RoutesNearResult
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopUserInfo
import org.onebusaway.android.database.oba.stopDisplayName
import org.onebusaway.android.database.oba.toStopUserInfoMap
import org.onebusaway.android.location.SearchCenter
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.routeDisplayNames
import org.onebusaway.android.util.runCatchingCancellable

/** Searches routes and stops near the user and combines them into one result list. */
interface SearchResultsRepository {

    suspend fun search(query: String): Result<List<SearchResultItem>>
}

/**
 * Default implementation over the api [LocationSearchDataSource]. Runs the routes-for-location
 * and stops-for-location requests in parallel (matching the legacy screen's single combined loader)
 * and merges them routes-first. All Android statics are quarantined here so [SearchResultsViewModel]
 * stays JVM-testable.
 */
class DefaultSearchResultsRepository @Inject constructor(
    private val searchCenter: SearchCenter,
    private val search: LocationSearchDataSource,
    private val stopDao: StopDao,
    private val importGate: ImportGate
) : SearchResultsRepository {

    override suspend fun search(query: String): Result<List<SearchResultItem>> = coroutineScope {
        val center = searchCenter.current()
            ?: return@coroutineScope Result.failure(IOException("No search location available"))

        // Each search resolves a non-OK code / transport failure to Result.failure (requireData).
        // runCatchingCancellable keeps a cancelled search out of that Result.failure, so cancellation
        // propagates through await() and cancels the sibling search too.
        val routes = async { runCatchingCancellable { searchRoutes(query, center) } }
        val stops = async { runCatchingCancellable { searchStops(query, center) } }
        val routeResult = routes.await()
        val stopResult = stops.await()

        // A true failure only when BOTH searches failed; otherwise show whatever came back.
        if (routeResult.isFailure && stopResult.isFailure) {
            return@coroutineScope Result.failure(
                routeResult.exceptionOrNull() ?: stopResult.exceptionOrNull()
                    ?: IOException("Search failed")
            )
        }

        // The favourite/custom-name enrichment is best-effort: a DB hiccup here must not fail a search
        // that already has route/stop results, so treat it as a soft miss (empty map) like the lookups.
        importGate.awaitReady()
        val userInfo = runCatchingCancellable { stopDao.userInfoMap().toStopUserInfoMap() }
            .getOrDefault(emptyMap())
        val items = buildList {
            routeResult.getOrNull()?.let { result ->
                result.routes.forEach { add(toRoute(it, result.agencyNames)) }
            }
            stopResult.getOrNull()?.forEach { add(toStop(it, userInfo[it.id])) }
        }
        Result.success(items)
    }

    /** Searches around the user, widening to the region's default center when nothing matches. */
    private suspend fun searchRoutes(query: String, center: Location): RoutesNearResult {
        val near = search
            .routesNear(center.latitude, center.longitude, query, SearchCenter.DEFAULT_SEARCH_RADIUS_METERS)
            .getOrThrow()
        if (near.routes.isNotEmpty()) return near
        val default = searchCenter.regionCenter() ?: return near
        return search
            .routesNear(default.latitude, default.longitude, query, SearchCenter.DEFAULT_SEARCH_RADIUS_METERS)
            .getOrThrow()
    }

    private suspend fun searchStops(query: String, center: Location): List<ObaStop> = search
        .stopsNear(center.latitude, center.longitude, query, SearchCenter.DEFAULT_SEARCH_RADIUS_METERS)
        .getOrThrow()

    private fun toRoute(route: ObaRoute, agencyNames: Map<String, String>): SearchResultItem.Route {
        val names = routeDisplayNames(route)
        return SearchResultItem.Route(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            url = route.url?.takeIf { it.isNotEmpty() },
            routeColor = route.color,
            // A blank agency name renders as no line (RouteRowContent guards isNullOrBlank).
            agency = agencyNames[route.agencyId]
        )
    }

    private fun toStop(stop: ObaStop, userInfo: StopUserInfo?) = SearchResultItem.Stop(
        id = stop.id,
        name = stopDisplayName(stop, userInfo),
        direction = stop.direction.orEmpty(),
        isFavorite = userInfo?.isFavorite == true,
        latitude = stop.latitude,
        longitude = stop.longitude
    )
}
