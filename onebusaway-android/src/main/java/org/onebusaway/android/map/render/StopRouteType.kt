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
package org.onebusaway.android.map.render

import org.onebusaway.android.io.elements.ObaRoute

// Rail/subway/tram/ferry (and the cablecar/gondola/funicular family) are more visually important
// than bus at transit hubs, so a stop served by any of them takes that icon. Ported from the legacy
// StopOverlay.ROUTE_TYPE_PRIORITY.
private val ROUTE_TYPE_PRIORITY = intArrayOf(
    ObaRoute.TYPE_RAIL,
    ObaRoute.TYPE_SUBWAY,
    ObaRoute.TYPE_TRAM,
    ObaRoute.TYPE_FERRY,
    ObaRoute.TYPE_CABLECAR,
    ObaRoute.TYPE_GONDOLA,
    ObaRoute.TYPE_FUNICULAR,
)

/**
 * The primary route type for a stop, used to pick its icon: the highest-priority type among the
 * routes serving it, falling back to bus. [typeByRouteId] maps routeId to its `ObaRoute.TYPE_*`.
 */
fun primaryRouteType(routeIds: Array<String>?, typeByRouteId: Map<String, Int>): Int {
    if (routeIds.isNullOrEmpty()) {
        return ObaRoute.TYPE_BUS
    }
    val types = routeIds.mapNotNull { typeByRouteId[it] }.toSet()
    for (type in ROUTE_TYPE_PRIORITY) {
        if (type in types) {
            return type
        }
    }
    return ObaRoute.TYPE_BUS
}
