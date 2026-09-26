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
package org.onebusaway.android.map.render

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.util.GeoPoint

private const val FILL_ALPHA = 0x33
private const val RGB_MASK = 0x00FFFFFF

private fun withAlpha(argb: Int, alpha: Int): Int = (alpha shl 24) or (argb and RGB_MASK)

/**
 * Even-odd ray cast of [point] against [ring] (closed or not). Planar lat/lon is exact enough for a
 * tap hit-test on a drawn polygon; it is **not** the service's containment semantics — those are the
 * server's `matchReason`.
 */
fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        val crosses = (a.latitude > point.latitude) != (b.latitude > point.latitude)
        if (crosses) {
            val lonAtLat = (b.longitude - a.longitude) * (point.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
            if (point.longitude < lonAtLat) inside = !inside
        }
        j = i
    }
    return inside
}

/** True inside the exterior ring and outside every hole. */
fun ZonePolygon.contains(point: GeoPoint): Boolean {
    val exterior = rings.firstOrNull() ?: return false
    return pointInRing(point, exterior) && rings.drop(1).none { pointInRing(point, it) }
}

/** The sphere radius of the spec §2.7 local projection. */
const val EARTH_RADIUS_METERS = 6_371_000.0

/** Inside this far from an edge the bar says which way the edge is (spec §2.7 "near edge"). */
const val ONDEMAND_NEAR_EDGE_METERS = 100.0

/**
 * Spec §2.7's local equirectangular projection about [origin]: `x` east and `y` north in metres.
 * Good to a few metres over the tens of kilometres a zone spans, and invertible, which is all the
 * nearest-edge search and the thumbnail need.
 */
class LocalProjection(val origin: GeoPoint) {
    private val cosLat = cos(Math.toRadians(origin.latitude))

    fun x(point: GeoPoint): Double = Math.toRadians(point.longitude - origin.longitude) * cosLat * EARTH_RADIUS_METERS

    fun y(point: GeoPoint): Double = Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS

    fun toGeo(x: Double, y: Double): GeoPoint = GeoPoint(
        latitude = origin.latitude + Math.toDegrees(y / EARTH_RADIUS_METERS),
        longitude = origin.longitude + Math.toDegrees(x / (EARTH_RADIUS_METERS * cosLat))
    )
}

/** The eight compass points, in bearing order from north. */
enum class CompassDirection { NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST }

/** `floor((bearing + 22.5) / 45) mod 8` over a bearing normalised to `[0, 360)`. */
fun compassDirection(bearingDegrees: Double): CompassDirection {
    val normalised = ((bearingDegrees % 360.0) + 360.0) % 360.0
    val bucket = floor((normalised + 22.5) / 45.0).toInt() % CompassDirection.entries.size
    return CompassDirection.entries[bucket]
}

/** The bearing from [from] to [to] in degrees clockwise from north, `[0, 360)`, in the local projection. */
fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
    val projection = LocalProjection(from)
    val degrees = Math.toDegrees(atan2(projection.x(to), projection.y(to)))
    return ((degrees % 360.0) + 360.0) % 360.0
}

/**
 * The nearest point of a service's boundary to the probe point. [edgeDirection] is the way to walk
 * to reach the edge (the inside near-edge title); [riderDirection] is where the rider stands relative
 * to the zone (the outside title). They are not interchangeable.
 */
data class ZoneEdge(val distanceMeters: Double, val point: GeoPoint, val bearingDegrees: Double) {
    val edgeDirection: CompassDirection get() = compassDirection(bearingDegrees)
    val riderDirection: CompassDirection get() = compassDirection(bearingDegrees + 180.0)
}

/**
 * The closest boundary point over every segment of every ring (exterior and holes) of every polygon of
 * [areas], by point-to-segment distance in the local projection about [from]. Null with no rings.
 */
