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
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.Polyline

/**
 * Snapping route stops onto their drawn shapes for the route-map overlays. Kept out of
 * [RouteMapPresentationPlan] (which is deliberately `android.location.Location`-free so it stays
 * JVM-testable): these snap against [Polyline], so they're covered by the instrumented
 * `RouteStopProjectionTest` rather than a plain-JVM unit test.
 */

/**
 * Snap each of a focused stop's exact scheduled stops to the closest successful shape of a trip that
 * serves it. A stop that can't be placed on any candidate shape is omitted (not carried at its raw
 * location — the caller keeps only stops it can draw on the focused geometry).
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
 * location, so it still shows — just off the line.
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
