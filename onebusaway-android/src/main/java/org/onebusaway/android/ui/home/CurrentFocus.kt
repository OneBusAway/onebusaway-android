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
package org.onebusaway.android.ui.home

import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.models.RouteDirectionKey

/** The one mutually-exclusive focus rendered by HOME. */
sealed interface CurrentFocus {
    data object None : CurrentFocus
    data class Stop(
        val stop: FocusedStop,
        val selectedRoute: StopRouteSelection? = null,
    ) : CurrentFocus
    data class Route(val target: RouteTarget) : CurrentFocus
    data class BikeStation(val id: String) : CurrentFocus

    /**
     * Trip-plan directions mode. A marker with no payload: the itinerary/plan identity lives in
     * `TripPlanViewModel`/`TripResultsViewModel` (and persists via their own SavedStateHandle), so
     * duplicating it here would create a second source of truth for "which itinerary". This only says
     * "the map is in directions mode" — the chrome swaps to the trip-plan form and the map draws the
     * itinerary the results VM selects.
     */
    data object Directions : CurrentFocus
}

val CurrentFocus.focusedStop: FocusedStop?
    get() = (this as? CurrentFocus.Stop)?.stop

val CurrentFocus.focusedBikeStationId: String?
    get() = (this as? CurrentFocus.BikeStation)?.id

/** Durable route identity. Vehicle-trip focus remains a transient [ShowRouteRequest] field. */
data class RouteTarget(
    val routeId: String,
    val directionStopId: String? = null,
    val directionId: Int? = null,
) {
    fun toRequest(focusTripId: String? = null) = ShowRouteRequest(
        routeId = routeId,
        directionStopId = directionStopId,
        focusTripId = focusTripId,
        initialDirectionId = directionId,
    )
}

internal fun ShowRouteRequest.toRouteTarget() = RouteTarget(
    routeId = routeId,
    directionStopId = directionStopId,
    directionId = initialDirectionId,
)

/** One route reached while following a vehicle block. */
data class RouteLeg(
    val routeId: String,
    val shortName: String,
    val directionId: Int? = null,
) {
    val routeDirection: RouteDirectionKey get() = RouteDirectionKey(routeId, directionId)
}

/** A route selected inside stop focus, anchored to its original arrivals row across continuations. */
data class StopRouteSelection(
    // Row *identity*, never display: with [originLeg]'s route + directionId it forms the row key
    // ([selectedArrivalRowKey]) fed to `resolveSelectedRouteGroup`, disambiguating the legacy case where
    // a response omits directionId and only the headsign string tells two directions apart. The headsign
    // the banner *shows* is read back from the resolved arrivals row, not from here — so don't render
    // this and don't duplicate the rest of the row onto the selection.
    val originHeadsign: String?,
    val legs: List<RouteLeg>,
) {
    init {
        require(legs.isNotEmpty()) { "StopRouteSelection requires at least one route leg" }
    }

    val originLeg: RouteLeg get() = legs.first()
    val currentLeg: RouteLeg get() = legs.last()
    fun target(stopId: String) = RouteTarget(currentLeg.routeId, stopId, currentLeg.directionId)

    fun continueTo(leg: RouteLeg): StopRouteSelection = copy(legs = legs + leg)
}
