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
import org.onebusaway.android.map.rental.visibleHeightMeters
import org.onebusaway.android.util.GeoPoint

/** Spec §3.4: fit the stack's union bbox with 20 % padding. */
const val ONDEMAND_ZOOM_OUT_PADDING_FRACTION = 0.2

/** Never smaller than twice the street gate, so the thumbnail tap lands at region level. */
const val ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS = 2 * ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS

/** Never larger than 0.9 × the outer window, so the layer stays drawn. */
const val ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS = 0.9 * ONDEMAND_MAX_VISIBLE_HEIGHT_METERS

/**
 * The corners the map fits when the bar's thumbnail is tapped: the union of [bounds] padded by
 * [ONDEMAND_ZOOM_OUT_PADDING_FRACTION]; when that is shorter than the minimum or taller than the
 * maximum, a box of that height centred on [probe], keeping the padded box's aspect ratio.
 */
fun onDemandZoomOutCorners(bounds: List<Pair<GeoPoint, GeoPoint>>, probe: GeoPoint): Pair<GeoPoint, GeoPoint>? {
    if (bounds.isEmpty()) return null
    val minLat = bounds.minOf { it.first.latitude }
    val maxLat = bounds.maxOf { it.second.latitude }
    val minLon = bounds.minOf { it.first.longitude }
    val maxLon = bounds.maxOf { it.second.longitude }
    val height = (maxLat - minLat) * (1 + ONDEMAND_ZOOM_OUT_PADDING_FRACTION)
    val width = (maxLon - minLon) * (1 + ONDEMAND_ZOOM_OUT_PADDING_FRACTION)
    val heightMeters = visibleHeightMeters(height)
    val clampedHeight = when {
        heightMeters < ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS -> ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS / METERS_PER_DEGREE_LATITUDE
        heightMeters > ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS -> ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS / METERS_PER_DEGREE_LATITUDE
        else -> null
    }
    if (clampedHeight == null) {
        val centreLat = (minLat + maxLat) / 2
        val centreLon = (minLon + maxLon) / 2
        return GeoPoint(centreLat - height / 2, centreLon - width / 2) to GeoPoint(centreLat + height / 2, centreLon + width / 2)
    }
    val clampedWidth = if (height == 0.0) clampedHeight else width * clampedHeight / height
    return GeoPoint(probe.latitude - clampedHeight / 2, probe.longitude - clampedWidth / 2) to GeoPoint(probe.latitude + clampedHeight / 2, probe.longitude + clampedWidth / 2)
}
