/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.dataview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphViewportTest {

    // graphW = 200-50-10 = 140, graphH = 110-10-30 = 70
    private fun viewport() = GraphViewport(marginLeft = 50f, marginTop = 10f, marginRight = 10f, marginBottom = 30f)

    private fun GraphViewport.fitted(): GraphViewport {
        setDataBounds(minDist = 0.0, maxDist = 1000.0, minTime = 0L, maxTime = 100_000L)
        setupVisibleWindow(200f, 110f)
        return this
    }

    @Test
    fun `distance maps left-to-right across the graph area`() {
        val v = viewport().fitted()
        assertEquals(v.marginLeft, v.toPixelX(0.0), 1e-3f) // min at the left edge
        assertEquals(v.graphRight, v.toPixelX(1000.0), 1e-3f) // max at the right edge
        assertEquals(120f, v.toPixelX(500.0), 1e-3f) // midpoint
    }

    @Test
    fun `time maps bottom-to-top (newer is higher)`() {
        val v = viewport().fitted()
        assertEquals(v.graphBottom, v.toPixelY(0L), 1e-3f) // oldest at the bottom
        assertEquals(v.marginTop, v.toPixelY(100_000L), 1e-3f) // newest at the top
        assertEquals(45f, v.toPixelY(50_000L), 1e-3f)
    }

    @Test
    fun `pinch zoom keeps the focal data point under the finger`() {
        val v = viewport().fitted()
        val focusX = 120f // data distance 500 sits here at scale 1
        v.applyScale(factor = 2f, focusX = focusX, focusY = 45f, viewWidth = 200f, viewHeight = 110f)
        v.setupVisibleWindow(200f, 110f)

        assertTrue(v.isZoomed)
        assertEquals(500.0, v.visMinDist + v.visDistRange / 2, 1.0) // 500 still centered under the finger
        assertEquals(focusX, v.toPixelX(500.0), 0.5f)
        assertEquals(500.0, v.visDistRange, 1e-6) // 2x zoom halves the visible range
    }

    @Test
    fun `at scale 1 panning is clamped to the data bounds`() {
        val v = viewport().fitted()
        v.applyPan(distanceX = 14f, distanceY = 0f, viewWidth = 200f, viewHeight = 110f)
        v.setupVisibleWindow(200f, 110f)
        assertEquals("the whole range is visible, so there is nothing to pan to", 0.0, v.visMinDist, 1e-9)
    }

    @Test
    fun `panning after a zoom shifts the visible window`() {
        val v = viewport().fitted()
        v.applyScale(2f, 120f, 45f, 200f, 110f) // visDistRange -> 500, room to pan
        v.applyPan(distanceX = -14f, distanceY = 0f, viewWidth = 200f, viewHeight = 110f) // drag right -> window left
        v.setupVisibleWindow(200f, 110f)
        assertTrue("window shifted toward the start", v.visMinDist < 250.0)
        assertTrue(v.visMinDist >= 0.0) // still clamped within bounds
    }

    @Test
    fun `resetZoom returns to the full window`() {
        val v = viewport().fitted()
        v.applyScale(4f, 120f, 45f, 200f, 110f)
        v.resetZoom()
        v.setupVisibleWindow(200f, 110f)
        assertFalse(v.isZoomed)
        assertEquals(1000.0, v.visDistRange, 1e-9)
        assertEquals(0.0, v.visMinDist, 1e-9)
    }

    @Test
    fun `niceStep rounds to 1, 2, 5, or 10 times the magnitude`() {
        assertEquals(1000.0, GraphViewport.niceStep(1000.0), 1e-9)
        assertEquals(2000.0, GraphViewport.niceStep(2000.0), 1e-9)
        assertEquals(5000.0, GraphViewport.niceStep(6000.0), 1e-9)
        assertEquals(10000.0, GraphViewport.niceStep(9000.0), 1e-9)
        assertEquals(100.0, GraphViewport.niceStep(150.0), 1e-9)
        assertEquals(1.0, GraphViewport.niceStep(0.0), 1e-9) // degenerate guard
    }

    @Test
    fun `distance ticks fall on nice rounded values within the window`() {
        val v = viewport().fitted()
        val ticks = buildList { v.forEachDistTick { _, dist -> add(dist) } }
        assertTrue(ticks.isNotEmpty())
        // step = niceStep(1000/5 = 200) = 200; ticks start at ceil(0/200)*200 = 0, up to < 1000.
        assertEquals(listOf(0.0, 200.0, 400.0, 600.0, 800.0), ticks)
    }
}
