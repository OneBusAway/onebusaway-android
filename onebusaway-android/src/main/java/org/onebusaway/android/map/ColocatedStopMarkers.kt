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
package org.onebusaway.android.map

import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.models.boardingPoint
import org.onebusaway.android.util.inInterchangeableOrder

/** Merge the drawing only; original feed IDs stay in the controller's cache and arrival actions. */
internal fun mergeColocatedStopMarkers(stops: Collection<StopMarker>, focusedStopId: String?): List<StopMarker> = stops.groupBy { marker ->
    // Route presentations can project stops onto different paths. Only combine coincident drawings
    // of the same boarding point, never unrelated stops that happen to project onto the same point.
    marker.stop.boardingPoint()?.let { it to marker.point } ?: marker.id
}.values.map { group ->
    if (group.size == 1) return@map group.single()
    // Preserve a requested ID; otherwise open a saved stop when there is one, so the drawer's star
    // agrees with the marker. Prefer the stop serving more routes, then its ID for stable ordering.
    val representative = group.firstOrNull { it.id == focusedStopId }
        ?: group.minWith(
            compareByDescending<StopMarker> { it.favorite }
                .thenByDescending { it.stop.routeIds.size }
                .thenBy { it.id }
        )
    representative.copy(
        colocatedStopIds = group.flatMapTo(linkedSetOf()) { it.colocatedStopIds + it.id } - representative.id,
        favorite = group.any { it.favorite },
        presentedRoutes = group.flatMapTo(linkedSetOf()) { it.presentedRoutes },
        routes = group.flatMap { it.routes }.inInterchangeableOrder(StopRoute::shortName)
    )
}
