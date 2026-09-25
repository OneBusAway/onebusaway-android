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

    /**
     * A route opened on its own — from search, recents, a deep link, a coach-number hit, or the
     * arrivals row menu's unscoped "Show route on map". [selectedTripId] is the trip drilled into over
     * it, the same rung a stop's route selection and a directions leg's carry (#2224).
     */
    data class Route(
        val target: RouteTarget,
        val selectedTripId: String? = null
    ) : CurrentFocus
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
     * board→alight segment drawn thick over it. Holds the [ShowRouteRequest] that produced this focus —
     * route id, the boarding stop the direction is narrowed to, the ridden spans — so restoring after a
     * back-press replays it faithfully. Everything it holds is durable; the one field it deliberately
     * drops is `focusTripId` (see [selectedTripId]). (Each stop's live ETAs are shown inline in the
     * drawer's Board/Alight rows, not here.)
     *
     * [boardStop] is where the rider gets on. It is carried on the focus, rather than read off whichever
     * itinerary row happens to be composed, because the map's ride selection reads the boarding stop's
     * live arrivals: HOME hoists one arrivals session for this stop, so the session's lifetime is the
     * focus rather than a `LazyColumn` row's and scrolling the itinerary cannot change what the map
     * draws. Null when the leg's OTP→OBA stop resolution failed or it carries no location.
     *
     * [selectedTripId] is the trip drilled into over this leg's route — the same rung a stop's route
     * selection carries (#2224), entered by tapping that trip's ETA pill in the leg's inline strip or
     * its vehicle on the map. It is held *here* rather than left inside [request]'s `focusTripId`
     * precisely because that field is a one-shot camera instruction: the stored [request] carries none,
     * so replaying it on a back-restore re-draws the route without re-flying and re-pinging the vehicle.
     */
    data class Route(
        val request: ShowRouteRequest,
        val boardStop: FocusedStop? = null,
        val selectedTripId: String? = null
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
 * The trip drilled into over whichever route this focus draws, or null when none is — including on the
 * focuses that draw no route at all.
 *
 * The one answer to "which vehicle is the rider looking at", for all three surfaces that can show one
 * (#2224): a stop's route selection, a route opened on its own, and a directions leg's route. It used
 * to exist only on the first; the other two carried the tapped trip in a [ShowRouteRequest.focusTripId]
 * that everything else treats as a one-shot camera instruction, or nowhere at all — which left the
 * map's [org.onebusaway.android.map.render.MapRenderState.selectedVehicleTripId] as an unowned second
 * truth there, invisible to Back, to the background tap, and to `SavedStateHandle`.
 *
 * (Reading it off a value already typed as [CurrentFocus.Route] resolves to that class's own member
 * instead of this extension. Same answer either way — the member *is* what this reads.)
 */
internal val CurrentFocus.selectedTripId: String?
    get() = when (val focus = this) {
        is CurrentFocus.Stop -> focus.selectedRoute?.selectedTripId
        is CurrentFocus.Route -> focus.selectedTripId
        is CurrentFocus.Directions -> (focus.subFocus as? DirectionsSubFocus.Route)?.selectedTripId
        CurrentFocus.None, is CurrentFocus.BikeStation -> null
    }

/**
 * This focus with [tripId] drilled into over the route it draws, or null when it draws no route and so
 * has nothing to drill into. The write half of [selectedTripId]; the two enumerate the same three
 * holders, so a fourth route-bearing focus can't be added to one and forgotten in the other.
 */
internal fun CurrentFocus.withSelectedTrip(tripId: String?): CurrentFocus? = when (val focus = this) {
    is CurrentFocus.Stop -> focus.selectedRoute?.let { focus.copy(selectedRoute = it.copy(selectedTripId = tripId)) }
    is CurrentFocus.Route -> focus.copy(selectedTripId = tripId)
    is CurrentFocus.Directions -> (focus.subFocus as? DirectionsSubFocus.Route)
        ?.let { focus.copy(subFocus = it.copy(selectedTripId = tripId)) }
    CurrentFocus.None, is CurrentFocus.BikeStation -> null
}

/**
 * This focus with its innermost attention layer removed, or null when there is nothing left to peel.
 * The one definition of the focus ladder: a drilled-into trip sits under every route this app can draw
 * (#2205, #2224), a stop's route selection under that trip, a directions overview under its focused leg,
 * and every top-level focus under the root.
 *
 * Both consumers read it from here rather than each spelling the levels out: a background tap peels one
 * rung ([org.onebusaway.android.ui.home.HomeViewModel.unfocusMapOneLevel]), and a focus restored after
 * process death — where only the deepest rung is persisted — rebuilds its undo history by walking the
 * whole ladder. A new level therefore has to be added in one place for both to agree.
 */
internal fun CurrentFocus.peeledOneLevel(): CurrentFocus? {
    // The drilled-into trip is the innermost rung wherever a route is drawn, so it goes first — the
    // route stays drawn and only its confidence band and pill outline are given up. Expressed once here
    // rather than per focus kind, which is what keeps a background tap in a directions leg or a
    // standalone route from skipping the rung and tearing the whole route down (#2224).
    if (selectedTripId != null) return withSelectedTrip(null)
    return when (this) {
        CurrentFocus.None -> null
        is CurrentFocus.Stop -> if (selectedRoute == null) CurrentFocus.None else CurrentFocus.Stop(stop)
        // A focused leg — its route, or an on-street leg framed on its own — becomes the plain itinerary
        // overview; a plain overview exits.
        is CurrentFocus.Directions -> if (subFocus == null) CurrentFocus.None else CurrentFocus.Directions()
        is CurrentFocus.Route, is CurrentFocus.BikeStation -> CurrentFocus.None
    }
}

val CurrentFocus.focusedStop: FocusedStop?
    get() = (this as? CurrentFocus.Stop)?.stop

val CurrentFocus.focusedBikeStationId: String?
    get() = (this as? CurrentFocus.BikeStation)?.id

/**
 * Durable route identity. It names no trip: the drilled-into trip is a level of its own on whichever
 * focus draws the route ([selectedTripId], #2205/#2224), and the [ShowRouteRequest.focusTripId] this
 * mints is the one-shot "fit that vehicle" camera instruction a drill-in gesture passes, not retained
 * state.
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
     * rather than a second, independently-mutated truth. Read generically as [CurrentFocus.selectedTripId],
     * which is where the other two route-bearing focuses hold the same rung (#2224).
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
