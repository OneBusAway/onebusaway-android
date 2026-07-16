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

import android.location.Location
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.Polyline

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
 * must resolve from IO/retained state first: the [presentation] itself (from the trip-observation
 * store), the GTFS-colour fallback ([routeColorFallback]) when the selected route carries no adjacency
 * colour, the selected direction's thinned [directionUnderlay], and the projected-onto-shape
 * [stopPresentation]. Bundled so the pure function stays a function of plain data.
 */
internal data class SelectedTripRenderInput(
    val presentation: SelectedTripPresentation,
    val routeColorFallback: Int,
    val directionUnderlay: List<RoutePolyline>,
    val stopPresentation: RouteStopPresentation,
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
 * Pure so the precedence table is unit-tested without standing up the controller. The focused-stop
 * projection is the one Location-dependent step, so it enters as a lazy [projectedFocusStops] thunk
 * (invoked only on the branch that draws focused-stop siblings) — the merge policy itself stays plain
 * data, JVM-testable across every branch. The projection geometry is covered by instrumented tests.
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
    projectedFocusStops: () -> Map<String, GeoPoint> = { emptyMap() },
): RouteMapPresentation {
    // Route badges accompany adjacency geometry; a whole-route emphasis has no adjacency entry to badge.
    val badges = if (emphasizedRoute == null) {
        focusedGeometry.toRouteBadges(focusedRoutes, routeColors)
    } else emptyList()

    // 1. Selected vehicle: draw its exact trip in place of the direction geometry. Stop focus alone
    // gates the underlay, not whether an adjacency color happened to be found for this exact
    // direction — see selectedTripStyle (#1902).
    val selectedTrip = selected?.let { input ->
        input.presentation.points.takeIf { it.size >= 2 }?.let { points ->
            val style = selectedTripStyle(
                focusTrips != null, input.presentation.routeDirection, routeColors, input.routeColorFallback,
            )
            focusedRoutePolyline(style.color, points, directional = true) to style
        }
    }
    if (selected != null && selectedTrip != null) {
        val (polyline, style) = selectedTrip
        return RouteMapPresentation(
            polylines = focusedGeometry.toTripFocusedRoutePolylines(
                selected.presentation.routeDirection,
                routeColors,
                if (style.includeUnderlay) selected.directionUnderlay else emptyList(),
                polyline,
            ),
            framingPolylines = listOf(polyline),
            routeModeScalesStopsWithZoom = isActive,
            badges = badges,
            stopPresentation = selected.stopPresentation,
        )
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

/**
 * Snap each of a focused stop's exact scheduled stops to the closest successful shape of a trip that
 * serves it. A stop that can't be placed on any candidate shape is omitted (not carried at its raw
 * location — the caller keeps only stops it can draw on the focused geometry). Pure; unit-tested.
 */
internal fun projectFocusedStops(
    trips: Set<FocusedTrip>,
    geometry: FocusedTripGeometry,
    stops: FocusedTripStops,
): Map<String, GeoPoint> {
    val tripById = trips.associateBy(FocusedTrip::tripId)
    val candidates = LinkedHashMap<String, MutableList<Polyline>>()
    for ((tripId, stopIds) in stops.stopIdsByTripId) {
        val shapeId = tripById[tripId]?.shapeId ?: continue
        val points = geometry.shapes.firstOrNull { it.shapeId == shapeId }?.points ?: continue
        if (points.size < 2) continue
        val polyline = Polyline(points.map(GeoPoint::toLocation))
        stopIds.forEach { candidates.getOrPut(it, ::mutableListOf).add(polyline) }
    }
    return buildMap {
        for ((stopId, shapes) in candidates) {
            val stop = stops.stopsById[stopId] ?: continue
            nearestPointAcross(shapes, stop.location)?.let { put(stopId, it) }
        }
    }
}

/**
 * Project [stops] onto the nearest point across [points]' drawable shapes (each candidate line needs
 * ≥2 points), keyed by stop id. A stop that can't be placed (no drawable shape) falls back to its own
 * location, so it still shows — just off the line. Pure; unit-tested.
 */
internal fun projectStopsOntoPolylines(
    stops: List<ObaStop>,
    points: List<List<GeoPoint>>,
): Map<String, GeoPoint> {
    val shapes = points
        .filter { it.size >= 2 }
        .map { line -> Polyline(line.map(GeoPoint::toLocation)) }
    if (shapes.isEmpty()) return emptyMap()
    return stops.associate { stop ->
        val loc = stop.location
        stop.id to (nearestPointAcross(shapes, loc) ?: loc.toGeoPoint())
    }
}

/**
 * The nearest point to [location] across [shapes] (the shared nearest-point-across-polylines kernel of
 * [projectFocusedStops] and [projectStopsOntoPolylines]), or null when no shape yields a point.
 */
private fun nearestPointAcross(shapes: List<Polyline>, location: Location): GeoPoint? =
    shapes
        .mapNotNull { it.nearestPoint(location.latitude, location.longitude) }
        .minByOrNull(location::distanceTo)
        ?.toGeoPoint()
