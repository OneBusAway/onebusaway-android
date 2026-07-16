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
import org.onebusaway.android.map.render.DEFAULT_FRAMING_PADDING_DP
import org.onebusaway.android.map.render.FramingIntent
import org.onebusaway.android.map.render.MapPadding
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.POINTS_FRAMING_PADDING_DP
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.framingCorners
import org.onebusaway.android.util.ViewUtils
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

        is CameraCommand.SetZoom -> map.moveCamera(CameraUpdateFactory.zoomTo(cmd.zoom.toDouble()))

        is CameraCommand.RestoreViewport -> {
            val current = map.cameraPosition
            val viewport = cmd.viewport
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(viewport.center.latitude, viewport.center.longitude))
                        .zoom(viewport.zoom)
                        .bearing(current.bearing)
                        .tilt(current.tilt)
                        .build()
                )
            )
        }

        CameraCommand.ZoomIn -> map.animateCamera(CameraUpdateFactory.zoomIn())

        CameraCommand.ZoomOut -> map.animateCamera(CameraUpdateFactory.zoomOut())

        // maplibre has no map-type tilt reset, so its host never dispatches this.
        CameraCommand.ResetTilt -> Unit
    }
}

/**
 * Applies the map's retained [FramingIntent] to the maplibre [MapLibreMap] — the counterpart of the
 * Google adapter's `applyFramingIntent`. Route framing folds the live overlay obstruction into its fit;
 * itinerary framing retains the old shape-only behavior. Bounds are re-read from [renderState]/the region
 * live, so it's safe to re-apply when a re-created adapter replays the framing.
 */
fun applyFramingIntent(intent: FramingIntent, map: MapLibreMap, renderState: MapRenderState, context: Context) {
    when (intent) {
        FramingIntent.Route -> {
            val bounds = routePolylineBounds(renderState.routeFramingPolylines) ?: return
            val pad = ViewUtils.dpToPixels(context, DEFAULT_FRAMING_PADDING_DP)
            map.animateBounds(bounds, pad, renderState.padding.value)
        }

        FramingIntent.Itinerary -> {
            val bounds = routePolylineBounds(renderState.getRoutePolylines()) ?: return
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
        }

        FramingIntent.Region -> {
            val region = RegionEntryPoint.get(context).currentRegion() ?: return
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(MapHelpMapLibre.getRegionBounds(region), 0))
        }

        is FramingIntent.Point -> map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(intent.lat, intent.lon), intent.zoom.toDouble())
        )

        is FramingIntent.Points -> {
            val (sw, ne) = framingCorners(intent.points) ?: return
            val bounds = LatLngBounds.Builder()
                .include(LatLng(sw.latitude, sw.longitude))
                .include(LatLng(ne.latitude, ne.longitude))
                .build()
            // maplibre's newLatLngBounds fits the box against *only* the padding passed here — unlike the
            // Google flavor it does NOT consult the map's persistent setPadding (its CameraBoundsUpdate
            // fits against its own padding array). So the route-header (top) + arrivals-sheet (bottom)
            // insets have to be folded into the fit explicitly, on top of the symmetric breathing room,
            // or the vehicle+stop pair lands under one of the two overlays that are open after an ETA tap.
            val pad = ViewUtils.dpToPixels(context, POINTS_FRAMING_PADDING_DP)
            map.animateBounds(bounds, pad, renderState.padding.value)
        }
    }
}

/** Fit [bounds] inside the current top/bottom overlay obstruction plus symmetric breathing room. */
private fun MapLibreMap.animateBounds(bounds: LatLngBounds, pad: Int, overlay: MapPadding) {
    animateCamera(
        CameraUpdateFactory.newLatLngBounds(
            bounds,
            pad,
            overlay.topPx + pad,
            pad,
            overlay.bottomPx + pad,
        )
    )
}

/** Bounds enclosing [polylines], or null if there are no points. */
private fun routePolylineBounds(polylines: Iterable<RoutePolyline>): LatLngBounds? {
    val builder = LatLngBounds.Builder()
    var any = false
    for (polyline in polylines) {
        for (point in polyline.points) {
            builder.include(LatLng(point.latitude, point.longitude))
            any = true
        }
    }
    return if (any) builder.build() else null
}
