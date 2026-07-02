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

/** The sentinel stop id a route/headsign favorite uses to mean "favorited for every stop". */
const val ALL_STOPS_FAVORITE = "all"

/**
 * Whether a route/headsign is a favorite at [stopId], resolved in memory from the favorite/exclude
 * rows for that stop (as returned by [RouteHeadsignFavoriteDao.favoritesForStopOrAll]). This is the
 * pure port of the legacy per-row `ObaContract.RouteHeadsignFavorites.isFavorite(routeId, headsign,
 * stopId)` so a whole arrivals list resolves from one query instead of a DB round-trip per row.
 *
 * The legacy precedence: a favorite recorded for this exact stop wins; otherwise an "all stops"
 * favorite applies unless this specific stop was explicitly excluded (unstarred after starring the
 * whole route). A null [headsign] is treated as the empty string, matching the legacy query.
 */
fun computeRouteHeadsignFavorite(
    rows: List<RouteHeadsignFavoriteRecord>,
    routeId: String,
    headsign: String?,
    stopId: String,
): Boolean {
    val hs = headsign ?: ""

    val favoritedForThisStop = rows.any {
        it.routeId == routeId && it.headsign == hs && it.stopId == stopId && it.exclude == 0
    }
    if (favoritedForThisStop) return true

    val favoritedForAllStops = rows.any {
        it.routeId == routeId && it.headsign == hs && it.stopId == ALL_STOPS_FAVORITE
    }
    if (!favoritedForAllStops) return false

    val excludedAtThisStop = rows.any {
        it.routeId == routeId && it.headsign == hs && it.stopId == stopId && it.exclude == 1
    }
    return !excludedAtThisStop
}
