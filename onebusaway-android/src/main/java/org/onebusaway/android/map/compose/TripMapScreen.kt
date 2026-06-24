/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.onebusaway.android.R
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.ui.compose.components.ObaTopAppBar

/**
 * The speed-estimation trip-focus map destination. Reuses the shared [MapViewModel] + the single map
 * render pipeline: on enter it puts the map into trip-focus mode (route shape + camera framing + the
 * live extrapolated overlay), restoring stop mode on dispose. The overlay itself is drawn by the
 * flavor renderers, which pull `MapRenderState.tripOverlaySampler` each display frame.
 *
 * Shares the "Trip status" [ObaTopAppBar] with the list view ([org.onebusaway.android.ui.tripdetails])
 * so the two presentations of the same trip read as one screen.
 */
@Composable
fun TripMapScreen(
    mapViewModel: MapViewModel,
    tripId: String,
    lineColorArgb: Int,
    onBack: () -> Unit,
) {
    DisposableEffect(tripId) {
        mapViewModel.enterTripFocus(tripId, lineColorArgb)
        onDispose { mapViewModel.exitTripFocus() }
    }

    Scaffold(
        topBar = { ObaTopAppBar(title = stringResource(R.string.trip_status), onBack = onBack) }
    ) { padding ->
        val seed = mapViewModel.cameraSeed
        ObaMap(
            host = mapViewModel.host,
            // The adapters require non-null callbacks; the trip map has no map taps to handle.
            callbacks = NoOpObaMapCallbacks,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            initialLatitude = seed.lat,
            initialLongitude = seed.lon,
            initialZoom = seed.zoom,
        )
    }
}
