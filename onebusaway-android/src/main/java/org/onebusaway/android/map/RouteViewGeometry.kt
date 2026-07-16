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
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.getRouteDisplayName

internal val ROUTE_VIEW_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY,
)

internal const val ROUTE_DEEMPHASIZED_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 0.275f
internal const val ROUTE_EMPHASIZED_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 1.5f

/**
 * Convert exact trip shapes into uniform-width directional route lines. When [emphasizedRoute] is
 * set, that route-direction uses a 1.5x stroke while siblings use a thin, plain stroke and render
 * first, keeping the emphasized variant visually on top at shared segments.
 */
internal fun FocusedTripGeometry.toRoutePolylines(
    emphasizedRoute: RouteDirectionKey? = null,
    routeColors: Map<RouteDirectionKey, Int> = emptyMap(),
): List<RoutePolyline> = buildList {
    val orderedShapes = if (emphasizedRoute == null) {
        shapes
    } else {
        shapes.sortedBy { if (it.routeDirection == emphasizedRoute) 1 else 0 }
    }
    orderedShapes.forEach { shape ->
        if (shape.points.size < 2) return@forEach
        val emphasized = emphasizedRoute == shape.routeDirection
        val widthDp = when {
            emphasizedRoute == null -> ROUTE_LINE_WIDTH_DP
            emphasized -> ROUTE_EMPHASIZED_LINE_WIDTH_DP
            else -> ROUTE_DEEMPHASIZED_LINE_WIDTH_DP
        }
        add(
            RoutePolyline(
                routeColors[shape.routeDirection] ?: shape.routeColor,
                shape.points,
                widthDp,
                directional = emphasizedRoute == null || emphasized,
                transforms = ROUTE_VIEW_TRANSFORMS,
            )
        )
    }
}

/**
 * One Google-first badge model per successfully drawn route-direction, preserving the focused-trip
 * order that mirrors the arrivals drawer. The shared layout chooses stable geographic line-center
 * anchors; flavor renderers only draw them.
 */
internal fun FocusedTripGeometry.toRouteBadges(
    routes: List<ObaRoute>,
    routeColors: Map<RouteDirectionKey, Int> = emptyMap(),
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
                spec.shapes.map { shape -> RouteBadgePath(shape.points) },
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
                    directionId = spec.key.directionId,
                )
            )
        }
    }
}

private data class RouteBadgeSpec(
    val key: RouteDirectionKey,
    val route: ObaRoute,
    val name: String,
    val shapes: List<FocusedTripShape>,
)

/** Presented route-direction identities at each scheduled stop, optionally narrowed to [route]. */
internal fun FocusedTripStops.routeDirectionsByStopId(
    trips: Set<FocusedTrip>,
    route: RouteDirectionKey? = null,
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
