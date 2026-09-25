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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Shared fixtures for the tests that render a vehicle marker and read its pixels back
 * ([VehicleMarkerOutlineTest], [VehicleMarkerPipTest]).
 *
 * What lives here is what the two of them have to **agree** on. Each keeps its own sampling — they scan
 * different regions to different ends — but a disc colour that satisfied one file's constraint and
 * quietly broke the other's is the kind of coupling a second private copy hides.
 */

/**
 * The disc colour both marker pixel tests render against. It has to satisfy two constraints at once,
 * which is exactly why it is one constant and not two:
 *
 *  - **Dark enough** that [MarkerRendering.legibleOn] inks the glyph white, which is what lets
 *    [VehicleMarkerPipTest] tell a black "full" pip apart from a washed "empty" one.
 *  - **Mid enough** that both a lighter and a darker rim land on the far side of it in *every* channel,
 *    which is what [VehicleMarkerOutlineTest] compares against.
 *
 * Retuning it for one of those without checking the other is the failure this shared constant prevents.
 */
internal const val SAMPLE_DISC = 0xFF1050C0.toInt()

/**
 * Contexts pinned to light and dark mode, for the marker colours that resolve out of qualified
 * resources (#2055).
 *
 * A test that used the bare target context would resolve whichever mode the emulator happens to be
 * running in, which is neither of the two things these tests mean to assert: the mode-comparing ones
 * would compare a mode against itself, and the mode-*independent* ones (the occupancy pips) would
 * silently start reading a white rim as pip ink on a dark device. Naming the mode makes both honest.
 */
internal fun lightContext(): Context = modeContext(Configuration.UI_MODE_NIGHT_NO)

internal fun darkContext(): Context = modeContext(Configuration.UI_MODE_NIGHT_YES)

private fun modeContext(nightMode: Int): Context {
    val base = InstrumentationRegistry.getInstrumentation().targetContext
    val configuration = Configuration(base.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }
    return base.createConfigurationContext(configuration)
}

/**
 * The grid-units-to-pixels scale this marker was rendered at, read back off the bitmap itself.
 *
 * Taken from the width rather than from `displayMetrics.density`, because a marker's width is the one
 * dimension that doesn't vary with the tab: tabbed and tabless bitmaps are both
 * `GRID + 2 * PAD_GRID` units across, and only the height carries the reserved tab depth. So one
 * expression locates the geometry in either shape, and neither test has to reach for a Context to do it.
 */
internal val Bitmap.markerScale: Float
    // PAD_GRID is @VisibleForTesting — this is that use.
    @Suppress("VisibleForTests")
    get() = width / (MarkerRendering.GRID + 2f * VehicleBitmaps.PAD_GRID)
