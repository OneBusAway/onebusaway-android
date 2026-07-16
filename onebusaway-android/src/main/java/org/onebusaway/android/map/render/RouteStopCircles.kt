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

import android.graphics.Color

/** Shared screen-space styling for GPU/native route-stop circles in both map flavors. */
object RouteStopCircles {
    const val RADIUS_PX = 10f
    const val STROKE_WIDTH_PX = 3f
    const val FOCUSED_SCALE = 1.5f
    const val INNER_RADIUS_SCALE = 0.4f

    const val FILL_COLOR = Color.WHITE
    const val STROKE_COLOR: Int = TripMarkerBitmaps.STROKE_COLOR
}
