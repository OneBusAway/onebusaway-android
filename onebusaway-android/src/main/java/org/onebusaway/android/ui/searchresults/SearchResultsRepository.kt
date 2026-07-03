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

import org.onebusaway.android.api.data.LocationSearchDataSource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.location.Location
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.provider.StopUserInfo
import org.onebusaway.android.provider.stopDisplayName
import org.onebusaway.android.provider.toStopUserInfoMap
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.routeDisplayNames

/** Searches routes and stops near the user and combines them into one result list. */
interface SearchResultsRepository {

    suspend fun search(query: String): Result<List<SearchResultItem>>
}

/**
 * Default implementation over the io.client [LocationSearchDataSource]. Runs the routes-for-location
 * and stops-for-location requests in parallel (matching the legacy screen's single combined loader)
 * and merges them routes-first. All Android statics are quarantined here so [SearchResultsViewModel]
 * stays JVM-testable.
 */
class DefaultSearchResultsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val search: LocationSearchDataSource,
    private val stopDao: StopDao,
    private val importGate: ImportGate,
) : SearchResultsRepository {

    override suspend fun search(query: String): Result<List<SearchResultItem>> = coroutineScope {
        val center = LocationUtils.getSearchCenter(context)
            ?: return@coroutineScope Result.failure(IOException("No search location available"))

        // Each search resolves a non-OK code / transport failure to Result.failure (requireData).
        val routes = async { runCatching { searchRoutes(query, center) } }
        val stops = async { runCatching { searchStops(query, center) } }
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
        val userInfo = runCatching { stopDao.userInfoMap().toStopUserInfoMap() }.getOrDefault(emptyMap())
        val items = buildList {
            routeResult.getOrNull()?.forEach { add(toRoute(it)) }
            stopResult.getOrNull()?.forEach { add(toStop(it, userInfo[it.id])) }
        }
        Result.success(items)
    }

    /** Searches around the user, widening to the region's default center when nothing matches. */
    private suspend fun searchRoutes(query: String, center: Location): List<ObaRoute> {
        val near = search
            .routesNear(center.latitude, center.longitude, query, LocationUtils.DEFAULT_SEARCH_RADIUS)
            .getOrThrow()
        if (near.isNotEmpty()) return near
        val default = LocationUtils.getDefaultSearchCenter(context) ?: return near
        return search
            .routesNear(default.latitude, default.longitude, query, LocationUtils.DEFAULT_SEARCH_RADIUS)
            .getOrThrow()
    }

    private suspend fun searchStops(query: String, center: Location): List<ObaStop> =
        search
            .stopsNear(center.latitude, center.longitude, query, LocationUtils.DEFAULT_SEARCH_RADIUS)
            .getOrThrow()

    private fun toRoute(route: ObaRoute): SearchResultItem.Route {
        val names = routeDisplayNames(route)
        return SearchResultItem.Route(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            url = route.url?.takeIf { it.isNotEmpty() }
        )
    }

    private fun toStop(stop: ObaStop, userInfo: StopUserInfo?) =
        SearchResultItem.Stop(
            id = stop.id,
            name = stopDisplayName(stop, userInfo),
            direction = stop.direction.orEmpty(),
            isFavorite = userInfo?.isFavorite == true,
            latitude = stop.latitude,
            longitude = stop.longitude
        )
}
