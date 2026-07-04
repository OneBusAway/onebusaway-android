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
 * A **retained** camera framing — "what the map is currently framing" — as opposed to a transient
 * [CameraCommand] gesture. A controller sets one via [MapRenderState.frame] and the flavor adapter fits
 * its imperative map to it; unlike a gesture, the current framing is held (replayed) so a late or
 * re-created adapter catches up and re-applies it. That replay is what lets the host drop the old
 * deferral machinery (`pendingFrameCommands`, `mapAttached`/`cameraCommandsSubscribed`, and the
 * `onCameraCommandsSubscribed` flush): a frame requested before the adapter subscribes — the directions
 * map composed behind the results sheet the instant a plan completes (#1640), or the region re-zoom
 * resolved at cold start — is caught by the replay instead of dropped into a no-replay flow.
 *
 * Flavor-neutral: like [CameraCommand] it carries *intent*, never a Google/maplibre `CameraUpdate`. The
 * bounds-fitting cases re-derive their bounds from the live render state + region each time they're
 * applied (so they are idempotent and safe to re-apply); [Point] carries an explicit target + zoom.
 */
sealed interface FramingIntent {

    /** Fit the route polyline bounds with the default padding. */
    object Route : FramingIntent

    /** Fit the itinerary polyline bounds, padding against the screen dimensions. */
    object Itinerary : FramingIntent

    /** Fit the current region's bounds. */
    object Region : FramingIntent

    /** Center on a fixed point at a fixed zoom (the degenerate directions itinerary: start == end). */
    data class Point(val lat: Double, val lon: Double, val zoom: Float) : FramingIntent
}
