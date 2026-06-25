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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.location.Location
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.io.request.ObaRoutesForLocationRequest
import org.onebusaway.android.io.request.ObaRoutesForLocationResponse
import org.onebusaway.android.io.request.ObaStopsForLocationRequest
import org.onebusaway.android.io.request.ObaStopsForLocationResponse
import org.onebusaway.android.provider.StopUserInfo
import org.onebusaway.android.provider.loadStopUserInfo
import org.onebusaway.android.provider.stopDisplayName
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.routeDisplayNames

/** Searches routes and stops near the user and combines them into one result list. */
interface SearchResultsRepository {

    suspend fun search(query: String): Result<List<SearchResultItem>>
}

/**
 * Default implementation. Runs the routes-for-location and stops-for-location requests in
 * parallel (matching the legacy screen's single combined loader) and merges them routes-first.
 * All Android statics are quarantined here so [SearchResultsViewModel] stays JVM-testable.
 */
class DefaultSearchResultsRepository @Inject constructor(@ApplicationContext private val context: Context) : SearchResultsRepository {

    override suspend fun search(query: String): Result<List<SearchResultItem>> =
        withContext(Dispatchers.IO) {
            val center = LocationUtils.getSearchCenter(context)
            // RequestBase.call() returns an error-coded (or null) response rather than throwing,
            // so neither async cancels the scope.
            val (routes, stops) = coroutineScope {
                val routesDeferred = async { searchRoutes(query, center) }
                val stopsDeferred = async { searchStops(query, center) }
                routesDeferred.await() to stopsDeferred.await()
            }

            val routeCode = routes?.code ?: ObaApi.OBA_IO_EXCEPTION
            val stopCode = stops?.code ?: ObaApi.OBA_IO_EXCEPTION
            // Legacy: a true communication error only when both failed and the route code is the
            // empty-body sentinel (0). Any other combination shows whatever results came back.
            if (routeCode != ObaApi.OBA_OK && stopCode != ObaApi.OBA_OK && routeCode == 0) {
                return@withContext Result.failure(IOException("Search failed"))
            }

            val userInfo = loadStopUserInfo(context)
            val items = buildList {
                routes?.routesForLocation?.forEach { add(toRoute(it)) }
                stops?.stops?.forEach { add(toStop(it, userInfo[it.id])) }
            }
            Result.success(items)
        }

    /** Searches around the user, widening to the region's default center when nothing matches. */
    private fun searchRoutes(query: String, center: Location?): ObaRoutesForLocationResponse? {
        val response = ObaRoutesForLocationRequest.Builder(context, center)
            .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
            .setQuery(query)
            .build()
            .call()
        if (response != null && response.code == ObaApi.OBA_OK
            && response.routesForLocation.isNotEmpty()
        ) {
            return response
        }
        val defaultCenter = LocationUtils.getDefaultSearchCenter(context) ?: return response
        return ObaRoutesForLocationRequest.Builder(context, defaultCenter)
            .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
            .setQuery(query)
            .build()
            .call()
    }

    private fun searchStops(query: String, center: Location?): ObaStopsForLocationResponse? =
        ObaStopsForLocationRequest.Builder(context, center)
            .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
            .setQuery(query)
            .build()
            .call()

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
