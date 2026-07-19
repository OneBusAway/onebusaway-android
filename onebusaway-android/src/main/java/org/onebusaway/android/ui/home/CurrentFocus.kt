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
import org.onebusaway.android.util.GeoPoint

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
     * Trip-plan directions mode. The itinerary/plan identity still lives in
     * `TripPlanViewModel`/`TripResultsViewModel` (persisted via their own SavedStateHandle), so this
     * does not duplicate "which itinerary". [routeFocus] is the one sub-state it does own: the transit
     * leg the user drilled into to examine its route (map recontextualized to that route + the
     * departing stop's arrivals board), mirroring the route-subordinate-to-stop focus. Null is the
     * plain itinerary overview.
     */
    data class Directions(val routeFocus: DirectionsRouteFocus? = null) : CurrentFocus
}

/**
 * A transit leg the user drilled into from the directions overview — the route-subordinate-to-directions
 * focus. Carries the OBA identity needed to recontextualize the map onto the route ([routeId], anchored
 * to the departing stop's direction via [boardStop]) and to show that stop's arrivals board. [boardStop]
 * is null when the boarding stop couldn't be resolved to an OBA id (e.g. the OTP1 path, or coordinates
 * missing), in which case the arrivals board is simply omitted. Ids here are already OBA-format — see
 * [org.onebusaway.android.directions.OtpObaIdResolver].
 */
data class DirectionsRouteFocus(
    val routeId: String,
    val boardStop: FocusedStop?,
    // The board→alight polyline (the OTP leg geometry) the user rides, drawn thick over the full route.
    val segment: List<GeoPoint> = emptyList(),
    val directionId: Int? = null,
)

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
    // The first leg already owns the row's route + direction identity. Headsign is only the legacy
    // fallback for responses that omit directionId, so don't duplicate the rest of the row here.
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
