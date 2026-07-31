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

import kotlin.math.abs
import kotlin.math.cos
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.haversineDistance

/**
 * Presenting a trip-plan leg's **ridden segment** over its route in route focus: draw the route's approach
 * to the boarding point, the segment cased on top, and keep only the segment's stops. Pure
 * geometry over flavor-neutral [GeoPoint]/[RoutePolyline]/[ObaStop] (like [RouteViewGeometry] /
 * [projectStopsOntoPolylines]), so it stays JVM-testable and out of [RouteMapController]'s state plumbing.
 */

/** How close a stop must sit to the ridden path to count as "on the segment" — well below transit stop
 *  spacing, so the nearest off-segment stop is never wrongly included. */
const val SEGMENT_STOP_TOLERANCE_METERS = 50.0

/** How close a board/alight anchor must sit to a direction shape variant to count as travelling that
 *  variant ([upstreamTo], [boundedThrough]). Same 50 m drift class as [SEGMENT_STOP_TOLERANCE_METERS],
 *  but its own knob: retuning stop inclusion must not silently retune variant applicability. */
const val VARIANT_MATCH_TOLERANCE_METERS = 50.0

/** A ridden segment worth drawing/framing: a polyline needs at least two points. Below that it's the
 *  "no segment" case — plain route focus (whole route drawn/framed, all stops kept). */
internal fun List<GeoPoint>.isDrawableSegment() = size >= 2

/**
 * Compose the route's polylines when [segment] is highlighted: the route [base] upstream of the boarding
 * point drawn as the selected line's approach, then the rider's [itineraryContext], then the ridden segment
 * in [routeColor]. This order makes the visual hierarchy match the semantics: where the vehicle comes from,
 * the committed journey, the current leg. Without a drawable segment, retains the ordinary plain-route
 * ordering: itinerary context beneath [base], with no approach restyle or selected overlay.
 *
 * The segment keeps [ITINERARY_RIDE_WIDTH_PROFILE] — the very weight it had as a leg of the itinerary it
 * was tapped from — and says it is the selected one with a case rather than by out-widening its
 * surroundings (#2082). Its approach carries the same case, so the two read as one route line stepping down
 * where the rider boards.
 */
internal fun routePolylinesWithSegment(
    base: List<RoutePolyline>,
    segment: List<GeoPoint>,
    routeColor: Int?,
    itineraryContext: List<RoutePolyline> = emptyList()
): List<RoutePolyline> {
    val overlay = segment.takeIf { it.isDrawableSegment() }?.let {
        RoutePolyline(color = routeColor, points = it, widthProfile = ITINERARY_RIDE_WIDTH_PROFILE, directional = true)
            .withCase()
    } ?: return itineraryContext + base
    return base.asSelectedRouteApproach() + itineraryContext + overlay
}

/**
 * Keep a direction's geometry only through [anchor], the boarding point. Each receiver entry is an
 * independent shape **variant** of the direction — stops-for-route supplies one complete travel-ordered
 * polyline per distinct trip shape (see `RouteMap.polylinesByDirection`), never consecutive fragments of
 * one line (#2104). Every variant the boarding point sits on is clipped at its own boarding projection;
 * a variant that never touches the boarding point isn't an approach to it and is dropped. When the
 * boarding point is off every variant (a leg-geometry mismatch), the single nearest variant is clipped
 * so the approach still draws.
 *
 * Variants that diverge only *after* the boarding point clip to the same approach, so the result is
 * de-duplicated: the shared trunk is drawn (and cased) once, not once per variant. That only catches
 * exactly-equal geometry — variants encoding the same street with different vertex sampling stay
 * distinct and still draw over each other.
 */
internal fun List<RoutePolyline>.upstreamTo(anchor: GeoPoint?): List<RoutePolyline> {
    anchor ?: return this
    val projections = variantProjections(anchor)
    return projections.travelledVariants()
        .ifEmpty { listOfNotNull(projections.minByOrNull { it.projection.distanceToPoint }) }
        .mapNotNull { (line, path, projection) ->
            path.subPolyline(0.0, projection.distanceAlong)
                ?.takeIf { it.isDrawableSegment() && it.first() != it.last() }
                ?.let { line.copy(points = it) }
        }
        .distinct()
}

