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
@file:JvmName("RouteDisplay")

package org.onebusaway.android.util

import org.onebusaway.android.models.ObaRoute
import org.onebusaway.util.comparators.AlphanumComparator

/** A route's two display lines: the prominent short name and an optional secondary line. */
data class RouteDisplayNames(val shortName: String, val longName: String?)

/**
 * Resolves a route's display names with the same short→long→description fallbacks the legacy
 * UIUtils.setRouteView applied: the short name falls back to the long name, and the secondary
 * line is the long name (or the description when the long name is missing or equals the short
 * name). Shared by the Compose route repositories.
 */
fun routeDisplayNames(route: ObaRoute): RouteDisplayNames =
    routeDisplayNames(route.shortName, route.longName, route.description)

/** Field-based overload, for callers (e.g. the modernized io/client DTOs) without an [ObaRoute]. */
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

fun getRouteDisplayName(route: ObaRoute): String {
    return getRouteDisplayName(route.shortName, route.longName)
}

fun getRouteDescription(route: ObaRoute): String? =
    getRouteDescription(route.shortName, route.longName, route.description)

/** Field-based overload, for callers (e.g. the modernized io/client DTOs) without an [ObaRoute]. */
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

/**
 * Returns a formatted and sorted list of route display names for presentation in a single line
 *
 * For example, the following list:
 *
 * 11,1,15, 8b
 *
 * ...would be formatted as:
 *
 * 4, 8b, 11, 15
 *
 * @param routeDisplayNames          list of route display names
 * @param nextArrivalRouteShortNames the short route names of the next X arrivals at the stop
 *                                   that are the same.  These will be highlighted in the
 *                                   results.
 * @return a formatted and sorted list of route display names for presentation in a single line
 */
fun formatRouteDisplayNames(
    routeDisplayNames: List<String>,
    nextArrivalRouteShortNames: List<String>
): String = routeDisplayNames.sortedWith(AlphanumComparator()).joinToString(", ") { name ->
    // Highlight (with "*") names that match one of the next X identical arrivals.
    if (nextArrivalRouteShortNames.any { it.equals(name, ignoreCase = true) }) "$name*" else name
}
