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
package org.onebusaway.android.ui.mylists

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.app.di.NetworkEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.provider.contentChanges
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.convertArrivals
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ReminderUtils
import org.onebusaway.android.util.getRouteDisplayName

/**
 * A My-tab list backed by the content provider: [observe] re-emits whenever the underlying table
 * changes (the legacy `ContentObserver` behavior). [remove], [clearAll], and [setSort] are optional
 * capabilities — a list overrides the ones it supports and inherits a no-op otherwise (e.g. recents
 * don't sort; reminders delete via the host, not the repository). All ContentResolver/cursor work is
 * quarantined on [Dispatchers.IO] so [MyListViewModel] stays Context-free and JVM-testable.
 */
interface MyListRepository<T> {
    fun observe(): Flow<List<T>>
    suspend fun remove(id: String) {}
    suspend fun clearAll() {}

    /** Changes the sort order (a 0-based index into the list's own sort options). */
    fun setSort(order: Int) {}
}

/** Sort-options indices (index 0 is "name" in every list's sort array). */
const val SORT_BY_NAME = 0
const val SORT_BY_FREQUENCY = 1

/** "Recently used" = accessed within the last 7 days, or used at least once; newest first, capped at 20. */
private const val RECENT_LIMIT = "20"
private val RECENT_WINDOW_MS = 7 * DateUtils.DAY_IN_MILLIS

/** The recent-list WHERE clause (the legacy "recently used" query); [cutoffMs] is the window start. */
private fun recentSelection(accessTime: String, useCount: String, cutoffMs: Long, regionWhere: String) =
    "(($accessTime IS NOT NULL AND $accessTime > $cutoffMs) OR ($useCount > 0))$regionWhere"

private fun regionWhere(context: Context, regionField: String): String {
    val region = RegionEntryPoint.get(context).region.value ?: return ""
    return " AND ($regionField=${region.id} OR $regionField IS NULL)"
}

private val STOP_PROJECTION = arrayOf(
    ObaContract.Stops._ID,
    ObaContract.Stops.UI_NAME,
    ObaContract.Stops.DIRECTION,
    ObaContract.Stops.LATITUDE,
    ObaContract.Stops.LONGITUDE,
    ObaContract.Stops.FAVORITE
)

private fun Cursor.toStopItem(context: Context): StopListItem {
    val rawDirection = getString(2)
    val directionText = rawDirection?.takeIf { it.isNotEmpty() }
        ?.let { context.getString(DisplayFormat.getStopDirectionText(it)) }
        ?.takeIf { it.isNotEmpty() }
    return StopListItem(
        id = getString(0),
        name = getString(1).orEmpty(),
        rawDirection = rawDirection,
        directionText = directionText,
        lat = getDouble(3),
        lon = getDouble(4),
        isFavorite = getInt(5) == 1
    )
}

private val ROUTE_PROJECTION = arrayOf(
    ObaContract.Routes._ID,
    ObaContract.Routes.SHORTNAME,
    ObaContract.Routes.LONGNAME,
    ObaContract.Routes.URL
)

private fun Cursor.toRouteItem() = RouteListItem(
    id = getString(0),
    shortName = getString(1).orEmpty(),
    longName = getString(2)?.takeIf { it.isNotEmpty() },
    url = getString(3)?.takeIf { it.isNotEmpty() }
)

/** Recently viewed stops, marked unused on removal/clear. */
class RecentStopsRepository(private val context: Context) : MyListRepository<StopListItem> {

