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
import kotlin.math.sin
import kotlin.math.sqrt
import org.onebusaway.android.map.layout.RouteBadgeLayoutInput
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.layoutRouteBadges
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.NEARBY_ROUTES_HOOP_WIDTH_PROFILE
import org.onebusaway.android.map.render.NEARBY_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint

/**
 * The "routes near here" hoop (#2004): a fixed-radius circle around a point on the base map, inside
 * which the routes that pass through are drawn lightly. [center] is where the layer was last surveyed
 * — deliberately *not* the live camera centre, so the ring marks the area actually queried and stays
 * put until the camera drifts far enough to warrant a refresh (see [NearbyRoutesController]).
 */
data class NearbyRoutesHoop(val center: GeoPoint, val radiusMeters: Double)

/** One route the hoop draws: its identity, badge label, and whole-route (all-directions) geometry. */
internal data class NearbyRouteShapes(
    val routeId: String,
    val displayName: String,
    val shapes: List<List<GeoPoint>>
)

/** The hoop layer's complete render plan: the ring + each route's clipped lines, and one badge per route. */
internal data class NearbyRoutesPresentation(
    val polylines: List<RoutePolyline>,
    val badges: List<RouteBadge>
)

/**
 * The routes serving a stop inside [hoop], nearest first, capped at [limit].
 *
 * "The routes inside the hoop" is derived from the stops the map has already loaded rather than from a
 * second query, because that is *exactly* how the server defines it: `routes-for-location` with no name
 * query resolves to "the routes of every stop within the bounds" (`RoutesBeanServiceImpl
 * .getRoutesWithoutRouteNameQuery`), which is the same set — and it returns that set from a `HashSet`,
 * i.e. in no useful order, so it could not rank the cap anyway. Ranking each route by its nearest
 * serving stop gives the cap a meaning the endpoint can't: the [limit] routes closest to the centre.
 * Ties break on route id so the drawn set is stable across refreshes.
 *
 * [stops] is the map's accumulated nearby-stop layer; stops outside [hoop] are ignored (the layer is
 * bounded to the stops nearest the camera centre, so an in-hoop stop is present whenever the viewport
 * load covered it).
 */
internal fun nearbyRouteIds(
    hoop: NearbyRoutesHoop,
    stops: Collection<StopMarker>,
    limit: Int
): List<String> {
    val nearestStopMeters = HashMap<String, Double>()
    for (marker in stops) {
        val distance = haversineMeters(hoop.center, marker.point)
        if (distance > hoop.radiusMeters) continue
        for (routeId in marker.stop.routeIds) {
            val best = nearestStopMeters[routeId]
            if (best == null || distance < best) nearestStopMeters[routeId] = distance
        }
    }
    return nearestStopMeters.entries
        .sortedWith(compareBy({ it.value }, { it.key }))
        .take(limit)
        .map { it.key }
}

/**
 * Build the hoop layer: the ring, then each route's shapes clipped to the hoop, then one badge per
 * route. A route whose shape doesn't actually enter the hoop is dropped entirely — its stop is inside
 * but the line only passes nearby — so the layer never badges a route it hasn't drawn.
 *
 * Every direction of a route shares one [colors] entry, per #2004: the hoop answers "what runs through
 * here", a question the direction split doesn't change. Lines are drawn at reduced alpha and half the
 * ordinary stroke so the layer reads as ambient context under the basemap's labels; the badge keeps
 * the palette colour at full opacity, since it is both the legend and the tap target.
 *
 * Pure, so the clipping and the badge/line plan are unit-tested without the controller.
 */