/** A route line whose eligible travel ends at [maxDistanceAlong]. */
internal data class BoundedRoutePath(
    val line: Polyline,
    val maxDistanceAlong: Double
) {
    /** The common sampler tolerance, paid once here instead of once per vehicle and frame. */
    private val samplerBounds = PaddedBounds.around(line.points, SEGMENT_STOP_TOLERANCE_METERS)

    fun mayContain(point: GeoPoint, toleranceMeters: Double): Boolean =
        toleranceMeters != SEGMENT_STOP_TOLERANCE_METERS || samplerBounds.contains(point)
}

private data class PaddedBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val crossesDateLine: Boolean
) {
    fun contains(point: GeoPoint): Boolean =
        point.latitude in minLatitude..maxLatitude &&
            (crossesDateLine || point.longitude in minLongitude..maxLongitude)

    companion object {
        fun around(points: List<GeoPoint>, paddingMeters: Double): PaddedBounds {
            if (points.isEmpty()) {
                return PaddedBounds(
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    crossesDateLine = false
                )
            }
            val minLatitude = points.minOf { it.latitude }
            val maxLatitude = points.maxOf { it.latitude }
            val minLongitude = points.minOf { it.longitude }
            val maxLongitude = points.maxOf { it.longitude }
            val latitudePadding = Math.toDegrees(paddingMeters / EARTH_RADIUS_METERS)
            val paddedAbsoluteLatitude = (maxOf(abs(minLatitude), abs(maxLatitude)) + latitudePadding)
                .coerceAtMost(90.0)
            val longitudeScale = cos(Math.toRadians(paddedAbsoluteLatitude))
            val longitudePadding = if (longitudeScale <= 0.0) {
                180.0
            } else {
                Math.toDegrees(paddingMeters / (EARTH_RADIUS_METERS * longitudeScale))
            }
            // A conventional min/max longitude box is intentionally disabled for date-line shapes.
            // Latitude rejection remains valid, and projection supplies the exact answer as before.
            val crossesDateLine = maxLongitude - minLongitude > 180.0
            return PaddedBounds(
                minLatitude - latitudePadding,
                maxLatitude + latitudePadding,
                minLongitude - longitudePadding,
                maxLongitude + longitudePadding,
                crossesDateLine
            )
        }
    }
}

/**
 * Preserve full geometry for projection while bounding travel at [anchor], the alighting point. As in
 * [upstreamTo], each receiver entry is an independent shape variant, so every variant the alighting
 * point sits on becomes its own eligible path bounded at its own alight projection — a vehicle upstream
 * on *any* variant serving the alighting point stays eligible, while a vehicle past the alighting point
 * is beyond the bound of every variant it rides and drops out (#2104). A variant that never touches the
 * alighting point can't carry a rider there, so it forms no eligible path. When the alighting point is
 * off every variant — the ride continues onto a later stay-aboard route, or the leg geometry doesn't
 * match this route's shape — there is no meaningful bound, and the whole shape stays eligible rather
 * than guessing one.
 *
 * That last fallback is deliberately the opposite of [upstreamTo]'s, which narrows to the single nearest
 * variant. The two answer different questions about the same mismatched anchor: a mismatch must not
 * multiply the *drawn* approach, and it must not drop a vehicle that is genuinely on the ride. So one
 * degrades restrictively and the other permissively, on purpose.
 *
 * The receiver may be the whole-route **merged** shape rather than a direction's own, since
 * `RouteMap.shapeForDirection` falls back to it for a direction that carried no shape on the wire. That
 * set holds both directions, so the alighting point also sits within tolerance of the reverse-direction
 * shape, which then becomes an eligible path bounded in the *opposite* travel order — a geographically
 * downstream vehicle projects upstream on it and survives. Ordinarily the poll is already narrowed by
 * trip `directionId` before this filter runs, so those vehicles never reach here; a stay-aboard
 * interline composite deliberately drops that direction filter (#2000) and is the case where the
 * geometry alone would have to exclude them, and can't.
 */
