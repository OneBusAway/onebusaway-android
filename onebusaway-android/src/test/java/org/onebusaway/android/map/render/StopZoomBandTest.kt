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
    fun `at the dot band the focused stop gets the accent dot, others the plain dot`() {
        assertEquals(StopIconKind.DOT, stopIconKind(focused = false, band = StopBand.DOT))
        assertEquals(StopIconKind.DOT_FOCUSED, stopIconKind(focused = true, band = StopBand.DOT))
    }

    @Test
    fun `at the full band focus selects the enlarged icon`() {
        assertEquals(StopIconKind.FULL, stopIconKind(focused = false, band = StopBand.FULL))
        assertEquals(StopIconKind.FULL_FOCUSED, stopIconKind(focused = true, band = StopBand.FULL))
    }
}
