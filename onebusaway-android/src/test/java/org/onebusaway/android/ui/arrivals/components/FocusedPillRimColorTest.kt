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
package org.onebusaway.android.ui.arrivals.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which colour the focused trip's ETA-pill rim wears (#1990). The rim's *presence* is covered from the
 * other side by the instrumented `EtaPillFocusOutlineTest`, which asserts semantics rather than colour;
 * this pins the colour choice, which is the part that can silently regress to the card's stroke.
 */
class FocusedPillRimColorTest {

    private val band = 0xFFC05010.toInt()
    private val routeLine = 0xFF0A5B3E.toInt()

    @Test
    fun `the band colour wins over the card's route colour`() {
        assertEquals(band, focusedPillRimColor(selected = true, bandColor = band, cardSelectionColor = routeLine))
    }

    @Test
    fun `with no band the pill borrows the card's stroke`() {
        // No vehicle selected yet, or a trip with no real-time fix to extrapolate: the map has no band
        // to name, so the pre-#1990 behaviour (#2205's borrowed stroke) is what's left to show.
        assertEquals(routeLine, focusedPillRimColor(selected = true, bandColor = null, cardSelectionColor = routeLine))
    }

    @Test
    fun `an unselected row is never outlined`() {
        // The band colour is one map-wide value handed to every row, so the gate is what keeps it from
        // outlining a pill in a row that isn't the focused one.
        assertNull(focusedPillRimColor(selected = false, bandColor = band, cardSelectionColor = routeLine))
        assertNull(focusedPillRimColor(selected = false, bandColor = null, cardSelectionColor = routeLine))
    }

    @Test
    fun `a selected row with no colour at all stays unoutlined`() {
        assertNull(focusedPillRimColor(selected = true, bandColor = null, cardSelectionColor = null))
    }
}
