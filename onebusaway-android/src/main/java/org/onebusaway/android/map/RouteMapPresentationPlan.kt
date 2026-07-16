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

import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey

/**
 * The complete render plan for one publish of the route map's line/badge/stop layers — the pure output
 * of [assembleRouteMapPresentation]. [RouteMapController] does nothing with it but forward each field to
 * [org.onebusaway.android.map.render.MapRenderState] and the [StopsMapController], so all the
 * mode-merging policy (which of the three presentation modes wins, whether the base route stays visible
 * underneath, which colour the selected trip keeps) lives in the pure function and is unit-tested.
 */
internal data class RouteMapPresentation(
    val polylines: List<RoutePolyline>,
    val framingPolylines: List<RoutePolyline>,
    val routeModeScalesStopsWithZoom: Boolean,
    val badges: List<RouteBadge>,
    val stopPresentation: RouteStopPresentation?,
)

/** A selected vehicle's exact trip, as read fresh after its shape/schedule resolve. */
internal data class SelectedTripPresentation(
    val points: List<GeoPoint>,
    val stopIds: List<String>,
    val routeDirection: RouteDirectionKey,
)

/**
 * Everything the selected-vehicle branch of [assembleRouteMapPresentation] needs that the controller
 * must resolve from IO/retained state: the [presentation] itself (from the trip-observation store) and
 * the GTFS-colour fallback ([routeColorFallback]) when the selected route carries no adjacency colour,
 * both cheap and always needed to decide drawability; plus the selected direction's thinned
 * [directionUnderlay] and the projected-onto-shape [stopPresentation], each of which the assembler uses
 * on only some branches — so they're deferred as thunks and computed only when that branch is taken
 * (the underlay does a per-segment copy, the stop presentation a `Location` projection). Bundled so the
 * pure function stays a function of plain data + lazily-resolved dependencies.
 */
internal data class SelectedTripRenderInput(
    val presentation: SelectedTripPresentation,
    val routeColorFallback: Int,
    val directionUnderlay: () -> List<RoutePolyline>,
    val stopPresentation: () -> RouteStopPresentation,
)

/**
 * Merge the three route-map presentation modes into one render plan. Precedence:
 *
 * 1. **Selected vehicle** ([selected] resolved, with a drawable ≥2-point trip): the exact trip line
 *    replaces the direction geometry, framed to that trip, styled via [selectedTripStyle] — its colour
 *    keeps the emphasized route's adjacency colour when one exists, falling back to the GTFS colour
 *    otherwise, but the thinned direction underlay is gated on stop-focus being active at all, not on
 *    whether an adjacency colour happened to be found for this exact direction. (The #1899 regression
 *    was exactly that: the underlay decision proxied off the colour lookup instead of the real
 *    stop-focus state, see #1902.)
 * 2. **No stop-focus session** ([focusTrips] null): the plain base route (or ordinary nearby stops).
 * 3. **Stop-focus session**: adjacency geometry for the focused stop's trips, drawn over the emphasized
 *    base route only when that route isn't itself one of the focused trips ([showBaseRoute]).
 *
 * Pure so the precedence table is unit-tested without standing up the controller. The `Location`-backed
 * work enters lazily so the merge policy itself stays plain data, JVM-testable across every branch: the
 * focused-stop projection as the [projectedFocusStops] thunk, the selected-trip underlay/stop projection
 * as thunks on [SelectedTripRenderInput]. That projection geometry is covered by instrumented tests.
 */
internal fun assembleRouteMapPresentation(
    isActive: Boolean,
    emphasizedRoute: RouteDirectionKey?,
    basePolylines: List<RoutePolyline>,
    baseStopPresentation: RouteStopPresentation?,
    focusTrips: Set<FocusedTrip>?,
    focusedGeometry: FocusedTripGeometry,
    focusedStops: FocusedTripStops,
    focusedRoutes: List<ObaRoute>,
    routeColors: Map<RouteDirectionKey, Int>,
    selected: SelectedTripRenderInput?,
    projectedFocusStops: () -> Map<String, GeoPoint>,
): RouteMapPresentation {
    // Route badges accompany adjacency geometry; a whole-route emphasis has no adjacency entry to badge.
    val badges = if (emphasizedRoute == null) {
        focusedGeometry.toRouteBadges(focusedRoutes, routeColors)
    } else emptyList()

    // 1. Selected vehicle: draw its exact trip in place of the direction geometry. Stop focus alone
    // gates the underlay, not whether an adjacency color happened to be found for this exact
    // direction — see selectedTripStyle (#1902).
    if (selected != null) {
        val style = selectedTripStyle(
            focusTrips != null, selected.presentation.routeDirection, routeColors, selected.routeColorFallback,
        )
        val selectedTrip = selected.presentation.points
            .takeIf { it.size >= 2 }
            ?.let { points -> focusedRoutePolyline(style.color, points, directional = true) }
        if (selectedTrip != null) {
            return RouteMapPresentation(
                polylines = focusedGeometry.toTripFocusedRoutePolylines(
                    selected.presentation.routeDirection,
                    routeColors,
                    if (style.includeUnderlay) selected.directionUnderlay() else emptyList(),
                    selectedTrip,
                ),
                framingPolylines = listOf(selectedTrip),
                routeModeScalesStopsWithZoom = isActive,
                badges = badges,
                stopPresentation = selected.stopPresentation(),
            )
        }
        // A resolved-but-undrawable (< 2-point) trip falls through to the base/focus branches.
    }

    // 2. No stop-focus session: the base route (or ordinary nearby stops when not in route mode).
    if (focusTrips == null) {
        return RouteMapPresentation(
            polylines = basePolylines,
            framingPolylines = basePolylines,
            routeModeScalesStopsWithZoom = isActive,
            badges = emptyList(),
            stopPresentation = baseStopPresentation,
        )
    }

    // 3. Stop-focus session: adjacency geometry, over the emphasized base route when that route is not
    //    itself one of the focused trips (otherwise its shape is already drawn by the adjacency layer).
    val showBaseRoute = isActive && emphasizedRoute != null &&
        focusTrips.none { it.routeDirection == emphasizedRoute }
    val routesByStopId = focusedStops.routeDirectionsByStopId(focusTrips, emphasizedRoute)
    return RouteMapPresentation(
        polylines = focusedGeometry.toRoutePolylines(emphasizedRoute, routeColors) +
            if (showBaseRoute) basePolylines else emptyList(),
        // Adjacency stays visible under route mode, but only the active route's fully loaded shape
        // defines the route-frame extent (the displayed adjacency would fit every route at the stop).
        framingPolylines = if (isActive) basePolylines else emptyList(),
        routeModeScalesStopsWithZoom = isActive,
        badges = badges,
        stopPresentation = if (showBaseRoute) {
            baseStopPresentation
        } else {
            RouteStopPresentation(
                stops = routesByStopId.keys.mapNotNull(focusedStops.stopsById::get),
                routes = focusedRoutes,
                routeDirectionsByStopId = routesByStopId,
                projectedPoints = projectedFocusStops(),
            )
        },
    )
}
