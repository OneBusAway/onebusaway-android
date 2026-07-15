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
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.roundToInt
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.focusedRouteStopScale
import org.onebusaway.android.map.render.stopZIndex

/**
 * Draws each size/style once and stamps the shared bitmap descriptor onto every route-stop marker.
 * Unlike [GoogleRouteStopCircleLayer], a settle applies the new size in one pass with no frame trickle.
 */
internal class GoogleRouteStopBitmapLayer(
    private val map: GoogleMap,
    private val density: Float,
) : GoogleRouteStopLayer {
    private data class RenderedStop(
        val marker: Marker,
        var stop: StopMarker,
    )

    private data class StampSizes(
        val normal: Int,
        val selected: Int,
    )

    private data class IconKey(
        val diameterPx: Int,
        val selected: Boolean,
    )

    private val stopsById = HashMap<String, RenderedStop>()
    private val icons = HashMap<IconKey, BitmapDescriptor>()
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedFocusedStopId: String? = null
    private var renderedSizes: StampSizes? = null

    override fun render(stops: List<StopMarker>, focusedStopId: String?, zoom: Float) {
        val routeStops = stops.filter(StopMarker::routeStop)
        if (routeStops == renderedStops && focusedStopId == renderedFocusedStopId) return
        val previousFocusedStopId = renderedFocusedStopId
        val previousSizes = renderedSizes
        renderedStops = routeStops
        renderedFocusedStopId = focusedStopId

        val sizes = stampSizes(zoom, focusedStopId != null)
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
                        .position(stop.point.toGoogleLatLng())
                        .icon(icon(sizes.forSelection(selected), selected))
                        .flat(true)
                        .anchor(0.5f, 0.5f)
                        .zIndex(zIndex(selected))
                )!!
                marker.tag = stop.id
                stopsById[stop.id] = RenderedStop(marker, stop)
            } else {
                if (rendered.stop.point != stop.point) {
                    rendered.marker.position = stop.point.toGoogleLatLng()
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
        val sizes = stampSizes(zoom, renderedFocusedStopId != null)
        if (sizes == renderedSizes) return
        renderedSizes = sizes
        stamp(sizes)
    }

    override fun stopForMarker(marker: Marker): StopMarker? =
        (marker.tag as? String)?.let(stopsById::get)?.stop

    override fun dispose() {
        stopsById.values.forEach { it.marker.remove() }
        stopsById.clear()
        icons.clear()
        renderedStops = emptyList()
        renderedFocusedStopId = null
        renderedSizes = null
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

    private fun icon(diameterPx: Int, selected: Boolean): BitmapDescriptor =
        icons.getOrPut(IconKey(diameterPx, selected)) {
            BitmapDescriptorFactory.fromBitmap(drawRouteStopBitmap(diameterPx, selected))
        }

    private fun zIndex(selected: Boolean): Float =
        stopZIndex(routeStop = true, favorite = false) + if (selected) 0.01f else 0f

    private fun StampSizes.forSelection(selected: Boolean): Int = if (selected) this.selected else normal

    private fun stampSizes(zoom: Float, stopFocused: Boolean): StampSizes = StampSizes(
        normal = routeStopDiameterPx(zoom, stopFocused, selected = false, density),
        selected = routeStopDiameterPx(zoom, stopFocused, selected = true, density),
    )
}

internal fun routeStopDiameterPx(
    zoom: Float,
    stopFocused: Boolean,
    selected: Boolean,
    density: Float,
): Int {
    val focusScale = if (stopFocused) focusedRouteStopScale(zoom) else 1f
    val selectedScale = if (selected) RouteStopCircles.FOCUSED_SCALE else 1f
    return (2f * RouteStopCircles.RADIUS_PX * focusScale * selectedScale * density)
        .roundToInt()
        .coerceAtLeast(1)
}

private fun drawRouteStopBitmap(diameterPx: Int, selected: Boolean): Bitmap {
    val bitmap = createBitmap(diameterPx, diameterPx)
    val canvas = Canvas(bitmap)
    val center = diameterPx / 2f
    val scale = diameterPx / (2f * RouteStopCircles.RADIUS_PX)
    val strokeWidth = RouteStopCircles.STROKE_WIDTH_PX * scale
    val radius = center - strokeWidth / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = Color.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, radius, paint)

    paint.color = RouteStopCircles.STROKE_COLOR
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    canvas.drawCircle(center, center, radius, paint)

    if (selected) {
        paint.style = Paint.Style.FILL
        canvas.drawCircle(center, center, radius * RouteStopCircles.INNER_RADIUS_SCALE, paint)
    }
    return bitmap
}

private fun GeoPoint.toGoogleLatLng() = LatLng(latitude, longitude)
