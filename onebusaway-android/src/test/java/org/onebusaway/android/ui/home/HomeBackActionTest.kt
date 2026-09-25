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
import org.junit.Test

/**
 * HOME's Back ladder, rung by rung. Each test pins one rung *and* that every rung above it yields:
 * the ordering is the whole contract, since it used to live in the composition order of two
 * handlers and a wrapper (see [homeBackAction]).
 */
class HomeBackActionTest {

    private fun action(
        returnsToSource: Boolean = false,
        directionsActive: Boolean = false,
        pickingEndpoint: Boolean = false,
        sheet: ArrivalsSheetState = ArrivalsSheetState.Hidden,
        canUndoMapAction: Boolean = false
    ) = homeBackAction(returnsToSource, directionsActive, pickingEndpoint, sheet, canUndoMapAction)

    @Test
    fun `the bare map claims nothing, so Back reaches the system`() {
        assertEquals(HomeBackAction.NONE, action())
        assertEquals(HomeBackAction.NONE, action(sheet = ArrivalsSheetState.Collapsed))
    }

    /** A map pushed above a page (#2310) returns to it before any local rung, whatever else is on. */
    @Test
    fun `a source page wins over every local rung`() {
        assertEquals(
            HomeBackAction.RETURN_TO_SOURCE,
            action(
                returnsToSource = true,
                directionsActive = true,
                pickingEndpoint = true,
                sheet = ArrivalsSheetState.Expanded,
                canUndoMapAction = true
            )
        )
        assertEquals(HomeBackAction.RETURN_TO_SOURCE, action(returnsToSource = true))
    }

    /** Back cancels a choose-on-map pick first, then unwinds directions one level at a time. */
    @Test
    fun `directions cancels a pick, then walks its own ladder, and never reaches undo`() {
        assertEquals(
            HomeBackAction.CANCEL_ENDPOINT_PICK,
            action(directionsActive = true, pickingEndpoint = true, canUndoMapAction = true)
        )
        assertEquals(
            HomeBackAction.NAVIGATE_BACK_IN_DIRECTIONS,
            action(directionsActive = true, canUndoMapAction = true)
        )
        assertEquals(HomeBackAction.NAVIGATE_BACK_IN_DIRECTIONS, action(directionsActive = true))
    }

    /** A pick outside directions is a stale flag the leave-directions effect is about to clear, not a rung. */
    @Test
    fun `a pick only counts while directions is active`() {
        assertEquals(HomeBackAction.NONE, action(pickingEndpoint = true))
        assertEquals(HomeBackAction.UNDO_MAP_ACTION, action(pickingEndpoint = true, canUndoMapAction = true))
    }

    /** An expanded sheet collapses to peek first, whatever it holds — the nearby list included (#2107). */
    @Test
    fun `an expanded sheet collapses before undo`() {
        assertEquals(HomeBackAction.COLLAPSE_SHEET, action(sheet = ArrivalsSheetState.Expanded))
        assertEquals(
            HomeBackAction.COLLAPSE_SHEET,
            action(sheet = ArrivalsSheetState.Expanded, canUndoMapAction = true)
        )
    }

    /**
     * At peek the sheet consumes nothing of its own: a focused stop steps out through undo, and the
     * ambient nearby list — no focus, so usually no history — lets the press through to the system
     * rather than stranding the rider.
     */
    @Test
    fun `at peek Back is undo when there is history and the system's when there is none`() {
        assertEquals(
            HomeBackAction.UNDO_MAP_ACTION,
            action(sheet = ArrivalsSheetState.Collapsed, canUndoMapAction = true)
        )
        assertEquals(HomeBackAction.NONE, action(sheet = ArrivalsSheetState.Collapsed))
        assertEquals(HomeBackAction.UNDO_MAP_ACTION, action(canUndoMapAction = true))
    }
}