internal fun List<RoutePolyline>.boundedThrough(anchor: GeoPoint): List<BoundedRoutePath> {
    val projections = variantProjections(anchor)
    return projections.travelledVariants()
        .map { BoundedRoutePath(it.path, it.projection.distanceAlong) }
        .ifEmpty { projections.map { BoundedRoutePath(it.path, Double.POSITIVE_INFINITY) } }
}

/** Whether [point] is near one of these paths without exceeding its along-route boundary.
 *  Runs on the 20 Hz vehicle sampler for every vehicle. Each path caches a bounding box padded by
 *  the normal tolerance, so most rejected vehicles skip the full polyline projection (#2124). */
internal fun List<BoundedRoutePath>.containsRoutePoint(
    point: GeoPoint,
    toleranceMeters: Double = SEGMENT_STOP_TOLERANCE_METERS
): Boolean = any { path ->
    if (!path.mayContain(point, toleranceMeters)) return@any false
    val projection = path.line.nearestProjection(point.latitude, point.longitude)
        ?: return@any false
    projection.distanceToPoint <= toleranceMeters && projection.distanceAlong <= path.maxDistanceAlong
}

/**
 * Whether a route-focus vehicle survives the selected leg's spatial filter. The explicitly requested
 * ETA-pill trip ([focusedTripId]) always survives: its pin is derived from the arrivals response's
 * exact active-trip + GPS identity, while the eligible paths are reconstructed independently from
 * route-wide geometry and can still miss it when the planned leg's geometry doesn't match the route
 * shape. Without this exception the route poll can contain the tapped vehicle yet discard it before
 * [RouteMapController] gets the chance to frame it.
 *
 * [focusedTripId] must be the trip's exemption for the whole focus context, not the one-shot pending
 * camera fit: this filter re-runs on every vehicle sample, so keying it to a focus that resolves
 * (and clears) on the first fit would drop the framed vehicle again on the very next sample (#2099).
 */
internal fun focusedRideKeepsVehicle(
    vehicleTripId: String?,
    focusedTripId: String?,
    eligiblePaths: List<BoundedRoutePath>,
    point: GeoPoint
): Boolean = (vehicleTripId != null && vehicleTripId == focusedTripId) ||
    eligiblePaths.containsRoutePoint(point)

private data class VariantProjection(
    val line: RoutePolyline,
    val path: Polyline,
    val projection: Polyline.Projection
)

/** Every variant paired with its nearest-[anchor] projection (a variant with no geometry drops out).
 *  Built once per call so the O(n) [Polyline] construction is shared by the tolerance filter, the
 *  callers' fallbacks, and the clip/bound they produce. */
private fun List<RoutePolyline>.variantProjections(anchor: GeoPoint): List<VariantProjection> = mapNotNull { line ->
    val path = Polyline(line.points)
    path.nearestProjection(anchor.latitude, anchor.longitude)
        ?.let { VariantProjection(line, path, it) }
}

/** The variants the anchor actually travels: those it sits on within [VARIANT_MATCH_TOLERANCE_METERS]. */
private fun List<VariantProjection>.travelledVariants(): List<VariantProjection> = filter { it.projection.distanceToPoint <= VARIANT_MATCH_TOLERANCE_METERS }

/**
 * Keep only the stops within [toleranceMeters] of [segment]'s path — the ride's stops, not the whole
 * route's. All stops are kept when there's no drawable segment (plain route focus). [segment] must be the
 * clipped board→alight polyline (not the full route line), or every stop falls within tolerance and none
 * are dropped.
 */
internal fun List<ObaStop>.onSegment(
    segment: List<GeoPoint>,
    toleranceMeters: Double = SEGMENT_STOP_TOLERANCE_METERS
): List<ObaStop> {
    val line = segment.takeIf { it.isDrawableSegment() }?.let { Polyline(it) } ?: return this
    return filter { stop ->
        val nearest = line.nearestPoint(stop.latitude, stop.longitude) ?: return@filter false
        haversineDistance(stop.latitude, stop.longitude, nearest.latitude, nearest.longitude) <= toleranceMeters
    }
}
