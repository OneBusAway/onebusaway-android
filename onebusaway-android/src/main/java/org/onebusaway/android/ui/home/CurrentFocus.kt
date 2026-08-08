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
import org.onebusaway.android.ui.tripresults.FocusedLeg

/** The one mutually-exclusive focus rendered by HOME. */
sealed interface CurrentFocus {
    data object None : CurrentFocus
    data class Stop(
        val stop: FocusedStop,
        val selectedRoute: StopRouteSelection? = null
    ) : CurrentFocus
    data class Route(val target: RouteTarget) : CurrentFocus
    data class BikeStation(val id: String) : CurrentFocus

    /**
     * Trip-plan directions mode. The itinerary/plan identity still lives in
     * `TripPlanViewModel`/`TripResultsViewModel` (persisted via their own SavedStateHandle), so this
     * does not duplicate "which itinerary". [subFocus] is the one sub-state it does own: the leg the
     * user drilled into from the overview. Null is the plain itinerary overview.
     */
    data class Directions(val subFocus: DirectionsSubFocus? = null) : CurrentFocus
}

/**
 * A leg the user tapped from the directions overview — the leg-subordinate-to-directions focus, one
 * attention level below the whole trip (mirroring the route-subordinate-to-stop focus). Being its own
 * level is what makes a background tap, or Back, drop to the itinerary overview rather than out of
 * directions, for every kind of leg alike (#2075).
 */
sealed interface DirectionsSubFocus {

    /**
     * A transit leg examined as its route: the map recontextualized onto that route with the traveled
     * board→alight segment drawn thick over it. Holds the exact [ShowRouteRequest] that produced this
     * focus (route id, the boarding stop the direction is narrowed to, the ridden `highlightedSegment`,
     * and — for a followed vehicle — its `focusTripId`) so restoring after a back-press replays it
     * faithfully. (Each stop's live ETAs are shown inline in the drawer's Board/Alight rows, not here.)
     *
     * [boardStop] is where the rider gets on. It is carried on the focus, rather than read off whichever
     * itinerary row happens to be composed, because the map's ride selection reads the boarding stop's
     * live arrivals: HOME hoists one arrivals session for this stop, so the session's lifetime is the
     * focus rather than a `LazyColumn` row's and scrolling the itinerary cannot change what the map
     * draws. Null when the leg's OTP→OBA stop resolution failed or it carries no location.
     */
    data class Route(
        val request: ShowRouteRequest,
        val boardStop: FocusedStop? = null
    ) : DirectionsSubFocus

    /**
     * An on-street (walk/bike) leg framed on its own, with the rest of the trip receding to context
     * around it (#2048) — the itinerary stays drawn, so only [leg]'s geometry + leg indices are needed
     * to re-apply the focus.
     */
    data class Leg(val leg: FocusedLeg) : DirectionsSubFocus
}

/**
 * Whether the drawn itinerary survives in this focus: the overview and an on-street leg focus both keep it
 * (the leg focus just restyles it), while a leg's route sub-focus recontextualizes the map onto that route
 * — and any focus outside directions tears the trip down altogether. So returning to a trip *from* a focus
 * that doesn't keep it has to redraw it first.
 */
internal val CurrentFocus.keepsDrawnItinerary: Boolean
    get() = this is CurrentFocus.Directions && subFocus !is DirectionsSubFocus.Route

/**
 * This focus with its innermost attention layer removed, or null when there is nothing left to peel.
 * The one definition of the focus ladder: a stop's route selection sits under its drilled-into trip
 * (#2205), a directions overview under its focused leg, and every top-level focus under the root.
 *
 * Both consumers read it from here rather than each spelling the levels out: a background tap peels one
 * rung ([org.onebusaway.android.ui.home.HomeViewModel.unfocusMapOneLevel]), and a focus restored after
 * process death — where only the deepest rung is persisted — rebuilds its undo history by walking the
 * whole ladder. A new level therefore has to be added in one place for both to agree.
 */