fun nearestBoundaryPoint(from: GeoPoint, areas: List<ServiceArea>): ZoneEdge? {
    val projection = LocalProjection(from)
    var best: ZoneEdge? = null
    for (area in areas) {
        for (polygon in area.polygons) {
            for (ring in polygon) {
                if (ring.size < 2) continue
                for (i in ring.indices) {
                    val a = ring[i]
                    val b = ring[(i + 1) % ring.size]
                    val ax = projection.x(a)
                    val ay = projection.y(a)
                    val bx = projection.x(b)
                    val by = projection.y(b)
                    val dx = bx - ax
                    val dy = by - ay
                    val lengthSquared = dx * dx + dy * dy
                    // The projection parameter of the origin (0,0) onto the segment, clamped to the segment.
                    val t = if (lengthSquared == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / lengthSquared
                    val clamped = t.coerceIn(0.0, 1.0)
                    val px = ax + clamped * dx
                    val py = ay + clamped * dy
                    val distance = sqrt(px * px + py * py)
                    if (best == null || distance < best.distanceMeters) {
                        val bearing = ((Math.toDegrees(atan2(px, py)) % 360.0) + 360.0) % 360.0
                        best = ZoneEdge(distance, projection.toGeo(px, py), bearing)
                    }
                }
            }
        }
    }
    return best
}

/**
 * Spec §2.3's label point: the midpoint of the widest run inside the polygon along the bbox
 * mid-latitude; else the exterior centroid when it lies inside the ring; else the bbox centre. The
 * one place client point-in-ring is allowed for something a rider sees.
 */
fun labelPoint(rings: List<List<GeoPoint>>, bbox: Pair<GeoPoint, GeoPoint>): GeoPoint {
    val midLatitude = (bbox.first.latitude + bbox.second.latitude) / 2
    val bboxCentre = GeoPoint(midLatitude, (bbox.first.longitude + bbox.second.longitude) / 2)
    val crossings = rings.flatMap { crossingsAt(it, midLatitude) }.sorted()
    if (crossings.size >= 2) {
        var widest = -1.0
        var midpoint = bboxCentre.longitude
        var i = 0
        while (i + 1 < crossings.size) {
            val width = crossings[i + 1] - crossings[i]
            if (width > widest) {
                widest = width
                midpoint = (crossings[i] + crossings[i + 1]) / 2
            }
            i += 2
        }
        return GeoPoint(midLatitude, midpoint)
    }
    val exterior = rings.firstOrNull() ?: return bboxCentre
    val centroid = ringCentroid(exterior) ?: return bboxCentre
    return if (pointInRing(centroid, exterior)) centroid else bboxCentre
}

/** Longitudes where [ring]'s segments cross [latitude], with the same half-open rule as [pointInRing]. */
private fun crossingsAt(ring: List<GeoPoint>, latitude: Double): List<Double> {
    if (ring.size < 3) return emptyList()
    val crossings = mutableListOf<Double>()
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        if ((a.latitude > latitude) != (b.latitude > latitude)) {
            crossings += (b.longitude - a.longitude) * (latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
        }
        j = i
    }
    return crossings
}

/** The planar (lat/lon) shoelace centroid of [ring]; null for fewer than three points or zero area. */
private fun ringCentroid(ring: List<GeoPoint>): GeoPoint? {
    if (ring.size < 3) return null
    var twiceArea = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        val cross = a.longitude * b.latitude - b.longitude * a.latitude
        twiceArea += cross
        cx += (a.longitude + b.longitude) * cross
        cy += (a.latitude + b.latitude) * cross
    }
    if (abs(twiceArea) < 1e-12) return null
    return GeoPoint(latitude = cy / (3 * twiceArea), longitude = cx / (3 * twiceArea))
}

/** The bounding box of [ring], or null when it is empty. */
fun ringBounds(ring: List<GeoPoint>): Pair<GeoPoint, GeoPoint>? {
    if (ring.isEmpty()) return null
    return GeoPoint(ring.minOf { it.latitude }, ring.minOf { it.longitude }) to GeoPoint(ring.maxOf { it.latitude }, ring.maxOf { it.longitude })
}

