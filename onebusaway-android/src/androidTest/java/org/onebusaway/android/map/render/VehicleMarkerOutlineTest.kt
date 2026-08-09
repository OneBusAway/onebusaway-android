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
 * The vehicle marker's rim follows the mode (#2055): black over the light base map, white over the dark
 * one, so the marker's edge survives a restyled map instead of dissolving into it.
 *
 * Both halves are checked, because either alone can pass while the marker stays broken — the resolved
 * color can flip while the renderer keeps stamping black, and the rendered rim can differ between the
 * two modes for some reason other than the color the app asked for. Rendering is `Canvas` work, so
 * reading the pixels back means an instrumented test.
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

    // previewBitmap is @VisibleForTesting — this is that test.
    @Suppress("VisibleForTests")
    private fun marker(context: Context): Bitmap = VehicleBitmaps.previewBitmap(context, ObaRoute.TYPE_BUS, disc, occupancy = null)

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
    }
}
