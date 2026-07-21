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

import org.onebusaway.android.util.GeoPoint

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

    /** Fit the itinerary polyline bounds within the map's content padding (directions form + results sheet). */
    object Itinerary : FramingIntent

    /** Fit the current region's bounds. */
    object Region : FramingIntent

    /** Center on a fixed point at a fixed zoom (the degenerate directions itinerary: start == end). */
    data class Point(val point: GeoPoint, val zoom: Float) : FramingIntent

    /**
     * Fit the bounds enclosing an explicit set of [points] with the default padding — the arrivals-row
     * tap fitting a route's live vehicle together with its originating stop, so the map frames the
     * relationship between the two. Self-contained (unlike [Route]/[Region], it carries its own points),
     * so a replay to a re-created adapter just re-fits the same box. The adapter expands a box smaller
     * than [minSpanDeg] up to that span so a tiny/degenerate box (a vehicle sitting on its stop) doesn't
     * zoom to the rooftops (see [framingCorners]); a tapped walking leg passes a tighter block-level
     * floor ([WALK_LEG_MIN_FRAMING_SPAN_DEG]) so a short hop frames closer in.
     */
    data class Points(
        val points: List<GeoPoint>,
        val minSpanDeg: Double = MIN_FRAMING_SPAN_DEG
    ) : FramingIntent
}

/** Default breathing room between route/itinerary bounds and the unobstructed map viewport. */
const val DEFAULT_FRAMING_PADDING_DP: Float = 20.0f

/** The smallest lat/lon span a [FramingIntent.Points] box is fit to, so near-coincident points don't over-zoom. */
const val MIN_FRAMING_SPAN_DEG: Double = 0.004

/**
 * The block-level minimum span used when framing a tapped walking leg — so a short hop (e.g. crossing a
 * street) frames closer in than the default [MIN_FRAMING_SPAN_DEG] rather than making the user zoom in
 * further. ~0.0014° of latitude ≈ 500 ft; longitude degrees run shorter at these latitudes, so the box
 * is a touch narrower than tall, matching the existing degree-uniform convention (see [framingCorners]).
 */
const val WALK_LEG_MIN_FRAMING_SPAN_DEG: Double = 0.0014

/**
 * Padding (dp) between a [FramingIntent.Points] box and the map edges — breathing room around the fit
 * pair. Flavor-neutral so both map adapters frame the vehicle+stop identically (each converts to pixels).
 */
const val POINTS_FRAMING_PADDING_DP: Float = 48.0f

/**
 * The SW and NE corners enclosing [points], each side widened to at least [minSpanDeg] around the box's
 * center so a degenerate/tiny box (a vehicle on top of its stop) frames at a comfortable zoom instead of
 * the maximum. Null when [points] is empty. Flavor-neutral so both map adapters share the same box math.
 */
fun framingCorners(
    points: List<GeoPoint>,
    minSpanDeg: Double = MIN_FRAMING_SPAN_DEG
): Pair<GeoPoint, GeoPoint>? {
    if (points.isEmpty()) return null
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val latPad = ((minSpanDeg - (maxLat - minLat)) / 2).coerceAtLeast(0.0)
    val lonPad = ((minSpanDeg - (maxLon - minLon)) / 2).coerceAtLeast(0.0)
    return GeoPoint(minLat - latPad, minLon - lonPad) to GeoPoint(maxLat + latPad, maxLon + lonPad)
}
