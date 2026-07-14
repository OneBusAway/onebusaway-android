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

import kotlin.math.cos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.TripPatternGeometry

private val ADJACENCY_ROUTE_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY,
)

internal const val ADJACENCY_DOWNSTREAM_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP
internal const val ADJACENCY_UPSTREAM_LINE_WIDTH_DP = ROUTE_LINE_WIDTH_DP * 0.275f

/**
 * Draws the exact trip-pattern lines with upcoming arrivals at one focused stop, narrowing the
 * upstream stroke and emphasizing downstream travel. This is an overlay on nearby-stops mode,
 * distinct from [RouteMapController]'s mutually exclusive single-route mode. It owns
 * [MapRenderState]'s route polylines only while [session] is non-null.
 */
internal class AdjacencyMapController(
    private val renderState: MapRenderState,
    private val repository: AdjacencyRouteShapeRepository,
    private val scope: CoroutineScope,
) {

    private data class Session(
        val stopId: String,
        val stopPoint: GeoPoint,
        val tripPatterns: Set<TripPatternGeometry>,
    )

    private var session: Session? = null

    private var loadJob: Job? = null

    /**
     * Load and draw [tripPatterns] for [stopId]. Repeating the same session is a no-op so periodic
     * arrivals refreshes do not repeatedly fetch unchanged shapes. A changed session replaces the
     * old lines.
     */
    fun start(
        stopId: String,
        stopPoint: GeoPoint,
        tripPatterns: Set<TripPatternGeometry>,
    ) {
        if (tripPatterns.isEmpty()) {
            stop()
            return
        }
        val next = Session(stopId, stopPoint, LinkedHashSet(tripPatterns))
        if (session == next) return

        stop()
        session = next
        loadJob = scope.launch {
            val result = repository.getShapes(next.tripPatterns)
            // A repository fetch can outlive a cancelled join through SingleFlight. Only the session
            // that is still current may publish into the shared route-polyline slot.
            if (session == next) {
                renderState.setRoutePolylines(result.toRoutePolylines(next.stopPoint))
            }
        }
    }

    /**
     * Cancel adjacency loading and clear its lines. If no adjacency session owns the shared slot, this
     * is deliberately a no-op so a late clear cannot erase a single-route controller's line.
     */
    fun stop() {
        if (session == null) return
        session = null
        loadJob?.cancel()
        loadJob = null
        renderState.clearRoutePolylines()
    }
}

/**
 * Convert every trip-pattern shape into a thin upstream segment and a wide downstream segment.
 * GTFS shape points are travel-ordered; [stopPoint] is projected onto the nearest segment so both
 * strokes meet exactly even when the stop falls between vertices.
 */
internal fun AdjacencyShapes.toRoutePolylines(stopPoint: GeoPoint): List<RoutePolyline> = buildList {
    shapes.values.forEach { shape ->
        val split = splitPolylineAtStop(shape.points, stopPoint) ?: return@forEach
        split.upstream.takeIf { it.size >= 2 }?.let { points ->
            add(
                RoutePolyline(
                    color = shape.routeColor,
                    points = points,
                    widthDp = ADJACENCY_UPSTREAM_LINE_WIDTH_DP,
                    transforms = ADJACENCY_ROUTE_TRANSFORMS,
                )
            )
        }
        split.downstream.takeIf { it.size >= 2 }?.let { points ->
            add(
                RoutePolyline(
                    color = shape.routeColor,
                    points = points,
                    widthDp = ADJACENCY_DOWNSTREAM_LINE_WIDTH_DP,
                    transforms = ADJACENCY_ROUTE_TRANSFORMS,
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
internal fun splitPolylineAtStop(
    points: List<GeoPoint>,
    stopPoint: GeoPoint,
): StopSplitPolyline? {
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
        val fraction = if (lengthSquared <= 0.0) {
            0.0
        } else {
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        }
        val projectedX = ax + fraction * dx
        val projectedY = ay + fraction * dy
        val distanceSquared =
            (projectedX - px) * (projectedX - px) +
                (projectedY - py) * (projectedY - py)
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
            latitude = start.latitude + bestFraction * (end.latitude - start.latitude),
            longitude = start.longitude + bestFraction * (end.longitude - start.longitude),
        )
    }
    val upstream = points.subList(0, bestSegment + 1).toMutableList()
    if (upstream.last() != splitPoint) upstream += splitPoint
    val downstream = mutableListOf(splitPoint)
    if (splitPoint == end) {
        downstream += points.subList(bestSegment + 2, points.size)
    } else {
        downstream += points.subList(bestSegment + 1, points.size)
    }
    return StopSplitPolyline(upstream, downstream)
}
