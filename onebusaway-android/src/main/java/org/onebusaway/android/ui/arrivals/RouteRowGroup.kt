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
package org.onebusaway.android.ui.arrivals

import org.onebusaway.util.comparators.AlphanumComparator

/**
 * The minimal shape the route-row grouping and ordering need from an arrival: its route, its
 * direction (headsign), its ETA in minutes, and its natural-sort line name (#1822). [ArrivalInfo]
 * implements this, but the pure grouping/ordering functions below key off the interface so they stay
 * unit-testable without a `Context` (which building an [ArrivalInfo] requires).
 */
interface RouteDirectionItem {
    val routeId: String
    val headsign: String?
    val eta: Long

    /** The stable line-name sort/display key (#1822) — e.g. "8", "40", "A Line" — resolved by the
     *  implementer to a never-blank value (see [ArrivalInfo.lineName]'s fallback chain), so a blank
     *  short name can't collapse every unnamed route into one comparator bucket. Unlike [headsign] or
     *  agency (the latter needs an external accessor — see [groupByRouteDirection]), the line name is
     *  intrinsic to the item and needs no outside lookup. */
    val lineName: String
}

/** Numeric-aware ("natural") comparator for [RouteDirectionItem.lineName] — "8" sorts before "40"
 *  before "550" — the same comparator [org.onebusaway.android.util.RouteDisplay] already uses for
 *  route short names, reused here rather than re-implementing natural sort. */
private val LINE_NAME_COMPARATOR = AlphanumComparator()

/** Case-insensitive, blank/null-last comparator shared by the agency and headsign sort fields below —
 *  unnamed/unknown data shouldn't get to dominate the top of the list. */
private val BLANK_LAST_COMPARATOR: Comparator<String?> = nullsLast(String.CASE_INSENSITIVE_ORDER)

/** Orders groups by (agency, line, headsign) (#1822): agency (from [agencyNameOf]) and headsign
 *  compare via [BLANK_LAST_COMPARATOR]; line name uses [LINE_NAME_COMPARATOR]. The headsign tiebreak
 *  makes a route serving two directions at the stop deterministic regardless of incoming order. Reads
 *  each group's representative (first) item once per comparison. */
private fun <T : RouteDirectionItem> routeSortComparator(
    agencyNameOf: (T) -> String?
): Comparator<List<T>> =
    compareBy<List<T>, String?>(BLANK_LAST_COMPARATOR) {
        agencyNameOf(it.first())?.takeIf(String::isNotBlank)
    }.thenBy(LINE_NAME_COMPARATOR) { it.first().lineName }
        .thenBy(BLANK_LAST_COMPARATOR) { it.first().headsign?.takeIf(String::isNotBlank) }

/**
 * One arrivals row: all upcoming trips for a single (route, direction) at the stop, ETA-sorted
 * (recent-past trips first, then upcoming). Replaces the per-trip row — a route serving two
 * directions at the stop produces two groups.
 *
 * [trips] is non-empty and ETA-sorted (its input is already ordered by
 * [org.onebusaway.android.util.ArrivalInfoUtils.InfoComparator]).
 */
data class RouteRowGroup(val trips: List<ArrivalInfo>) {
    init {
        require(trips.isNotEmpty()) { "RouteRowGroup must hold at least one trip" }
    }

    /** The soonest trip; source of the row's route badge, headsign, and route-level actions/color. */
    val representative: ArrivalInfo get() = trips.first()

    /** Index of the soonest *upcoming* (first non-negative ETA) trip within [trips], or null when every
     *  trip is recent-past. Lets a row justify that pill to the leading edge so the recent-past pills
     *  overflow before it. */
    val firstUpcomingIndex: Int? get() = trips.indexOfFirst { it.eta >= 0 }.takeIf { it >= 0 }

    val routeId: String get() = representative.routeId

    /** The first active service-alert situation id affecting *any* trip in the group (scanned in ETA
     *  order, representative first), or null when none is affected — so a row flags an alert whenever
     *  any of its grouped trips is, not just the soonest. [actionsFor] supplies each trip's resolved
     *  [ArrivalActions] (the group itself holds only [ArrivalInfo]), mirroring how the row looks up
     *  per-trip actions elsewhere. */
    fun activeAlertSituationId(actionsFor: (ArrivalInfo) -> ArrivalActions?): String? =
        trips.firstNotNullOfOrNull { actionsFor(it)?.alertSituationId }

