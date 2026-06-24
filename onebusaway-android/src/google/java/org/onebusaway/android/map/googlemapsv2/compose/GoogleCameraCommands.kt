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
package org.onebusaway.android.map.googlemapsv2.compose

import android.content.Context
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.map.googlemapsv2.MapHelpV2
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.util.ViewUtils
import kotlin.math.abs

// The same constants the imperative GoogleMapHost used for these camera moves.
private const val CAMERA_DEFAULT_ZOOM = 16.0f
private const val DEFAULT_MAP_PADDING_DP = 20.0f

/**
 * Applies one [CameraCommand] to the imperative [GoogleMap] — a faithful port of the `GoogleMapHost`
 * methods that called `mMap.animateCamera/moveCamera` directly (the maps-compose `CameraPositionState`
 * detour is gone). The math (bounds, the route-header recenter bias, the closest-vehicle visibility
 * short-circuit) is unchanged, now reading the live camera from [map] and the route shape from
 * [renderState]. Google's camera calls are fire-and-forget, so this is not a suspend function.
 */
fun applyCameraCommand(
    cmd: CameraCommand,
    map: GoogleMap,
    renderState: MapRenderState,
    context: Context,
) {
    when (cmd) {
        is CameraCommand.Recenter -> {
            val current = map.cameraPosition
            var target = LatLng(cmd.lat, cmd.lon)
            if (cmd.applyRouteBias) {
                // Map padding doesn't get the route-header offset quite right, so nudge the target up
                // by a fraction of the visible longitude span (the legacy setMapCenter bias).
                val bounds = map.projection.visibleRegion.latLngBounds
                val span = abs(bounds.northeast.longitude - bounds.southwest.longitude)
                target = LatLng(cmd.lat - span * 0.2 / 2, cmd.lon)
            }
            val update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target)
                    .zoom(current.zoom).bearing(current.bearing).tilt(current.tilt).build()
            )
            if (cmd.animate) map.animateCamera(update) else map.moveCamera(update)
        }

        is CameraCommand.MoveToLocation -> {
            val zoom = if (cmd.useDefaultZoom) CAMERA_DEFAULT_ZOOM else map.cameraPosition.zoom
            val update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(cmd.lat, cmd.lon)).zoom(zoom).build()
            )
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

        is CameraCommand.SetZoom -> map.moveCamera(CameraUpdateFactory.zoomTo(cmd.zoom))

        CameraCommand.FitToRoute -> {
            val bounds = routePolylineBounds(renderState)
            if (bounds == null) {
                Toast.makeText(context, R.string.route_info_no_shape_data, Toast.LENGTH_SHORT).show()
            } else {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, ViewUtils.dpToPixels(context, DEFAULT_MAP_PADDING_DP))
                )
            }
        }

        CameraCommand.FitToItinerary -> {
            val bounds = routePolylineBounds(renderState) ?: return
            val dm = context.resources.displayMetrics
            map.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds, dm.widthPixels, dm.heightPixels,
                    ViewUtils.dpToPixels(context, DEFAULT_MAP_PADDING_DP)
                )
            )
        }

        CameraCommand.ZoomToRegion -> {
            val region = Application.get().currentRegion ?: return
            val bounds = MapHelpV2.getRegionBounds(region)
            // Use screen dimensions to avoid IllegalStateException (#581).
            val dm = context.resources.displayMetrics
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dm.widthPixels, dm.heightPixels, 0))
        }

        CameraCommand.ZoomIn -> map.animateCamera(CameraUpdateFactory.zoomIn())

        CameraCommand.ZoomOut -> map.animateCamera(CameraUpdateFactory.zoomOut())

        CameraCommand.ResetTilt -> {
            val current = map.cameraPosition
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(current.target).zoom(current.zoom).tilt(0f).build()
                )
            )
        }
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
