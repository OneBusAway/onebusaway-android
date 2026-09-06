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

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.util.GeoPoint

/** Stop interaction shared by the map flavors; nonselecting callers keep their original callbacks. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StopSelectionMap(
    renderState: MapRenderState,
    callbacks: ObaMapCallbacks?,
    modifier: Modifier = Modifier,
    stopSelectionEnabled: Boolean = false,
    content: @Composable (ObaMapCallbacks?, Modifier) -> Unit
) {
    if (!stopSelectionEnabled || callbacks == null) {
        content(callbacks, modifier)
        return
    }
    var stopChoices by remember(renderState, callbacks) { mutableStateOf<List<StopMarker>>(emptyList()) }
    val tapPosition = remember(renderState) { MapTapPosition() }
    val touchSize = LocalViewConfiguration.current.minimumTouchTargetSize
    val density = LocalDensity.current
    val targetWidthPx = with(density) { touchSize.width.toPx() }
    val targetHeightPx = with(density) { touchSize.height.toPx() }
    val mapCallbacks = remember(renderState, callbacks, targetWidthPx, targetHeightPx) {
        object : ObaMapCallbacks by callbacks {
            private fun chooseStop(marker: StopMarker?, point: GeoPoint? = null): Boolean {
                val projector = renderState.projector.value
                val tap = tapPosition.take() ?: point?.let { projector?.toScreen(it) }
                val choices = stopChoicesAt(
                    marker,
                    renderState.snapshot.value.stops,
                    tap,
                    projector,
                    targetWidthPx,
                    targetHeightPx
                )
                when (choices.size) {
                    0 -> return false
                    1 -> callbacks.onStopClick(choices.single())
                    else -> stopChoices = choices
                }
                return true
            }

            override fun onStopClick(marker: StopMarker) {
                chooseStop(marker)
            }

            override fun onMapClick(point: GeoPoint?) {
                if (!chooseStop(null, point)) callbacks.onMapClick(point)
            }

            override fun onMapLongClick(point: GeoPoint) {
                tapPosition.take()
                callbacks.onMapLongClick(point)
            }
        }
    }
    if (stopChoices.isNotEmpty()) {
        StopChoiceDialog(
            stops = stopChoices,
            onSelect = { selected ->
                stopChoices = emptyList()
                callbacks.onStopClick(selected)
            },
            onDismiss = { stopChoices = emptyList() }
        )
    }
    content(mapCallbacks, modifier.motionEventSpy(tapPosition::observe))
}

/** Observe without consuming: the map SDK still decides whether a gesture is a click or a pan. */
private class MapTapPosition {
    private var position: ScreenOffset? = null

    fun observe(event: MotionEvent) {
        // motionEventSpy supplies the original Compose-view event, already in root coordinates.
        // Projectors add the map's inset to their map-local coordinates; adding it here would double it.
        position = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> ScreenOffset(event.x, event.y)
            else -> null
        }
    }

    fun take(): ScreenOffset? = position.also { position = null }
}