    /** The direction name shown on top of the row (may be blank). */
    val headsign: String? get() = representative.headsign

    /** A stable LazyColumn key; NUL-separated so a route id and headsign can't collide across rows. */
    val key: String get() = routeRowKey(routeId, headsign)
}

/** Stable identity shared by grouping, LazyColumn, and the home drawer's reactive row selection. */
internal fun routeRowKey(routeId: String, headsign: String?): String =
    "$routeId\u0000${headsign.orEmpty()}"

/** The grouping key for a (route, direction): a blank headsign and a null headsign group together. */
private fun RouteDirectionItem.groupKey(): String = routeRowKey(routeId, headsign)

/**
 * Groups [items] into (route, direction) rows, then orders the rows by the **stable** (agency, line,
 * headsign) key (#1822) — not by ETA, so a row's position doesn't drift as arrivals tick down or a
 * poll refreshes; only the *within-row* trip order stays ETA-driven (unchanged, see
 * [RouteRowGroup.trips]). [agencyNameOf] resolves a group's agency name from its representative
 * (first) item — the one part of the key not intrinsic to [RouteDirectionItem] (see
 * [groupArrivalsByRouteDirection]'s caller for why). Stable, so groups with an equal key keep
 * first-seen order. Generic over [RouteDirectionItem] so it's testable with lightweight fakes.
 */
fun <T : RouteDirectionItem> groupByRouteDirection(
    items: List<T>,
    agencyNameOf: (T) -> String?
): List<List<T>> {
    val groups = LinkedHashMap<String, MutableList<T>>()
    for (item in items) {
        groups.getOrPut(item.groupKey()) { mutableListOf() }.add(item)
    }
    return groups.values.sortedWith(routeSortComparator(agencyNameOf))
}

/**
 * Builds the (agency, line, headsign)-ordered route rows for the arrivals list (#1822; see
 * [groupByRouteDirection]). [agencyNameOf] resolves an arrival's agency display name — not carried on
 * [ArrivalInfo] itself, only resolvable from the loaded snapshot's route/agency refs (see
 * `ArrivalsRepository.toData`, the sole production caller) — null/blank sorts the row last.
 */
fun groupArrivalsByRouteDirection(
    arrivals: List<ArrivalInfo>,
    agencyNameOf: (ArrivalInfo) -> String?
): List<RouteRowGroup> =
    groupByRouteDirection(arrivals, agencyNameOf).map { RouteRowGroup(it) }

/**
 * Stable favorite-first ordering: items whose [routeIdOf] is in [favoriteRouteIds] move to the top,
 * each partition keeping its incoming order. Generic (over any `T` + a route-id selector) so it's
 * unit-testable with lightweight fakes, mirroring [groupByRouteDirection].
 */
fun <T> orderGroupsByFavorite(
    groups: List<T>,
    favoriteRouteIds: Set<String>,
    routeIdOf: (T) -> String
): List<T> = groups.sortedByDescending { routeIdOf(it) in favoriteRouteIds }

/**
 * The final display order (#1707, #1822): **starred routes first, then the rest, each in
 * (agency, line, headsign) order.** [groups] is already sorted that way (see
 * [groupArrivalsByRouteDirection]) and the sort is stable, so this only lifts the starred rows to the
 * top without disturbing the within-partition order. A star is the wholesale `routes.favorite` bit
 * (#1751), so it keys off the group's route id.
 */
fun orderRouteGroupsByFavorite(
    groups: List<RouteRowGroup>,
    favoriteRouteIds: Set<String>
): List<RouteRowGroup> = orderGroupsByFavorite(groups, favoriteRouteIds) { it.routeId }

/**
 * Reactively promotes [selectedKey] without changing [groups]. Clearing selection returns the original
 * list instance, restoring its favorite/agency/line/direction order without maintaining a second order.
 */
internal fun promoteSelectedRouteGroup(
    groups: List<RouteRowGroup>,
    selectedKey: String?,
): List<RouteRowGroup> {
    val index = groups.indexOfFirst { it.key == selectedKey }
    if (index <= 0) return groups
    return buildList(groups.size) {
        add(groups[index])
        addAll(groups.subList(0, index))
        addAll(groups.subList(index + 1, groups.size))
    }
}
