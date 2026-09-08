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
package org.onebusaway.android.map.compose

import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.util.ROUTE_NAME_ORDER

/**
 * Stops captured by a circular screen-space touch target, not a geographic equivalence rule. The caller passes
 * the touch-target radius in pixels and the live camera projection, so zooming
 * in separates nearby stops. A native marker/route-label hit always survives the lookup, including a
 * snapshot refresh; exact overlapping anchors retain their chooser when the tap lands on a label.
 */
internal fun stopChoicesAt(
    tapped: StopMarker?,
    stops: List<StopMarker>,
    tap: ScreenOffset? = null,
    projector: MapProjector? = null,
    targetRadiusPx: Float = 0f
): List<StopMarker> = (listOfNotNull(tapped) + stops)
    .filter { stop ->
        if (stop.id == tapped?.id || stop.point == tapped?.point) return@filter true
        if (tap == null) return@filter false
        val screen = projector?.toScreen(stop.point) ?: return@filter false
        val dx = screen.x - tap.x
        val dy = screen.y - tap.y
        dx * dx + dy * dy <= targetRadiusPx * targetRadiusPx
    }
    .distinctBy { it.id }
    .sortedWith(compareBy<StopMarker, String>(ROUTE_NAME_ORDER) { it.stop.stopCode.orEmpty() }.thenBy { it.id })
