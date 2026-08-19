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
package org.onebusaway.android.ui.home.directions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the map's "navigate here" bubble lands against the point that was pressed (#2243) — the whole
 * claim the bubble makes, since a bubble that doesn't name its point is just a floating button.
 */
class NavigateHereBubbleTest {

    // A 1080x1920 screen, a bubble about the size the pill measures, and the component's own tail and
    // margin in pixels at 3x density.
    private val screenWidth = 1080
    private val screenHeight = 1920
    private val bubbleWidth = 400
    private val bubbleHeight = 120
    private val tail = 42f
    private val margin = 36f

    private fun placeAt(x: Float, y: Float) = navigateHereBubblePlacement(
        anchorX = x,
        anchorY = y,
        bubbleWidth = bubbleWidth,
        bubbleHeight = bubbleHeight,
        containerWidth = screenWidth,
        containerHeight = screenHeight,
        tailSizePx = tail,
        marginPx = margin
    )

    /** The ordinary press, mid-map: centred over the point and sitting just above it. */
    @Test
    fun `a press in open space puts the bubble over the point`() {
        val placement = placeAt(x = 540f, y = 960f)

        assertTrue("the bubble should sit above the press", placement.above)
        assertEquals(540 - bubbleWidth / 2, placement.x)
        // The bubble's bottom edge stops half a tail short of the point, which is what the tail spans.
        assertEquals((960f - tail / 2f).toInt() - bubbleHeight, placement.y)
        assertEquals(540, placement.tailCenterX)
    }

    /** Too near the top to fit above: it flips below the point rather than covering it. */
    @Test
    fun `a press near the top of the map flips the bubble below it`() {
        val placement = placeAt(x = 540f, y = 40f)

        assertFalse("there is no room above a press this high", placement.above)
        assertEquals((40f + tail / 2f).toInt(), placement.y)
        assertEquals(540, placement.tailCenterX)
    }

    /** A press against an edge: the bubble is held inside the margin, and the tail stays on the point. */
    @Test
    fun `a press near the edge keeps the bubble on screen and the tail on the point`() {
        val placement = placeAt(x = 20f, y = 960f)

        assertEquals(margin.toInt(), placement.x)
        assertTrue(
            "the tail should stay within the bubble it hangs off",
            placement.tailCenterX >= placement.x && placement.tailCenterX <= placement.x + bubbleWidth
        )
        // Pinned by the margin rather than left over the point, so it is the tail that reaches across.
        assertTrue("the bubble is no longer centred on the press", placement.tailCenterX < 540)
    }

    /** The mirror image, so the clamp isn't one-sided. */
    @Test
    fun `a press against the far edge is held off it too`() {
        val placement = placeAt(x = screenWidth - 10f, y = 960f)

        assertEquals(screenWidth - margin.toInt() - bubbleWidth, placement.x)
        assertTrue(
            "the tail should stay within the bubble it hangs off",
            placement.tailCenterX <= placement.x + bubbleWidth
        )
    }

    /**
     * A press at the very bottom — over the arrivals drawer's peek. It still goes above the point (the
     * whole reason above is preferred), and the vertical clamp never pushes it back down over it.
     */
    @Test
    fun `a press at the bottom of the map still puts the bubble above it`() {
        val placement = placeAt(x = 540f, y = screenHeight - 5f)

        assertTrue(placement.above)
        assertTrue(
            "the bubble should stay clear of the bottom edge",
            placement.y + bubbleHeight <= screenHeight - margin
        )
    }
}
