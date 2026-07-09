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
package org.onebusaway.android.region

import org.onebusaway.android.util.RegionUtils

/**
 * The center and lat/lon span of a region — a typed replacement for the `double[4]` out-param that
 * [RegionUtils.getRegionSpan] fills. Also exposes the region's bounding box, which geocoding uses to
 * bias/limit its search.
 */
data class RegionSpan(
    val latSpan: Double,
    val lonSpan: Double,
    val centerLat: Double,
    val centerLon: Double,
) {
    val minLat: Double get() = centerLat - latSpan / 2
    val minLon: Double get() = centerLon - lonSpan / 2
    val maxLat: Double get() = centerLat + latSpan / 2
    val maxLon: Double get() = centerLon + lonSpan / 2
}

/**
 * The [RegionSpan] for this region. Wraps [RegionUtils.getRegionSpan] (which stays the single source
 * of truth for the bounds math) so callers get a value instead of hand-reading a `double[4]`.
 */
fun Region.span(): RegionSpan {
    val results = DoubleArray(4)
    RegionUtils.getRegionSpan(this, results)
    return RegionSpan(
        latSpan = results[0],
        lonSpan = results[1],
        centerLat = results[2],
        centerLon = results[3],
    )
}
