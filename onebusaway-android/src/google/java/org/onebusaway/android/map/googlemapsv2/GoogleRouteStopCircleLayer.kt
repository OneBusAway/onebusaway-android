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

import android.view.Choreographer
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.pow
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.focusedRouteStopScale
import org.onebusaway.android.map.render.stopZIndex

/**
 * Owns the Google Maps route-stop circle subsystem: native overlay lifecycle, tap lookup, and the
 * bounded settle-time resize policy. Google Maps overlay mutations are main-thread-only, so radius
 * updates are deliberately fed through [FrameBatcher] instead of blocking one frame for a whole route.
 */
internal class GoogleRouteStopCircleLayer(
    private val map: GoogleMap,
    choreographer: Choreographer = Choreographer.getInstance(),
) : GoogleRouteStopLayer {
    private data class RouteStopCircle(
        val outer: Circle,
        var inner: Circle? = null,
        var stop: StopMarker,
    )

    private val circlesByStopId = HashMap<String, RouteStopCircle>()
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedFocusedStopId: String? = null
    private var renderedZoom: Float? = null
    private val resizeBatcher = FrameBatcher<RouteStopCircle>(
        batchSize = RESIZE_BATCH_SIZE,
        scheduleNextFrame = { callback -> choreographer.postFrameCallback { callback() } },
    )

    override fun render(stops: List<StopMarker>, focusedStopId: String?, zoom: Float) {
        val routeStops = stops.filter(StopMarker::routeStop)
        if (routeStops == renderedStops && focusedStopId == renderedFocusedStopId) return
        renderedStops = routeStops
        renderedFocusedStopId = focusedStopId

        val liveIds = routeStops.mapTo(HashSet(), StopMarker::id)
        val gone = circlesByStopId.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                remove(entry.value)
                gone.remove()
            }
        }

        for (stop in routeStops) {
            val selected = stop.id == focusedStopId
            val routeCircle = circlesByStopId[stop.id] ?: RouteStopCircle(
                outer = addCircle(stop, inner = false, zoom),
                stop = stop,
            ).also { circlesByStopId[stop.id] = it }

            if (routeCircle.stop.point != stop.point) {
                val center = stop.point.toGoogleLatLng()
                routeCircle.outer.center = center
                routeCircle.inner?.center = center
            }
            routeCircle.stop = stop

            if (selected && routeCircle.inner == null) {
                routeCircle.inner = addCircle(stop, inner = true, zoom)
                routeCircle.outer.strokeWidth =
                    RouteStopCircles.STROKE_WIDTH_PX * RouteStopCircles.FOCUSED_SCALE
            } else if (!selected && routeCircle.inner != null) {
                routeCircle.inner?.remove()
                routeCircle.inner = null
                routeCircle.outer.strokeWidth = RouteStopCircles.STROKE_WIDTH_PX
            }
        }

        renderedZoom = null
        onCameraSettled(zoom)
    }

    override fun onCameraSettled(zoom: Float) {
        if (renderedZoom == zoom) return
        renderedZoom = zoom
        val stopFocusScale = if (renderedFocusedStopId == null) 1f else focusedRouteStopScale(zoom)
        resizeBatcher.submit(circlesByStopId.values.toList()) { routeCircle ->
            val selectedScale = if (routeCircle.inner == null) 1f else RouteStopCircles.FOCUSED_SCALE
            val radius = radiusMeters(routeCircle.stop.point, zoom) * stopFocusScale * selectedScale
            routeCircle.outer.radius = radius
            routeCircle.inner?.radius = radius * RouteStopCircles.INNER_RADIUS_SCALE
        }
    }

    override fun onCameraMoveStarted() {
        if (resizeBatcher.cancel()) renderedZoom = null
    }

    override fun stopForCircle(circle: Circle): StopMarker? =
        (circle.tag as? String)?.let(circlesByStopId::get)?.stop

    override fun dispose() {
        resizeBatcher.cancel()
        circlesByStopId.values.forEach(::remove)
        circlesByStopId.clear()
        renderedStops = emptyList()
        renderedFocusedStopId = null
        renderedZoom = null
    }

    private fun addCircle(stop: StopMarker, inner: Boolean, zoom: Float): Circle {
        val circleScale = if (inner) {
            RouteStopCircles.FOCUSED_SCALE * RouteStopCircles.INNER_RADIUS_SCALE
        } else {
            1f
        }
        val stopFocusScale = if (renderedFocusedStopId == null) 1f else focusedRouteStopScale(zoom)
        return map.addCircle(
            CircleOptions()
                .center(stop.point.toGoogleLatLng())
                .radius(radiusMeters(stop.point, zoom) * stopFocusScale * circleScale)
                .fillColor(if (inner) RouteStopCircles.STROKE_COLOR else RouteStopCircles.FILL_COLOR)
                .strokeColor(RouteStopCircles.STROKE_COLOR)
                .strokeWidth(if (inner) 0f else RouteStopCircles.STROKE_WIDTH_PX)
                .clickable(true)
                .zIndex(stopZIndex(routeStop = true, favorite = false) + if (inner) 0.01f else 0f)
        ).also { it.tag = stop.id }
    }

    private fun remove(routeCircle: RouteStopCircle) {
        routeCircle.outer.remove()
        routeCircle.inner?.remove()
    }

    private fun radiusMeters(point: GeoPoint, zoom: Float): Double =
        RouteStopCircles.RADIUS_PX * metersPerPixel(point.latitude, zoom)

    private fun metersPerPixel(latitude: Double, zoom: Float): Double =
        156543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom.toDouble())

    private companion object {
        // Tuned independently of renderer behavior; one batch is one bounded UI-thread work slice.
        const val RESIZE_BATCH_SIZE = 8
    }
}

private fun GeoPoint.toGoogleLatLng() = LatLng(latitude, longitude)