internal fun CurrentFocus.peeledOneLevel(): CurrentFocus? = when (this) {
    CurrentFocus.None -> null
    is CurrentFocus.Stop -> when {
        selectedRoute == null -> CurrentFocus.None
        // The trip drilled into is the innermost rung, so it goes first — the route stays drawn and
        // only its confidence band and pill outline are given up.
        selectedRoute.selectedTripId != null ->
            CurrentFocus.Stop(stop, selectedRoute.copy(selectedTripId = null))
        else -> CurrentFocus.Stop(stop)
    }
    // A focused leg — its route, or an on-street leg framed on its own — becomes the plain itinerary
    // overview; a plain overview exits.
    is CurrentFocus.Directions -> if (subFocus == null) CurrentFocus.None else CurrentFocus.Directions()
    is CurrentFocus.Route, is CurrentFocus.BikeStation -> CurrentFocus.None
}

val CurrentFocus.focusedStop: FocusedStop?
    get() = (this as? CurrentFocus.Stop)?.stop

val CurrentFocus.focusedBikeStationId: String?
    get() = (this as? CurrentFocus.BikeStation)?.id

/**
 * Durable route identity. It names no trip: within stop focus the drilled-into trip is a level of its own
 * ([StopRouteSelection.selectedTripId], #2205), and the [ShowRouteRequest.focusTripId] this mints is the
 * one-shot "fit that vehicle" camera instruction a drill-in gesture passes, not retained state.
 */
data class RouteTarget(
    val routeId: String,
    val directionStopId: String? = null,
    val directionId: Int? = null
) {
    fun toRequest(focusTripId: String? = null) = ShowRouteRequest(
        routeId = routeId,
        directionStopId = directionStopId,
        focusTripId = focusTripId,
        initialDirectionId = directionId
    )
}

internal fun ShowRouteRequest.toRouteTarget() = RouteTarget(
    routeId = routeId,
    directionStopId = directionStopId,
    directionId = initialDirectionId
)

/** One route reached while following a vehicle block. */
data class RouteLeg(
    val routeId: String,
    val shortName: String,
    val directionId: Int? = null
) {
    val routeDirection: RouteDirectionKey get() = RouteDirectionKey(routeId, directionId)
}

/** A route selected inside stop focus, anchored to its original arrivals row across continuations. */
data class StopRouteSelection(
    // Row *identity*, never display: with [originLeg]'s route + directionId it forms the row key
    // ([selectedArrivalRowKey]) fed to `resolveSelectedRouteGroupKey`, disambiguating the legacy case
    // where a response omits directionId and only the headsign string tells two directions apart. What
    // the user sees is the resolved arrivals row itself, drawn as the drawer's focus outline — so don't
    // render this and don't duplicate the rest of the row onto the selection.
    val originHeadsign: String?,
    val legs: List<RouteLeg>,
    /**
     * The one trip drilled into from this route — the stop→route→trip level (#2205), entered by tapping
     * that trip's ETA pill or its vehicle on the map. Both gestures mean the same thing, so both land
     * here. Null is the plain stop→route focus.
     *
     * A level of its own rather than a render-only vehicle selection, so a background tap peels it before
     * the route (see [peeledOneLevel]) — which is what makes the map's
     * [org.onebusaway.android.map.render.MapRenderState.selectedVehicleTripId] a projection of this focus
     * rather than a second, independently-mutated truth.
     */
    val selectedTripId: String? = null
) {
    init {
        require(legs.isNotEmpty()) { "StopRouteSelection requires at least one route leg" }
    }

    val originLeg: RouteLeg get() = legs.first()
    val currentLeg: RouteLeg get() = legs.last()
    fun target(stopId: String) = RouteTarget(currentLeg.routeId, stopId, currentLeg.directionId)

    /** Follow the block onto [leg]. The trip level doesn't survive: the continuation is a *different*
     *  trip of that block, and this tap doesn't name its id. */
    fun continueTo(leg: RouteLeg): StopRouteSelection = copy(legs = legs + leg, selectedTripId = null)
}
