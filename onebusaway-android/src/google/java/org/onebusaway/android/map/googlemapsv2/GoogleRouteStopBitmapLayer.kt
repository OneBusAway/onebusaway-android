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

import android.util.LruCache
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.roundToInt
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.RouteStopIconKey
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.drawRouteStopBitmap
import org.onebusaway.android.map.render.focusedRouteStopScale
import org.onebusaway.android.map.render.routeStopIconKey
import org.onebusaway.android.map.render.stopZIndex

/** Route-colored boarding markers, re-stamped only when their artwork or settled zoom changes. */
internal class GoogleRouteStopBitmapLayer(
    private val map: GoogleMap,
    private val density: Float,
    private val fillColor: Int,
    private val outlineColor: Int
) : GoogleRouteStopLayer {
    private data class RenderedStop(val marker: Marker, var stop: StopMarker, var iconKey: RouteStopIconKey)
    private val stopsById = HashMap<String, RenderedStop>()
    private val icons = LruCache<RouteStopIconKey, BitmapDescriptor>(128)
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedFocusedStopId: String? = null
    private var renderedScaleWithZoom = false
    private var renderedRecedeAdjacent = false
    private var renderedZoom: Float? = null

    override fun render(
        stops: List<StopMarker>,
        focusedStopId: String?,
        scaleWithZoom: Boolean,
        recedeAdjacent: Boolean,
        zoom: Float
    ) {
        val routeStops = stops.filter { it.routeStop && it.id != focusedStopId }
        if (routeStops == renderedStops &&
            focusedStopId == renderedFocusedStopId &&
            scaleWithZoom == renderedScaleWithZoom &&
            recedeAdjacent == renderedRecedeAdjacent &&
            zoom == renderedZoom
        ) {
            return
        }
        renderedZoom = zoom
        renderedStops = routeStops
        renderedFocusedStopId = focusedStopId
        renderedScaleWithZoom = scaleWithZoom
        renderedRecedeAdjacent = recedeAdjacent
        val liveIds = renderedStops.mapTo(HashSet(), StopMarker::id)
        val gone = stopsById.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                entry.value.marker.remove()
                gone.remove()
            }
        }
        for (stop in renderedStops) {
            val key = routeStopIconKey(
                stop,
                routeStopDiameterPx(zoom, scaleWithZoom, recedeAdjacent, density),
                outlineColor
            )
            val rendered = stopsById[stop.id]
            if (rendered == null) {
                val marker = map.addMarkerOrFail(
                    MarkerOptions().position(stop.point.toLatLng()).icon(icon(key))
                        .flat(true).anchor(0.5f, 0.5f).zIndex(stopZIndex(routeStop = true, favorite = false))
                )
                marker.tag = stop.id
                stopsById[stop.id] = RenderedStop(marker, stop, key)
            } else {
                if (rendered.stop.point != stop.point) rendered.marker.position = stop.point.toLatLng()
                if (rendered.iconKey != key) {
                    rendered.marker.setIcon(icon(key))
                    rendered.marker.zIndex = stopZIndex(routeStop = true, favorite = false)
                    rendered.iconKey = key
                }
                rendered.stop = stop
            }
        }
    }

    override fun onCameraSettled(zoom: Float) = render(
        renderedStops,
        renderedFocusedStopId,
        renderedScaleWithZoom,
        renderedRecedeAdjacent,
        zoom
    )

    override fun stopForMarker(marker: Marker): StopMarker? = (marker.tag as? String)?.let(stopsById::get)?.stop

    override fun dispose() {
        stopsById.values.forEach { it.marker.remove() }
        stopsById.clear()
        icons.evictAll()
        renderedStops = emptyList()
        renderedFocusedStopId = null
    }

    private fun icon(key: RouteStopIconKey): BitmapDescriptor = icons.get(key) ?: BitmapDescriptorFactory
        .fromBitmap(drawRouteStopBitmap(key, fillColor)).also { icons.put(key, it) }
}

internal fun routeStopDiameterPx(
    zoom: Float,
    scaleWithZoom: Boolean,
    recedeAdjacent: Boolean,
    density: Float
): Int {
    val focusScale = if (scaleWithZoom) focusedRouteStopScale(zoom) else 1f
    val emphasisScale = if (recedeAdjacent) RouteStopCircles.ADJACENT_SCALE else 1f
    return (2f * RouteStopCircles.RADIUS_PX * focusScale * emphasisScale * density)
        .roundToInt()
        .coerceAtLeast(1)
}
