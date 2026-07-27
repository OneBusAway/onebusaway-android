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
import org.onebusaway.android.map.layout.RouteBadgeLayoutInput
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.layoutRouteBadges
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO
import org.onebusaway.android.map.render.NEARBY_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint

/**
 * The "routes near here" hoop (#2004): a circle around a point on the base map, holding the routes
 * nearest it — which are then drawn lightly in full, well beyond the circle. [center] is where the layer
 * was last surveyed, which is what the drawn routes answer for; the ring a rider sees is drawn in screen
 * space at the viewport centre and never reads it (see [hoopRadiusDp]).
 *
 * [radiusMeters] is **derived, not fixed**: it is however far you have to reach from [center] to gather
 * the target number of routes, out to a ceiling (see [hoopRadiusForTarget]). A fixed radius made the
 * layer's density a property of the neighbourhood — half a mile of downtown Seattle holds fifty routes
 * and half a mile of a residential street holds four — so the same ring was unreadable in one place and
 * empty in the other. Scaling it to the routes instead keeps roughly the same amount on screen wherever
 * there is transit to show, which is what makes the layer legible at all.
 *
 * The ceiling is what keeps it honest. Reaching however far it took to hit the target would, in a sparse
 * place, draw a multi-kilometre ring around routes a rider cannot get to, labelled as "near you". Capped,
 * the ring always means "within a plausible trip of here" — and where that holds fewer routes than the
 * target, the answer is simply that there are fewer routes.
 */
data class NearbyRoutesHoop(val center: GeoPoint, val radiusMeters: Double)

/** One route the survey found, and how far [NearbyRoutesHoop.center] is from its nearest serving stop. */
internal data class RankedRoute(val route: ObaRoute, val meters: Double)

/**
 * Every route served by [stops], ordered by how close its nearest stop comes to [center] — the distance
 * a rider actually cares about, since a stop is where they'd board.
 *
 * [routes] is the stop query's own reference pool, which resolves every route id the stops carry; an id
 * it can't resolve is dropped rather than drawn nameless. Ties break on route id so the drawn set and
 * its palette stay stable between settles of the same corner.
 */
internal fun rankRoutesByNearestStop(
    center: GeoPoint,
    stops: Collection<ObaStop>,
    routes: Collection<ObaRoute>
): List<RankedRoute> {
    val nearestMeters = HashMap<String, Double>()
    for (stop in stops) {
        val distance = haversineMeters(center, GeoPoint(stop.latitude, stop.longitude))
        for (routeId in stop.routeIds) {
            val best = nearestMeters[routeId]
            if (best == null || distance < best) nearestMeters[routeId] = distance
        }
    }
    val byId = routes.associateBy(ObaRoute::id)
    return nearestMeters.entries
        .mapNotNull { (routeId, meters) -> byId[routeId]?.let { RankedRoute(it, meters) } }
        .sortedWith(compareBy({ it.meters }, { it.route.id }))
}

/**
 * How far the hoop reaches. [maxMeters] — the distance actually searched — unless that holds more than
 * [target] routes, in which case it pulls in to the [target]-th nearest.
 *
 * The ring is the search area, not a wrapper drawn around whatever was found. Shrinking is purely a
 * legibility measure for the places that would otherwise be unreadable: a dense downtown holds fifty
 * routes within the search radius, so the ring closes in until it holds about [target]. Where fewer than
 * that exist the ring stays at full reach and simply holds fewer — pulling it in to hug the farthest of
 * six routes would misreport the search as having been narrower than it was, and would make the ring
 * jitter between settles as the outermost route came and went.
 *
 * The drawn count lands *on or above* [target] rather than exactly on it, because the radius is a distance
 * and routes sharing a stop share a distance — downtown, one transit-tunnel stop can add a dozen routes at
 * a single stroke. Taking the radius and then drawing everything inside it keeps that honest; truncating
 * to exactly [target] would mean picking arbitrarily among routes the data says are equally near.
 */
internal fun hoopRadiusForTarget(ranked: List<RankedRoute>, target: Int, maxMeters: Double): Double {
    if (ranked.size <= target) return maxMeters
    return ranked[target - 1].meters.coerceAtMost(maxMeters)
}

/**
 * The hoop's bounding box, as the (latitude, longitude) spans stops-for-location takes — a square around
 * the circle, so the corners reach past the ring.
 */
internal fun NearbyRoutesHoop.spanDegrees(): Pair<Double, Double> {
    val latSpan = Math.toDegrees(2.0 * radiusMeters / EARTH_RADIUS_METERS)
    // Longitude degrees shrink toward the poles; guard the division for a camera parked at one.
    val cosLatitude = cos(Math.toRadians(center.latitude)).coerceAtLeast(MIN_LONGITUDE_SCALE)
    return latSpan to latSpan / cosLatitude
}

/** One route the hoop draws: its identity, badge label, and whole-route (all-directions) geometry. */
internal data class NearbyRouteShapes(
    val routeId: String,
    val displayName: String,
    val shapes: List<List<GeoPoint>>
)

/**
 * The subset of [routes] whose shapes are in [resolved], in [routes]' own (nearest-first) order.
 *
 * Used both to seed a survey from what the previous one drew and to build each emission as shapes land,
 * so the drawn order is always the ranked one. That matters because badge layout is order-priority —
 * earlier entries are placed first and never moved — so ordering by rank keeps a route's label where it
 * was as the plan fills in, instead of letting it depend on which fetch happened to return first.
 */
internal fun nearbyRoutePlan(
    routes: List<ObaRoute>,
    resolved: Map<String, NearbyRouteShapes>
): List<NearbyRouteShapes> = routes.mapNotNull { resolved[it.id] }

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
 * [routes] are the ones the survey selected — the nearest routes to the hoop's centre, chosen by the
 * ranking that also set its radius. The hoop selects; it doesn't crop, so everything passed in is drawn
 * *in full*, and the layer answers "where do the routes running past me actually go", not just "what
 * does the area around me look like".
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

/** Applies [alpha01] (`0..1`) to [baseColor]'s RGB, producing an ARGB colour. */
private fun withAlpha(baseColor: Int, alpha01: Float): Int = ((alpha01.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (baseColor and 0x00FFFFFF)

/** How lightly the route lines are drawn — ambient context beneath the basemap's own labels. */
private const val NEARBY_ROUTE_LINE_ALPHA = 0.65f

/** Guards the longitude-span division for a camera parked at a pole. */
private const val MIN_LONGITUDE_SCALE = 1e-6

/** Mercator is undefined at the poles; clamp like the route-render pipeline does. */
private const val MAX_MERCATOR_LATITUDE = 85.05112878

/** A defensive clamp on the zoom exponent, mirroring the route-render pipeline's. */
private const val MAX_MAP_ZOOM = 30.0
