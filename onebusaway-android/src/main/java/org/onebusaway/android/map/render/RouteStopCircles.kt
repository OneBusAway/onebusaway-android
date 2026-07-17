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

/** Shared screen-space styling for GPU/native route-stop circles in both map flavors. */
object RouteStopCircles {
    const val RADIUS_PX = 10f
    const val STROKE_WIDTH_PX = 2.7f
    const val FOCUSED_SCALE = 1.8f
    const val INNER_RADIUS_SCALE = 0.36f

    // All three route-stop circle colors are theme-aware resources resolved by the flavor layers (this
    // pure styling layer has no Context): the unselected fill `R.color.route_stop_fill`, the outline
    // `R.color.route_stop_outline`, and the selected fill `R.color.map_stop_focus` (the shared
    // selected-stop highlight — lighter in light mode, deeper in dark mode).
}
