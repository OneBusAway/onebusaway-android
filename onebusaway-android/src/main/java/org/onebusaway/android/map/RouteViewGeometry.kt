/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.onebusaway.android.map

import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.FocusedTrip

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
