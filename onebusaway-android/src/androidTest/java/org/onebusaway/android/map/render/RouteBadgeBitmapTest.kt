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

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stacked route-label bitmap (#2083): a label naming an interchangeable ride draws one row per route,
 * each filled with that route's own line colour. Instrumented rather than a JVM test because this is
 * `Canvas` drawing — the unit-test `android.graphics` stubs draw nothing — so the rows are checked the only
 * way they can be: by reading the pixels back.
 */
@RunWith(AndroidJUnit4::class)
class RouteBadgeBitmapTest {

    private val blue = 0xFF1050C0.toInt()

    private val green = 0xFF107030.toInt()

    @Test
    fun oneRoutePerRow_stackedInTheOrderGiven_eachInItsOwnColor() {
        val single = badge(listOf(BadgedRoute("1 Line", blue)))
        val stacked = badge(listOf(BadgedRoute("1 Line", blue), BadgedRoute("2 Line", green)))

        // One row per route, all of one height: the stack is exactly as tall as its rows.
        assertEquals(single.height * 2, stacked.height)
        // Each row is filled with its own route's colour, in the order the label reads them. Sampled inside
        // the leading padding — clear of the centred name and of the casing along the edge.
        assertEquals(blue, stacked.rowFill(row = 0, rows = 2))
        assertEquals(green, stacked.rowFill(row = 1, rows = 2))
        // A single-route label is unchanged by all this: still one row in that route's colour.
        assertEquals(blue, single.rowFill(row = 0, rows = 1))
    }

    @Test
    fun everyRowIsAsWideAsTheWidestName() {
        val stacked = badge(listOf(BadgedRoute("8", blue), BadgedRoute("Seattle - Bremerton", green)))
        val long = badge(listOf(BadgedRoute("Seattle - Bremerton", green)))

        // Sized to the widest name, and the short row is filled across the whole of that width rather than
        // ending where its own name does.
        assertEquals(long.width, stacked.width)
        assertTrue(stacked.width > badge(listOf(BadgedRoute("8", blue))).width)
        assertEquals(blue, stacked.getPixel(stacked.width - SAMPLE_INSET_PX, rowCenterY(stacked, row = 0, rows = 2)))
    }

    @Test
    fun aScaledLabelIsTheSameDrawingAtADifferentSize() {
        // The directions map's labels recede with the zoom (#2102). What has to hold is that the scale is
        // *uniform* — type, padding and corner all follow it — so a shrunk label is the same pill, not a
        // squashed one or a full-size name in a smaller box.
        val full = badge(listOf(BadgedRoute("1 Line", blue)))
        val half = badge(listOf(BadgedRoute("1 Line", blue)), scale = 0.5f)

        // A proportionality check, not a pixel-exact one: the row height is rounded up to a whole pixel, the
        // bitmap is truncated to one, and a font's glyph advances don't have to divide exactly in half.
        assertEquals(full.width / 2f, half.width.toFloat(), 2f)
        assertEquals(full.height / 2f, half.height.toFloat(), 2f)
        // Still the route's own colour inside its (now smaller) padding, so the pill didn't lose its fill or
        // its casing to the rounding.
        assertEquals(blue, half.rowFill(row = 0, rows = 1))
    }

    @Test
    fun aStackedLabelScalesRowByRow() {
        val stacked = badge(listOf(BadgedRoute("1 Line", blue), BadgedRoute("2 Line", green)), scale = 0.5f)
        val single = badge(listOf(BadgedRoute("1 Line", blue)), scale = 0.5f)

        assertEquals(single.height * 2, stacked.height)
        assertEquals(blue, stacked.rowFill(row = 0, rows = 2))
        assertEquals(green, stacked.rowFill(row = 1, rows = 2))
    }

    private fun badge(routes: List<BadgedRoute>, scale: Float = 1f): Bitmap = ContinuationBadgeBitmaps.badge(routes, density = 1f, darkMode = false, scale = scale)

    /** The fill colour of one row, read from inside its leading padding. */
    private fun Bitmap.rowFill(row: Int, rows: Int): Int = getPixel(SAMPLE_INSET_PX, rowCenterY(this, row, rows))

    private fun rowCenterY(bitmap: Bitmap, row: Int, rows: Int): Int {
        val rowHeight = bitmap.height / rows
        return rowHeight * row + rowHeight / 2
    }

    private companion object {
        /**
         * How far in from the badge's edge a fill is sampled: past the casing stroke (2dp at the density
         * above) and — at a row's vertical centre — past the corner rounding, but well short of the name,
         * which is centred inside a wider horizontal padding.
         */
        const val SAMPLE_INSET_PX = 8
    }
}
