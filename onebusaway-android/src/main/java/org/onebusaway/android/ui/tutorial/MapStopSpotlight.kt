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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker

/**
 * Drives the welcome tutorial's map-stop spotlight in a map-SDK-agnostic way: while the
 * [WelcomeTutorial.KEY_MAP_STOP] step is up it picks the visible stop nearest the screen center,
 * projects it to screen coordinates through the neutral [projector], and reports those bounds to the
 * spotlight overlay. When the user advances past the step (not "X"), it focuses that stop via
 * [onFocusStop] so the arrivals tutorial continues.
 *
 * It depends only on the flavor-neutral map seam ([MapProjector] / [StopMarker]), never a map SDK, so
 * any map flavor that publishes a projector lights this up. A null [projector] (map not laid out, or a
 * flavor that doesn't publish one) leaves the overlay showing its plain full-screen card.
 *
 * The overlay is modal (it blocks map gestures) so the camera is static during the step; the short poll
 * just keeps the target fresh until the projector is ready and stops have loaded.
 */
@Composable
fun MapStopSpotlight(
    projector: MapProjector?,
    currentStops: () -> List<StopMarker>,
    onFocusStop: (ObaStop) -> Unit,
) {
    val tutorialState = LocalTutorialState.current ?: return
    val active = tutorialState.current?.id == WelcomeTutorial.KEY_MAP_STOP
    val windowSize = LocalWindowInfo.current.containerSize
    val markerRadiusPx = with(LocalDensity.current) { 20.dp.toPx() }
    var chosenStop by remember { mutableStateOf<ObaStop?>(null) }

    LaunchedEffect(active, projector) {
        val proj = projector ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        val centerX = windowSize.width / 2f
        val centerY = windowSize.height / 2f
        while (true) {
            val nearest = nearestProjected(currentStops(), { it.point }, centerX, centerY) {
                proj.toScreen(it)
            }
            if (nearest != null) {
                val (marker, offset) = nearest
                chosenStop = marker.stop
                tutorialState.reportBounds(
                    WelcomeTutorial.KEY_MAP_STOP,
                    Rect(
                        offset.x - markerRadiusPx,
                        offset.y - markerRadiusPx,
                        offset.x + markerRadiusPx,
                        offset.y + markerRadiusPx,
                    ),
                )
            }
            delay(120)
        }
    }

    val completed = tutorialState.completedStepId
    LaunchedEffect(completed) {
        if (completed == WelcomeTutorial.KEY_MAP_STOP) {
            chosenStop?.let(onFocusStop)
            tutorialState.consumeCompletion()
        }
    }
}

/**
 * The item whose geographic point ([pointOf]) projects nearest to [centerX],[centerY], paired with that
 * projected position — or null if [items] is empty or none of them project on screen ([project] returns
 * null). Pure, so the spotlight's target selection is JVM-unit-testable independently of the map SDK.
 */
internal fun <T> nearestProjected(
    items: List<T>,
    pointOf: (T) -> GeoPoint,
    centerX: Float,
    centerY: Float,
    project: (GeoPoint) -> ScreenOffset?,
): Pair<T, ScreenOffset>? {
    var best: Pair<T, ScreenOffset>? = null
    var bestDistance = Float.MAX_VALUE
    items.forEach { item ->
        val offset = project(pointOf(item)) ?: return@forEach
        val dx = offset.x - centerX
        val dy = offset.y - centerY
        val distance = dx * dx + dy * dy
        if (distance < bestDistance) {
            bestDistance = distance
            best = item to offset
        }
    }
    return best
}
