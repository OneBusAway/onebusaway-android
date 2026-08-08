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
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.models.ObaRoute

/**
 * The occupancy tab actually reaches the pixels (#2194). [VehicleIconAllocationTest] pins the *key*
 * contract — that a change in fullness mints a different icon — which a marker that keyed on fullness
 * but drew nothing would satisfy just as well. This checks the drawing itself: that the tab exists only
 * when there is fullness to report, that it holds a full row of pips at every level, and that the pips
 * fill left to right.
 *
 * Instrumented rather than a JVM test because this is `Canvas` work — the unit-test `android.graphics`
 * stubs draw nothing — so the only way to see the tab is to read the pixels back.
 */
@RunWith(AndroidJUnit4::class)
class VehicleMarkerPipTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** A disc dark enough that [MarkerRendering.legibleOn] inks the glyph and pips white. */
    private val disc = 0xFF1050C0.toInt()

    private val ink = Color.WHITE

    /**
     * A vehicle reporting no fullness draws a plain disc: nothing at all below the disc's rim. The
     * tabless marker is the pre-occupancy circle, so "no data" costs a rider no marker area.
     */
    @Test
    fun aVehicleWithoutFullnessDrawsNoTab() {
        assertEquals(
            "a marker with no fullness must leave the tab band empty",
            0,
            marker(fill = null).opaqueIn(TAB_BAND_TOP_GRID, TAB_BAND_BOTTOM_GRID)
        )
        assertTrue(
            "...while a marker that reports fullness fills it",
            marker(fill = 0).opaqueIn(TAB_BAND_TOP_GRID, TAB_BAND_BOTTOM_GRID) > 0
        )
    }

    /**
     * The tab is the same size at every fullness level — it's a reserved zone, not a bar that grows.
     * Measured as opaque (non-transparent) pixels, which counts the tab's own body regardless of how
     * much of it the pips ink.
     */
    @Test
    fun theTabIsTheSameSizeAtEveryLevel() {
        val areas = (0..VehicleBitmaps.MAX_PIPS).map { marker(fill = it).opaqueIn(TAB_BAND_TOP_GRID, TAB_BAND_BOTTOM_GRID) }

        assertTrue("the tab must have area at all (saw $areas)", areas[0] > 0)
        for (fill in 1..VehicleBitmaps.MAX_PIPS) {
            assertEquals("the tab must not change size between fullness levels (saw $areas)", areas[0], areas[fill])
        }
    }

    /**
     * Each additional filled pip inks more of the tab, and the empty marker inks least — so the row
     * reads as a rising scale. Stated as a strict ordering rather than absolute counts because the exact
     * pixel count depends on the person artwork's extent, which isn't what's under test.
     *
     * The `0` end matters most: three hollow pips must put *some* ink in the tab (their outlines) yet
     * strictly less than one filled pip, which is the whole difference between "empty" and "1 of 3".
     */
    @Test
    fun eachFilledPipInksMoreOfTheTab() {
        val counts = (0..VehicleBitmaps.MAX_PIPS).map { marker(fill = it).inkIn(TAB_BAND_TOP_GRID, TAB_BAND_BOTTOM_GRID) }

        for (fill in 1..VehicleBitmaps.MAX_PIPS) {
            assertTrue(
                "$fill filled pips must ink more of the tab than ${fill - 1} (saw $counts)",
                counts[fill] > counts[fill - 1]
            )
        }
    }

    /**
     * The pips fill **left to right**: at one filled pip the ink is in the tab's left third, at two it
     * has reached the middle third, and only at three does the right third carry any. Halves would pass
     * a mere "more ink" check while filling from the wrong end.
     */
    @Test
    fun pipsFillFromTheLeft() {
        val left = (0..VehicleBitmaps.MAX_PIPS).map { marker(fill = it).inkInThird(0) }
        val right = (0..VehicleBitmaps.MAX_PIPS).map { marker(fill = it).inkInThird(2) }

        assertTrue("one filled pip must ink the tab's left third (saw $left)", left[1] > left[0])
        assertEquals(
            "one filled pip must leave the tab's right third as it was when empty (saw $right)",
            right[0],
            right[1]
        )
        assertTrue("three filled pips must ink the right third (saw $right)", right[3] > right[0])
    }

    // previewBitmap is @VisibleForTesting — this is that test.
    @Suppress("VisibleForTests")
    private fun marker(fill: Int?): Bitmap = VehicleBitmaps.previewBitmap(context, ObaRoute.TYPE_BUS, disc, fill)

    private val scale: Float
        get() = context.resources.displayMetrics.density * VehicleBitmaps.MARKER_SIZE_DP / MarkerRendering.GRID

    /**
     * Counts [ink]-colored pixels between two grid-unit rows. The bitmap carries a transparent border of
     * [VehicleBitmaps.PAD_GRID] *and* reserves [VehicleBitmaps.TAB_DEPTH_GRID] above the disc, so grid
     * row `g` sits at `(PAD_GRID + TAB_DEPTH_GRID + g) * scale` pixels down. The transform reads the
     * production constants — it only *locates* the band and is not what's under test; the band bounds
     * below are the independent expectation and stay local.
     */
    private fun Bitmap.inkIn(topGrid: Float, bottomGrid: Float): Int = countIn(topGrid, bottomGrid, 0, width) { it == ink }

    /** As [inkIn], but any non-transparent pixel — the tab's body, not just what the pips ink. */
    private fun Bitmap.opaqueIn(topGrid: Float, bottomGrid: Float): Int = countIn(topGrid, bottomGrid, 0, width) { Color.alpha(it) > 0 }

    /** [ink] pixels in the pip row, restricted to one of three equal columns of the tab (0, 1 or 2). */
    private fun Bitmap.inkInThird(third: Int): Int {
        val tabLeft = (VehicleBitmaps.PAD_GRID + MarkerRendering.GRID / 2f - TAB_HALF_WIDTH_GRID) * scale
        val tabWidth = 2f * TAB_HALF_WIDTH_GRID * scale
        val from = (tabLeft + third * tabWidth / 3f).toInt()
        val to = (tabLeft + (third + 1) * tabWidth / 3f).toInt()
        return countIn(TAB_BAND_TOP_GRID, TAB_BAND_BOTTOM_GRID, from, to) { it == ink }
    }

    private fun Bitmap.countIn(topGrid: Float, bottomGrid: Float, fromX: Int, toX: Int, match: (Int) -> Boolean): Int {
        val originGrid = VehicleBitmaps.PAD_GRID + VehicleBitmaps.TAB_DEPTH_GRID
        val top = ((originGrid + topGrid) * scale).toInt().coerceIn(0, height)
        val bottom = ((originGrid + bottomGrid) * scale).toInt().coerceIn(top, height)
        val left = fromX.coerceIn(0, width)
        val right = toX.coerceIn(left, width)
        assertTrue("the sampled band must be at least a pixel tall", bottom > top)
        assertTrue("the sampled band must be at least a pixel wide", right > left)

        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (match(getPixel(x, y))) count++
            }
        }
        return count
    }

    private companion object {
        // The band under test, stated independently of VehicleBitmaps' geometry: a test that read the
        // constants it verifies would follow a geometry mistake instead of catching it.
        //
        // The tab hangs below the disc from grid y 24 to 30.6, holding a pip row centered at 25.8.
        // Sampled just inside so a half-pixel of rounding at the edges can't decide the count — and
        // starting below 24 keeps the disc itself, which is opaque everywhere, out of the area counts.
        const val TAB_BAND_TOP_GRID = 24.3f
        const val TAB_BAND_BOTTOM_GRID = 30.3f

        /** The tab's half-width, for locating its thirds. Independent of the production constant. */
        const val TAB_HALF_WIDTH_GRID = 9.3f
    }
}
