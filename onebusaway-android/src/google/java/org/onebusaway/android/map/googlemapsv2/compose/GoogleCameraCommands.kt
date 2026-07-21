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
import kotlin.math.abs
import org.onebusaway.android.R
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.map.googlemapsv2.MapHelpV2
import org.onebusaway.android.map.googlemapsv2.toLatLng
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.DEFAULT_FRAMING_PADDING_DP
import org.onebusaway.android.map.render.FramingIntent
import org.onebusaway.android.map.render.MapPadding
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.POINTS_FRAMING_PADDING_DP
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.framingCorners
import org.onebusaway.android.util.ViewUtils

// The same constants the imperative GoogleMapHost used for these camera moves.
private const val CAMERA_DEFAULT_ZOOM = 16.0f

/**
 * Applies one transient [CameraCommand] gesture to the imperative [GoogleMap] — a faithful port of the
 * `GoogleMapHost` methods that called `mMap.animateCamera/moveCamera` directly (the maps-compose
 * `CameraPositionState` detour is gone). The route-header recenter bias is unchanged, now reading the
 * live camera from [map]. Google's camera calls are fire-and-forget, so this is not a suspend function.
 * Retained framing (fit route / itinerary / region) is applied by [applyFramingIntent].
 */
fun applyCameraCommand(
    cmd: CameraCommand,
    map: GoogleMap
) {
    when (cmd) {
        is CameraCommand.Recenter -> {
            val current = map.cameraPosition
            var target = cmd.point.toLatLng()
            if (cmd.applyRouteBias) {
                // Map padding doesn't get the route-header offset quite right, so nudge the target up
                // by a fraction of the visible longitude span (the legacy setMapCenter bias).
                val bounds = map.projection.visibleRegion.latLngBounds
                val span = abs(bounds.northeast.longitude - bounds.southwest.longitude)
                target = LatLng(cmd.point.latitude - span * 0.2 / 2, cmd.point.longitude)
            }
            val update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target)
                    .zoom(current.zoom).bearing(current.bearing).tilt(current.tilt).build()
            )
            if (cmd.animate) map.animateCamera(update) else map.moveCamera(update)
        }

        is CameraCommand.MoveToLocation -> {
            val zoom = if (cmd.useDefaultZoom) CAMERA_DEFAULT_ZOOM else map.cameraPosition.zoom
            // newCameraPosition centers the target within the map's content padding (applied globally via
            // setPadding), so the point lands in the visible band above the directions results sheet.
            val update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(cmd.point.toLatLng()).zoom(zoom).build()
            )
            if (cmd.animate) map.animateCamera(update) else map.moveCamera(update)
        }

        is CameraCommand.SetZoom -> map.moveCamera(CameraUpdateFactory.zoomTo(cmd.zoom))

        is CameraCommand.RestoreViewport -> {
            val current = map.cameraPosition
            val viewport = cmd.viewport
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(viewport.center.latitude, viewport.center.longitude))
                        .zoom(viewport.zoom.toFloat())
                        .bearing(current.bearing)
                        .tilt(current.tilt)
                        .build()
                )
            )
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

/**
 * Applies the map's retained [FramingIntent] to the imperative [GoogleMap] — the bounds/target math
 * ported unchanged from the former `FitToRoute`/`FitToItinerary`/`ZoomToRegion` camera commands,
 * re-reading the route shape from [renderState] and the region bounds live so it's safe to re-apply when
 * a re-created adapter replays the current framing. Fire-and-forget, so not a suspend function.
 */
fun applyFramingIntent(
    intent: FramingIntent,
    map: GoogleMap,
    renderState: MapRenderState,
    context: Context
) {
    when (intent) {
        FramingIntent.Route -> {
            val bounds = routePolylineBounds(renderState.routeFramingPolylines)
            if (bounds == null) {
                Toast.makeText(context, R.string.route_info_no_shape_data, Toast.LENGTH_SHORT).show()
            } else {
                map.animateBounds(bounds, ViewUtils.dpToPixels(context, DEFAULT_FRAMING_PADDING_DP), renderState.padding.value)
            }
        }

        FramingIntent.Itinerary -> {
            val bounds = routePolylineBounds(renderState.getRoutePolylines()) ?: return
            // Fit within the directions form (top) + results sheet (bottom) insets so the whole itinerary
            // lands in the visible band, matching Route/Points. Those insets are Compose-measured after
            // this first fit, so MapHost re-emits the frame as they land to self-correct (#1954).
            map.animateBounds(bounds, ViewUtils.dpToPixels(context, DEFAULT_FRAMING_PADDING_DP), renderState.padding.value)
        }

        FramingIntent.Region -> {
            val region = RegionEntryPoint.get(context).currentRegion() ?: return
            val bounds = MapHelpV2.getRegionBounds(region)
            // Use screen dimensions to avoid IllegalStateException (#581).
            val dm = context.resources.displayMetrics
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dm.widthPixels, dm.heightPixels, 0))
        }

        is FramingIntent.Point -> map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(intent.point.toLatLng(), intent.zoom)
        )

        is FramingIntent.Points -> {
            val (sw, ne) = framingCorners(intent.points, intent.minSpanDeg) ?: return
            val bounds = LatLngBounds(LatLng(sw.latitude, sw.longitude), LatLng(ne.latitude, ne.longitude))
            // An ETA tap emits the padding update and this framing together with no ordering guarantee, so
            // fold the current insets in here (see animateBounds) or the vehicle+stop pair lands under the
            // overlays open right after the tap.
            map.animateBounds(bounds, ViewUtils.dpToPixels(context, POINTS_FRAMING_PADDING_DP), renderState.padding.value)
        }
    }
}

/**
 * Ease the camera to fit [bounds] within [overlay] (the top/bottom content-padding obstruction) plus
 * [paddingPx] of symmetric breathing room. Applies [overlay] via [applyMapPadding] first so Google's
 * newLatLngBounds fits inside it: framing and the padding collector ([GoogleComposeAdapter]) are
 * independent coroutines with no ordering guarantee, so setting the current obstruction here makes the fit
 * self-sufficient (idempotent with the collector — setPadding is absolute, not additive). animateCamera
 * (not moveCamera) eases in, matching the maplibre adapter's animateBounds (#1719). The Route/Itinerary/
 * Points branches differ only in [paddingPx]; MapHost re-emits the frame as late-measured insets land so
 * the fit self-corrects (#1954).
 */
private fun GoogleMap.animateBounds(bounds: LatLngBounds, paddingPx: Int, overlay: MapPadding) {
    applyMapPadding(overlay)
    animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
}

/**
 * Push a [MapPadding] onto the [GoogleMap] as content padding — the single place the top/bottom insets
 * map onto `setPadding`'s slots (left/right stay 0). Shared by the [GoogleComposeAdapter] padding
 * collector and [animateBounds], so both agree on the mapping.
 */
internal fun GoogleMap.applyMapPadding(padding: MapPadding) = setPadding(0, padding.topPx, 0, padding.bottomPx)

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
