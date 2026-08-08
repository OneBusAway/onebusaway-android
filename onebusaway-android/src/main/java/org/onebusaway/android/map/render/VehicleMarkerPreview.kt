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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.models.ObaRoute

/**
 * A grid of every vehicle marker [VehicleBitmaps] can render — the five modes down, the five fullness
 * states across — so the composited disc/tab/glyph/pips can be eyeballed without live vehicles on the
 * map. The [color] would normally be the schedule-deviation color; a fixed sample is used here.
 *
 * The leftmost column is the marker for a vehicle that reports no fullness: a plain disc with no tab,
 * which is the shape to check a disc-geometry change against. The rest are the tab, filling left to
 * right, and the pair to look hardest at is "empty" vs "1 of 3" — an all-hollow row and a row with one
 * inked pip are the closest two readings on the scale.
 */
@Composable
fun VehicleMarkerGrid(color: Color = Color(0xFF2266CC)) {
    val argb = color.toArgb()
    val modes = listOf(
        ObaRoute.TYPE_BUS to "bus",
        ObaRoute.TYPE_RAIL to "rail",
        ObaRoute.TYPE_SUBWAY to "subway",
        ObaRoute.TYPE_TRAM to "tram",
        ObaRoute.TYPE_FERRY to "ferry"
    )

    Column(Modifier.background(Color.White).padding(8.dp)) {
        FullnessHeader()
        modes.forEach { (type, name) ->
            MarkerRow(label = name, vehicleType = type, argb = argb)
        }
    }
}

@Composable
private fun FullnessHeader() {
    Row {
        Spacer(Modifier.width(56.dp))
        listOf("none", "empty", "1 of 3", "2 of 3", "full").forEach {
            Text(it, Modifier.width(44.dp), fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MarkerRow(label: String, vehicleType: Int, argb: Int) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), fontSize = 12.sp)
        for (fill in listOf(null, 0, 1, 2, 3)) {
            // previewBitmap is @VisibleForTesting; this @Preview catalog is dev-only tooling
            // (not a production render path), so calling it here is intentional.
            @Suppress("VisibleForTests")
            val bmp = remember(vehicleType, argb, fill) {
                VehicleBitmaps.previewBitmap(context, vehicleType, argb, fill).asImageBitmap()
            }
            Image(bmp, contentDescription = null, modifier = Modifier.width(44.dp).height(56.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 300, heightDp = 340)
@Composable
private fun VehicleMarkerGridPreview() {
    VehicleMarkerGrid()
}
