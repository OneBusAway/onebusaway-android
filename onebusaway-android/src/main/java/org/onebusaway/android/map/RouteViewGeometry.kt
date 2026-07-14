/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.onebusaway.android.map

import kotlin.math.cos
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform

internal val ROUTE_VIEW_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY,
)

internal const val ROUTE_DOWNSTREAM_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP
internal const val ROUTE_UPSTREAM_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 0.275f

/** Convert exact trip shapes into narrow-upstream/wide-downstream directional route lines. */
internal fun FocusedTripGeometry.toRoutePolylines(stopPoint: GeoPoint): List<RoutePolyline> = buildList {
    shapes.values.forEach { shape ->
        val split = splitPolylineAtStop(shape.points, stopPoint) ?: return@forEach
        split.upstream.takeIf { it.size >= 2 }?.let { points ->
            add(
                RoutePolyline(
                    shape.routeColor,
                    points,
                    ROUTE_UPSTREAM_LINE_WIDTH_DP,
                    directional = true,
                    transforms = ROUTE_VIEW_TRANSFORMS,
                )
            )
        }
        split.downstream.takeIf { it.size >= 2 }?.let { points ->
            add(
                RoutePolyline(
                    shape.routeColor,
                    points,
                    ROUTE_DOWNSTREAM_LINE_WIDTH_DP,
                    directional = true,
                    transforms = ROUTE_VIEW_TRANSFORMS,
                )
            )
        }
    }
}

internal data class StopSplitPolyline(
    val upstream: List<GeoPoint>,
    val downstream: List<GeoPoint>,
)

/** Project [stopPoint] onto [points], then split the travel-ordered line at that projection. */
internal fun splitPolylineAtStop(points: List<GeoPoint>, stopPoint: GeoPoint): StopSplitPolyline? {
    if (points.size < 2) return null
    val cosLat = cos(Math.toRadians(stopPoint.latitude))
    val px = stopPoint.longitude * cosLat
    val py = stopPoint.latitude
    var bestSegment = 0
    var bestFraction = 0.0
    var bestDistanceSquared = Double.MAX_VALUE

    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        val ax = start.longitude * cosLat
        val ay = start.latitude
        val dx = end.longitude * cosLat - ax
        val dy = end.latitude - ay
        val lengthSquared = dx * dx + dy * dy
        val fraction = if (lengthSquared <= 0.0) 0.0 else
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val projectedX = ax + fraction * dx
        val projectedY = ay + fraction * dy
        val distanceSquared =
            (projectedX - px) * (projectedX - px) + (projectedY - py) * (projectedY - py)
        if (distanceSquared < bestDistanceSquared) {
            bestDistanceSquared = distanceSquared
            bestSegment = index
            bestFraction = fraction
        }
    }

    val start = points[bestSegment]
    val end = points[bestSegment + 1]
    val splitPoint = when (bestFraction) {
        0.0 -> start
        1.0 -> end
        else -> GeoPoint(
            start.latitude + bestFraction * (end.latitude - start.latitude),
            start.longitude + bestFraction * (end.longitude - start.longitude),
        )
    }
    val upstream = points.subList(0, bestSegment + 1).toMutableList()
    if (upstream.last() != splitPoint) upstream += splitPoint
    val downstream = mutableListOf(splitPoint)
    if (splitPoint == end) downstream += points.subList(bestSegment + 2, points.size)
    else downstream += points.subList(bestSegment + 1, points.size)
    return StopSplitPolyline(upstream, downstream)
}
