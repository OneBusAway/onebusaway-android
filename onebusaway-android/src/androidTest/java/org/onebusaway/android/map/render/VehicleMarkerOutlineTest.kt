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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.models.ObaRoute

/**
 * Which parts of the vehicle marker are rimmed, and in what color (#2055).
 *
 * **The body is**, and its rim follows the mode — black over the light base map, white over the dark one
 * — so the marker's edge survives a restyled map instead of dissolving into it. Both halves of that are
 * checked, because either alone can pass while the marker stays broken: the resolved color can flip
 * while the renderer keeps stamping black, and the rendered rim can differ between the two modes for
 * some reason other than the color the app asked for.
 *
 * **The glyph is not.** It sits on a disc already chosen to contrast with it, so the rim it used to carry
 * separated it from nothing and thickened it into a blot on a light route color.
 *
 * Rendering is `Canvas` work, so reading the pixels back means an instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class VehicleMarkerOutlineTest {

    /**
     * A disc dark enough for a black rim to be nearly lost against a night base map, and mid enough that
     * both a lighter and a darker rim are unambiguously on the far side of it in every channel — which is
     * what the pixel assertions below compare against.
     */
    private val disc = 0xFF1050C0.toInt()

    /** The resolved color: what the renderer is handed, before any drawing can misuse it. */
    @Test
    fun theRimColorFlipsWithTheMode() {
        assertEquals("a light-mode rim is black", Color.BLACK, VehicleBitmaps.outlineColor(lightContext()))
        assertEquals("a dark-mode rim is white", Color.WHITE, VehicleBitmaps.outlineColor(darkContext()))
    }

    /**
     * The rim as drawn. Read as the marker's **topmost non-transparent pixel** down its center column —
     * whatever its antialiased coverage, the first ink the map sees above the disc is the rim's outer
     * edge, and nothing else is up there on a tabless marker.
     *
     * Asserted structurally against [disc] (strictly darker / strictly lighter in every channel) rather
     * than as an exact black and white: that edge pixel may be partly blended with the fill beneath it,
     * and pinning the blend would restate production's arithmetic. Blending cannot cross the disc, so
     * the direction is the whole claim and needs no tolerance.
     */
    @Test
    fun theDrawnRimFlipsWithTheMode() {
        val light = marker(lightContext()).topmostInk()
        val dark = marker(darkContext()).topmostInk()

        assertTrue(
            "a light-mode rim must be darker than the disc in every channel (saw ${light.hex()})",
            light.darkerThanDiscEverywhere()
        )
        assertTrue(
            "a dark-mode rim must be lighter than the disc in every channel (saw ${dark.hex()})",
            dark.lighterThanDiscEverywhere()
        )
    }

    /**
     * The two modes are different bitmaps, which is why the mode has to reach the icon cache key — see
     * [VehicleBitmaps.iconKey], where the rim color is keyed on for exactly this reason. A key that
     * ignored it would serve a marker its previous mode's rim until the process died.
     */
    @Test
    fun theTwoModesAreDifferentBitmaps() {
        assertFalse(
            "the light- and dark-mode markers must not be the same bitmap",
            marker(lightContext()).sameAs(marker(darkContext()))
        )
    }

    /**
     * The glyph draws flat: nowhere inside the disc is there any of the black the dilate used to lay
     * around it. Read over a square inscribed in the disc — it holds the whole glyph and nothing else,
     * since the rim is outside it and the tab (absent here anyway) is below.
     *
     * On this deliberately dark [disc] the glyph is white, so every pixel in the square is white, the
     * disc, or a blend of the two — and no blend of those two is black. That the glyph is *there* is
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
    private fun marker(context: Context): Bitmap = VehicleBitmaps.previewBitmap(context, ObaRoute.TYPE_BUS, disc, occupancy = null)

    /**
     * Every pixel of a square inscribed in the disc, centered on it.
     *
     * [INSCRIBED_HALF_GRID] is stated here rather than derived from the marker's geometry: it has to be
     * wide enough to hold the glyph and narrow enough that its corners stay off the rim, and both of
     * those are claims *about* that geometry — a bound computed from it would follow a mistake in it
     * instead of catching one. Only the scale reads production, and only to locate the square.
     */
    private fun Bitmap.insideTheDisc(): List<Int> {
        // Density is a property of the device, not of the mode, so either context answers for it.
        val scale = lightContext().resources.displayMetrics.density * VehicleBitmaps.MARKER_SIZE_DP / MarkerRendering.GRID
        val half = INSCRIBED_HALF_GRID * scale
        val from = (width / 2f - half).toInt()
        val to = (width / 2f + half).toInt()
        assertTrue("the sampled square must be inside the bitmap", from >= 0 && to <= width && to > from)
        // The bitmap is square (a tabless marker), so one range indexes both axes.
        return (from until to).flatMap { y -> (from until to).map { x -> getPixel(x, y) } }
    }

    /** The first non-transparent pixel scanning down the bitmap's center column. */
    private fun Bitmap.topmostInk(): Int {
        val x = width / 2
        for (y in 0 until height) {
            val pixel = getPixel(x, y)
            // Above a threshold rather than > 0, so a single near-empty antialiasing sample — whose
            // un-premultiplied color is the least trustworthy of the edge — can't be what's read.
            if (Color.alpha(pixel) > MIN_INK_ALPHA) return pixel
        }
        throw AssertionError("the marker's center column carried no ink at all")
    }

    private fun Int.darkerThanDiscEverywhere(): Boolean = Color.red(this) < Color.red(disc) &&
        Color.green(this) < Color.green(disc) &&
        Color.blue(this) < Color.blue(disc)

    private fun Int.lighterThanDiscEverywhere(): Boolean = Color.red(this) > Color.red(disc) &&
        Color.green(this) > Color.green(disc) &&
        Color.blue(this) > Color.blue(disc)

    private fun Int.hex(): String = "#%08X".format(this)

    private companion object {
        const val MIN_INK_ALPHA = 32

        /**
         * Half the side of the square sampled inside the disc, in grid units. Comfortably wider than the
         * glyph's 5.4-unit half-extent, and its corners land 10.2 units from the center — inside the
         * 24-unit disc's 12-unit radius with the rim to spare.
         */
        const val INSCRIBED_HALF_GRID = 7.2f
    }
}
