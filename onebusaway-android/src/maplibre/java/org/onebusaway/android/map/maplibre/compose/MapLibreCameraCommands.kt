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
package org.onebusaway.android.map.maplibre.compose

import android.content.Context
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.map.maplibre.MapHelpMapLibre
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.FramingIntent
import org.onebusaway.android.map.render.MapRenderState
import kotlin.math.abs

// The same default zoom the imperative MapLibreMapHost used for these camera moves.
private const val CAMERA_DEFAULT_ZOOM = 16.0

/**
 * Applies one transient [CameraCommand] gesture to the maplibre [MapLibreMap] — the declarative
 * counterpart of the Google adapter's `applyCameraCommand`. It's a faithful port of the old
 * `MapLibreMapHost` camera methods (which called `mMap.animateCamera/moveCamera` directly); now the host
 * dispatches gestures and the adapter applies them here. maplibre's camera calls are fire-and-forget
 * (not suspending), so this isn't a suspend function. The route-header recenter bias reads the live
 * viewport from [map]. Retained framing is applied by [applyFramingIntent].
 */
fun applyCameraCommand(cmd: CameraCommand, map: MapLibreMap) {
    when (cmd) {
        is CameraCommand.Recenter -> {
            val cp = map.cameraPosition
            var target = LatLng(cmd.lat, cmd.lon)
            if (cmd.applyRouteBias) {
                val vr = map.projection.visibleRegion.latLngBounds
                val span = abs(vr.getLonEast() - vr.getLonWest())
                target = LatLng(cmd.lat - span * 0.2 / 2, cmd.lon)
            }
            val pos = CameraPosition.Builder().target(target)
                .zoom(cp.zoom).bearing(cp.bearing).tilt(cp.tilt).build()
            val update = CameraUpdateFactory.newCameraPosition(pos)
            if (cmd.animate) map.animateCamera(update) else map.moveCamera(update)
        }

        is CameraCommand.MoveToLocation -> {
            val zoom = if (cmd.useDefaultZoom) CAMERA_DEFAULT_ZOOM else map.cameraPosition.zoom
            val pos = CameraPosition.Builder().target(LatLng(cmd.lat, cmd.lon)).zoom(zoom).build()
            val update = CameraUpdateFactory.newCameraPosition(pos)
            if (cmd.animate) map.animateCamera(update) else map.moveCamera(update)
        }

        is CameraCommand.CenterOnStopTap -> {
            val pos = LatLng(cmd.lat, cmd.lon)
            val update = if (map.cameraPosition.zoom < CAMERA_DEFAULT_ZOOM) {
                CameraUpdateFactory.newLatLngZoom(pos, CAMERA_DEFAULT_ZOOM)
            } else {
                CameraUpdateFactory.newLatLng(pos)
            }
            map.animateCamera(update)
        }

        is CameraCommand.SetZoom -> map.moveCamera(CameraUpdateFactory.zoomTo(cmd.zoom.toDouble()))

        CameraCommand.ZoomIn -> map.animateCamera(CameraUpdateFactory.zoomIn())

        CameraCommand.ZoomOut -> map.animateCamera(CameraUpdateFactory.zoomOut())

        // maplibre has no map-type tilt reset, so its host never dispatches this.
        CameraCommand.ResetTilt -> Unit
    }
}

/**
 * Applies the map's retained [FramingIntent] to the maplibre [MapLibreMap] — the counterpart of the
 * Google adapter's `applyFramingIntent`. As on the old host, route/itinerary framing both just frame the
 * route shape (the screen-dimension padding was a Google-only refinement); the bounds are re-read from
 * [renderState]/the region live, so it's safe to re-apply when a re-created adapter replays the framing.
 */
fun applyFramingIntent(intent: FramingIntent, map: MapLibreMap, renderState: MapRenderState, context: Context) {
    when (intent) {
        FramingIntent.Route,
        FramingIntent.Itinerary -> {
            val bounds = routePolylineBounds(renderState) ?: return
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
        }

        FramingIntent.Region -> {
            val region = RegionEntryPoint.get(context).currentRegion() ?: return
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(MapHelpMapLibre.getRegionBounds(region), 0))
        }

        is FramingIntent.Point -> map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(intent.lat, intent.lon), intent.zoom.toDouble())
        )
    }
}

/** Bounds enclosing the current route/itinerary polylines, or null if there are no points. */
private fun routePolylineBounds(renderState: MapRenderState): LatLngBounds? {
    val builder = LatLngBounds.Builder()
    var any = false
    for (polyline in renderState.snapshot.value.routePolylines) {
        for (point in polyline.points) {
            builder.include(LatLng(point.latitude, point.longitude))
            any = true
        }
    }
    return if (any) builder.build() else null
}
