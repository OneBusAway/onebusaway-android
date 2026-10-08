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
 * The rows a stop's arrivals board shows under its route filter (#2366): every row whose route isn't
 * in [hiddenRouteIds], plus the row keyed [selectedKey] even when its route is hidden — picking a route
 * on the map is an explicit ask to see it, and an empty selection would be worse than a filter briefly
 * stepping aside. Order is kept, so starred routes stay on top within what's shown.
 *
 * Returns [groups] itself when nothing is hidden, so an unfiltered board pays nothing.
 */
internal fun visibleRouteGroups(
    groups: List<RouteRowGroup>,
    hiddenRouteIds: Set<String>,
    selectedKey: String?
): List<RouteRowGroup> {
    if (hiddenRouteIds.isEmpty()) return groups
    return groups.filter { it.routeId !in hiddenRouteIds || it.key == selectedKey }
}

/**
 * Which route-filter items a row's long-press menu offers (#2366), for route [routeId] at a stop that
 * serves [stopRouteIds] with [hiddenRouteIds] hidden.
 *
 * - **Show only this route** and **Hide this route** need another route still showing — hiding the last
 *   one would blank the board, and "only this one" would change nothing. A row whose route is itself
 *   hidden (shown only because it's selected on the map) offers "show only" regardless, as the way to
 *   bring it back on its own, and never "hide".
 * - **Show all routes** whenever anything is hidden.
 */
internal data class RouteFilterMenu(
    val showOnly: Boolean,
    val hide: Boolean,
    val showAll: Boolean
) {
    companion object {
        fun of(routeId: String, stopRouteIds: Set<String>, hiddenRouteIds: Set<String>): RouteFilterMenu {
            val isHidden = routeId in hiddenRouteIds
            val othersShowing = stopRouteIds.any { it != routeId && it !in hiddenRouteIds }
            return RouteFilterMenu(
                showOnly = isHidden || othersShowing,
                hide = !isHidden && othersShowing,
                showAll = hiddenRouteIds.isNotEmpty()
            )
        }
    }
}

/** The routes to hide at a stop serving [stopRouteIds] so that only [routeId] shows. */
internal fun hideAllRoutesExcept(routeId: String, stopRouteIds: Set<String>): Set<String> = stopRouteIds - routeId
