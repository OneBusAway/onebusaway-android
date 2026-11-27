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

import org.onebusaway.android.ui.arrivals.components.RouteFilterActions

/**
 * The rows a stop's arrivals board shows under its route filter (#2366): every row whose route isn't
 * in [hiddenRouteIds], plus the row keyed [selectedKey] even when its route is hidden — picking a route
 * on the map is an explicit ask to see it, and an empty selection would be worse than a filter briefly
 * stepping aside. Order is kept, so starred routes stay on top within what's shown.
 */
internal fun visibleRouteGroups(
    groups: List<RouteRowGroup>,
    hiddenRouteIds: Set<String>,
    selectedKey: String?
): List<RouteRowGroup> = groups.filter { it.routeId !in hiddenRouteIds || it.key == selectedKey }

/**
 * [routeId]'s route-filter menu items (#2366) at a stop that serves [stopRouteIds] with [hiddenRouteIds]
 * hidden, bound to these callbacks. An item that wouldn't help is left null, so the menu doesn't offer it:
 *
 * - **Show only this route** and **Hide this route** need another route still showing — hiding the last
 *   one would blank the board, and "only this one" would change nothing. A row whose route is itself
 *   hidden (shown only because it's selected on the map) offers "show only" regardless, as the way to
 *   bring it back on its own, and never "hide".
 * - **Show all routes** whenever anything is hidden.
 */
internal fun RouteFilterCallbacks.forRoute(
    routeId: String,
    stopRouteIds: Set<String>,
    hiddenRouteIds: Set<String>
): RouteFilterActions {
    val isHidden = routeId in hiddenRouteIds
    val othersShowing = stopRouteIds.any { it != routeId && it !in hiddenRouteIds }
    return RouteFilterActions(
        onShowOnly = { onShowOnly(routeId) }.takeIf { isHidden || othersShowing },
        onHide = { onHide(routeId) }.takeIf { !isHidden && othersShowing },
        onShowAll = onShowAll.takeIf { hiddenRouteIds.isNotEmpty() }
    )
}