    override fun observe(): Flow<List<StopListItem>> =
        context.contentChanges(ObaContract.Stops.CONTENT_URI)
            .conflate()
            .map { queryRecentStops() }
            .flowOn(Dispatchers.IO)

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ObaContract.Stops.markAsUnused(
            context, Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, id)
        )
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        ObaContract.Stops.markAsUnused(context, ObaContract.Stops.CONTENT_URI)
        Unit
    }

    private fun queryRecentStops(): List<StopListItem> {
        val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
        val selection = recentSelection(
            ObaContract.Stops.ACCESS_TIME, ObaContract.Stops.USE_COUNT, cutoff,
            regionWhere(context, ObaContract.Stops.REGION_ID)
        )
        val uri = ObaContract.Stops.CONTENT_URI.buildUpon()
            .appendQueryParameter("limit", RECENT_LIMIT).build()
        val sort = "${ObaContract.Stops.ACCESS_TIME} desc, ${ObaContract.Stops.USE_COUNT} desc"
        return context.contentResolver.query(uri, STOP_PROJECTION, selection, null, sort)
            ?.use { c -> buildList { while (c.moveToNext()) add(c.toStopItem(context)) } }
            ?: emptyList()
    }
}

/** Recently viewed routes, marked unused on removal/clear. */
class RecentRoutesRepository(private val context: Context) : MyListRepository<RouteListItem> {

    override fun observe(): Flow<List<RouteListItem>> =
        context.contentChanges(ObaContract.Routes.CONTENT_URI)
            .conflate()
            .map { queryRecentRoutes() }
            .flowOn(Dispatchers.IO)

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ObaContract.Routes.markAsUnused(
            context, Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, id)
        )
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        ObaContract.Routes.markAsUnused(context, ObaContract.Routes.CONTENT_URI)
        Unit
    }

    private fun queryRecentRoutes(): List<RouteListItem> {
        val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
        val selection = recentSelection(
            ObaContract.Routes.ACCESS_TIME, ObaContract.Routes.USE_COUNT, cutoff,
            regionWhere(context, ObaContract.Routes.REGION_ID)
        )
        val uri = ObaContract.Routes.CONTENT_URI.buildUpon()
            .appendQueryParameter("limit", RECENT_LIMIT).build()
        val sort = "${ObaContract.Routes.ACCESS_TIME} desc, ${ObaContract.Routes.USE_COUNT} desc"
        return context.contentResolver.query(uri, ROUTE_PROJECTION, selection, null, sort)
            ?.use { c -> buildList { while (c.moveToNext()) add(c.toRouteItem()) } }
            ?: emptyList()
    }
}

/** Persists the chosen sort order as the matching [optionsRes] string under [prefKeyRes] (legacy format). */
private fun saveSortOrder(context: Context, order: Int, @ArrayRes optionsRes: Int, @StringRes prefKeyRes: Int) {
    val options = context.resources.getStringArray(optionsRes)
    PreferenceUtils.saveString(context.getString(prefKeyRes), options[order.coerceIn(options.indices)])
}

/** Starred stops, sorted by name or frequency, with live next-arrivals refreshed on a 60s poll. */
class StarredStopsRepository(private val context: Context) : MyListRepository<StopListItem> {

    private val sort = MutableStateFlow(PreferenceUtils.getStopSortOrderFromPreferences())

