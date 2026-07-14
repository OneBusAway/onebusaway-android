/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.onebusaway.android.map

import org.onebusaway.android.map.layout.RouteBadgeLayoutInput
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.layoutRouteBadges
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.getRouteDisplayName

internal val ROUTE_VIEW_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY,
)

internal const val ROUTE_DEEMPHASIZED_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 0.275f

/**
 * Convert exact trip shapes into uniform-width directional route lines. When [emphasizedRouteId] is
 * set, sibling routes use a thin, plain stroke and render first so the emphasized route remains
 * visually on top at shared segments.
 */
internal fun FocusedTripGeometry.toRoutePolylines(
    emphasizedRouteId: String? = null,
): List<RoutePolyline> = buildList {
    val orderedShapes = if (emphasizedRouteId == null) {
        shapes.values
    } else {
        shapes.values.sortedBy { if (it.routeId == emphasizedRouteId) 1 else 0 }
    }
    orderedShapes.forEach { shape ->
        if (shape.points.size < 2) return@forEach
        val deemphasized = emphasizedRouteId != null && shape.routeId != emphasizedRouteId
        add(
            RoutePolyline(
                shape.routeColor,
                shape.points,
                if (deemphasized) ROUTE_DEEMPHASIZED_LINE_WIDTH_DP else ROUTE_LINE_WIDTH_DP,
                directional = !deemphasized,
                transforms = ROUTE_VIEW_TRANSFORMS,
            )
        )
    }
}

/**
 * One Google-first badge model per successfully drawn route, preserving the focused-trip/shape order
 * that mirrors the arrivals drawer. The shared layout chooses stable geographic line-center anchors;
 * flavor renderers only draw them.
 */
internal fun FocusedTripGeometry.toRouteBadges(routes: List<ObaRoute>): List<RouteBadge> {
    val metadata = routes.associateBy(ObaRoute::id)
    val shapesByRoute = shapes.values.groupBy(FocusedTripShape::routeId)
    val specs = shapesByRoute.mapNotNull { (routeId, routeShapes) ->
        val route = metadata[routeId] ?: return@mapNotNull null
        val name = getRouteDisplayName(route).takeIf(String::isNotBlank) ?: return@mapNotNull null
        RouteBadgeSpec(route, name, routeShapes)
    }
    val placements = layoutRouteBadges(
        specs.map { spec ->
            RouteBadgeLayoutInput(
                spec.route.id,
                spec.shapes.map { shape -> RouteBadgePath(shape.points, shape.directionId) },
            )
        }
    ).associateBy { it.routeId }
    return buildList {
        for (spec in specs) {
            val placement = placements[spec.route.id] ?: continue
            add(
                RouteBadge(
                    routeId = spec.route.id,
                    routeShortName = spec.name,
                    color = spec.shapes.firstNotNullOfOrNull(FocusedTripShape::routeColor)
                        ?: spec.route.color
                        ?: DEFAULT_ROUTE_LINE_COLOR,
                    point = placement.point,
                    directionId = placement.directionId,
                )
            )
        }
    }
}

private data class RouteBadgeSpec(
    val route: ObaRoute,
    val name: String,
    val shapes: List<FocusedTripShape>,
)

/** Exact scheduled stop ids for all focused trips, or only those belonging to [routeId]. */
internal fun FocusedTripStops.stopIdsForRoute(
    trips: Set<FocusedTrip>,
    routeId: String?,
): Set<String> {
    if (routeId == null) return stopIds
    return buildSet {
        trips.asSequence()
            .filter { it.routeId == routeId }
            .map(FocusedTrip::tripId)
            .forEach { tripId -> stopIdsByTripId[tripId]?.let { addAll(it) } }
    }
}
