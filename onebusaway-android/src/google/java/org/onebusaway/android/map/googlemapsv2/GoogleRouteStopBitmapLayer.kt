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
package org.onebusaway.android.map.googlemapsv2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.StopBitmaps
import org.onebusaway.android.map.render.StopDirection
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.focusedRouteStopScale
import org.onebusaway.android.map.render.stopZIndex

/**
 * Draws each size/style once and stamps the shared bitmap descriptor onto every route-stop marker,
 * so a zoom settle applies the new size in one pass with no per-annotation frame trickle.
 */
internal class GoogleRouteStopBitmapLayer(
    private val map: GoogleMap,
    private val density: Float,
    private val fillColor: Int,
    private val selectedFillColor: Int,
    private val outlineColor: Int
) : GoogleRouteStopLayer {
    private data class RenderedStop(
        val marker: Marker,
        var stop: StopMarker
    )

    private data class StampSizes(
        val normal: Int,
        val selected: Int
    )

    private data class IconKey(
        val diameterPx: Int,
        val selected: Boolean,
        // The focused stop's service-direction arrow angle (clockwise degrees from north); null draws no
        // arrow. Part of the key so two focused stops facing different ways cache distinct bitmaps.
        val arrowAngleDeg: Float?
    )

    private val stopsById = HashMap<String, RenderedStop>()
    private val icons = HashMap<IconKey, BitmapDescriptor>()
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedFocusedStopId: String? = null
    private var renderedScaleWithZoom = false
    private var renderedRecedeAdjacent = false
    private var renderedSizes: StampSizes? = null

    // The focused stop's arrow angle, resolved in render() and reused when re-stamping on zoom settle.
    private var renderedArrowAngle: Float? = null

    override fun render(
        stops: List<StopMarker>,
        focusedStopId: String?,
        scaleWithZoom: Boolean,
        recedeAdjacent: Boolean,
        zoom: Float
    ) {
        val routeStops = stops.filter(StopMarker::routeStop)
        if (
            routeStops == renderedStops &&
            focusedStopId == renderedFocusedStopId &&
            scaleWithZoom == renderedScaleWithZoom &&
            recedeAdjacent == renderedRecedeAdjacent
        ) {
            return
        }
        val previousFocusedStopId = renderedFocusedStopId
        val previousSizes = renderedSizes
        renderedStops = routeStops
        renderedFocusedStopId = focusedStopId
        renderedScaleWithZoom = scaleWithZoom
        renderedRecedeAdjacent = recedeAdjacent
        renderedArrowAngle = routeStops.firstOrNull { it.id == focusedStopId }?.let(::arrowAngle)

        val sizes = stampSizes(zoom, scaleWithZoom, recedeAdjacent)
        val liveIds = routeStops.mapTo(HashSet(), StopMarker::id)
        val gone = stopsById.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                entry.value.marker.remove()
                gone.remove()
            }
        }

        for (stop in routeStops) {
            val selected = stop.id == focusedStopId
            val rendered = stopsById[stop.id]
            if (rendered == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(stop.point.toLatLng())
                        .icon(icon(sizes.forSelection(selected), selected))
                        .flat(true)
                        .anchor(0.5f, 0.5f)
                        .zIndex(zIndex(selected))
                )!!
                marker.tag = stop.id
                stopsById[stop.id] = RenderedStop(marker, stop)
            } else {
                if (rendered.stop.point != stop.point) {
                    rendered.marker.position = stop.point.toLatLng()
                }
                rendered.stop = stop
            }
        }

        renderedSizes = sizes
        when {
            previousSizes != null && sizes != previousSizes -> stamp(sizes)
            focusedStopId != previousFocusedStopId -> {
                previousFocusedStopId?.let { restyle(it, sizes) }
                focusedStopId?.let { restyle(it, sizes) }
            }
        }
    }

    override fun onCameraSettled(zoom: Float) {
        val sizes = stampSizes(zoom, renderedScaleWithZoom, renderedRecedeAdjacent)
        if (sizes == renderedSizes) return
        renderedSizes = sizes
        stamp(sizes)
    }

    override fun stopForMarker(marker: Marker): StopMarker? = (marker.tag as? String)?.let(stopsById::get)?.stop

    override fun dispose() {
        stopsById.values.forEach { it.marker.remove() }
        stopsById.clear()
        icons.clear()
        renderedStops = emptyList()
        renderedFocusedStopId = null
        renderedScaleWithZoom = false
        renderedRecedeAdjacent = false
        renderedSizes = null
        renderedArrowAngle = null
    }

    private fun stamp(sizes: StampSizes) {
        val normalIcon = icon(sizes.normal, selected = false)
        val selectedIcon = icon(sizes.selected, selected = true)
        for ((stopId, rendered) in stopsById) {
            val selected = stopId == renderedFocusedStopId
            rendered.marker.setIcon(if (selected) selectedIcon else normalIcon)
            rendered.marker.zIndex = zIndex(selected)
        }
    }

    private fun restyle(stopId: String, sizes: StampSizes) {
        val rendered = stopsById[stopId] ?: return
        val selected = stopId == renderedFocusedStopId
        rendered.marker.setIcon(icon(sizes.forSelection(selected), selected))
        rendered.marker.zIndex = zIndex(selected)
    }

    // Only the focused stop carries a direction arrow, in the selected fill; adjacent circles pass null.
    private fun icon(diameterPx: Int, selected: Boolean): BitmapDescriptor {
        val arrowAngleDeg = if (selected) renderedArrowAngle else null
        return icons.getOrPut(IconKey(diameterPx, selected, arrowAngleDeg)) {
            BitmapDescriptorFactory.fromBitmap(
                drawRouteStopBitmap(
                    diameterPx,
                    selected,
                    if (selected) selectedFillColor else fillColor,
                    outlineColor,
                    arrowAngleDeg,
                    RouteStopCircles.FOCUS_ARROW_GAP_DP * density
                )
            )
        }
    }

    /** The stop's service-direction arrow angle (clockwise degrees from north), or null when it has none. */
    private fun arrowAngle(stop: StopMarker): Float? = StopDirection.fromKey(stop.direction).takeIf { it != StopDirection.NONE }?.compassAngle

    private fun zIndex(selected: Boolean): Float = stopZIndex(routeStop = true, favorite = false) + if (selected) 0.01f else 0f

    private fun StampSizes.forSelection(selected: Boolean): Int = if (selected) this.selected else normal

    private fun stampSizes(zoom: Float, scaleWithZoom: Boolean, recedeAdjacent: Boolean): StampSizes = StampSizes(
        normal = routeStopDiameterPx(zoom, scaleWithZoom, selected = false, recedeAdjacent, density),
        // The focused stop never recedes — it's the one being emphasized.
        selected = routeStopDiameterPx(zoom, scaleWithZoom, selected = true, recedeAdjacent = false, density)
    )
}

