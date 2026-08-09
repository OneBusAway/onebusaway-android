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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R

/**
 * The trip markers take the uncertainty band's colour as their disc fill (#1990), and everything drawn
 * on that disc — the ring and the glyph — flips to whichever of black/white reads on it.
 *
 * Instrumented rather than a JVM test because this is `Canvas` work: the unit-test `android.graphics`
 * stubs draw nothing, so the only way to see the disc is to read the pixels back. The polarity is
 * checked on both a light and a dark fill, since a marker that hardcoded either ink would pass on one
 * of them — which is exactly what the pre-#1990 fixed gray-on-white pair did.
 */
@RunWith(AndroidJUnit4::class)
class TripMarkerBitmapsTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val density: Float get() = context.resources.displayMetrics.density

    @Test
    fun aLightFillInksTheRingAndGlyphBlack() = assertDiscInk(0xFFFFE08A.toInt(), Color.BLACK)

    @Test
    fun aDarkFillInksTheRingAndGlyphWhite() = assertDiscInk(0xFF1050C0.toInt(), Color.WHITE)

    @Test
    fun aTranslucentFillIsDrawnOpaque() {
        // The disc must not blend with the map beneath it — overlapping, per-frame-moving markers would
        // shimmer. The caller's alpha is therefore dropped rather than honoured.
        val bitmap = TripMarkerBitmaps.circle(context, R.drawable.ic_fast_estimate, 0x401050C0)
        assertEquals(0xFF1050C0.toInt(), bitmap.fillSample())
    }

    @Test
    fun eachFillGetsItsOwnDisc() {
        // Two colours must not collide in the (drawable, fill) cache, and the second must not be served
        // the first's bitmap — the fill is what tells a rider which band a marker belongs to.
        val warm = TripMarkerBitmaps.circle(context, R.drawable.ic_signal_indicator, 0xFFFFE08A.toInt())
        val cool = TripMarkerBitmaps.circle(context, R.drawable.ic_signal_indicator, 0xFF1050C0.toInt())
        assertNotEquals(warm.fillSample(), cool.fillSample())
    }

    @Test
    fun theSameFillIsServedFromTheCache() {
        val first = TripMarkerBitmaps.circle(context, R.drawable.ic_fast_estimate, 0xFF1050C0.toInt())
        val second = TripMarkerBitmaps.circle(context, R.drawable.ic_fast_estimate, 0xFF1050C0.toInt())
        assertTrue("the same (drawable, fill) must reuse one bitmap", first === second)
    }

    /** Both glyphs, so neither drawable's own authored colour survives into the disc. */
    private fun assertDiscInk(fill: Int, expectedInk: Int) {
        for (glyph in listOf(R.drawable.ic_fast_estimate, R.drawable.ic_signal_indicator)) {
            val bitmap = TripMarkerBitmaps.circle(context, glyph, fill)

            assertEquals("disc fill", fill, bitmap.fillSample())
            // The ring is stroked centered on `radius - strokeWidth/2`, so its own half-width row down
            // the vertical centerline is solidly inside it.
            assertEquals("ring ink", expectedInk, bitmap.getPixel(bitmap.width / 2, (strokeWidthPx / 2f).toInt()))
            // Glyph ink, counted strictly inside the fill disc so no ring pixel can stand in for it: the
            // glyph is tinted, not left at whatever colour its vector authored.
            assertTrue("glyph ink", bitmap.countInkInsideDisc(expectedInk) > 0)
        }
    }

    private val strokeWidthPx: Float get() = TripMarkerBitmaps.STROKE_WIDTH_DP * density

    private val paddingPx: Float get() = TripMarkerBitmaps.ICON_PADDING_DP * density

    /**
     * A pixel that is inside the fill but outside the glyph's bounds: on the vertical centerline, between
     * the ring's inner edge (`strokeWidth` from the top) and the glyph box's top edge (`padding`).
     */
    private fun Bitmap.fillSample(): Int = getPixel(width / 2, ((strokeWidthPx + paddingPx) / 2f).toInt())

    /** Exact-[ink] pixels at least 2px inside the fill disc — everything there is glyph, never ring. */
    private fun Bitmap.countInkInsideDisc(ink: Int): Int {
        val center = width / 2f
        val innerRadius = center - strokeWidthPx - 2f
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x + 0.5f - center
                val dy = y + 0.5f - center
                if (dx * dx + dy * dy <= innerRadius * innerRadius && getPixel(x, y) == ink) count++
            }
        }
        return count
    }
}
