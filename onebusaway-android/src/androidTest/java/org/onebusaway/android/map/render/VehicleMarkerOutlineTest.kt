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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute

/**
 * Which parts of the vehicle marker are rimmed, in what colour, and how wide (#2055).
 *
 * **The body is**, in whichever of black/white reads against its disc — the rule the trip map's estimate
 * markers apply to their own fill (#1990), so the markers a rider sees together are drawn one way. That
 * is what makes the rim light on a dark marker, which is what #2055 asked for; keying it to the *theme*
 * instead only moved the failure onto pale route colours. Its *width* is checked against those same
 * estimate markers.
 *
 * **The glyph is not.** It sits on a disc already chosen to contrast with it, so the rim it used to carry
 * separated it from nothing and thickened it into a blot on a light route color.
 *
 * Rendering is `Canvas` work, so reading the pixels back means an instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class VehicleMarkerOutlineTest {

    /**
     * The rim as drawn takes the disc's own contrasting ink: white on a dark disc, black on a pale one.
     * Both directions, since a rim hardcoded to either would satisfy one of them.
     *
     * Matched **exactly** against [MarkerRendering.legibleOn], which is available because the rim is
     * [MarkerRendering.MARKER_STROKE_DP] wide — several pixels at any real density — so it has a solid
     * interior no antialiasing touches. (While it was a hairline this could only be a structural
     * "darker/lighter than the disc" comparison.)
     */
    @Test
    fun theRimTakesTheDiscsContrastingInk() {
        for (disc in listOf(SAMPLE_DISC, PALE_DISC)) {
            assertEquals(
                "the rim on disc ${disc.hex()} must be legibleOn it",
                MarkerRendering.legibleOn(disc).hex(),
                marker(disc = disc).solidRimPixel().hex()
            )
        }
        // ...and those two discs must actually disagree, or the loop above proves nothing.
        assertNotEquals(
            "the sample discs must call for opposite ink",
            MarkerRendering.legibleOn(SAMPLE_DISC),
            MarkerRendering.legibleOn(PALE_DISC)
        )
    }

    /**
     * The rim reads the disc, not the theme — so the same vehicle is the same marker in light and dark
     * mode. This is what lets the icon cache key stay free of the mode: keying on the disc colour already
     * determines the rim.
     */
    @Test
    fun theRimDoesNotFollowTheTheme() {
        assertTrue(
            "a mode switch must not change the marker",
            marker(lightContext()).sameAs(marker(darkContext()))
        )
    }

    /**
     * The rim is the same width as the one on the fast-estimate / last-fix markers that accompany a
     * vehicle on the trip map — the thing a rider actually compares when both are on screen.
     *
     * Measured off **both bitmaps** and compared, rather than checked against
     * [MarkerRendering.MARKER_STROKE_DP]: the two convert that dp through different geometry (a 40 dp
     * marker over a 24-unit grid here, 28 dp of raw pixels there), and it was precisely that conversion
     * that had them 5x apart while both "used 2 dp". Comparing the artifacts tests the conversion;
     * comparing each to the constant would not.
     *
     * Each is measured in its own rim colour — the same [MarkerRendering.legibleOn] rule, applied to the
     * disc colour each family fills with.
     *
     * One pixel of tolerance, for the rounding each geometry does independently on the way to integers.
     */
    @Test
    fun theRimMatchesTheTripMarkersWidth() {
        val context = lightContext()
        val vehicle = marker(context).rimThickness(MarkerRendering.legibleOn(SAMPLE_DISC))
        val estimate = TripMarkerBitmaps.circle(context, R.drawable.ic_fast_estimate)
            .rimThickness(MarkerRendering.legibleOn(TripMarkerBitmaps.DEFAULT_FILL_COLOR))

        assertTrue("the estimate marker must have a measurable rim (saw $estimate px)", estimate > 0)
        assertTrue(
            "vehicle rim ${vehicle}px must match the trip markers' ${estimate}px",
            kotlin.math.abs(vehicle - estimate) <= 1
        )
    }

    /**
     * The glyph draws flat: nowhere inside the disc is there any of the black the dilate used to lay
     * around it. Read over a square inscribed in the disc — it holds the whole glyph and nothing else,
     * since the rim is outside it and the tab (absent here anyway) is below.
     *
     * On the deliberately dark [SAMPLE_DISC] the glyph is white, so every pixel in the square is white,
     * the disc, or a blend of the two — and no blend of those two is black. That the glyph is *there* is
     * asserted alongside, since "no black in the disc" is also what an empty disc looks like.
     */
    @Test
    fun theGlyphCarriesNoRim() {
        val square = marker(lightContext()).insideTheDisc()

        assertFalse(
            "the glyph must lay no black inside the disc",
            square.any { it == Color.BLACK }
        )
        assertTrue(
            "...but must still be drawn there",
            square.any { it.lighterThanDiscEverywhere() }
        )
    }

    // previewBitmap is @VisibleForTesting — this is that test.
    @Suppress("VisibleForTests")
    private fun marker(context: Context = lightContext(), disc: Int = SAMPLE_DISC): Bitmap = VehicleBitmaps.previewBitmap(context, ObaRoute.TYPE_BUS, disc, occupancy = null)

    /**
     * Every pixel of a square inscribed in the disc, centered on it.
     *
     * [INSCRIBED_HALF_GRID] is stated here rather than derived from the marker's geometry: it has to be
     * wide enough to hold the glyph's edges and narrow enough that its corners stay off the rim, and both
     * of those are claims *about* that geometry — a bound computed from it would follow a mistake in it
     * instead of catching one. Only the scale reads production, and only to locate the square.
     */
    private fun Bitmap.insideTheDisc(): IntArray {
        val half = INSCRIBED_HALF_GRID * markerScale
        val from = (width / 2f - half).toInt()
        val to = (width / 2f + half).toInt()
        assertTrue("the sampled square must be inside the bitmap", from >= 0 && to <= width && to > from)
        // The bitmap is square (a tabless marker), so one range bounds both axes.
        val side = to - from
        return IntArray(side * side).also { getPixels(it, 0, side, from, from, side, side) }
    }

    /**
     * The first fully-opaque pixel scanning down the bitmap's center column — a point inside the rim's
     * solid interior, past the antialiased outer edge and well above the fill.
     */
    private fun Bitmap.solidRimPixel(): Int {
        val x = width / 2
        for (y in 0 until height) {
            val pixel = getPixel(x, y)
            if (Color.alpha(pixel) == OPAQUE) return pixel
        }
        throw AssertionError("the marker's center column carried no solid ink at all")
    }

    /**
     * How many pixels deep the rim runs at the top of the center column: the contiguous run of pixels
     * exactly equal to [rim], starting where that run begins.
     *
     * Exact matches only, so the count is of the rim's *solid* body and both markers give up their
     * antialiased edges the same way — which is what makes the two counts comparable. Reading down the
     * center column measures the rim across, since both silhouettes are locally flat at their top.
     */
    private fun Bitmap.rimThickness(rim: Int): Int {
        val x = width / 2
        var count = 0
        for (y in 0 until height) {
            if (getPixel(x, y) == rim) {
                count++
            } else if (count > 0) {
                break
            }
        }
        return count
    }

    private fun Int.lighterThanDiscEverywhere(): Boolean = Color.red(this) > Color.red(SAMPLE_DISC) &&
        Color.green(this) > Color.green(SAMPLE_DISC) &&
        Color.blue(this) > Color.blue(SAMPLE_DISC)

    private fun Int.hex(): String = "#%08X".format(this)

    private companion object {
        const val OPAQUE = 255

        /** A pale disc, the counterpart to [SAMPLE_DISC]: it calls for the opposite ink. */
        val PALE_DISC = 0xFFFFEB3B.toInt()

        /**
         * Half the side of the square sampled inside the disc, in grid units — chosen to sit between two
         * bounds rather than to frame the glyph exactly.
         *
         * Its corners land 10.2 units from the center, clear of the rim's inner edge at 10.55, so nothing
         * the *body* draws is in the sample. And it is wide enough to hold the glyph's edges, which is
         * where a dilate would put its black — a square that held only the glyph's interior could pass
         * while the halo sat just outside it.
         */
        const val INSCRIBED_HALF_GRID = 7.2f
    }
}
