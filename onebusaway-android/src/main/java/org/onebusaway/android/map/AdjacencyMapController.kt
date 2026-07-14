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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_DP
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform

private val ADJACENCY_ROUTE_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY,
)

/**
 * Draws the whole-route lines for the routes with upcoming arrivals at one focused stop. This is an
 * overlay on nearby-stops mode, distinct from [RouteMapController]'s mutually exclusive single-route
 * mode. It owns [MapRenderState]'s route polylines only while [session] is non-null.
 */
internal class AdjacencyMapController(
    private val renderState: MapRenderState,
    private val repository: AdjacencyRouteShapeRepository,
    private val scope: CoroutineScope,
) {

    private data class Session(val stopId: String, val routeIds: Set<String>)

    private var session: Session? = null

    private var loadJob: Job? = null

    /**
     * Load and draw [routeIds] for [stopId]. Repeating the same session is a no-op so periodic arrivals
     * refreshes do not repeatedly fetch unchanged shapes. A changed session replaces the old lines.
     */
    fun start(stopId: String, routeIds: Set<String>) {
        if (routeIds.isEmpty()) {
            stop()
            return
        }
        val next = Session(stopId, LinkedHashSet(routeIds))
        if (session == next) return

        stop()
        session = next
        loadJob = scope.launch {
            val result = repository.getShapes(next.routeIds)
            // A repository fetch can outlive a cancelled join through SingleFlight. Only the session
            // that is still current may publish into the shared route-polyline slot.
            if (session == next) {
                renderState.setRoutePolylines(result.toRoutePolylines())
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

/** Convert every successfully resolved whole-route shape into the existing shared render model. */
internal fun AdjacencyShapes.toRoutePolylines(): List<RoutePolyline> =
    shapes.values.flatMap { shape ->
        shape.polylines
            .filter { it.size >= 2 }
            .map { points ->
                RoutePolyline(
                    color = shape.route?.color,
                    points = points,
                    widthDp = ROUTE_LINE_WIDTH_DP,
                    transforms = ADJACENCY_ROUTE_TRANSFORMS,
                )
            }
    }
