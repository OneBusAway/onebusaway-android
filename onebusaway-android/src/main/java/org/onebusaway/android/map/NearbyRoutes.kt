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
import kotlin.math.pow
import kotlin.math.sqrt
import org.onebusaway.android.map.layout.RouteBadgeLayoutInput
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.layoutRouteBadges
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO
import org.onebusaway.android.map.render.NEARBY_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint

/**
 * The "routes near here" hoop (#2004): a fixed-radius circle around a point on the base map, selecting
 * the routes that pass through it — which are then drawn lightly in full, well beyond the circle.
 * [center] is where the layer was last surveyed, which is what the drawn routes answer for; the ring a
 * rider sees is drawn in screen space at the viewport centre and never reads it (see [hoopRadiusDp]).
 */
data class NearbyRoutesHoop(val center: GeoPoint, val radiusMeters: Double)

/** One route the hoop draws: its identity, badge label, and whole-route (all-directions) geometry. */
internal data class NearbyRouteShapes(
    val routeId: String,
    val displayName: String,
    val shapes: List<List<GeoPoint>>
)

/** The hoop layer's complete render plan: each qualifying route's full shape, and one badge per route. */
internal data class NearbyRoutesPresentation(
    val polylines: List<RoutePolyline>,
    val badges: List<RouteBadge>
)

/**
 * Build the hoop layer: the **whole** shape of every route that enters the hoop, and one badge per
 * route. (The ring itself isn't here — it is drawn in screen space over the map, so that a pan slides
 * it with the gesture; see [hoopRadiusDp].)
 *
 * [routes] are the ones that already qualified — the hoop selects, it doesn't crop, and that selection
 * ([entersHoop]) is made once per route as its shape resolves rather than re-derived here. Everything
 * passed in is drawn *in full*, so the layer answers "where do the routes running past me actually go",
 * not just "what does the half mile around me look like".
 *
 * Badges are laid out along the routes' **whole** shapes, spread across the map the way a transit map
 * labels its lines — never anchored inside the ring. The hoop is a selector, not a legend box: piling
 * every label into it stacks them on one spot, and a label sitting on the line it names is what makes
 * the line readable where the rider is looking at it.
 *
 * Every direction of a route shares one [colors] entry, per #2004: the hoop answers "what runs through
 * here", a question the direction split doesn't change. Lines are drawn at reduced alpha and a thin
 * stroke so the layer reads as ambient context under the basemap's labels; the badge keeps the palette
 * colour at full opacity, since it is both the legend and the tap target.
 *
 * Pure, so the badge/line plan is unit-tested without the controller.
 */
internal fun assembleNearbyRoutesPresentation(
    routes: List<NearbyRouteShapes>,
    colors: Map<String, Int>
): NearbyRoutesPresentation {
    val polylines = buildList {
        for (route in routes) {
            val color = colors[route.routeId] ?: DEFAULT_ROUTE_LINE_COLOR
            for (shape in route.shapes) {
                add(
                    RoutePolyline(
                        color = withAlpha(color, NEARBY_ROUTE_LINE_ALPHA),
                        points = shape,
                        widthProfile = NEARBY_ROUTE_LINE_WIDTH_PROFILE,
                        // Whole-route geometry, so it needs the same viewport clip + zoom
                        // simplification route view uses to stay cheap to draw.
                        transforms = ROUTE_VIEW_TRANSFORMS
                    )
                )
            }
        }
    }
    val placements = layoutRouteBadges(
        routes.map { route ->
            RouteBadgeLayoutInput(
                // The hoop badges a whole route, not one of its directions, so the badge carries no
                // direction: a tap enters route focus on the route's own default direction.
                RouteDirectionKey(route.routeId, null),
                route.shapes.map(::RouteBadgePath)
            )
        }
    ).associateBy { it.route.routeId }
    val badges = routes.mapNotNull { route ->
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

/**
 * The hoop's on-screen radius, in dp, at [zoom] and [latitude] — Web Mercator's ground resolution,
 * which is what a map zoom level means.
 *
 * The ring is drawn in screen space at the centre of the viewport rather than as map geometry, so that
 * a pan carries it along with the gesture (a geographic circle would slide away under the drag) while
 * the route survey waits for the camera to settle. Mercator is conformal, so at this scale a circle on
 * the ground really is a circle on screen — only its radius changes, and only with zoom.
 */
internal fun hoopRadiusDp(radiusMeters: Double, zoom: Double, latitude: Double): Float {
    val metersPerPixel = METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO *
        cos(Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))) /
        2.0.pow(zoom.coerceIn(0.0, MAX_MAP_ZOOM))
    return (radiusMeters / metersPerPixel).toFloat()
}

/**
 * Whether any part of [points] lies inside [hoop] — the layer's membership test, and the only question
 * the hoop's geometry is asked. A route qualifies by its *shape* passing through the circle, not merely
 * by serving a stop inside it, so a line that only skirts the ring is never drawn or badged.
 *
 * The whole route is what gets drawn and labelled, so the intersection itself is of no interest and is
 * never built: this walks the segments and stops at the first one that reaches the circle.
 */
internal fun entersHoop(points: List<GeoPoint>, hoop: NearbyRoutesHoop): Boolean {
    if (points.size < 2) return false
    val projection = LocalMeters(hoop.center)
    var previous = projection.toMeters(points[0])
    for (index in 0 until points.lastIndex) {
        val next = projection.toMeters(points[index + 1])
        if (insideCircleSpan(previous, next, hoop.radiusMeters) != null) return true
        previous = next
    }
    return false
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
}

/** Applies [alpha01] (`0..1`) to [baseColor]'s RGB, producing an ARGB colour. */
private fun withAlpha(baseColor: Int, alpha01: Float): Int = ((alpha01.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (baseColor and 0x00FFFFFF)

/**
 * The hoop's radius: half a mile (#2004). A fixed radius — not a zoom-derived one — is the point of
 * the layer: it always answers the same question ("what runs within a walk of here"), so the drawn set
 * doesn't silently change meaning as the camera moves.
 */
internal const val NEARBY_ROUTES_RADIUS_METERS = 800.0

/** How lightly the route lines are drawn — ambient context beneath the basemap's own labels. */
private const val NEARBY_ROUTE_LINE_ALPHA = 0.65f

/** Mercator is undefined at the poles; clamp like the route-render pipeline does. */
private const val MAX_MERCATOR_LATITUDE = 85.05112878

/** A defensive clamp on the zoom exponent, mirroring the route-render pipeline's. */
private const val MAX_MAP_ZOOM = 30.0
