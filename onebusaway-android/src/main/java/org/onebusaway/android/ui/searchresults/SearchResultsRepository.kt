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
import org.onebusaway.android.api.data.VehicleMatch
import org.onebusaway.android.api.data.VehicleSearchDataSource
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

/** Searches routes and stops near the user, plus vehicles by coach number, into one result list. */
interface SearchResultsRepository {

    suspend fun search(query: String): Result<List<SearchResultItem>>
}

/**
 * Default implementation over the api [LocationSearchDataSource] and [VehicleSearchDataSource]. Runs
 * the routes-for-location, stops-for-location and coach-number searches in parallel (matching the
 * legacy screen's single combined loader) and merges them routes-first, then stops, then vehicles.
 * All Android statics are quarantined here so [SearchResultsViewModel] stays JVM-testable.
 *
 * The vehicle search is deliberately not gated on the query "looking like" a coach number — the
 * region's vehicle index decides what matches, so a route/stop query simply comes back empty.
 */
class DefaultSearchResultsRepository @Inject constructor(
    private val searchCenter: SearchCenter,
    private val search: LocationSearchDataSource,
    private val vehicleSearch: VehicleSearchDataSource,
    private val stopDao: StopDao,
    private val importGate: ImportGate
) : SearchResultsRepository {

    override suspend fun search(query: String): Result<List<SearchResultItem>> = coroutineScope {
        // Only the near-me searches need a location; a coach number resolves without one, so a
        // missing center fails those two rather than the whole search.
        val center = searchCenter.current()

        val routes = async { nearMe(center) { searchRoutes(query, it) } }
        val stops = async { nearMe(center) { searchStops(query, it) } }
        val vehicles = async { vehicleSearch.vehiclesMatching(query) }
        val routeResult = routes.await()
        val stopResult = stops.await()
        val vehicleResult = vehicles.await()

        // A true failure when both near-me legs failed and the vehicle leg has nothing to put in their
        // place. The vehicle leg answering "no coach by that name" is not an answer about the routes and
        // stops we couldn't reach, so an empty vehicle success must not turn an outage into the
        // no-results screen (which offers no retry) — only actual vehicle hits do.
        val matchedVehicles = vehicleResult.getOrNull().orEmpty()
        if (routeResult.isFailure && stopResult.isFailure && matchedVehicles.isEmpty()) {
            val results = listOf(routeResult, stopResult, vehicleResult)
            return@coroutineScope Result.failure(
                results.firstNotNullOfOrNull { it.exceptionOrNull() } ?: IOException("Search failed")
            )
        }

        val matchedStops = stopResult.getOrNull().orEmpty()
        val items = buildList {
            routeResult.getOrNull()?.let { result ->
                result.routes.forEach { add(toRoute(it, result.agencyNames)) }
            }
            val userInfo = stopUserInfo(matchedStops)
            matchedStops.forEach { add(toStop(it, userInfo[it.id])) }
            matchedVehicles.forEach { add(toVehicle(it)) }
        }
        Result.success(items)
    }

    /**
     * Favourite/custom-name info for [stops], or an empty map when there are none to enrich — a
     * coach-number-only (or location-less) search then skips the import-gate await and the query
     * entirely. Best-effort otherwise: a DB hiccup must not fail a search that already has results,
     * so it's a soft miss (empty map) like the lookups.
     */
    private suspend fun stopUserInfo(stops: List<ObaStop>): Map<String, StopUserInfo> {
        if (stops.isEmpty()) return emptyMap()
        importGate.awaitReady()
        return runCatchingCancellable { stopDao.userInfoMap().toStopUserInfoMap() }
            .getOrDefault(emptyMap())
    }

    /**
     * Runs a location-scoped [lookup], or reports the missing center as that lookup's failure. The
     * lookup resolves a non-OK code / transport failure to Result.failure (requireData);
     * runCatchingCancellable keeps a cancelled search out of that Result.failure, so cancellation
     * propagates through await() and cancels the sibling searches too.
     */
    private suspend fun <T> nearMe(center: Location?, lookup: suspend (Location) -> T): Result<T> = if (center == null) {
        Result.failure(IOException("No search location available"))
    } else {
        runCatchingCancellable { lookup(center) }
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

    private fun toVehicle(match: VehicleMatch) = SearchResultItem.Vehicle(
        id = match.vehicleId,
        coachNumber = match.coachNumber,
        agency = match.agencyName,
        assignment = match.assignment
    )

    private fun toStop(stop: ObaStop, userInfo: StopUserInfo?) = SearchResultItem.Stop(
        id = stop.id,
        name = stopDisplayName(stop, userInfo),
        direction = stop.direction.orEmpty(),
        isFavorite = userInfo?.isFavorite == true,
        latitude = stop.latitude,
        longitude = stop.longitude
    )
}
