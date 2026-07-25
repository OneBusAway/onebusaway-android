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
package org.onebusaway.android.ui.home.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.hoopRadiusDp

/**
 * The nearby-routes hoop's ring (#2004): the half-mile circle whose interior selects the routes the
 * map is drawing.
 *
 * Drawn here in **screen space**, over the map, rather than as map geometry — which is what makes a
 * drag carry it along with the gesture instead of leaving it planted on the ground while the map
 * slides out from under it. The consequence is that it needs no per-frame work during a pan at all: a
 * geographic circle centred on the camera *is* a fixed circle at the centre of the viewport, so only a
 * zoom changes anything, and that comes from [org.onebusaway.android.map.MapHost.cameraLens] (which,
 * unlike the idle-only camera, reports mid-gesture, so the ring tracks a pinch instead of snapping at
 * the end).
 *
 * The camera's target sits at the centre of the map's *padded* viewport, so the ring is centred
 * against the same insets the renderer applies — otherwise it would drift away from the routes it
 * bounds whenever the arrivals sheet or a banner is up.
 *
 * Both live values are read inside the draw lambda, not in composition: a pinch then only invalidates
 * this canvas's draw pass instead of recomposing anything.
 */
@Composable
fun NearbyRoutesHoopRing(mapViewModel: MapViewModel, modifier: Modifier = Modifier) {
    val hoop by mapViewModel.nearbyRoutesHoop.collectAsStateWithLifecycle()
    val radiusMeters = hoop?.radiusMeters ?: return
    val lens = mapViewModel.host.cameraLens.collectAsStateWithLifecycle()
    val padding = mapViewModel.renderState.padding.collectAsStateWithLifecycle()
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HOOP_RING_ALPHA)

    Canvas(modifier.fillMaxSize()) {
        val camera = lens.value ?: return@Canvas
        val insets = padding.value
        val radius = hoopRadiusDp(radiusMeters, camera.zoom, camera.latitude).dp.toPx()
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(
                x = size.width / 2f,
                y = (insets.topPx + size.height - insets.bottomPx) / 2f
            ),
            style = Stroke(width = HOOP_RING_STROKE.toPx())
        )
    }
}

/** Faint enough to read as a lens over the map rather than as a drawn feature of it. */
private const val HOOP_RING_ALPHA = 0.35f

private val HOOP_RING_STROKE = 2.dp
