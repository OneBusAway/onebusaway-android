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

/**
 * Applies (or clears) a route/headsign/stop favorite and reconciles the route's overall favorite flag,
 * faithfully reproducing the legacy `ObaContract.RouteHeadsignFavorites.markAsFavorite` semantics:
 * favoriting inserts a `(route, headsign, stop|all, exclude=0)` row and stars the route; unfavoriting
 * deletes the matching rows, clears the route star when no non-excluded favorites remain, and — when a
 * single stop is unstarred while the whole route is starred — records an `exclude=1` row. A null
 * [stopId] means "all stops".
 *
 * The reconciliation is inherently DB-semantic (delete, then re-query `routeHasFavorite` /
 * `favoritesForStopOrAll`, then conditionally insert), so it's hoisted here as a self-contained,
 * DAO-only unit — no Context/analytics/import gating — that an in-memory Room test can drive directly
 * (see `RouteHeadsignFavoriteWriterTest`). [DefaultArrivalsRepository] calls this, then reports analytics.
 */
suspend fun applyRouteHeadsignFavorite(
    headsignDao: RouteHeadsignFavoriteDao,
    routeDao: RouteDao,
    routeId: String,
    headsign: String?,
    stopId: String?,
    favorite: Boolean,
) {
    val stopIdInternal = stopId ?: ALL_STOPS_FAVORITE
    if (favorite) {
        if (stopIdInternal != ALL_STOPS_FAVORITE) {
            headsignDao.deleteMatch(routeId, headsign, stopIdInternal)
        }
        headsignDao.insert(
            RouteHeadsignFavoriteRecord(
                routeId = routeId, headsign = headsign.orEmpty(), stopId = stopIdInternal, exclude = 0
            )
        )
        routeDao.setFavorite(routeId, 1)
    } else {
        headsignDao.deleteMatch(routeId, headsign, stopIdInternal)
        if (stopIdInternal == ALL_STOPS_FAVORITE) {
            headsignDao.deleteForRoute(routeId, headsign)
        }
        if (!headsignDao.routeHasFavorite(routeId)) {
            routeDao.setFavorite(routeId, 0)
        }
        // A single stop unstarred while the whole route is starred -> record an exclusion. Resolve the
        // (post-delete) favorite state through the same pure helper the read path uses.
        val stillFavorite = stopId != null && computeRouteHeadsignFavorite(
            headsignDao.favoritesForStopOrAll(stopId), routeId, headsign, stopId
        )
        if (stillFavorite) {
            headsignDao.insert(
                RouteHeadsignFavoriteRecord(
                    routeId = routeId, headsign = headsign.orEmpty(), stopId = stopIdInternal, exclude = 1
                )
            )
        }
    }
}
