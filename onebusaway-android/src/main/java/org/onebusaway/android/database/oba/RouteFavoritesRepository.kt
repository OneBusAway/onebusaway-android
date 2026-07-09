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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.region.RegionRepository

/**
 * The single owner of route-favorite membership — a wholesale `routes.favorite` bit (#1751). Every
 * surface that stars a route (the per-arrival star, the route-map header) writes through [setFavorite]
 * so the import gating, the row-existence guarantee, and the bookmark analytics live in one place
 * instead of being re-implemented (and drifting) per surface; every surface that shows the star reads
 * one of the reactive flows below, so a toggle from any surface re-flags the others with no re-fetch.
 */
@Singleton
class RouteFavoritesRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val regionRepository: RegionRepository,
    private val importGate: ImportGate,
    private val obaAnalytics: ObaAnalytics,
    @param:ApplicationContext private val context: Context,
) {

    /** The starred route ids, live: import-gated, and deduped so an unrelated `routes` write (a route
     *  recorded on an arrivals load) doesn't re-emit an identical set. */
    fun favoriteRouteIds(): Flow<Set<String>> =
        routeDao.favoriteRouteIds()
            .onStart { importGate.awaitReady() }
            .map { it.toSet() }
            .distinctUntilChanged()

    /** Whether [routeId] is starred, live — deduped against unrelated `routes` writes. */
    fun isFavorite(routeId: String): Flow<Boolean> =
        routeDao.isFavorite(routeId).distinctUntilChanged()

    /**
     * Stars (or unstars) [routeId] wholesale. Ensures the `routes` row exists (so the Starred Routes
     * folder can JOIN it for its display name/URL) before flipping the flag, gated on the one-time
     * import, and reports the bookmark event. Pass [url] when the caller already has the loaded route
     * (it's stored); pass null to leave any existing URL untouched (the arrivals path backfills the
     * full details from the network afterward).
     */
    suspend fun setFavorite(
        routeId: String,
        shortName: String?,
        longName: String?,
        url: String?,
        favorite: Boolean,
    ) {
        importGate.awaitReady()
        val regionId = regionRepository.region.value?.id
        // Ensure the row for the folder JOIN without counting a "use" — a favorite toggle must not bump
        // the recents / frequency ordering (#1727 review). Passing url=null preserves any existing URL.
        routeDao.ensureRouteDetails(routeId, shortName, longName, url, regionId)
        routeDao.setFavorite(routeId, if (favorite) 1 else 0)
        reportBookmarkAnalytics(routeId, favorite)
    }

    private fun reportBookmarkAnalytics(routeId: String, favorite: Boolean) {
        val event = context.getString(
            if (favorite) R.string.analytics_label_star_route else R.string.analytics_label_unstar_route
        )
        obaAnalytics.reportUiEvent(PlausibleAnalytics.REPORT_BOOKMARK_EVENT_URL, event, routeId)
    }
}
