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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.onebusaway.android.R
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.app.di.NetworkEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.database.oba.ReminderRow
import org.onebusaway.android.database.oba.RouteListRow
import org.onebusaway.android.database.oba.RouteRecentRow
import org.onebusaway.android.database.oba.StopListRow
import org.onebusaway.android.database.oba.StopRecentRow
import org.onebusaway.android.database.oba.TripDepartureTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.convertArrivals
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.getRouteDisplayName
import org.onebusaway.android.util.runCatchingCancellable

/**
 * A My-tab list backed by the unified Room database: [observe] re-emits whenever the underlying table
 * changes (Room's per-table invalidation, replacing the legacy `ContentObserver`). [remove],
 * [clearAll], and [setSort] are optional capabilities — a list overrides the ones it supports and
 * inherits a no-op otherwise (e.g. recents don't sort; reminders delete via the host, not the
 * repository). DAOs are resolved from a [DatabaseEntryPoint] because these repositories are hand-built
 * from a [Context] at the Compose call site; all work stays on [Dispatchers.IO] so [MyListViewModel]
 * stays Context-free and JVM-testable.
 *
 * The recent/starred lists scope to the active region reactively (via [RegionEntryPoint]'s region
 * flow), so switching regions refreshes the lists immediately — the legacy lists only refreshed on the
 * next stop/route write.
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
private val RECENT_WINDOW_MS = 7 * DateUtils.DAY_IN_MILLIS

// Device-clock recent-window cutoff, compared against locally-stamped access times (same clock).
@Suppress("RawClockArithmetic")
internal fun recentCutoff(): Long = System.currentTimeMillis() - RECENT_WINDOW_MS

private fun StopListRow.toStopItem(context: Context): StopListItem {
    val directionText = DisplayFormat.stopDirectionText(context, direction)
    return StopListItem(
        id = id,
        name = uiName.orEmpty(),
        rawDirection = direction,
        directionText = directionText,
        lat = latitude,
        lon = longitude,
        isFavorite = favorite == 1
    )
}

private fun RouteListRow.toRouteItem() = RouteListItem(
    id = id,
    shortName = shortName,
    longName = longName?.takeIf { it.isNotEmpty() },
    url = url?.takeIf { it.isNotEmpty() }
)

// The recent-row variants embed the list row + access_time, so reuse the exact list-row mappers on the
// embedded [row] and keep the display formatting in one place.
internal fun StopRecentRow.toRecentItem(context: Context) = RecentItem.Stop(row.toStopItem(context), accessTime)

internal fun RouteRecentRow.toRecentItem() = RecentItem.Route(row.toRouteItem(), accessTime)

/** How many recents the search dropdown holds; ~4 are visible, the rest scroll (matches the list caps). */
private const val RECENTS_LIMIT = 20

/** Merge recent stops and routes into one newest-first list, capped at [limit]. Pure; unit-tested. */
internal fun mergeRecents(
    stops: List<RecentItem>,
    routes: List<RecentItem>,
    limit: Int
): List<RecentItem> = (stops + routes)
    .sortedByDescending { it.accessTime ?: Long.MIN_VALUE }
    .take(limit)

/**
 * Filter [items] to those whose stop name / route short-or-long name contains [query] (case-insensitive).
 * A blank query passes the list through unchanged. Pure; unit-tested.
 */
internal fun filterRecents(items: List<RecentItem>, query: String): List<RecentItem> {
    val q = query.trim()
    if (q.isEmpty()) return items
    return items.filter { item ->
        when (item) {
            is RecentItem.Stop -> item.stop.name.contains(q, ignoreCase = true)
            is RecentItem.Route ->
                item.route.shortName.contains(q, ignoreCase = true) ||
                    item.route.longName?.contains(q, ignoreCase = true) == true
        }
    }
}

/** The unified recent stops+routes list behind the search-box dropdown; read-only (no remove/clear). */
class SearchRecentsRepository(private val context: Context) : MyListRepository<RecentItem> {

    private val entryPoint = DatabaseEntryPoint.get(context)
    private val stopDao = entryPoint.stopDao()
    private val routeDao = entryPoint.routeDao()
    private val region = RegionEntryPoint.get(context).region

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<RecentItem>> = region.flatMapLatest { r ->
        val cutoff = recentCutoff()
        combine(
            stopDao.recentsForSearch(cutoff, r?.id),
            routeDao.recentsForSearch(cutoff, r?.id)
        ) { stops, routes ->
            mergeRecents(
                stops.map { it.toRecentItem(context) },
                routes.map { it.toRecentItem() },
                RECENTS_LIMIT
            )
        }
    }.flowOn(Dispatchers.IO)
}