    override fun setSort(order: Int) {
        saveSortOrder(context, order, R.array.sort_stops, R.string.preference_key_default_stop_sort)
        sort.value = order
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<StopListItem>> =
        combine(context.contentChanges(ObaContract.Stops.CONTENT_URI).conflate(), sort) { _, order ->
            queryStarredStops(order)
        }.flatMapLatest { stops ->
            // Restart the arrivals poll whenever the stop set (or sort) changes: show the stops with
            // their Loading badges first, then re-emit them with each poll's results merged in.
            if (stops.isEmpty()) {
                flowOf(stops)
            } else {
                arrivalsPoll(stops.map { it.id })
                    .map { byStop ->
                        stops.map { it.copy(arrivals = StopArrivals.Loaded(byStop[it.id].orEmpty())) }
                    }
                    .onStart { emit(stops) }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ObaContract.Stops.markAsFavorite(
            context, Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, id), false
        )
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        ObaContract.Stops.markAsFavorite(context, ObaContract.Stops.CONTENT_URI, false)
        Unit
    }

    private fun queryStarredStops(order: Int): List<StopListItem> {
        val selection = "${ObaContract.Stops.FAVORITE}=1" + regionWhere(context, ObaContract.Stops.REGION_ID)
        val sortOrder = if (order == SORT_BY_FREQUENCY) {
            "${ObaContract.Stops.USE_COUNT} desc"
        } else {
            "${ObaContract.Stops.UI_NAME} asc"
        }
        return context.contentResolver
            .query(ObaContract.Stops.CONTENT_URI, STOP_PROJECTION, selection, null, sortOrder)
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) add(c.toStopItem(context).copy(arrivals = StopArrivals.Loading))
                }
            }
            ?: emptyList()
    }

    /** Emits the per-stop arrival badges immediately, then re-emits every [ARRIVALS_REFRESH_MS]. */
    private fun arrivalsPoll(stopIds: List<String>): Flow<Map<String, List<ArrivalBadge>>> = flow {
        while (true) {
            emit(fetchArrivals(context, stopIds))
            delay(ARRIVALS_REFRESH_MS)
        }
    }
}

/** Starred routes, sorted by name or frequency. */
class StarredRoutesRepository(private val context: Context) : MyListRepository<RouteListItem> {

    private val sort = MutableStateFlow(PreferenceUtils.getStopSortOrderFromPreferences())

    override fun setSort(order: Int) {
        saveSortOrder(context, order, R.array.sort_stops, R.string.preference_key_default_stop_sort)
        sort.value = order
    }

    override fun observe(): Flow<List<RouteListItem>> =
        combine(context.contentChanges(ObaContract.Routes.CONTENT_URI).conflate(), sort) { _, order ->
            queryStarredRoutes(order)
        }.flowOn(Dispatchers.IO)

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ObaContract.Routes.markAsFavorite(
            context, Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, id), false
        )
        ObaContract.RouteHeadsignFavorites.markAsFavorite(context, id, null, null, false)
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        ObaContract.Routes.markAsFavorite(context, ObaContract.Routes.CONTENT_URI, false)
        ObaContract.RouteHeadsignFavorites.clearAllFavorites(context)
        Unit
    }

    private fun queryStarredRoutes(order: Int): List<RouteListItem> {
        val selection = "${ObaContract.Routes.FAVORITE}=1" + regionWhere(context, ObaContract.Routes.REGION_ID)
        val sortOrder = if (order == SORT_BY_FREQUENCY) {
            "${ObaContract.Routes.USE_COUNT} desc"
        } else {
            "length(${ObaContract.Routes.SHORTNAME}), ${ObaContract.Routes.SHORTNAME} asc"
        }
        return context.contentResolver
            .query(ObaContract.Routes.CONTENT_URI, ROUTE_PROJECTION, selection, null, sortOrder)
            ?.use { c -> buildList { while (c.moveToNext()) add(c.toRouteItem()) } }
            ?: emptyList()
    }
}

/** Saved trip reminders, sorted by name or departure time. Deletion is a host concern (it cancels the
 *  scheduled alarm), so this only observes + sorts; the ContentObserver reflects deletions. */
class RemindersRepository(private val context: Context) : MyListRepository<ReminderItem> {

    private val sort = MutableStateFlow(PreferenceUtils.getReminderSortOrderFromPreferences())

    override fun setSort(order: Int) {
        saveSortOrder(context, order, R.array.sort_reminders, R.string.preference_key_default_reminder_sort)
        sort.value = order
    }

    override fun observe(): Flow<List<ReminderItem>> =
        combine(context.contentChanges(ObaContract.Trips.CONTENT_URI).conflate(), sort) { _, order ->
            queryReminders(order)
        }.flowOn(Dispatchers.IO)