internal fun assembleNearbyRoutesPresentation(
    hoop: NearbyRoutesHoop,
    routes: List<NearbyRouteShapes>,
    colors: Map<String, Int>
): NearbyRoutesPresentation {
    val drawn = routes.mapNotNull { route ->
        route.shapes
            .flatMap { shape -> clipToHoop(shape, hoop) }
            .takeIf { it.isNotEmpty() }
            ?.let { route to it }
    }
    val polylines = buildList {
        add(hoopRingPolyline(hoop))
        for ((route, segments) in drawn) {
            val color = colors[route.routeId] ?: DEFAULT_ROUTE_LINE_COLOR
            for (segment in segments) {
                add(
                    RoutePolyline(
                        color = withAlpha(color, NEARBY_ROUTE_LINE_ALPHA),
                        points = segment,
                        widthProfile = NEARBY_ROUTE_LINE_WIDTH_PROFILE,
                        // No viewport clip: the hoop already bounds these to a half-mile circle.
                        transforms = setOf(RoutePolylineTransform.ZOOM_SIMPLIFY)
                    )
                )
            }
        }
    }
    val placements = layoutRouteBadges(
        drawn.map { (route, segments) ->
            RouteBadgeLayoutInput(
                // The hoop badges a whole route, not one of its directions, so the badge carries no
                // direction: a tap enters route focus on the route's own default direction.
                RouteDirectionKey(route.routeId, null),
                segments.map(::RouteBadgePath)
            )
        }
    ).associateBy { it.route.routeId }
    val badges = drawn.mapNotNull { (route, _) ->
        val placement = placements[route.routeId] ?: return@mapNotNull null
        RouteBadge(
            routeId = route.routeId,
            routeShortName = route.displayName,
            color = colors[route.routeId] ?: DEFAULT_ROUTE_LINE_COLOR,
            point = placement.point,
            directionId = null
        )
    }
    return NearbyRoutesPresentation(polylines, badges)
}

/** The hoop's own ring: a faint closed circle marking the extent the layer surveyed. */
internal fun hoopRingPolyline(hoop: NearbyRoutesHoop): RoutePolyline = RoutePolyline(
    color = NEARBY_ROUTES_HOOP_COLOR,
    points = hoopRing(hoop),
    widthProfile = NEARBY_ROUTES_HOOP_WIDTH_PROFILE
)

/** The hoop's circumference as a closed polyline (first point repeated last). */
internal fun hoopRing(hoop: NearbyRoutesHoop, segments: Int = HOOP_RING_SEGMENTS): List<GeoPoint> {
    val projection = LocalMeters(hoop.center)
    return (0..segments).map { step ->
        val angle = 2.0 * Math.PI * step / segments
        projection.toGeoPoint(hoop.radiusMeters * cos(angle), hoop.radiusMeters * sin(angle))
    }
}

/**
 * The portions of [points] that lie inside [hoop], as separate polylines — a route that crosses the
 * circle twice yields two. Entry/exit points are interpolated onto the circle so each drawn line ends
 * exactly on the ring; interior vertices are passed through untouched. Runs shorter than two points
 * are dropped.
 */
internal fun clipToHoop(points: List<GeoPoint>, hoop: NearbyRoutesHoop): List<List<GeoPoint>> {
    if (points.size < 2) return emptyList()
    val projection = LocalMeters(hoop.center)
    val projected = points.map(projection::toMeters)
    val runs = mutableListOf<List<GeoPoint>>()
    var current: MutableList<GeoPoint>? = null

    fun flush() {
        current?.takeIf { it.size >= 2 }?.let(runs::add)
        current = null
    }

    for (index in 0 until points.lastIndex) {
        val span = insideCircleSpan(projected[index], projected[index + 1], hoop.radiusMeters)
        if (span == null) {
            flush()
            continue
        }
        val (entry, exit) = span
        val start = if (entry <= 0.0) points[index] else interpolate(points[index], points[index + 1], entry)
        val end = if (exit >= 1.0) points[index + 1] else interpolate(points[index], points[index + 1], exit)
        val run = current
        if (run == null || entry > 0.0) {
            flush()
            current = mutableListOf(start, end)
        } else {
            run.add(end)
        }
        // The segment left the circle here, so the run ends at this exit point.
        if (exit < 1.0) flush()
    }
    flush()
    return runs
}

