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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the stop dot/full zoom banding the renderers use to decide a stop marker's
 * icon — the band threshold and the focus/band → icon-kind mapping, shared identically by both map
 * flavors. The actual bitmap build + marker reconcile is exercised on device.
 */
class StopZoomBandTest {

    @Test
    fun `adjacent route stops stack above favorites and ordinary stops`() {
        assertEquals(0.75f, stopZIndex(routeStop = true, favorite = false), 0f)
        assertEquals(0.75f, stopZIndex(routeStop = true, favorite = true), 0f)
        assertEquals(0.5f, stopZIndex(routeStop = false, favorite = true), 0f)
        assertEquals(0f, stopZIndex(routeStop = false, favorite = false), 0f)
    }

    @Test
    fun `below the threshold is the dot band, at or above is the full band`() {
        assertEquals(StopBand.DOT, stopZoomBand(STOP_DOT_ZOOM_THRESHOLD - 0.01f))
        assertEquals(StopBand.DOT, stopZoomBand(0f))
        assertEquals(StopBand.FULL, stopZoomBand(STOP_DOT_ZOOM_THRESHOLD))
        assertEquals(StopBand.FULL, stopZoomBand(STOP_DOT_ZOOM_THRESHOLD + 0.01f))
        assertEquals(StopBand.FULL, stopZoomBand(21f))
    }

    @Test
    fun `the band flips at zoom 15 (pins the threshold constant, not just the boundary)`() {
        // Literal anchors (not STOP_DOT_ZOOM_THRESHOLD) so retuning the constant is a deliberate change
        // that has to update this test — the ± boundary cases above would otherwise silently follow it.
        assertEquals(15f, STOP_DOT_ZOOM_THRESHOLD, 0f)
        assertEquals(StopBand.DOT, stopZoomBand(14.99f))
        assertEquals(StopBand.FULL, stopZoomBand(15f))
    }

    @Test
    fun `stop-focus route circles use their own ramp from 30 percent at zoom 11 to full at 16`() {
        assertEquals(0.3f, focusedRouteStopScale(10f), 0f)
        assertEquals(0.3f, focusedRouteStopScale(11f), 0f)
        assertEquals(0.65f, focusedRouteStopScale(13.5f), 0.0001f)
        assertEquals(1f, focusedRouteStopScale(16f), 0f)
        assertEquals(1f, focusedRouteStopScale(18f), 0f)
    }

    @Test
    fun `at the dot band the focused stop gets the accent dot, others the plain dot`() {
        assertEquals(StopIconKind.DOT, stopIconKind(focused = false, band = StopBand.DOT))
        assertEquals(StopIconKind.DOT_FOCUSED, stopIconKind(focused = true, band = StopBand.DOT))
    }

    @Test
    fun `at the full band focus selects the enlarged icon`() {
        assertEquals(StopIconKind.FULL, stopIconKind(focused = false, band = StopBand.FULL))
        assertEquals(StopIconKind.FULL_FOCUSED, stopIconKind(focused = true, band = StopBand.FULL))
    }

    @Test
    fun `a starred stop gets the star in place of its icon or dot, focus enlarging it`() {
        assertEquals(
            StopIconKind.FAVORITE,
            stopIconKind(focused = false, band = StopBand.FULL, favorite = true)
        )
        assertEquals(
            StopIconKind.FAVORITE_FOCUSED,
            stopIconKind(focused = true, band = StopBand.FULL, favorite = true)
        )
        assertEquals(
            StopIconKind.FAVORITE_DOT,
            stopIconKind(focused = false, band = StopBand.DOT, favorite = true)
        )
        assertEquals(
            StopIconKind.FAVORITE_DOT_FOCUSED,
            stopIconKind(focused = true, band = StopBand.DOT, favorite = true)
        )
    }

    @Test
    fun `favorite defaults to false so a non-starred stop keeps its plain icon`() {
        assertEquals(StopIconKind.FULL, stopIconKind(focused = false, band = StopBand.FULL))
        assertEquals(StopIconKind.DOT, stopIconKind(focused = false, band = StopBand.DOT))
    }
}
