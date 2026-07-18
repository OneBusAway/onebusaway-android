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

import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint

private const val MAX_MERCATOR_LATITUDE = 85.05112878
private const val VIEWPORT_MARGIN_MULTIPLIER = 1.0
private const val SIMPLIFICATION_ERROR_PIXELS = 0.75
private const val MIN_SIMPLIFICATION_METERS = 2.0
private const val COORDINATE_EPSILON = 1e-12

internal data class RoutePolylineRenderContext(val camera: CameraSnapshot?)

internal fun interface RoutePolylineRenderPass {
    fun apply(polylines: List<RoutePolyline>, context: RoutePolylineRenderContext): List<RoutePolyline>
}

/** Ordered, flavor-neutral processing of canonical route geometry before a native renderer sees it. */
internal class RoutePolylineRenderPipeline(
    private val passes: List<RoutePolylineRenderPass>,
) {
    fun apply(polylines: List<RoutePolyline>, camera: CameraSnapshot?): List<RoutePolyline> =
        passes.fold(polylines) { current, pass ->
            pass.apply(current, RoutePolylineRenderContext(camera))
        }
}

private val DEFAULT_ROUTE_POLYLINE_PIPELINE = RoutePolylineRenderPipeline(
    listOf(ViewportClipRoutePolylinePass(), ZoomSimplifyRoutePolylinePass())
)

