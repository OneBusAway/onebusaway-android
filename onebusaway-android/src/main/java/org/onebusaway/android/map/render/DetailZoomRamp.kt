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

/** Shared zoom interval for route-detail styling. */
const val DETAIL_RAMP_START_ZOOM = 11f
const val DETAIL_RAMP_END_ZOOM = 16f

/** Route-line scale: half width when zoomed out, full width at zoom 16 and above. */
const val ROUTE_DETAIL_DISTANT_SCALE = 0.5f

/** Route-line width policy; stop circles use the same machinery with their own endpoint scale. */
fun routeDetailScale(zoom: Float): Float = detailZoomRamp(
    zoom,
    startZoom = DETAIL_RAMP_START_ZOOM,
    endZoom = DETAIL_RAMP_END_ZOOM,
    distantValue = ROUTE_DETAIL_DISTANT_SCALE,
    closeValue = 1f,
)

/** Linear interpolation machinery for zoom-dependent map-detail styling. */
internal fun detailZoomRamp(
    zoom: Float,
    startZoom: Float,
    endZoom: Float,
    distantValue: Float,
    closeValue: Float,
): Float {
    val progress = ((zoom - startZoom) / (endZoom - startZoom)).coerceIn(0f, 1f)
    return distantValue + (closeValue - distantValue) * progress
}
