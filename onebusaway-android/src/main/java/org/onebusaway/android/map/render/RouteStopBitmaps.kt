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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.max

/** Everything that changes a route stop's artwork, shared by both map providers. */
data class RouteStopIconKey(
    val diameterPx: Int,
    val routeColor: Int,
    val arrowAngleDeg: Float?
)

fun routeStopIconKey(stop: StopMarker, diameterPx: Int, neutralColor: Int): RouteStopIconKey = RouteStopIconKey(
    diameterPx,
    stop.routeColor ?: neutralColor,
    StopDirection.fromKey(stop.direction).takeIf { it != StopDirection.NONE }?.compassAngle
)

/** Route-colored rings for unselected stops, centered on their geographic boarding positions. */
fun drawRouteStopBitmap(key: RouteStopIconKey, surfaceColor: Int): Bitmap {
    val (diameterPx, routeColor, arrowAngleDeg) = key
    val scale = diameterPx / (2f * RouteStopCircles.RADIUS_DP)
    val strokeWidth = RouteStopCircles.STROKE_WIDTH_DP * scale
    val radius = diameterPx / 2f - strokeWidth / 2f
    val arrowOutlineWidth = RouteStopCircles.ARROW_OUTLINE_WIDTH_DP * scale
    val circleReach = diameterPx / 2f
    val arrowReach = if (arrowAngleDeg == null) {
        0f
    } else {
        StopBitmaps.directionArrowReach(radius) + arrowOutlineWidth / 2f
    }
    // Symmetric padding keeps the boarding location at the circle center, with or without an arrow.
    val size = ceil(2f * max(circleReach, arrowReach)).toInt() + 2
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    // Keep the arrow's white border behind the disc so it cannot cut into the circular rim.
    if (arrowAngleDeg != null) {
        StopBitmaps.drawDirectionArrow(
            canvas, center, center, radius, routeColor, routeColor, arrowAngleDeg,
            outlineColor = surfaceColor, outlineWidthPx = arrowOutlineWidth
        )
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = surfaceColor
    canvas.drawCircle(center, center, radius, paint)
    paint.color = routeColor
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    canvas.drawCircle(center, center, radius, paint)
    return bitmap
}
