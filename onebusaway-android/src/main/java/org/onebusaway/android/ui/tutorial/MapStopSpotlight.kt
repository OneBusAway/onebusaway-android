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
package org.onebusaway.android.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.StopMarker

/**
 * Puts the scripted tour's "tap a stop" spotlight over a real stop marker on the map (#2164), in a
 * map-SDK-agnostic way: while that step is up it finds [targetStopId] among the drawn markers, projects
 * it to screen coordinates through the neutral [projector], and reports those bounds to the spotlight
 * overlay. The caller names the stop, so this composable knows nothing of the demo system.
 *
 * Pinning to a *named* stop is the whole point. The tour's caption promises a stop served by three
 * routes, and the step after it opens that stop's arrivals — so the spotlight has to land on the stop
 * the script is about, not on whichever one happens to be nearest the middle of the screen (which is
 * all the retired live-data welcome tutorial could manage, and why its next step so often described
 * arrivals the rider wasn't looking at).
 *
 * Advancing past the step is handled by `ScriptedTutorialDirector`, which focuses the same stop through
 * the ordinary reveal path — so every one of the tour's actions is driven from one place rather than a
 * couple of them reaching in from the map layer.
 *
 * It depends only on the flavor-neutral map seam ([MapProjector] / [StopMarker]), never a map SDK, so
 * any map flavor that publishes a projector lights this up. Until the projector is ready and the demo
 * stop has been drawn, no bounds are reported and the overlay shows its plain full-screen card, which is
 * the correct rendering for "there is nothing to point at yet".
 *
 * The overlay is modal (it blocks map gestures) so the camera is static during the step; the short poll
 * just keeps the target fresh while the stops load in.
 */
@Composable
fun MapStopSpotlight(
    projector: MapProjector?,
    currentStops: () -> List<StopMarker>,
    targetStopId: String
) {
    val tutorialState = LocalTutorialState.current ?: return
    val active = tutorialState.current?.anchorId == ScriptedTutorial.KEY_STOP
    val markerRadiusPx = with(LocalDensity.current) { 20.dp.toPx() }

    LaunchedEffect(active, projector, targetStopId) {
        val proj = projector ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        while (true) {
            val offset = currentStops()
                .firstOrNull { it.id == targetStopId }
                ?.let { proj.toScreen(it.point) }
            if (offset != null) {
                tutorialState.reportBounds(
                    ScriptedTutorial.KEY_STOP,
                    Rect(
                        offset.x - markerRadiusPx,
                        offset.y - markerRadiusPx,
                        offset.x + markerRadiusPx,
                        offset.y + markerRadiusPx
                    )
                )
            }
            delay(120)
        }
    }
}
