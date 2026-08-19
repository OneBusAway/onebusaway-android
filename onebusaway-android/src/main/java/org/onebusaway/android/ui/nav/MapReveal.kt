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
package org.onebusaway.android.ui.nav

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.geoPointOrNull

/**
 * Navigate to an in-app [route], popping up to HOME and de-duping the top — the single navigation
 * idiom every in-app caller uses (drawer/top-bar actions, the arrivals sheet, the My* lists, the home
 * overlays), replacing the former `HomeViewModel.stageDeepLinkRoute` route latch and its `DeepLinkEffect`
 * consumer. Matches the options that consumer applied, so the back-stack behavior is unchanged: a pushed
 * destination collapses back to HOME, and an already-open destination isn't re-created.
 */
fun NavController.navigateFromHome(route: String) = navigate(route) {
    popUpTo(NavRoutes.HOME) { inclusive = false }
    launchSingleTop = true
}

/**
 * "Show route / stop on the map" expressed as navigation rather than a reach-through to the host's
 * ViewModels. A pushed destination (route info, search, the My* lists) hands the reveal to the HOME
 * back-stack entry's [androidx.lifecycle.SavedStateHandle] and pops back to it; the HOME destination
 * observes these keys, applies them to the shared map/home ViewModels (which it already holds in scope),
 * and consumes them. This is the idiomatic Navigation-Compose "return a result to a previous destination"
 * pattern — observable, process-death-safe, and the consume (set-null) persists.
 *
 * HOME is the NavHost start destination, so it is always on the back stack and [getBackStackEntry] is safe.
 */
const val RESULT_MAP_ROUTE_ID = "mapReveal.routeId"
const val RESULT_MAP_ROUTE_DIRECTION_STOP_ID = "mapReveal.routeDirectionStopId"
const val RESULT_MAP_ROUTE_FOCUS_TRIP_ID = "mapReveal.routeFocusTripId"
const val RESULT_MAP_ROUTE_INITIAL_DIRECTION_ID = "mapReveal.routeInitialDirectionId"
const val RESULT_MAP_STOP_ID = "mapReveal.stopId"
const val RESULT_MAP_STOP_NAME = "mapReveal.stopName"
const val RESULT_MAP_STOP_LAT = "mapReveal.stopLat"
const val RESULT_MAP_STOP_LON = "mapReveal.stopLon"

/**
 * Reveal the map in route mode for [request], popping back to HOME. The [ShowRouteRequest] is serialized
 * to the `RESULT_MAP_ROUTE_*` keys and read back by [consumeRouteReveal] — the one place field names live.
 */
fun NavController.revealRouteOnMap(request: ShowRouteRequest) {
    getBackStackEntry(NavRoutes.HOME).savedStateHandle.putRouteReveal(request)
    popBackStack(NavRoutes.HOME, false) // inclusive = false
}

/** The write half of the route-reveal round trip — every [ShowRouteRequest] field, so a request
 *  survives the navigation hop intact (verified against [consumeRouteReveal] by `MapRevealTest`). */
internal fun SavedStateHandle.putRouteReveal(request: ShowRouteRequest) {
    set(RESULT_MAP_ROUTE_ID, request.routeId)
    set(RESULT_MAP_ROUTE_DIRECTION_STOP_ID, request.directionStopId)
    set(RESULT_MAP_ROUTE_FOCUS_TRIP_ID, request.focusTripId)
    set(RESULT_MAP_ROUTE_INITIAL_DIRECTION_ID, request.initialDirectionId)
}

/** Reveal the whole route [routeId] on the map (no direction focus) — the plain-route launchers. */
fun NavController.revealRouteOnMap(routeId: String) = revealRouteOnMap(ShowRouteRequest(routeId))

/**
 * Reads and consumes a pending route reveal from the HOME [SavedStateHandle] — the symmetric typed
 * *read* for [revealRouteOnMap], keeping the `RESULT_MAP_ROUTE_*` key names in this one file rather
 * than re-reading them in the consumer. The keys are cleared together (so a stale direction anchor
 * can't linger past the reveal); returns null when no route id is present.
 */