/** The polygon (its rings) with the largest exterior area in metres², across every area. */
fun largestPolygon(areas: List<ServiceArea>): List<List<GeoPoint>>? = areas
    .flatMap { it.polygons }
    .filter { it.firstOrNull()?.size ?: 0 >= 3 }
    .maxByOrNull { polygon -> projectedArea(polygon.first()) }

/** Where a service's one pin goes (spec §3.1): the label point of its largest polygon. */
fun pinPointFor(areas: List<ServiceArea>): GeoPoint? {
    val polygon = largestPolygon(areas) ?: return null
    val bounds = ringBounds(polygon.first()) ?: return null
    return labelPoint(polygon, bounds)
}

/** Shoelace area of [ring] in the local projection about its first vertex, metres². */
private fun projectedArea(ring: List<GeoPoint>): Double {
    val projection = LocalProjection(ring.first())
    var twiceArea = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        twiceArea += projection.x(a) * projection.y(b) - projection.x(b) * projection.y(a)
    }
    return abs(twiceArea) / 2
}

/**
 * How a zone draws at the current level (spec §2.3): region fills, street strokes only; a highlighted
 * service draws at full alpha while the others at street level dim.
 */
enum class ZoneStyle { REGION, REGION_HIGHLIGHTED, STREET, STREET_DIMMED, STREET_HIGHLIGHTED }

/** Only region-level polygons take a tap (spec §3.1); at street level the polygon covers the screen. */
val ZoneStyle.clickable: Boolean get() = this == ZoneStyle.REGION || this == ZoneStyle.REGION_HIGHLIGHTED

/** Spec §2.3: 2 pt at region level, 4 pt at street level and for any highlight. */
val ZoneStyle.strokeWidthDp: Float get() = if (this == ZoneStyle.REGION) 2f else 4f

/** The soft halo drawn beneath a street-level stroke where the platform allows a second overlay. */
const val ZONE_HALO_WIDTH_DP = 10f

private const val REGION_STROKE_ALPHA = 0xCC
private const val FULL_ALPHA = 0xFF
private const val DIMMED_STROKE_ALPHA = 0x99
private const val HALO_ALPHA = 0x40
private const val DIMMED_HALO_ALPHA = 0x26

fun zoneFillColor(color: Int, style: ZoneStyle): Int = withAlpha(color, if (style == ZoneStyle.REGION || style == ZoneStyle.REGION_HIGHLIGHTED) FILL_ALPHA else 0)

fun zoneStrokeColor(color: Int, style: ZoneStyle): Int = withAlpha(
    color,
    when (style) {
        ZoneStyle.REGION -> REGION_STROKE_ALPHA
        ZoneStyle.STREET_DIMMED -> DIMMED_STROKE_ALPHA
        ZoneStyle.REGION_HIGHLIGHTED, ZoneStyle.STREET, ZoneStyle.STREET_HIGHLIGHTED -> FULL_ALPHA
    }
)

/** The halo colour at street level; null at region level, where no halo is drawn. */
fun zoneHaloColor(color: Int, style: ZoneStyle): Int? = when (style) {
    ZoneStyle.REGION, ZoneStyle.REGION_HIGHLIGHTED -> null
    ZoneStyle.STREET_DIMMED -> withAlpha(color, DIMMED_HALO_ALPHA)
    ZoneStyle.STREET, ZoneStyle.STREET_HIGHLIGHTED -> withAlpha(color, HALO_ALPHA)
}

/** The topmost *tappable* zone under [point]: region-level only, so street-level taps fall through. */
fun tappableZone(zones: List<ZonePolygon>, point: GeoPoint): ZonePolygon? = zones.lastOrNull { it.style.clickable && it.contains(point) }
