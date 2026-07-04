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

/**
 * A one-shot, **transient** camera gesture (zoom step, recenter, my-location move, stop-tap centering).
 * A use-case controller dispatches one of these to [MapRenderState.cameraGestures], and the flavor
 * adapter applies it against its imperative map (`GoogleMap`/`MapLibreMap.animateCamera(...)`) — so the
 * camera is driven from state, decoupled from any one screen, rather than poked directly on the map.
 *
 * Transient: a gesture dispatched while no map is subscribed is *meant* to be discarded (the flow has no
 * replay). Persistent framing — fit the route / itinerary / region — is the separate [FramingIntent],
 * which is retained so a late subscriber catches up.
 *
 * Flavor-neutral: it carries *intent*, never a Google/maplibre `CameraUpdate`. The renderer computes
 * the actual target from the current render state + the live viewport; the route-mode recenter bias is
 * resolved by the renderer from the live camera.
 */
sealed interface CameraCommand {

    /**
     * Center on a point, preserving the current zoom/bearing/tilt. [applyRouteBias] nudges the target
     * up by a fraction of the visible longitude span (the legacy route-header offset), evaluated by
     * the host since it owns the map mode.
     */
    data class Recenter(
        val lat: Double,
        val lon: Double,
        val animate: Boolean,
        val applyRouteBias: Boolean,
    ) : CameraCommand

    /** Move to the user's location at either the default zoom or the current zoom (the my-location FAB). */
    data class MoveToLocation(
        val lat: Double,
        val lon: Double,
        val useDefaultZoom: Boolean,
        val animate: Boolean,
    ) : CameraCommand

    /** Center on a tapped stop, bumping to the default zoom only if currently zoomed out past it. */
    data class CenterOnStopTap(val lat: Double, val lon: Double) : CameraCommand

    /** Set the zoom level (preserving the current center). */
    data class SetZoom(val zoom: Float) : CameraCommand

    /** Step zoom in/out (the zoom FABs). */
    object ZoomIn : CameraCommand

    object ZoomOut : CameraCommand

    /** Reset the camera tilt to 0 (after a 2D/3D/satellite map-type change). */
    object ResetTilt : CameraCommand
}
