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
 * The occupancy pips actually reach the pixels (#2194). [VehicleIconAllocationTest] pins the *key*
 * contract — that a change in occupancy mints a different icon — which a marker that keyed on occupancy
 * but drew nothing would satisfy just as well. This checks the drawing itself, and that the pips didn't
 * take the heading arrow's space to get it.
 *
 * Instrumented rather than a JVM test because this is `Canvas` work — the unit-test `android.graphics`
 * stubs draw nothing — so the only way to see the pips is to read the pixels back.
 */
@RunWith(AndroidJUnit4::class)
class VehicleMarkerPipTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** A disc dark enough that [MarkerRendering.legibleOn] inks the glyph, pips, and arrow white. */
    private val disc = 0xFF1050C0.toInt()

    private val ink = Color.WHITE

    /** Due south — the octant that swings the heading chevron closest to the pip row. */
    private val south = 4

    @Test
    fun eachPipCountIsItsOwnMarker() {
        val markers = (0..3).map { marker(pips = it) }

        for (a in 0..3) {
            for (b in a + 1..3) {
                assertTrue(
                    "a $a-pip and a $b-pip marker must not render identically",
                    !markers[a].sameAs(markers[b])
                )
            }
        }
    }

    /**
     * The pips land in the band reserved for them, and each one adds ink to it.
     *
     * Asserted as a strictly increasing count rather than "zero ink when pipless": the mode glyph sits
     * just above the band and its outline halo may grave a pixel or two into the top of it, which would
     * make an absolute threshold a guess about the bus artwork's exact extent. Monotonicity holds
     * whatever that constant contribution is, and it's what the reading depends on — a rider tells a full
     * bus from an empty one by there being *more* silhouettes.
     */
    @Test
    fun eachPipAddsInkToThePipRow() {
        val counts = (0..3).map { marker(pips = it).inkIn(PIP_BAND_TOP_GRID, PIP_BAND_BOTTOM_GRID) }

        for (pips in 1..3) {
            assertTrue(
                "$pips pips must put more ink in the pip row than ${pips - 1} (saw $counts)",
                counts[pips] > counts[pips - 1]
            )
        }
    }

    /**
     * The pip row doesn't cost the marker its heading arrow. Due south is the worst case — the rotated
     * chevron's nearest approach to the row — so if the arrow survives here it survives in every octant.
     * Compared against the pipless marker's own arrow, so this pins "unchanged", not merely "present".
     */
    @Test
    fun theSouthHeadingArrowSurvivesAFullMarker() {
        val bare = marker(pips = 0, halfWind = south).inkIn(ARROW_BAND_TOP_GRID, ARROW_BAND_BOTTOM_GRID)
        val full = marker(pips = 3, halfWind = south).inkIn(ARROW_BAND_TOP_GRID, ARROW_BAND_BOTTOM_GRID)

        assertTrue("the south-heading arrow must draw at all", bare > 0)
        assertEquals("three pips must not encroach on the heading arrow", bare, full)
    }

    // previewBitmap is @VisibleForTesting — this is that test.
    @Suppress("VisibleForTests")
    private fun marker(pips: Int, halfWind: Int = 0): Bitmap = VehicleBitmaps.previewBitmap(context, ObaRoute.TYPE_BUS, halfWind, disc, pips)

    /**
     * Counts [ink]-colored pixels between two grid-unit rows. The bitmap carries a [PAD_GRID]-wide
     * transparent border, so grid row `g` sits at `(PAD_GRID + g) * scale` pixels down.
     */
    private fun Bitmap.inkIn(topGrid: Float, bottomGrid: Float): Int {
        val scale = context.resources.displayMetrics.density * MARKER_SIZE_DP / MarkerRendering.GRID
        val top = ((PAD_GRID + topGrid) * scale).toInt().coerceIn(0, height)
        val bottom = ((PAD_GRID + bottomGrid) * scale).toInt().coerceIn(top, height)
        assertTrue("the sampled band must be at least a pixel tall", bottom > top)

        var count = 0
        for (y in top until bottom) {
            for (x in 0 until width) {
                if (getPixel(x, y) == ink) count++
            }
        }
        return count
    }

    private companion object {
        // Mirrors of VehicleBitmaps' private geometry. Duplicated rather than opened up: a test that
        // reads the constants it verifies would follow a geometry mistake instead of catching it.
        const val MARKER_SIZE_DP = 40f
        const val PAD_GRID = 0.6f

        // The pip row spans grid y 15.7..18.7; sampled just inside so a half-pixel of rounding at the
        // edges can't decide the count.
        const val PIP_BAND_TOP_GRID = 16f
        const val PIP_BAND_BOTTOM_GRID = 18.4f

        // Due south puts the chevron at grid y 20.66..23. Sampled inside that, below the pip row.
        const val ARROW_BAND_TOP_GRID = 21f
        const val ARROW_BAND_BOTTOM_GRID = 22.5f
    }
}
