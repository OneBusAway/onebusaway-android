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

import org.onebusaway.android.map.rental.visibleHeightMeters

/** Spec §2.2: decided from the visible map height alone, so the layer, the dock and tests read one answer. */
enum class OnDemandZoomLevel { HIDDEN, REGION, STREET }

/** At or below this much visible latitude the map is at street level: strokes only, and the docked bar. */
const val ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS = 4_000.0

fun onDemandZoomLevel(latSpan: Double): OnDemandZoomLevel {
    val height = visibleHeightMeters(latSpan)
    return when {
        height > ONDEMAND_MAX_VISIBLE_HEIGHT_METERS -> OnDemandZoomLevel.HIDDEN
        height > ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS -> OnDemandZoomLevel.REGION
        else -> OnDemandZoomLevel.STREET
    }
}
