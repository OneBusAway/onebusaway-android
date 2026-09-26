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
package org.onebusaway.android.map

import org.onebusaway.android.map.rental.METERS_PER_DEGREE_LATITUDE
import org.onebusaway.android.util.GeoPoint

/** Spec §3.4: fit the stack's union bbox with 20 % padding. */
const val ONDEMAND_ZOOM_OUT_PADDING_FRACTION = 0.2

/** Never smaller than twice the street gate, so the thumbnail tap lands at region level. */
const val ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS = 2 * ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS

/** Never larger than 0.9 × the outer window, so the layer stays drawn. */
const val ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS = 0.9 * ONDEMAND_MAX_VISIBLE_HEIGHT_METERS

/** Where the bar's thumbnail tap points the camera: [center], showing [latSpan] degrees of latitude. */
data class OnDemandZoomOutTarget(val center: GeoPoint, val latSpan: Double)

/**
 * The thumbnail tap's target: the union of [bounds] padded by [ONDEMAND_ZOOM_OUT_PADDING_FRACTION],
 * centred on the union. [viewportAspect] is the map's lonSpan / latSpan; a box wider than the
 * viewport is shown by its width, so the latitude the map would show is what gets clamped. Outside
 * the min/max height the target is the clamped height centred on [probe].
 *
 * The result is a centre and a span rather than corners to fit: a fit adds the map's content padding
 * (search bar, dock, framing margin) on top of the box, which can push a clamped box past
 * [ONDEMAND_MAX_VISIBLE_HEIGHT_METERS] and hide the layer the tap was meant to show.
 */
fun onDemandZoomOutTarget(bounds: List<Pair<GeoPoint, GeoPoint>>, probe: GeoPoint, viewportAspect: Double): OnDemandZoomOutTarget? {
    if (bounds.isEmpty()) return null
    val minLat = bounds.minOf { it.first.latitude }
    val maxLat = bounds.maxOf { it.second.latitude }
    val minLon = bounds.minOf { it.first.longitude }
    val maxLon = bounds.maxOf { it.second.longitude }
    val height = (maxLat - minLat) * (1 + ONDEMAND_ZOOM_OUT_PADDING_FRACTION)
    val width = (maxLon - minLon) * (1 + ONDEMAND_ZOOM_OUT_PADDING_FRACTION)
    val shownLatSpan = maxOf(height, width / viewportAspect)
    val minLatSpan = ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS / METERS_PER_DEGREE_LATITUDE
    val maxLatSpan = ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS / METERS_PER_DEGREE_LATITUDE
    return when {
        shownLatSpan < minLatSpan -> OnDemandZoomOutTarget(probe, minLatSpan)
        shownLatSpan > maxLatSpan -> OnDemandZoomOutTarget(probe, maxLatSpan)
        else -> OnDemandZoomOutTarget(GeoPoint((minLat + maxLat) / 2, (minLon + maxLon) / 2), shownLatSpan)
    }
}
