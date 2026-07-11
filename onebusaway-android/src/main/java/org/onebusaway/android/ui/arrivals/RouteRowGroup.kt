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

/**
 * The minimal shape the route-row grouping and ordering need from an arrival: its route, its
 * direction (headsign), and its ETA in minutes. [ArrivalInfo] implements this, but the pure
 * grouping/ordering functions below key off the interface so they stay unit-testable without a
 * `Context` (which building an [ArrivalInfo] requires).
 */
interface RouteDirectionItem {
    val routeId: String
    val headsign: String?
    val eta: Long
}

/** A group's departure sort key: the soonest *upcoming* (non-negative) ETA, or [Long.MAX_VALUE] when
 *  every trip is already in the past. Negative (recent-past) ETAs deliberately don't count toward
 *  ordering (#1707) — a row sorts by its next real arrival, not a just-departed one. */
private fun List<RouteDirectionItem>.departureSortEta(): Long =
    firstOrNull { it.eta >= 0 }?.eta ?: Long.MAX_VALUE

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

    /** Index of the soonest *upcoming* (first non-negative ETA) trip — the ETA the row is sorted by
     *  (see [departureSortEta]) — or null when every trip is recent-past. Lets a row justify that pill
     *  to the leading edge so the recent-past pills overflow before it. */
    val firstUpcomingIndex: Int? get() = trips.indexOfFirst { it.eta >= 0 }.takeIf { it >= 0 }

    val routeId: String get() = representative.routeId

    /** The direction name shown on top of the row (may be blank). */
    val headsign: String? get() = representative.headsign

    /** A stable LazyColumn key; NUL-separated so a route id and headsign can't collide across rows. */
    val key: String get() = "$routeId\u0000${headsign.orEmpty()}"
}

/** The grouping key for a (route, direction): a blank headsign and a null headsign group together. */
private fun RouteDirectionItem.groupKey(): String = "$routeId\u0000${headsign.orEmpty()}"

/**
 * Groups [items] into (route, direction) rows, then orders the rows by **departure** — each row's
 * soonest *upcoming* (non-negative) ETA, so a route whose next real arrival is far off sorts below a
 * sooner one even if it also has a just-departed trip (#1707). Items within a row keep their incoming
 * ETA order (recent-past first, then upcoming). Stable, so rows with equal departure keys keep
 * first-seen order. Generic over [RouteDirectionItem] so it's testable with lightweight fakes.
 */
fun <T : RouteDirectionItem> groupByRouteDirection(items: List<T>): List<List<T>> {
    val groups = LinkedHashMap<String, MutableList<T>>()
    for (item in items) {
        groups.getOrPut(item.groupKey()) { mutableListOf() }.add(item)
    }
    return groups.values.sortedBy { it.departureSortEta() }
}

/** Builds the departure-ordered route rows for the arrivals list (see [groupByRouteDirection]). */
fun groupArrivalsByRouteDirection(arrivals: List<ArrivalInfo>): List<RouteRowGroup> =
    groupByRouteDirection(arrivals).map { RouteRowGroup(it) }

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
 * The final display order (#1707): **starred routes first, then the rest, each in departure order.**
 * [groups] is already departure-ordered (see [groupArrivalsByRouteDirection]) and the sort is stable,
 * so this only lifts the starred rows to the top without disturbing the within-partition order. A star
 * is the wholesale `routes.favorite` bit (#1751), so it keys off the group's route id.
 */
fun orderRouteGroupsByFavorite(
    groups: List<RouteRowGroup>,
    favoriteRouteIds: Set<String>
): List<RouteRowGroup> = orderGroupsByFavorite(groups, favoriteRouteIds) { it.routeId }