fun SavedStateHandle.consumeRouteReveal(): ShowRouteRequest? {
    val routeId = get<String>(RESULT_MAP_ROUTE_ID)
    val directionStopId = get<String>(RESULT_MAP_ROUTE_DIRECTION_STOP_ID)
    val focusTripId = get<String>(RESULT_MAP_ROUTE_FOCUS_TRIP_ID)
    val initialDirectionId = get<Int>(RESULT_MAP_ROUTE_INITIAL_DIRECTION_ID)
    set(RESULT_MAP_ROUTE_ID, null)
    set(RESULT_MAP_ROUTE_DIRECTION_STOP_ID, null)
    set(RESULT_MAP_ROUTE_FOCUS_TRIP_ID, null)
    set(RESULT_MAP_ROUTE_INITIAL_DIRECTION_ID, null)
    return routeId?.let { ShowRouteRequest(it, directionStopId, focusTripId, initialDirectionId) }
}

/**
 * "Show me this stop" — the one currency every stop affordance in the app speaks, whether it is a
 * navigation hand-back ([revealStopOnMap]), an external launch translated at the entry boundary
 * ([IntentRouteMapper.stopRevealForIntent]), or a list row handing one to its host.
 *
 * Only [stopId] is required, because it is the only thing every requester has: a deep link, an FCM
 * arrival push, a pinned shortcut and a reminder row carry nothing else. [name] is the arrivals sheet's
 * pre-load title and [point] completes the focus before its arrivals land (the recenter button, the
 * banner star, "report a problem"); both are filled in from the loaded stop by
 * `HomeViewModel.onArrivalsLoaded` when a requester couldn't supply them, so passing them is a
 * head start rather than a requirement.
 */
data class StopReveal(
    val stopId: String,
    val name: String? = null,
    val point: GeoPoint? = null
)

/** Reveal the map focused on [reveal]'s stop, popping back to HOME. */
fun NavController.revealStopOnMap(reveal: StopReveal) {
    getBackStackEntry(NavRoutes.HOME).savedStateHandle.apply {
        set(RESULT_MAP_STOP_ID, reveal.stopId)
        set(RESULT_MAP_STOP_NAME, reveal.name)
        set(RESULT_MAP_STOP_LAT, reveal.point?.latitude)
        set(RESULT_MAP_STOP_LON, reveal.point?.longitude)
    }
    popBackStack(NavRoutes.HOME, false)
}

/** Reveal the stop [stopId] on the map, knowing nothing else about it. */
fun NavController.revealStopOnMap(stopId: String) = revealStopOnMap(StopReveal(stopId))

/**
 * Reads and consumes a pending stop reveal from the HOME [SavedStateHandle] — the symmetric typed *read*
 * for [revealStopOnMap], keeping the `RESULT_MAP_STOP_*` keys and their types in this one file rather
 * than re-naming them in the consumer. Every key is cleared together regardless of completeness (so a
 * stale name or lat/lon pair can't linger past the reveal); returns null when no stop id is present.
 * A lone coordinate — which the producer never writes — reads as no location at all rather than half
 * of one.
 */
fun SavedStateHandle.consumeStopReveal(): StopReveal? {
    val stopId = get<String>(RESULT_MAP_STOP_ID)
    val name = get<String>(RESULT_MAP_STOP_NAME)
    val lat = get<Double>(RESULT_MAP_STOP_LAT)
    val lon = get<Double>(RESULT_MAP_STOP_LON)
    set(RESULT_MAP_STOP_ID, null)
    set(RESULT_MAP_STOP_NAME, null)
    set(RESULT_MAP_STOP_LAT, null)
    set(RESULT_MAP_STOP_LON, null)
    return stopId?.let { StopReveal(it, name, geoPointOrNull(lat, lon)) }
}