/**
 * The sub-interval of the segment `start`→`end` (as fractions in `0..1`) that lies within
 * [radiusMeters] of the origin, or null when no part of it does. Both endpoints are already in a
 * local metre frame centred on the circle, so this is the ordinary quadratic ray/circle intersection.
 */
private fun insideCircleSpan(start: MeterPoint, end: MeterPoint, radiusMeters: Double): Pair<Double, Double>? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val a = dx * dx + dy * dy
    val c = start.x * start.x + start.y * start.y - radiusMeters * radiusMeters
    // A zero-length segment is inside iff its (single) point is.
    if (a == 0.0) return if (c <= 0.0) 0.0 to 1.0 else null
    val b = 2.0 * (start.x * dx + start.y * dy)
    val discriminant = b * b - 4.0 * a * c
    if (discriminant <= 0.0) return null
    val root = sqrt(discriminant)
    val entry = ((-b - root) / (2.0 * a)).coerceAtLeast(0.0)
    val exit = ((-b + root) / (2.0 * a)).coerceAtMost(1.0)
    return if (exit > entry) entry to exit else null
}

/** Straight-line interpolation in lat/lon — exact enough over the hoop's sub-kilometre segments. */
private fun interpolate(start: GeoPoint, end: GeoPoint, fraction: Double) = GeoPoint(
    start.latitude + (end.latitude - start.latitude) * fraction,
    start.longitude + (end.longitude - start.longitude) * fraction
)

private data class MeterPoint(val x: Double, val y: Double)

/**
 * An equirectangular metre frame centred on [origin] — the same local projection the route-line
 * simplifier uses, and accurate well past the hoop's radius.
 */
private class LocalMeters(private val origin: GeoPoint) {
    private val latitudeScale = EARTH_RADIUS_METERS
    private val longitudeScale = EARTH_RADIUS_METERS * cos(Math.toRadians(origin.latitude))

    fun toMeters(point: GeoPoint) = MeterPoint(
        x = Math.toRadians(point.longitude - origin.longitude) * longitudeScale,
        y = Math.toRadians(point.latitude - origin.latitude) * latitudeScale
    )

    fun toGeoPoint(x: Double, y: Double) = GeoPoint(
        latitude = origin.latitude + Math.toDegrees(y / latitudeScale),
        longitude = origin.longitude + Math.toDegrees(x / longitudeScale)
    )
}

/** Applies [alpha01] (`0..1`) to [baseColor]'s RGB, producing an ARGB colour. */
private fun withAlpha(baseColor: Int, alpha01: Float): Int = ((alpha01.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (baseColor and 0x00FFFFFF)

/**
 * The hoop's radius: half a mile (#2004). A fixed radius — not a zoom-derived one — is the point of
 * the layer: it always answers the same question ("what runs within a walk of here"), so the drawn set
 * doesn't silently change meaning as the camera moves.
 */
internal const val NEARBY_ROUTES_RADIUS_METERS = 800.0

/**
 * How many routes the hoop draws. A display + network budget, not a data rule: each route costs one
 * (cached, shared) stops-for-route fetch, and a downtown hoop can contain far more routes than read
 * legibly at once. [nearbyRouteIds] spends the budget on the routes nearest the centre.
 */
internal const val MAX_NEARBY_ROUTES = 12

/** How lightly the route lines are drawn — ambient context beneath the basemap's own labels. */
private const val NEARBY_ROUTE_LINE_ALPHA = 0.65f

/** The ring's colour: neutral grey at low alpha, legible over both the light and dark basemaps. */
private const val NEARBY_ROUTES_HOOP_COLOR = 0x59757575

/** Enough segments that the ring reads as a circle at any zoom the layer is shown at. */
private const val HOOP_RING_SEGMENTS = 96
