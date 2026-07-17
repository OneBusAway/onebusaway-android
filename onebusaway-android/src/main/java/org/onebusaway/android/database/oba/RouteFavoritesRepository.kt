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
package org.onebusaway.android.database.oba

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.api.data.RouteDataSource
import org.onebusaway.android.app.di.AppScope
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.routeDisplayNames

/**
 * The narrow route-starring surface a consumer needs (the live starred-id set plus the wholesale
 * star/unstar write), segregated from [RouteFavoritesRepository]'s full dependency set (Context,
 * analytics, app scope) so a repository that only stars routes — e.g. the arrivals repository — is
 * JVM-unit-testable with a fake (#1909).
 */
interface RouteFavorites {

    /** The starred route ids, live (see [RouteFavoritesRepository.favoriteRouteIds]). */
    fun favoriteRouteIds(): Flow<Set<String>>

    /** Stars (or unstars) [routeId] wholesale (see [RouteFavoritesRepository.setFavorite]). */
    suspend fun setFavorite(
        routeId: String,
        shortName: String?,
        longName: String?,
        url: String?,
        favorite: Boolean,
    )
}

/**
 * The single owner of route-favorite membership — a wholesale `routes.favorite` bit (#1751). The
 * arrival-row star writes through [setFavorite] so the import gating, the row-existence guarantee, and
 * the bookmark analytics live in one place instead of being re-implemented (and drifting) per surface;
 * every surface that shows the star reads [favoriteRouteIds] below, so a toggle re-flags every row with
 * no re-fetch.
 */
@Singleton
class RouteFavoritesRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val routeDataSource: RouteDataSource,
    private val regionRepository: RegionRepository,
    private val importGate: ImportGate,
    private val obaAnalytics: ObaAnalytics,
    @param:AppScope private val appScope: CoroutineScope,
    @param:ApplicationContext private val context: Context,
) : RouteFavorites {

    /** The starred route ids, live: import-gated, and deduped so an unrelated `routes` write (a route
     *  recorded on an arrivals load) doesn't re-emit an identical set. */
    override fun favoriteRouteIds(): Flow<Set<String>> =
        routeDao.favoriteRouteIds()
            .onStart { importGate.awaitReady() }
            .map { it.toSet() }
            .distinctUntilChanged()

    /**
     * Stars (or unstars) [routeId] wholesale. Ensures the `routes` row exists (so the Starred Routes
     * folder can JOIN it for its display name/URL) before flipping the flag, gated on the one-time
     * import, and reports the bookmark event. Pass whatever [shortName]/[longName]/[url] the caller has
     * in hand (null/empty values leave any existing value untouched); on a **star** the full details are
     * then backfilled from the network so the folder always shows a proper long name — regardless of
     * which surface starred it (a starring surface's loaded route often carries an empty long name).
     */
    override suspend fun setFavorite(
        routeId: String,
        shortName: String?,
        longName: String?,
        url: String?,
        favorite: Boolean,
    ) {
        importGate.awaitReady()
        val regionId = regionRepository.region.value?.id
        // Ensure the row for the folder JOIN without counting a "use" — a favorite toggle must not bump
        // the recents / frequency ordering (#1727 review).
        routeDao.ensureRouteDetails(routeId, shortName, longName, url, regionId)
        routeDao.setFavorite(routeId, if (favorite) 1 else 0)
        reportBookmarkAnalytics(routeId, favorite)
        // Backfill the full details off the star action's scope, so it completes even if the user
        // navigates away immediately. Only on a star — an unstar drops the row from the folder anyway.
        if (favorite) {
            appScope.launch { backfillRouteDetails(routeId, regionId) }
        }
    }

    /**
     * Fetches the route's full details from the network and writes name/URL back, so a starred route
     * shows a proper long name in the folder even when the starring surface only had a bare short name.
     * A no-op if the fetch fails (the row keeps whatever it had). Does not count a "use".
     */
    private suspend fun backfillRouteDetails(routeId: String, regionId: Long?) {
        val route = routeDataSource.getRoute(routeId).getOrNull() ?: return
        // Resolve the display short/long names with the shared fallbacks (short→long; long→description)
        // so the folder shows a real name even when the agency ships an empty long_name (e.g. KC Metro).
        val names = routeDisplayNames(route.shortName, route.longName, route.description)
        routeDao.ensureRouteDetails(route.id, names.shortName, names.longName, route.url, regionId)
    }

    private fun reportBookmarkAnalytics(routeId: String, favorite: Boolean) {
        val event = context.getString(
            if (favorite) R.string.analytics_label_star_route else R.string.analytics_label_unstar_route
        )
        obaAnalytics.reportUiEvent(PlausibleAnalytics.REPORT_BOOKMARK_EVENT_URL, event, routeId)
    }
}
