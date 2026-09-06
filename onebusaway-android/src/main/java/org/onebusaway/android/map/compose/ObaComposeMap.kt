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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.map.MapHost
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.util.GeoPoint

/**
 * The flavor-neutral, declarative map surface. A flavor adapter (the Google `GoogleMap {}` content,
 * or the maplibre `MapView` wrapped in an `AndroidView`) implements [Content]; `src/main` selects the
 * implementation by reflection on `BuildConfig.MAP_COMPOSE_ADAPTER_CLASS`. The adapter binds to the
 * shared [MapHost]: it renders the host's [MapRenderState], reports taps through [ObaMapCallbacks], and
 * drives the host (camera read-back, styling, location) — there is no imperative host any more, and it
 * does not depend on *which* use-case view model owns the host.
 */
interface ObaComposeMapAdapter {

    @Composable
    fun Content(
        host: MapHost,
        callbacks: ObaMapCallbacks?,
        modifier: Modifier,
        initialLatitude: Double,
        initialLongitude: Double,
        initialZoom: Float
    )

    companion object {
        /** Reflectively builds the flavor adapter named by `BuildConfig.MAP_COMPOSE_ADAPTER_CLASS`. */
        fun newInstance(): ObaComposeMapAdapter = Class.forName(BuildConfig.MAP_COMPOSE_ADAPTER_CLASS)
            .getDeclaredConstructor()
            .newInstance() as ObaComposeMapAdapter
    }
}

/** The neutral map composable: resolves the flavor adapter once and renders its [ObaComposeMapAdapter.Content]. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ObaMap(
    host: MapHost,
    callbacks: ObaMapCallbacks?,
    modifier: Modifier = Modifier,
    initialLatitude: Double = 0.0,
    initialLongitude: Double = 0.0,
    initialZoom: Float = 16f
) {
    val adapter = remember { ObaComposeMapAdapter.newInstance() }
    var stopChoices by remember(host, callbacks) { mutableStateOf<List<StopMarker>>(emptyList()) }
    val tapPosition = remember(host) { MapTapPosition() }
    val touchSize = LocalViewConfiguration.current.minimumTouchTargetSize
    val density = LocalDensity.current
    val targetWidthPx = with(density) { touchSize.width.toPx() }
    val targetHeightPx = with(density) { touchSize.height.toPx() }
    val mapCallbacks = remember(host, callbacks, targetWidthPx, targetHeightPx) {
        callbacks?.let { downstream ->
            object : ObaMapCallbacks by downstream {
                private fun chooseStop(marker: StopMarker?, point: GeoPoint? = null): Boolean {
                    val projector = host.renderState.projector.value
                    val tap = tapPosition.take() ?: point?.let { projector?.toScreen(it) }
                    val choices = stopChoicesAt(
                        marker,
                        host.renderState.snapshot.value.stops,
                        tap,
                        projector,
                        targetWidthPx,
                        targetHeightPx
                    )
                    when (choices.size) {
                        0 -> return false
                        1 -> downstream.onStopClick(choices.single())
                        else -> stopChoices = choices
                    }
                    return true
                }

                override fun onStopClick(marker: StopMarker) {
                    chooseStop(marker)
                }

                override fun onMapClick(point: GeoPoint?) {
                    if (!chooseStop(null, point)) downstream.onMapClick(point)
                }

                override fun onMapLongClick(point: GeoPoint) {
                    tapPosition.take()
                    downstream.onMapLongClick(point)
                }
            }
        }
    }
    if (stopChoices.isNotEmpty()) {
        StopChoiceDialog(
            stops = stopChoices,
            onSelect = { selected ->
                stopChoices = emptyList()
                callbacks?.onStopClick(selected)
            },
            onDismiss = { stopChoices = emptyList() }
        )
    }
    adapter.Content(
        host,
        mapCallbacks,
        modifier
            .onGloballyPositioned { tapPosition.rootOffset = it.positionInRoot() }
            .motionEventSpy(tapPosition::observe),
        initialLatitude,
        initialLongitude,
        initialZoom
    )
}

/** Observe without consuming: the map SDK still decides whether a gesture is a click or a pan. */
private class MapTapPosition {
    var rootOffset = Offset.Zero
    private var position: ScreenOffset? = null

    fun observe(event: MotionEvent) {
        position = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> ScreenOffset(event.x + rootOffset.x, event.y + rootOffset.y)
            else -> null
        }
    }

    fun take(): ScreenOffset? = position.also { position = null }
}
