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
package org.onebusaway.android.util

import org.onebusaway.android.models.ObaRoute
import org.onebusaway.util.comparators.AlphanumComparator

/** A route's two display lines: the prominent short name and an optional secondary line. */
data class RouteDisplayNames(val shortName: String, val longName: String?)

/**
 * The app's one route-name order: numeric-aware ("natural"), so "8" sorts before "40" before "550"
 * rather than lexicographically. Everywhere a list of route names is shown in name order — the
 * arrivals rows, a trip leg's interchangeable routes (#2010) — sorts with this, so the same set of
 * routes reads the same way wherever it appears.
 */
val ROUTE_NAME_ORDER: Comparator<String> = AlphanumComparator()

/**
 * The routes of one interchangeable ride (#2010) as it is read: deduplicated by [name] and in
 * [ROUTE_NAME_ORDER], the plan's own choice given no pride of place — "1 Line/2 Line" reads the same
 * whichever of them the trip planner picked, and which one it did pick is still on the option card's
 * header line and its ETA strip.
 *
 * Shared by the places routes are *badged together* — the drawer/picker's joined chip (`legBadge`), the
 * label on a ride's line on the map (`itineraryRouteBadges`, #2083), and the routes a stop marker names at
 * transit-centre zoom (#2107) — so a rider looking at two at once can't be shown one set of routes named in
 * two orders. The dedupe is a badge's rule wherever it applies: a badge has only the name to show, so two
 * routes sharing one (the feeds do collide) would draw as the same row twice. It is deliberately not what
 * [org.onebusaway.android.directions.model.interchangeableRoutes] applies when it produces the candidates:
 * that list is also what the map's route focus loads (#2063), so it sorts by the same order but keeps every
 * route, name collisions included. Deduplication is a badge's rule, not the plan's.
 */
fun <T> List<T>.inInterchangeableOrder(name: (T) -> String): List<T> = distinctBy(name).sortedWith(compareBy(ROUTE_NAME_ORDER, name))

/**
 * Resolves a route's display names with the same short→long→description fallbacks the legacy
 * UIUtils.setRouteView applied: the short name falls back to the long name, and the secondary
 * line is the long name (or the description when the long name is missing or equals the short
 * name). Shared by the Compose route repositories.
 */
fun routeDisplayNames(route: ObaRoute): RouteDisplayNames = routeDisplayNames(route.shortName, route.longName, route.description)

/** Field-based overload, for callers (e.g. the modernized api/ DTOs) without an [ObaRoute]. */
fun routeDisplayNames(
    shortName: String?,
    longName: String?,
    description: String?
): RouteDisplayNames = RouteDisplayNames(
    shortName = MyTextUtils.formatDisplayText(getRouteDisplayName(shortName, longName)).orEmpty(),
    longName = getRouteDescription(shortName, longName, description)?.takeIf { it.isNotEmpty() }
)

fun getRouteDisplayName(routeShortName: String?, routeLongName: String?): String {
    if (!routeShortName.isNullOrEmpty()) {
        return routeShortName
    }
    if (!routeLongName.isNullOrEmpty()) {
        return routeLongName
    }
    // Just so we never return null.
    return ""
}

fun getRouteDisplayName(route: ObaRoute): String = getRouteDisplayName(route.shortName, route.longName)

fun getRouteDescription(route: ObaRoute): String? = getRouteDescription(route.shortName, route.longName, route.description)

/** Field-based overload, for callers (e.g. the modernized api/ DTOs) without an [ObaRoute]. */
fun getRouteDescription(shortName: String?, longName: String?, description: String?): String? {
    var resolvedShort = shortName
    var resolvedLong = longName

    if (resolvedShort.isNullOrEmpty()) {
        resolvedShort = resolvedLong
    }
    if (resolvedLong.isNullOrEmpty() || resolvedShort == resolvedLong) {
        resolvedLong = description
    }
    return MyTextUtils.formatDisplayText(resolvedLong)
}