/** Recently viewed stops, marked unused on removal/clear. */
class RecentStopsRepository(private val context: Context) : MyListRepository<StopListItem> {

    private val entryPoint = DatabaseEntryPoint.get(context)
    private val stopDao = entryPoint.stopDao()
    private val importGate = entryPoint.importGate()
    private val region = RegionEntryPoint.get(context).region

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<StopListItem>> = region.flatMapLatest { r -> stopDao.recents(recentCutoff(), r?.id) }
        .map { rows -> rows.map { it.toStopItem(context) } }
        .flowOn(Dispatchers.IO)

    // Gate the writes: a remove/clear racing the one-time importer's clear-then-insert would otherwise
    // be silently wiped. Reads self-heal via Room invalidation once the import commits, so they don't.
    override suspend fun remove(id: String) {
        importGate.awaitReady()
        stopDao.markUnused(id)
    }

    override suspend fun clearAll() {
        importGate.awaitReady()
        stopDao.markAllUnused()
    }
}

/** Recently viewed routes, marked unused on removal/clear. */
class RecentRoutesRepository(private val context: Context) : MyListRepository<RouteListItem> {

    private val entryPoint = DatabaseEntryPoint.get(context)
    private val routeDao = entryPoint.routeDao()
    private val importGate = entryPoint.importGate()
    private val region = RegionEntryPoint.get(context).region

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<RouteListItem>> = region.flatMapLatest { r -> routeDao.recents(recentCutoff(), r?.id) }
        .map { rows -> rows.map { it.toRouteItem() } }
        .flowOn(Dispatchers.IO)

    override suspend fun remove(id: String) {
        importGate.awaitReady()
        routeDao.markUnused(id)
    }

    override suspend fun clearAll() {
        importGate.awaitReady()
        routeDao.markAllUnused()
    }
}

/** Persists the chosen sort order as the matching [optionsRes] string under [prefKeyRes] (legacy format). */
private fun saveSortOrder(context: Context, order: Int, @ArrayRes optionsRes: Int, @StringRes prefKeyRes: Int) {
    val options = context.resources.getStringArray(optionsRes)
    PreferenceUtils.saveString(context.getString(prefKeyRes), options[order.coerceIn(options.indices)])
}

/** Starred stops, sorted by name or frequency, with live next-arrivals refreshed on a 60s poll. */
class StarredStopsRepository(private val context: Context) : MyListRepository<StopListItem> {

    private val entryPoint = DatabaseEntryPoint.get(context)
    private val stopDao = entryPoint.stopDao()
    private val importGate = entryPoint.importGate()
    private val region = RegionEntryPoint.get(context).region
    private val sort = MutableStateFlow(PreferenceUtils.getStopSortOrderFromPreferences(context))

    override fun setSort(order: Int) {
        saveSortOrder(context, order, R.array.sort_stops, R.string.preference_key_default_stop_sort)
        sort.value = order
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<StopListItem>> = combine(region, sort) { r, order -> r?.id to order }
        .flatMapLatest { (regionId, order) ->
            val rows = if (order == SORT_BY_FREQUENCY) {
                stopDao.starredByFrequency(regionId)
            } else {
                stopDao.starredByName(regionId)
            }
            rows.map { list -> list.map { it.toStopItem(context).copy(arrivals = StopArrivals.Loading) } }
        }
        .flatMapLatest { stops ->
            // Restart the arrivals poll whenever the stop set (or sort) changes: show the stops with
            // their Loading badges first, then re-emit them with each poll's results merged in.
            if (stops.isEmpty()) {
                flowOf(stops)
            } else {
                arrivalsPoll(context, stops.map { it.id })
                    .map { byStop ->
                        stops.map { it.copy(arrivals = StopArrivals.Loaded(byStop[it.id].orEmpty())) }
                    }
                    .onStart { emit(stops) }
            }
        }
        .flowOn(Dispatchers.IO)

    override suspend fun remove(id: String) {
        importGate.awaitReady()
        stopDao.setFavorite(id, 0)
    }

    override suspend fun clearAll() {
        importGate.awaitReady()
        stopDao.clearAllFavorites()
    }
}

/** Starred routes, sorted by name or frequency. */
class StarredRoutesRepository(private val context: Context) : MyListRepository<RouteListItem> {