    private fun queryReminders(order: Int): List<ReminderItem> {
        val sortOrder = if (order == SORT_BY_NAME) {
            "${ObaContract.Trips.NAME} asc"
        } else {
            "${ObaContract.Trips.DEPARTURE} asc"
        }
        return context.contentResolver
            .query(ObaContract.Trips.CONTENT_URI, TRIP_PROJECTION, null, null, sortOrder)
            ?.use { c -> buildList { while (c.moveToNext()) add(c.toReminderItem(context)) } }
            ?: emptyList()
    }
}

private val TRIP_PROJECTION = arrayOf(
    ObaContract.Trips._ID,
    ObaContract.Trips.NAME,
    ObaContract.Trips.HEADSIGN,
    ObaContract.Trips.DEPARTURE,
    ObaContract.Trips.ROUTE_ID,
    ObaContract.Trips.STOP_ID
)

private fun Cursor.toReminderItem(context: Context): ReminderItem {
    val routeId = getString(4)
    val routeName = ReminderUtils.getRouteShortName(context, routeId)
    val departureMs = ObaContract.Trips.convertDBToTime(getInt(3))
    return ReminderItem(
        tripId = getString(0),
        stopId = getString(5),
        routeId = routeId,
        name = getString(1).orEmpty().ifEmpty { context.getString(R.string.trip_info_noname) },
        headsign = getString(2)?.takeIf { it.isNotEmpty() }?.let { MyTextUtils.formatDisplayText(it) },
        routeText = routeName?.let { context.getString(R.string.trip_info_route, it) },
        departureText = context.getString(R.string.trip_info_depart, DisplayFormat.formatTime(context, departureMs))
    )
}

// --- Starred-stops live arrivals -------------------------------------------------------------

private const val ARRIVALS_REFRESH_MS = 60_000L
private const val ARRIVALS_MINUTES_AFTER = 35
private const val MAX_ARRIVALS_PER_STOP = 3

/** Fetches each stop's next arrivals concurrently (the blocking per-stop calls fan out on IO) so the
 *  refresh latency is the slowest single request, not their sum. */
private suspend fun fetchArrivals(
    context: Context,
    stopIds: List<String>
): Map<String, List<ArrivalBadge>> = coroutineScope {
    stopIds.map { stopId -> async { stopId to fetchStopBadges(context, stopId) } }
        .awaitAll()
        .toMap()
}

/** One stop's badges. [convertArrivals] already sorts by ETA; a non-OK code/error yields no badges. */
private suspend fun fetchStopBadges(context: Context, stopId: String): List<ArrivalBadge> =
    runCatching {
        val snapshot = NetworkEntryPoint.getStopArrivals(context)
            .arrivals(stopId, ARRIVALS_MINUTES_AFTER)
            .getOrThrow()
        // Server clock as the ETA baseline so badges cancel device clock skew (#1612).
        convertArrivals(context, snapshot.arrivals, null, snapshot.currentTime, false)
            .take(MAX_ARRIVALS_PER_STOP)
            .map { it.toBadge(context) }
    }.getOrDefault(emptyList())

private fun ArrivalInfo.toBadge(context: Context): ArrivalBadge {
    val etaText = if (eta <= 0) {
        context.getString(R.string.starred_stop_arrival_now)
    } else {
        context.getString(R.string.starred_stop_arrival_min, eta.toInt())
    }
    return ArrivalBadge(
        text = context.getString(
            R.string.starred_stop_arrival_badge, getRouteDisplayName(shortName, routeLongName), etaText
        ),
        colorRes = badgeColor(color)
    )
}

@ColorRes
private fun badgeColor(@ColorRes arrivalColor: Int): Int = when (arrivalColor) {
    R.color.stop_info_ontime -> R.color.badge_ontime
    R.color.stop_info_delayed -> R.color.badge_delayed
    R.color.stop_info_early -> R.color.badge_early
    else -> R.color.badge_scheduled
}
