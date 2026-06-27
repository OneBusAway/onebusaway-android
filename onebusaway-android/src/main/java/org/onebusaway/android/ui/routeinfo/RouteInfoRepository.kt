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

import org.onebusaway.android.api.data.RouteDataSource
import org.onebusaway.android.api.data.RouteStopsDataSource

import android.content.ContentValues
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.models.RouteDetails
import org.onebusaway.android.models.RouteStopGroup
import org.onebusaway.android.models.ObaStop
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
 * Default implementation backed by the modernized client: the route metadata comes from the shared
 * [RouteDataSource] and the stops-for-route from [RouteStopsDataSource], fetched in parallel (matching
 * the legacy screen's two concurrent loaders). Registers the route in the recents/search provider on
 * success. Stays on [Dispatchers.IO] for that blocking provider write; all Android statics are
 * quarantined here so [RouteInfoViewModel] stays JVM-testable.
 */
class DefaultRouteInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val routeRepository: RouteDataSource,
    private val routeStopsRepository: RouteStopsDataSource,
) : RouteInfoRepository {

    override suspend fun loadRouteInfo(routeId: String): Result<RouteInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Fetch route metadata and stops-for-route in parallel; both complete before unwrap.
                val (routeResult, stopsResult) = coroutineScope {
                    val routeDeferred = async { routeRepository.getRoute(routeId) }
                    val stopsDeferred = async { routeStopsRepository.stopsForRoute(routeId) }
                    routeDeferred.await() to stopsDeferred.await()
                }
                val route = routeResult.getOrThrow()
                val directions = stopsResult.getOrThrow().toRouteDirections()
                registerRouteUsage(route)
                toRouteInfo(route, directions)
            }
                .onFailure { Log.e(TAG, "loadRouteInfo($routeId) failed", it) }
                // The VM renders Result.failure's message, so surface a user-facing route error
                // string. Preserve the OBA code when we have one (e.g. 404 -> "route not found");
                // a transport/parse error has none, so fall back to the connection message.
                .recoverCatching { error ->
                    val code = (error as? ObaApiException)?.code ?: ObaApi.OBA_IO_EXCEPTION
                    throw IOException(ObaRequestErrors.getRouteErrorString(context, code))
                }
        }

    /** Records the route in the provider so it appears in recents and search (legacy parity). */
    private fun registerRouteUsage(route: RouteDetails) {
        val values = ContentValues()
        values.put(ObaContract.Routes.SHORTNAME, route.shortName)
        values.put(ObaContract.Routes.LONGNAME, route.longName)
        values.put(ObaContract.Routes.URL, route.url)
        regionRepository.region.value?.let {
            values.put(ObaContract.Routes.REGION_ID, it.id)
        }
        ObaContract.Routes.insertOrUpdate(context, route.id, values, true)
    }

    private fun toRouteInfo(route: RouteDetails, directions: List<RouteDirection>): RouteInfo {
        val names = routeDisplayNames(route.shortName, route.longName, route.description)
        return RouteInfo(
            id = route.id,
            shortName = names.shortName,
            longName = names.longName,
            agencyName = route.agency?.name,
            url = route.url?.takeIf { it.isNotEmpty() },
            directions = directions
        )
    }

    private companion object {
        const val TAG = "RouteInfoRepository"
    }
}

/**
 * Maps the per-direction [RouteStopGroup]s (model stops) to the [RouteDirection]s the UI shows,
 * applying the display-text formatting. Pure, so it is JVM-unit-tested.
 */
internal fun List<RouteStopGroup>.toRouteDirections(): List<RouteDirection> = map { group ->
    RouteDirection(
        name = MyTextUtils.formatDisplayText(group.name).orEmpty(),
        stops = group.stops.map { it.toRouteStopItem() }
    )
}

private fun ObaStop.toRouteStopItem() = RouteStopItem(
    id = id,
    name = MyTextUtils.formatDisplayText(name).orEmpty(),
    direction = direction.orEmpty(),
    latitude = latitude,
    longitude = longitude
)
