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
package org.onebusaway.android.ui.routeinfo

import android.content.ContentValues
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.io.request.ObaRouteRequest
import org.onebusaway.android.io.request.ObaRouteResponse
import org.onebusaway.android.io.request.ObaStopsForRouteRequest
import org.onebusaway.android.io.request.ObaStopsForRouteResponse
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.ObaRequestErrors
import org.onebusaway.android.util.routeDisplayNames

/** Loads a route's metadata and its stops grouped by direction. */
interface RouteInfoRepository {

    suspend fun loadRouteInfo(routeId: String): Result<RouteInfo>
}

/**
 * Default implementation. Fetches the route metadata and stops-for-route in parallel (matching
 * the legacy screen's two concurrent loaders), and registers the route in the recents/search
 * provider on success. All Android statics are quarantined here so [RouteInfoViewModel] stays
 * JVM-testable.
 */
class DefaultRouteInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
) : RouteInfoRepository {

    override suspend fun loadRouteInfo(routeId: String): Result<RouteInfo> =
        withContext(Dispatchers.IO) {
            // RequestBase.call() returns an error-coded (or null) response rather than throwing,
            // so neither async cancels the scope; we inspect both results below.
            val (route, stops) = coroutineScope {
                val routeDeferred = async {
                    ObaRouteRequest.newRequest(context, routeId).call()
                }
                val stopsDeferred = async {
                    ObaStopsForRouteRequest.Builder(context, routeId)
                        .setIncludeShapes(false)
                        .build()
                        .call()
                }
                routeDeferred.await() to stopsDeferred.await()
            }

            val routeCode = route?.code ?: ObaApi.OBA_IO_EXCEPTION
            val stopsCode = stops?.code ?: ObaApi.OBA_IO_EXCEPTION
            if (routeCode != ObaApi.OBA_OK || stopsCode != ObaApi.OBA_OK) {
                val failedCode = if (routeCode != ObaApi.OBA_OK) routeCode else stopsCode
                return@withContext Result.failure(
                    IOException(ObaRequestErrors.getRouteErrorString(context, failedCode))
                )
            }

            registerRouteUsage(route!!)
            Result.success(toRouteInfo(route, stops!!))
        }

    /** Records the route in the provider so it appears in recents and search (legacy parity). */
    private fun registerRouteUsage(route: ObaRouteResponse) {
        val values = ContentValues()
        values.put(ObaContract.Routes.SHORTNAME, route.shortName)
        values.put(ObaContract.Routes.LONGNAME, route.longName)
        values.put(ObaContract.Routes.URL, route.url)
        regionRepository.region.value?.let {
            values.put(ObaContract.Routes.REGION_ID, it.id)
        }
        ObaContract.Routes.insertOrUpdate(context, route.id, values, true)
    }

    private fun toRouteInfo(route: ObaRouteResponse, stops: ObaStopsForRouteResponse): RouteInfo {
        val names = routeDisplayNames(route)
        return RouteInfo(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            agencyName = route.agency?.name,
            url = route.url?.takeIf { it.isNotEmpty() },
            directions = toDirections(stops)
        )
    }

    private fun toDirections(stops: ObaStopsForRouteResponse): List<RouteDirection> {
        val stopsById = stops.stops.associateBy { it.id }
        return stops.stopGroupings.flatMap { grouping ->
            grouping.stopGroups.map { group ->
                RouteDirection(
                    name = MyTextUtils.formatDisplayText(group.name).orEmpty(),
                    stops = group.stopIds.mapNotNull { stopsById[it]?.let(::toStopItem) }
                )
            }
        }
    }

    private fun toStopItem(stop: ObaStop) = RouteStopItem(
        id = stop.id,
        name = MyTextUtils.formatDisplayText(stop.name).orEmpty(),
        direction = stop.direction.orEmpty(),
        latitude = stop.latitude,
        longitude = stop.longitude
    )
}
