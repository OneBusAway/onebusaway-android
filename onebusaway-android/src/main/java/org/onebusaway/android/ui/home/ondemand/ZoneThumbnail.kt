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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.util.GeoPoint

/** How the probe point is drawn (spec §3.6 item 4): the rider's blue dot, a gray dot for the map centre, a pin for a chosen place. */
enum class ProbeDotStyle { RIDER, MAP_CENTER, POINT }

private val THUMBNAIL_INSET = 4.dp
private val DOT_RADIUS = 3.dp
private val DOT_RING = 1.5.dp
private val PIN_SIZE = 18.dp
private const val SHAPE_FILL_ALPHA = 0.35f
private val RIDER_BLUE = Color(0xFF3B82F6)
private val CENTER_GRAY = Color(0xFF636366)
private val PIN_RED = Color(0xFFD32F2F)

/**
 * A Canvas drawing of zone rings (spec §3.4 thumbnail, R14): every shape filled at 0.35 and stroked
 * 1 dp in its colour, projected about [centre] and scaled so every vertex (and the probe, when
 * [fitProbe]) fits the square with a 4 dp inset. The probe is drawn only when it lands inside the
 * square, so a detail thumbnail centred on the zone omits a far-away rider.
 */
@Composable
fun ZoneThumbnail(
    shapes: List<ThumbnailShape>,
    centre: GeoPoint?,
    probePoint: GeoPoint?,
    probeStyle: ProbeDotStyle,
    modifier: Modifier = Modifier,
    fitProbe: Boolean = true
) {
    val pin = painterResource(R.drawable.ic_location_on)
    Canvas(modifier) {
        val frameCentre = centre ?: return@Canvas
        val vertices = shapes.flatMap { it.rings.flatten() } + listOfNotNull(probePoint.takeIf { fitProbe })
        val frame = thumbnailFrame(vertices, frameCentre, size.minDimension, THUMBNAIL_INSET.toPx())
        // The frame fits a sizePx square; on a non-square Canvas (e.g. the detail page's full-width
        // strip) that square must be centred in the full canvas rather than anchored to its top-left.
        translate(left = (size.width - frame.sizePx) / 2, top = (size.height - frame.sizePx) / 2) {
            for (shape in shapes) {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    for (ring in shape.rings) {
                        ring.forEachIndexed { index, point ->
                            val (x, y) = frame.toPixels(point)
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                }
                val colour = Color(shape.color)
                drawPath(path, colour.copy(alpha = SHAPE_FILL_ALPHA))
                drawPath(path, colour, style = Stroke(width = 1.dp.toPx()))
            }
            val point = probePoint ?: return@translate
            val (x, y) = frame.toPixels(point)
            if (x !in 0f..frame.sizePx || y !in 0f..frame.sizePx) return@translate
            when (probeStyle) {
                ProbeDotStyle.RIDER -> ringedDot(x, y, RIDER_BLUE)
                ProbeDotStyle.MAP_CENTER -> ringedDot(x, y, CENTER_GRAY)
                ProbeDotStyle.POINT -> {
                    val side = PIN_SIZE.toPx()
                    translate(left = x - side / 2, top = y - side) {
                        with(pin) { draw(Size(side, side), colorFilter = ColorFilter.tint(PIN_RED)) }
                    }
                }
            }
        }
    }
}

private fun DrawScope.ringedDot(x: Float, y: Float, colour: Color) {
    drawCircle(Color.White, radius = DOT_RADIUS.toPx() + DOT_RING.toPx(), center = Offset(x, y))
    drawCircle(colour, radius = DOT_RADIUS.toPx(), center = Offset(x, y))
}