internal fun routeStopDiameterPx(
    zoom: Float,
    scaleWithZoom: Boolean,
    selected: Boolean,
    recedeAdjacent: Boolean,
    density: Float
): Int {
    val focusScale = if (scaleWithZoom) focusedRouteStopScale(zoom) else 1f
    val emphasisScale = when {
        selected -> RouteStopCircles.FOCUSED_SCALE
        recedeAdjacent -> RouteStopCircles.ADJACENT_SCALE
        else -> 1f
    }
    return (2f * RouteStopCircles.RADIUS_PX * focusScale * emphasisScale * density)
        .roundToInt()
        .coerceAtLeast(1)
}

private fun drawRouteStopBitmap(
    diameterPx: Int,
    selected: Boolean,
    fillColor: Int,
    outlineColor: Int,
    arrowAngleDeg: Float?,
    arrowGapPx: Float
): Bitmap {
    val scale = diameterPx / (2f * RouteStopCircles.RADIUS_PX)
    // The selected circle is drawn at FOCUSED_SCALE, but its ring keeps the same on-screen weight as
    // every other stop — divide the selection scale back out of the stroke (not the diameter).
    val selectedScale = if (selected) RouteStopCircles.FOCUSED_SCALE else 1f
    val strokeWidth = RouteStopCircles.STROKE_WIDTH_PX * scale / selectedScale
    val radius = diameterPx / 2f - strokeWidth / 2f

    // The arrow's outline is a fraction of the marker's ring width — finer on the smaller arrow shape.
    val arrowOutlineWidth = strokeWidth * RouteStopCircles.FOCUS_ARROW_OUTLINE_SCALE

    // With an arrow the bitmap grows to fit its overhang at any rotation (plus half the stroke, which
    // straddles the tip); the circle stays centered so the marker's (0.5, 0.5) anchor keeps the circle
    // center on the stop point either way.
    val size = if (arrowAngleDeg != null) {
        val reach = StopBitmaps.directionArrowReach(radius, RouteStopCircles.FOCUS_ARROW_SCALE, arrowGapPx)
        ceil(2f * (reach + arrowOutlineWidth / 2f)).toInt() + 2
    } else {
        diameterPx
    }
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = fillColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, radius, paint)

    paint.color = outlineColor
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    canvas.drawCircle(center, center, radius, paint)

    if (selected) {
        paint.style = Paint.Style.FILL
        canvas.drawCircle(center, center, radius * RouteStopCircles.INNER_RADIUS_SCALE, paint)
    }

    if (arrowAngleDeg != null) {
        // A solid arrow in the circle's own (selected) fill — same colour twice — enlarged and pushed out
        // past the circle, ringed in the marker's own outline colour and width.
        StopBitmaps.drawDirectionArrow(
            canvas,
            center,
            center,
            radius,
            fillColor,
            fillColor,
            arrowAngleDeg,
            RouteStopCircles.FOCUS_ARROW_SCALE,
            arrowGapPx,
            outlineColor,
            arrowOutlineWidth
        )
    }
    return bitmap
}
