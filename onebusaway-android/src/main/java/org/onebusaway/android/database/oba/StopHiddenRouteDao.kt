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

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One route the rider has hidden from one stop's arrivals board (#2366) — the per-stop route filter
 * that #1815 retired, restored.
 *
 * Stored as the routes **hidden**, not (as the retired `stop_routes_filter` did) the routes shown. The
 * difference is what happens to a route the rider never chose about — one that starts serving the stop
 * later, or that wasn't in the stop's route list when they filtered: an allow-list hides it silently,
 * which reads as missing service; a hide-list shows it. "Show only this route" is therefore written as
 * "hide every other route the stop serves right now".
 */
@Entity(tableName = "stop_hidden_routes", primaryKeys = ["stop_id", "route_id"])
data class StopHiddenRouteRecord(
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "route_id") val routeId: String
)

/**
 * One row of a rider's **old** route filter, carried over from a release that still had it (#2366) and
 * waiting to be converted into [StopHiddenRouteRecord]s.
 *
 * The old filter (the legacy ContentProvider's `stop_routes_filter`, removed in #1815) was an
 * allow-list: the routes to *show* at a stop. Turning that into the routes to hide needs the routes the
 * stop serves, which only an arrivals response knows — not the importer, which runs before any network.
 * So the import parks the allow-list here as it was, and the first load of the stop converts it
 * ([StopHiddenRouteDao.adoptLegacyFilter]) and deletes it. Only a rider updating straight from a release
 * with the filter (26.1.x or older) still has one to carry: the import deletes the legacy file, so the
 * releases in between lost theirs for good.
 */
@Entity(tableName = "legacy_stop_route_filters", primaryKeys = ["stop_id", "route_id"])
data class LegacyStopRouteFilterRecord(
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "route_id") val routeId: String
)

/**
 * The routes to hide at a stop whose old allow-list filter showed only [shownRouteIds], given the
 * routes it serves now ([servedRouteIds]): every served route the rider didn't choose to show.
 *
 * Empty when no route the filter showed is still served. That filter would hide the stop's entire
 * service, which no rider chose — it is what an allow-list decays into once its routes are renumbered
 * or withdrawn — so it is dropped rather than carried into a blank board.
 */
internal fun hiddenRoutesFromLegacyFilter(
    shownRouteIds: Set<String>,
    servedRouteIds: Set<String>
): Set<String> = if (shownRouteIds.none { it in servedRouteIds }) emptySet() else servedRouteIds - shownRouteIds

@Dao
interface StopHiddenRouteDao {

    /** The route ids hidden at [stopId], re-emitting on every change to the table. */
    @Query("SELECT route_id FROM stop_hidden_routes WHERE stop_id = :stopId")
    fun observeHiddenRouteIds(stopId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<StopHiddenRouteRecord>)

    @Query("DELETE FROM stop_hidden_routes WHERE stop_id = :stopId")
    suspend fun clear(stopId: String)

    /** Makes [routeIds] exactly the routes hidden at [stopId]; an empty set shows every route. */
    @Transaction
    suspend fun replace(stopId: String, routeIds: Set<String>) {
        clear(stopId)
        insert(routeIds.map { StopHiddenRouteRecord(stopId, it) })
    }

    @Query("SELECT route_id FROM legacy_stop_route_filters WHERE stop_id = :stopId")
    suspend fun legacyShownRouteIds(stopId: String): List<String>

    @Query("DELETE FROM legacy_stop_route_filters WHERE stop_id = :stopId")
    suspend fun clearLegacy(stopId: String)

    /**
     * Converts [stopId]'s carried-over allow-list filter, if it has one, into the routes it hides — see
     * [LegacyStopRouteFilterRecord] — now that a load has told us the routes the stop serves
     * ([servedRouteIds]). The old filter replaces whatever is hidden at the stop, and is then deleted,
     * so this happens once per stop.
     */
    @Transaction
    suspend fun adoptLegacyFilter(stopId: String, servedRouteIds: Set<String>) {
        val shown = legacyShownRouteIds(stopId).toSet()
        if (shown.isEmpty()) return
        replace(stopId, hiddenRoutesFromLegacyFilter(shown, servedRouteIds))
        clearLegacy(stopId)
    }
}
