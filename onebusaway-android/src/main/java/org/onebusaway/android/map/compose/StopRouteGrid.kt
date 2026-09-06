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

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import org.onebusaway.android.R
import org.onebusaway.android.map.render.ContinuationBadgeBitmaps
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.map.render.StopRouteGridOrientation
import org.onebusaway.android.map.render.stopRouteLabelGrid
import org.onebusaway.android.ui.compose.theme.isDarkTheme

/** The map's route grid, without its marker offset, sized for reading in a Compose surface. */
@Composable
internal fun StopRouteGrid(
    routes: List<StopRoute>,
    modifier: Modifier = Modifier,
    orientation: StopRouteGridOrientation = StopRouteGridOrientation.Vertical
) {
    if (routes.isEmpty()) return
    val dark = MaterialTheme.colorScheme.isDarkTheme()
    val density = LocalDensity.current
    val textSizePx = with(density) { MaterialTheme.typography.bodyMedium.fontSize.toPx() }
    val bitmap = remember(routes, orientation, dark, density.density, textSizePx) {
        ContinuationBadgeBitmaps.badgeGridForTextSize(
            stopRouteLabelGrid(routes, dark, orientation),
            density.density,
            dark,
            textSizePx
        ).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.map_stop_routes, routes.joinToString(", ") { it.shortName }),
        modifier = modifier
    )
}
