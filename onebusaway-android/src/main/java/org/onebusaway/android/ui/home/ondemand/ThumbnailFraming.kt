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
package org.onebusaway.android.ui.home.ondemand

import kotlin.math.abs
import org.onebusaway.android.map.render.LocalProjection
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.util.GeoPoint

/** One polygon of a service in the thumbnail: exterior ring first, then holes, in the service colour. */
data class ThumbnailShape(val rings: List<List<GeoPoint>>, val color: Int)

fun List<ServiceArea>.toThumbnailShapes(color: Int): List<ThumbnailShape> = flatMap { area -> area.polygons.map { ThumbnailShape(it, color) } }

/**
 * Spec §3.4: the §2.7 projection about the frame's centre, scaled so every vertex handed to
 * [thumbnailFrame] fits inside the square with its inset. `y` grows south on screen, north on the ground.
 */
data class ThumbnailFrame(val projection: LocalProjection, val pixelsPerMeter: Double, val sizePx: Float) {
    fun toPixels(point: GeoPoint): Pair<Float, Float> = (sizePx / 2 + projection.x(point) * pixelsPerMeter).toFloat() to (sizePx / 2 - projection.y(point) * pixelsPerMeter).toFloat()
}

fun thumbnailFrame(vertices: List<GeoPoint>, centre: GeoPoint, sizePx: Float, insetPx: Float): ThumbnailFrame {
    val projection = LocalProjection(centre)
    val extent = vertices.maxOfOrNull { maxOf(abs(projection.x(it)), abs(projection.y(it))) } ?: 0.0
    val half = (sizePx / 2 - insetPx).coerceAtLeast(1f)
    val pixelsPerMeter = if (extent <= 0.0) 1.0 else half / extent
    return ThumbnailFrame(projection, pixelsPerMeter, sizePx)
}

/** The bbox centre of every vertex, for a thumbnail centred on the zone rather than the probe. */
fun shapesCentre(shapes: List<ThumbnailShape>): GeoPoint? {
    val points = shapes.flatMap { it.rings.flatten() }
    if (points.isEmpty()) return null
    return GeoPoint((points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2, (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2)
}
