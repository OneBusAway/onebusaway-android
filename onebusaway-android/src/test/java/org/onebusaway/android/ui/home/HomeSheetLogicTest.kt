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
package org.onebusaway.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision-table tests for the pure arrivals-sheet logic extracted from [HomeScreen]. These cover
 * the parity-sensitive behavior the on-device smoke test would otherwise be the only check on: which
 * focus/tab states show the sheet, when a reconcile peeks it open vs. leaves the user's position, and
 * how the chevron toggle + back-press unwind it.
 */
class HomeSheetLogicTest {

    private val stop = FocusedStop("1", "Main St", "100", 47.6, -122.3)

    // --- shouldShowSheet ---

    @Test
    fun `sheet shows only with a focused stop`() {
        assertTrue(shouldShowSheet(stop))
        assertFalse(shouldShowSheet(null))
    }

    // --- toggleSheetTarget ---

    @Test
    fun `the chevron toggles full to peek and otherwise expands`() {
        assertEquals(ArrivalsSheetState.Collapsed, toggleSheetTarget(ArrivalsSheetState.Expanded))
        assertEquals(ArrivalsSheetState.Expanded, toggleSheetTarget(ArrivalsSheetState.Collapsed))
        assertEquals(ArrivalsSheetState.Expanded, toggleSheetTarget(ArrivalsSheetState.Hidden))
    }

    // --- sheetBackAction ---

    @Test
    fun `back collapses a full sheet, clears focus from peek, and passes through when hidden`() {
        assertEquals(SheetBackAction.COLLAPSE, sheetBackAction(ArrivalsSheetState.Expanded))
        assertEquals(SheetBackAction.CLEAR_FOCUS, sheetBackAction(ArrivalsSheetState.Collapsed))
        assertEquals(SheetBackAction.NONE, sheetBackAction(ArrivalsSheetState.Hidden))
    }

    // --- arrivalsPeekTier ---

    @Test
    fun `peek tier follows the previewed arrival count`() {
        assertEquals(ArrivalsPeekTier.NONE, arrivalsPeekTier(0))
        assertEquals(ArrivalsPeekTier.ONE, arrivalsPeekTier(1))
        assertEquals(ArrivalsPeekTier.TWO_OR_MORE, arrivalsPeekTier(2))
        assertEquals(ArrivalsPeekTier.TWO_OR_MORE, arrivalsPeekTier(5))
        // Defensive: a negative count falls back to the no-arrivals tier.
        assertEquals(ArrivalsPeekTier.NONE, arrivalsPeekTier(-1))
    }
}