    private val entryPoint = DatabaseEntryPoint.get(context)
    private val routeDao = entryPoint.routeDao()
    private val importGate = entryPoint.importGate()
    private val region = RegionEntryPoint.get(context).region
    private val sort = MutableStateFlow(PreferenceUtils.getStopSortOrderFromPreferences(context))

    override fun setSort(order: Int) {
        saveSortOrder(context, order, R.array.sort_stops, R.string.preference_key_default_stop_sort)
        sort.value = order
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<RouteListItem>> = combine(region, sort) { r, order -> r?.id to order }
        .flatMapLatest { (regionId, order) ->
            if (order == SORT_BY_FREQUENCY) {
                routeDao.starredByFrequency(regionId)
            } else {
                routeDao.starredByName(regionId)
            }
        }
        .map { rows -> rows.map { it.toRouteItem() } }
        .flowOn(Dispatchers.IO)

    // Unstarring a route just clears its single favorite bit (#1751).
    override suspend fun remove(id: String) {
        importGate.awaitReady()
        routeDao.setFavorite(id, 0)
    }

    override suspend fun clearAll() {
        importGate.awaitReady()
        routeDao.clearAllFavorites()
    }
}

/** Saved trip reminders, sorted by name or departure time. Deletion is a host concern (it cancels the
 *  scheduled alarm), so this only observes + sorts; Room reflects deletions automatically. */
class RemindersRepository(private val context: Context) : MyListRepository<ReminderItem> {

    private val tripDao = DatabaseEntryPoint.get(context).tripDao()
    private val sort = MutableStateFlow(PreferenceUtils.getReminderSortOrderFromPreferences(context))

    override fun setSort(order: Int) {
        saveSortOrder(context, order, R.array.sort_reminders, R.string.preference_key_default_reminder_sort)
        sort.value = order
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<ReminderItem>> = sort.flatMapLatest { order ->
        if (order == SORT_BY_NAME) tripDao.remindersByName() else tripDao.remindersByDeparture()
    }
        .map { rows -> rows.map { it.toReminderItem(context) } }
        .flowOn(Dispatchers.IO)
}

private fun ReminderRow.toReminderItem(context: Context): ReminderItem {
    val departureMs = TripDepartureTime.toEpochMillis(departure)
    return ReminderItem(
        tripId = tripId,
        stopId = stopId,
        routeId = routeId.orEmpty(),
        name = name.orEmpty().ifEmpty { context.getString(R.string.trip_info_noname) },
        headsign = headsign?.takeIf { it.isNotEmpty() }?.let { MyTextUtils.formatDisplayText(it) },
        routeText = routeShortName?.let { context.getString(R.string.trip_info_route, it) },
        departureText = context.getString(R.string.trip_info_depart, DisplayFormat.formatTime(context, departureMs))
    )
}

// --- Starred-stops live arrivals -------------------------------------------------------------

private const val ARRIVALS_REFRESH_MS = 60_000L
private const val ARRIVALS_MINUTES_AFTER = 35
private const val MAX_ARRIVALS_PER_STOP = 3

/** Emits the per-stop arrival badges immediately, then re-emits every [ARRIVALS_REFRESH_MS]. */
private fun arrivalsPoll(context: Context, stopIds: List<String>): Flow<Map<String, List<ArrivalBadge>>> = flow {
    while (true) {
        emit(fetchArrivals(context, stopIds))
        delay(ARRIVALS_REFRESH_MS)
    }
}

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
private suspend fun fetchStopBadges(context: Context, stopId: String): List<ArrivalBadge> = runCatchingCancellable {
    val snapshot = NetworkEntryPoint.getStopArrivals(context)
        .arrivals(stopId, ARRIVALS_MINUTES_AFTER)
        .getOrThrow()
    // Server clock as the ETA baseline so badges cancel device clock skew (#1612). These badge
    // rows don't render the favorite star.
    convertArrivals(context, snapshot.arrivals, ServerTime(snapshot.currentTime), false)
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
            R.string.starred_stop_arrival_badge,
            getRouteDisplayName(shortName, routeLongName),
            etaText
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