/**
 * The route half of a stop-scoped reveal: which of a focused stop's rows to select inside it.
 *
 * Deliberately *not* standalone route focus. Framing the route alone drops the rider onto the whole
 * line with no drawer and no sense of where they are on it; what a tracked row means is "this route,
 * at my stop", which is a route selected subordinate to a stop focus (`CurrentFocus.Stop` carrying a
 * `StopRouteSelection`). The two look similar from the outside and are easy to confuse.
 *
 * Only the route half, because the stop half of the same intent is already parsed by
 * `FocusedStop.fromIntent` — one contract wants one reader, and two of them drift.
 */
data class RouteRevealExtras(
    val routeId: String,
    /** Labels the selected row's leg; the drawer resolves everything else off the arrivals row. */
    val routeShortName: String,
    val headsign: String?
) {
    /**
     * The request that selects this row *within* the stop focused at [stopId]. Carrying
     * `directionStopId` is what makes it stop-scoped rather than standalone — see
     * `HomeViewModel.selectArrivalRoute`, which branches on exactly that.
     */
    fun request(stopId: String): ShowRouteRequest = ShowRouteRequest(
        routeId = routeId,
        directionStopId = stopId,
        directionHeadsign = headsign
    )
}

/**
 * Writes a stop-scoped route reveal onto an intent bound for `HomeActivity`, so a launch from
 * *outside* the NavHost — a notification's PendingIntent — can open the map on [stop] with one of its
 * routes selected.
 *
 * The saved-state round trips above cannot serve that: they hand a reveal between destinations that
 * already exist, and a PendingIntent fired from the shade has no NavController to hand it to. So this
 * is the same reveal in the only vocabulary an Intent has, over the [MapParams] keys the map's other
 * intent state already lives under — the stop half deliberately in the exact shape
 * `FocusedStop.fromIntent` reads, so that stays the one parser of it.
 */
fun Intent.putStopRouteReveal(stop: FocusedStop, route: RouteRevealExtras): Intent = apply {
    putExtra(MapParams.STOP_ID, stop.id)
    putExtra(MapParams.STOP_NAME, stop.name)
    putExtra(MapParams.STOP_CODE, stop.code)
    // Only when the stop's location is known: `FocusedStop.fromIntent` reads their absence as "not
    // resolved yet" and lets the arrivals load supply it, which is exactly what a zero pair would mean.
    stop.point?.let {
        putExtra(MapParams.CENTER_LAT, it.latitude)
        putExtra(MapParams.CENTER_LON, it.longitude)
    }
    putExtra(MapParams.ROUTE_ID, route.routeId)
    putExtra(EXTRA_ROUTE_SHORT_NAME, route.routeShortName)
    putExtra(EXTRA_ROUTE_DIRECTION_HEADSIGN, route.headsign)
}

/**
 * The route half a launch intent carries, or null when it is not a route reveal. The stop half is
 * read by `FocusedStop.fromIntent`; a caller needs both. See [putStopRouteReveal].
 */
fun Intent.readRouteReveal(): RouteRevealExtras? {
    val routeId = getStringExtra(MapParams.ROUTE_ID) ?: return null
    val routeShortName = getStringExtra(EXTRA_ROUTE_SHORT_NAME) ?: return null
    return RouteRevealExtras(
        routeId = routeId,
        routeShortName = routeShortName,
        headsign = getStringExtra(EXTRA_ROUTE_DIRECTION_HEADSIGN)
    )
}

/**
 * The two extras only this file writes and reads. Their neighbours in [MapParams] are genuinely
 * shared — HomeActivity, MapViewModel and the focus persistence all read STOP_ID/ROUTE_ID/CENTER_* —
 * but nothing in the map subsystem reads these, and the headsign reaches it through
 * [ShowRouteRequest.directionHeadsign] rather than by being parsed there. Keeping them here means
 * changing the map's intent handling does not require proving they are unused.
 */
private const val EXTRA_ROUTE_DIRECTION_HEADSIGN = ".RouteDirectionHeadsign"
private const val EXTRA_ROUTE_SHORT_NAME = ".RouteShortName"