/**
 * Combines canonical geometry with the last settled camera and performs opt-in transforms away from
 * the UI thread. Collection resumes in the adapter's main-thread scope for native map mutations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun routePolylineRenderFlow(
    snapshot: StateFlow<MapRenderSnapshot>,
    camera: StateFlow<CameraSnapshot?>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    pipeline: RoutePolylineRenderPipeline = DEFAULT_ROUTE_POLYLINE_PIPELINE,
): Flow<List<RoutePolyline>> =
    combine(
        snapshot.map { it.routePolylines }.distinctUntilChanged(),
        camera,
    ) { polylines, viewport -> polylines to viewport }
        .mapLatest { (polylines, viewport) ->
            withContext(dispatcher) { pipeline.apply(polylines, viewport) }
        }
        .distinctUntilChanged()

internal class ViewportClipRoutePolylinePass : RoutePolylineRenderPass {
    override fun apply(
        polylines: List<RoutePolyline>,
        context: RoutePolylineRenderContext,
    ): List<RoutePolyline> {
        if (polylines.none { RoutePolylineTransform.VIEWPORT_CLIP in it.transforms }) return polylines
        val camera = context.camera
            ?: return polylines.filterNot { RoutePolylineTransform.VIEWPORT_CLIP in it.transforms }
        val clipBounds = camera.toPaddedMercatorBounds()
        return buildList {
            for (polyline in polylines) {
                if (RoutePolylineTransform.VIEWPORT_CLIP !in polyline.transforms) {
                    add(polyline)
                } else {
                    clipPolyline(polyline, camera.center.longitude, clipBounds).forEach(::add)
                }
            }
        }
    }
}

internal class ZoomSimplifyRoutePolylinePass : RoutePolylineRenderPass {
    override fun apply(
        polylines: List<RoutePolyline>,
        context: RoutePolylineRenderContext,
    ): List<RoutePolyline> {
        if (polylines.none { RoutePolylineTransform.ZOOM_SIMPLIFY in it.transforms }) return polylines
        val camera = context.camera ?: return polylines
        val latitude = camera.center.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val metresPerPixel = METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO *
            cos(Math.toRadians(latitude)) / 2.0.pow(camera.zoom.coerceIn(0.0, 30.0))
        val tolerance = maxOf(MIN_SIMPLIFICATION_METERS, metresPerPixel * SIMPLIFICATION_ERROR_PIXELS)
        var changed = false
        val simplified = polylines.map { polyline ->
            if (RoutePolylineTransform.ZOOM_SIMPLIFY !in polyline.transforms) return@map polyline
            val points = simplifyRoutePolyline(polyline.points, tolerance)
            if (points === polyline.points) {
                polyline
            } else {
                changed = true
                polyline.copy(points = points)
            }
        }
        return if (changed) simplified else polylines
    }
}

internal data class MercatorPoint(
    val x: Double,
    val y: Double,
    val source: GeoPoint? = null,
)

internal data class MercatorBounds(
    val left: Double,
    val right: Double,
    val bottom: Double,
    val top: Double,
) {
    fun contains(point: MercatorPoint): Boolean =
        point.x in left..right && point.y in bottom..top
}

private data class ClippedSegment(val start: MercatorPoint, val end: MercatorPoint)

internal fun CameraSnapshot.toPaddedMercatorBounds(): MercatorBounds {
    val west = projectedLongitude(southWest.longitude, center.longitude)
    var east = projectedLongitude(northEast.longitude, center.longitude)
    if (east < west) east += 2.0 * PI
    val south = projectedLatitude(southWest.latitude)
    val north = projectedLatitude(northEast.latitude)
    val horizontalMargin = (east - west) * VIEWPORT_MARGIN_MULTIPLIER
    val verticalMargin = (north - south) * VIEWPORT_MARGIN_MULTIPLIER
    return MercatorBounds(
        left = west - horizontalMargin,
        right = east + horizontalMargin,
        bottom = south - verticalMargin,
        top = north + verticalMargin,
    )
}

internal fun clipPolyline(
    polyline: RoutePolyline,
    centerLongitude: Double,
    bounds: MercatorBounds,
): List<RoutePolyline> {
    if (polyline.points.size < 2) return emptyList()
    val projected = polyline.points.map { it.toMercator(centerLongitude) }
    if (projected.all(bounds::contains)) return listOf(polyline)

    val fragments = mutableListOf<MutableList<MercatorPoint>>()
    var current: MutableList<MercatorPoint>? = null
    fun finishCurrent() {
        current?.takeIf { it.size >= 2 }?.let(fragments::add)
        current = null
    }

    for (index in 0 until projected.lastIndex) {
        val clipped = clipSegment(projected[index], projected[index + 1], bounds)
        if (clipped == null) {
            finishCurrent()
            continue
        }
        val fragment = current
        if (fragment == null || !fragment.last().samePosition(clipped.start)) {
            finishCurrent()
            current = mutableListOf(clipped.start, clipped.end)
        } else if (!fragment.last().samePosition(clipped.end)) {
            fragment.add(clipped.end)
        }
    }
    finishCurrent()

    return fragments.map { fragment ->
        polyline.copy(points = fragment.map(MercatorPoint::toGeoPoint))
    }
}

private fun clipSegment(
    start: MercatorPoint,
    end: MercatorPoint,
    bounds: MercatorBounds,
): ClippedSegment? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    var entry = 0.0
    var exit = 1.0

    fun retain(p: Double, q: Double): Boolean {
        if (p == 0.0) return q >= 0.0
        val ratio = q / p
        if (p < 0.0) {
            if (ratio > exit) return false
            if (ratio > entry) entry = ratio
        } else {
            if (ratio < entry) return false
            if (ratio < exit) exit = ratio
        }
        return true
    }

    if (!retain(-dx, start.x - bounds.left) ||
        !retain(dx, bounds.right - start.x) ||
        !retain(-dy, start.y - bounds.bottom) ||
        !retain(dy, bounds.top - start.y)
    ) {
        return null
    }
    return ClippedSegment(
        start = interpolate(start, end, entry),
        end = interpolate(start, end, exit),
    )
}

private fun interpolate(start: MercatorPoint, end: MercatorPoint, fraction: Double): MercatorPoint =
    when {
        fraction <= COORDINATE_EPSILON -> start
        fraction >= 1.0 - COORDINATE_EPSILON -> end
        else -> MercatorPoint(
            x = start.x + (end.x - start.x) * fraction,
            y = start.y + (end.y - start.y) * fraction,
        )
    }

private fun GeoPoint.toMercator(centerLongitude: Double): MercatorPoint = MercatorPoint(
    x = projectedLongitude(longitude, centerLongitude),
    y = projectedLatitude(latitude),
    source = this,
)

private fun MercatorPoint.toGeoPoint(): GeoPoint = source ?: GeoPoint(
    latitude = Math.toDegrees(2.0 * atan(exp(y)) - PI / 2.0),
    longitude = normalizeLongitude(Math.toDegrees(x)),
)

private fun MercatorPoint.samePosition(other: MercatorPoint): Boolean =
    kotlin.math.abs(x - other.x) <= COORDINATE_EPSILON &&
        kotlin.math.abs(y - other.y) <= COORDINATE_EPSILON

private fun projectedLongitude(longitude: Double, centerLongitude: Double): Double {
    var delta = longitude - centerLongitude
    while (delta > 180.0) delta -= 360.0
    while (delta < -180.0) delta += 360.0
    return Math.toRadians(centerLongitude + delta)
}

private fun projectedLatitude(latitude: Double): Double {
    val radians = Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
    return ln(tan(PI / 4.0 + radians / 2.0))
}

private fun normalizeLongitude(longitude: Double): Double {
    var normalized = longitude
    while (normalized > 180.0) normalized -= 360.0
    while (normalized < -180.0) normalized += 360.0
    return normalized
}

/** Douglas-Peucker simplification in a local metre projection. Endpoints are always retained. */
internal fun simplifyRoutePolyline(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
    if (points.size <= 2 || toleranceMeters <= 0.0) return points

    val meanLatitudeRadians = Math.toRadians(points.sumOf(GeoPoint::latitude) / points.size)
    val longitudeScale = EARTH_RADIUS_METERS * cos(meanLatitudeRadians)
    val latitudeScale = EARTH_RADIUS_METERS
    val originLatitudeRadians = Math.toRadians(points.first().latitude)
    val originLongitudeRadians = Math.toRadians(points.first().longitude)
    val x = DoubleArray(points.size)
    val y = DoubleArray(points.size)
    for (index in points.indices) {
        var longitudeDelta = Math.toRadians(points[index].longitude) - originLongitudeRadians
        if (longitudeDelta > PI) longitudeDelta -= 2.0 * PI
        if (longitudeDelta < -PI) longitudeDelta += 2.0 * PI
        x[index] = longitudeDelta * longitudeScale
        y[index] = (Math.toRadians(points[index].latitude) - originLatitudeRadians) * latitudeScale
    }

    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val ranges = ArrayDeque<IntRange>()
    ranges.addLast(0..points.lastIndex)
    val toleranceSquared = toleranceMeters * toleranceMeters

    while (ranges.isNotEmpty()) {
        val range = ranges.removeLast()
        val start = range.first
        val end = range.last
        val segmentX = x[end] - x[start]
        val segmentY = y[end] - y[start]
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
        var farthestIndex = -1
        var farthestDistanceSquared = toleranceSquared

        for (index in start + 1 until end) {
            val fraction = if (segmentLengthSquared == 0.0) {
                0.0
            } else {
                (((x[index] - x[start]) * segmentX + (y[index] - y[start]) * segmentY) /
                    segmentLengthSquared).coerceIn(0.0, 1.0)
            }
            val offsetX = x[index] - (x[start] + fraction * segmentX)
            val offsetY = y[index] - (y[start] + fraction * segmentY)
            val distanceSquared = offsetX * offsetX + offsetY * offsetY
            if (distanceSquared > farthestDistanceSquared) {
                farthestDistanceSquared = distanceSquared
                farthestIndex = index
            }
        }

        if (farthestIndex >= 0) {
            keep[farthestIndex] = true
            ranges.addLast(start..farthestIndex)
            ranges.addLast(farthestIndex..end)
        }
    }

    if (keep.all { it }) return points
    return points.filterIndexed { index, _ -> keep[index] }
}
