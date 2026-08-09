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

    /** A disc dark enough that [MarkerRendering.legibleOn] inks the *glyph* white. */
    private val disc = 0xFF1050C0.toInt()

    // The pips' polarity is fixed rather than derived from the disc: a full pip is black, an empty one a
    // white wash. Both are asserted, in opposite directions, so a regression that collapsed the two
    // into one colour (which is what following the disc's ink would do on this very disc) fails here
    // rather than silently drawing an unreadable row.
    //
    // A full pip is matched exactly. An empty one can't be — its colour is a blend of white and the tab,
    // and pinning the arithmetic here would just restate production's — so it's matched structurally:
    // strictly lighter than the tab in every channel. On this deliberately dark disc nothing else in the
    // band qualifies, since the only other things there are the tab itself and black ink, and the rule
    // needs no tolerance to state.
    private val full: (Int) -> Boolean = { it == Color.BLACK }

    private val empty: (Int) -> Boolean = {
        Color.red(it) > Color.red(disc) && Color.green(it) > Color.green(disc) && Color.blue(it) > Color.blue(disc)
    }

    /**
     * A vehicle reporting no fullness draws a plain disc: the pre-occupancy circle, in a bitmap that
     * reserves no tab depth at all.
     *
     * Asserted as the bitmap's *shape* rather than as an empty band, because with no tab there is no band
     * — a tabless marker is square, so the rows a tab would occupy don't exist to be counted. That is the
     * point: "no data" costs a rider no marker area and costs the icon cache no transparent pixels.
     */
    @Test
    fun aVehicleWithoutFullnessDrawsNoTab() {
        val tabless = marker(null)
        val tabbed = marker(OccupancyBucket.EMPTY)

        assertEquals("a tabless marker must be the plain square disc badge", tabless.width, tabless.height)
        assertTrue("a tabbed marker must be taller than it is wide", tabbed.height > tabbed.width)
        assertTrue("...and must actually put a tab there", tabbed.opaqueInTab() > 0)
    }

    /**
     * The tab is the same size at every fullness level — it's a reserved zone, not a bar that grows.
     * Measured as opaque (non-transparent) pixels, which counts the tab's own body regardless of how
     * much of it the pips ink.
     */
    @Test
    fun theTabIsTheSameSizeAtEveryLevel() {
        val areas = OccupancyBucket.entries.map { marker(it).opaqueInTab() }

        assertTrue("the tab must have area at all (saw $areas)", areas.first() > 0)
        for (area in areas) {
            assertEquals("the tab must not change size between fullness levels (saw $areas)", areas.first(), area)
        }
    }

    /**
     * Each additional filled pip puts more black in the tab and less white wash — the row reads as a
     * rising scale in one colour and a falling one in the other. Stated as strict orderings rather than
     * absolute counts because the exact pixel count depends on the person artwork's extent, which isn't
     * what's under test.
     *
     * Asserting *both* directions is the point: a single "more black" check would still pass if empty
     * pips stopped being drawn at all, which would turn the reserved row back into a bare count.
     */
    @Test
    fun eachFilledPipInksMoreOfTheTab() {
        // One render per rung — `marker()` bypasses the bitmap LRU, so building the ladder twice would
        // pay for eight full composites (bitmap alloc, two vector inflates, the union) for nothing.
        val ladder = OccupancyBucket.entries.map { marker(it) }
        val blacks = ladder.map { it.countOf(full) }
        val whites = ladder.map { it.countOf(empty) }

        for (rung in 1 until OccupancyBucket.entries.size) {
            assertTrue(
                "rung $rung must put more black in the tab than ${rung - 1} (saw $blacks)",
                blacks[rung] > blacks[rung - 1]
            )
            assertTrue(
                "rung $rung must leave less white than ${rung - 1} (saw $whites)",
                whites[rung] < whites[rung - 1]
            )
        }
        assertTrue("an all-empty row must still draw its washed pips (saw $whites)", whites.first() > 0)
        assertTrue("a full row must leave no washed pips (saw $whites)", whites.last() == 0)
    }

    /**
     * The pips fill **left to right**: at one filled pip the ink is in the tab's left third, at two it
     * has reached the middle third, and only at three does the right third carry any. Halves would pass
     * a mere "more ink" check while filling from the wrong end.
     */
    @Test
    fun pipsFillFromTheLeft() {
        // Only three rungs say anything here, so only three are rendered.
        val none = marker(OccupancyBucket.EMPTY)
        val one = marker(OccupancyBucket.MANY_SEATS)
        val all = marker(OccupancyBucket.FULL)

        assertTrue(
            "one full pip must blacken the tab's left third",
            one.countOfInThird(full, 0) > none.countOfInThird(full, 0)
        )
        assertEquals(
            "one full pip must leave the tab's right third as it was when empty",
            none.countOfInThird(full, 2),
            one.countOfInThird(full, 2)
        )
        assertTrue(
            "a full row must blacken the right third",
            all.countOfInThird(full, 2) > none.countOfInThird(full, 2)
        )
    }

    // previewBitmap is @VisibleForTesting — this is that test.
    @Suppress("VisibleForTests")
    private fun marker(occupancy: OccupancyBucket?): Bitmap = VehicleBitmaps.previewBitmap(context, ObaRoute.TYPE_BUS, disc, occupancy)

    private val scale: Float
        get() = context.resources.displayMetrics.density * VehicleBitmaps.MARKER_SIZE_DP / MarkerRendering.GRID

    /**
     * Counts [color]-colored pixels across the tab band. The bitmap carries a transparent border of
     * [VehicleBitmaps.PAD_GRID] *and* reserves [VehicleBitmaps.TAB_DEPTH_GRID] above the disc, so grid
     * row `g` sits at `(PAD_GRID + TAB_DEPTH_GRID + g) * scale` pixels down. The transform reads the
     * production constants — it only *locates* the band and is not what's under test; the band bounds
     * below are the independent expectation and stay local.
     */
    private fun Bitmap.countOf(match: (Int) -> Boolean): Int = countInTab(0, width, match)

    /** Any non-transparent pixel in the tab band — the tab's body, not just what the pips ink. */
    private fun Bitmap.opaqueInTab(): Int = countInTab(0, width) { Color.alpha(it) > 0 }

    /** Matching pixels in the pip row, restricted to one of three equal columns of the tab (0, 1 or 2). */
    private fun Bitmap.countOfInThird(match: (Int) -> Boolean, third: Int): Int {
        val tabLeft = (VehicleBitmaps.PAD_GRID + MarkerRendering.GRID / 2f - TAB_HALF_WIDTH_GRID) * scale
        val tabWidth = 2f * TAB_HALF_WIDTH_GRID * scale
        val from = (tabLeft + third * tabWidth / 3f).toInt()
        val to = (tabLeft + (third + 1) * tabWidth / 3f).toInt()
        return countInTab(from, to, match)
    }

    private fun Bitmap.countInTab(fromX: Int, toX: Int, match: (Int) -> Boolean): Int {
        // Grid row 0 sits below the transparent border and, on a tabbed marker, below the mirrored tab
        // depth reserved above the disc. A tabless marker reserves none, so its origin is just the border
        // — which is why this reads the bitmap's own height rather than assuming the taller geometry.
        val reserved = (height - MarkerRendering.GRID * scale - 2f * VehicleBitmaps.PAD_GRID * scale) / 2f
        val originPx = VehicleBitmaps.PAD_GRID * scale + reserved
        val top = (originPx + TAB_BAND_TOP_GRID * scale).toInt().coerceIn(0, height)
        val bottom = (originPx + TAB_BAND_BOTTOM_GRID * scale).toInt().coerceIn(top, height)
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
        // The tab hangs below the disc from grid y 24 to 31.6, holding a pip row centered at 26.1.
        // Sampled just inside so a half-pixel of rounding at the edges can't decide the count — and
        // starting below 24 keeps the disc itself, which is opaque everywhere, out of the area counts.
        const val TAB_BAND_TOP_GRID = 24.3f
        const val TAB_BAND_BOTTOM_GRID = 31.3f

        /** The tab's half-width, for locating its thirds. Independent of the production constant. */
        const val TAB_HALF_WIDTH_GRID = 10.7f
    }
}
