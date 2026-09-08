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

/** Shared screen-space styling for geographic route-stop markers in both map flavors. */
object RouteStopCircles {
    const val RADIUS_PX = 11.25f
    const val STROKE_WIDTH_PX = 4.125f

    /**
     * Adjacent (non-focused) route-stop circles shrink to 80% size in stop focus, before any route
     * is selected, so the focused stop stands out and the mode reads distinctly from selected-route
     * focus (#1985). Their stroke rides the smaller radius, thinning in proportion.
     */
    const val ADJACENT_SCALE = 0.8f

    /** The arrow keeps its finer outline independently of the heavier circle rim. */
    const val ARROW_OUTLINE_WIDTH_DP = 1.75f

    // Route-colored and neutral gray rims have white centers in both light and dark mode.
    // The selected stop uses the ordinary orange stop icon instead.
}
