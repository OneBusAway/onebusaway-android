/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.onebusaway.android.map

import org.onebusaway.android.map.layout.RouteBadgeLayoutInput
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.layoutRouteBadges
import org.onebusaway.android.map.render.ADJACENT_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.FOCUSED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.getRouteDisplayName

internal val ROUTE_VIEW_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY
)

/** The line presentation shared by single-route view and a selected route in focused-stop mode. */
internal fun focusedRoutePolyline(
    color: Int?,
    points: List<GeoPoint>,
    directional: Boolean
) = RoutePolyline(
    color = color,
    points = points,
    widthProfile = FOCUSED_ROUTE_LINE_WIDTH_PROFILE,
    directional = directional,
    transforms = ROUTE_VIEW_TRANSFORMS
)

/** The active route's broader geometry retained beneath an exact selected-trip line. */
internal fun List<RoutePolyline>.asDeemphasizedRouteUnderlay(): List<RoutePolyline> = map { line ->
    line.copy(
        widthProfile = DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE,
        directional = false
    )
}

/**
 * Convert exact trip shapes into route lines. When [emphasizedRoute] is null (stop focus, no route
 * selected) every shape is a thin, plain adjacency line with no direction chevrons. When it's set,
 * that route-direction uses a 1.5x directional stroke while siblings use a thin, plain stroke and
 * render first, keeping the emphasized variant visually on top at shared segments.
 */
internal fun FocusedTripGeometry.toRoutePolylines(
    emphasizedRoute: RouteDirectionKey? = null,
    routeColors: Map<RouteDirectionKey, Int> = emptyMap()
): List<RoutePolyline> = buildList {
    val orderedShapes = if (emphasizedRoute == null) {
        shapes
    } else {
        shapes.sortedBy { if (it.routeDirection == emphasizedRoute) 1 else 0 }
    }
    orderedShapes.forEach { shape ->
        if (shape.points.size < 2) return@forEach
        val emphasized = emphasizedRoute == shape.routeDirection
        val polyline = if (emphasized) {
            focusedRoutePolyline(
                routeColors[shape.routeDirection] ?: shape.routeColor,
                shape.points,
                directional = true
            )
        } else {
            val widthProfile = if (emphasizedRoute == null) {
                ADJACENT_ROUTE_LINE_WIDTH_PROFILE
            } else {
                DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE
            }
            RoutePolyline(
                routeColors[shape.routeDirection] ?: shape.routeColor,
                shape.points,
                widthProfile,
                // Adjacent routes in stop focus are plain thin lines — no direction chevrons — so the
                // mode reads as "these routes pass here", reserving chevrons for a selected route (#1985).
                directional = false,
                transforms = ROUTE_VIEW_TRANSFORMS
            )
        }
        add(polyline)
    }
}

/** Sibling routes, then the selected route's thin underlay, then its exact trip shape. */
internal fun FocusedTripGeometry.toTripFocusedRoutePolylines(
    selectedRoute: RouteDirectionKey,
    routeColors: Map<RouteDirectionKey, Int>,
    selectedRouteUnderlay: List<RoutePolyline>,
    selectedTrip: RoutePolyline
): List<RoutePolyline> = FocusedTripGeometry(shapes.filterNot { it.routeDirection == selectedRoute })
    .toRoutePolylines(selectedRoute, routeColors) +
    selectedRouteUnderlay +
    selectedTrip

/**
 * One badge model per successfully drawn route-direction, preserving the focused-trip order that
 * mirrors the arrivals drawer. The shared layout chooses stable geographic line-center anchors;
 * flavor renderers only draw them.
 */
internal fun FocusedTripGeometry.toRouteBadges(
    routes: List<ObaRoute>,
    routeColors: Map<RouteDirectionKey, Int> = emptyMap()
): List<RouteBadge> {
    val metadata = routes.associateBy(ObaRoute::id)
    val specs = shapes.groupBy(FocusedTripShape::routeDirection).mapNotNull { (key, routeShapes) ->
        val route = metadata[key.routeId] ?: return@mapNotNull null
        val name = getRouteDisplayName(route).takeIf(String::isNotBlank) ?: return@mapNotNull null
        RouteBadgeSpec(key, route, name, routeShapes)
    }
    val placements = layoutRouteBadges(
        specs.map { spec ->
            RouteBadgeLayoutInput(
                spec.key,
                spec.shapes.map { shape -> RouteBadgePath(shape.points) }
            )
        }
    ).associateBy { it.route }
    return buildList {
        for (spec in specs) {
            val placement = placements[spec.key] ?: continue
            add(
                RouteBadge(
                    routeId = spec.key.routeId,
                    routeShortName = spec.name,
                    color = routeColors[spec.key]
                        ?: spec.shapes.firstNotNullOfOrNull(FocusedTripShape::routeColor)
                        ?: spec.route.color
                        ?: DEFAULT_ROUTE_LINE_COLOR,
                    point = placement.point,
                    directionId = spec.key.directionId
                )
            )
        }
    }
}

private data class RouteBadgeSpec(
    val key: RouteDirectionKey,
    val route: ObaRoute,
    val name: String,
    val shapes: List<FocusedTripShape>
)

/** The selected-trip line's color and whether the generic same-direction underlay stays beneath it. */
internal data class SelectedTripStyle(val color: Int, val includeUnderlay: Boolean)

/**
 * [stopFocusActive] alone gates the underlay: inside stop focus the focused-stop's own siblings
 * already carry the route's other geometry, so the underlay is dropped even when [selectedRouteDirection]
 * isn't among the focused stop's own trips and [routeColors] carries no adjacency entry for it — that
 * combination used to fall back to a whole-route-style underlay (the #1899 regression fixed by #1902),
 * because the underlay decision was proxied off the color lookup instead of the real stop-focus state.
 * A color miss still falls back to [gtfsColor]; only the underlay must not follow it.
 */
internal fun selectedTripStyle(
    stopFocusActive: Boolean,
    selectedRouteDirection: RouteDirectionKey,
    routeColors: Map<RouteDirectionKey, Int>,
    gtfsColor: Int
): SelectedTripStyle = SelectedTripStyle(
    color = routeColors[selectedRouteDirection] ?: gtfsColor,
    includeUnderlay = !stopFocusActive
)

/** Presented route-direction identities at each scheduled stop, optionally narrowed to [route]. */
internal fun FocusedTripStops.routeDirectionsByStopId(
    trips: Set<FocusedTrip>,
    route: RouteDirectionKey? = null
): Map<String, Set<RouteDirectionKey>> {
    val routesByTripId = trips.associate { it.tripId to it.routeDirection }
    val result = LinkedHashMap<String, MutableSet<RouteDirectionKey>>()
    for ((tripId, stopIds) in stopIdsByTripId) {
        val tripRoute = routesByTripId[tripId] ?: continue
        if (route != null && tripRoute != route) continue
        stopIds.forEach { stopId -> result.getOrPut(stopId, ::linkedSetOf).add(tripRoute) }
    }
    return result
}
