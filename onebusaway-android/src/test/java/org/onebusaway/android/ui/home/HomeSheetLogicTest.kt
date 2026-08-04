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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

/**
 * Decision-table tests for the pure arrivals-sheet logic extracted from [HomeScreen]. These cover
 * the parity-sensitive behavior the on-device smoke test would otherwise be the only check on: which
 * focus/tab states show the sheet, when a reconcile peeks it open vs. leaves the user's position, and
 * how the chevron toggle + back-press unwind it.
 */
class HomeSheetLogicTest {

    private val stop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))

    // --- shouldShowSheet ---

    @Test
    fun `sheet shows only with a focused stop`() {
        assertTrue(shouldShowSheet(CurrentFocus.Stop(stop)))
        assertFalse(shouldShowSheet(CurrentFocus.Route(RouteTarget("route"))))
        assertFalse(shouldShowSheet(CurrentFocus.BikeStation("bike")))
        assertFalse(shouldShowSheet(CurrentFocus.None))
    }

    @Test
    fun `stop and route focus use the measured focus banner edge`() {
        val stopRoute = StopRouteSelection(
            originHeadsign = null,
            legs = listOf(RouteLeg("route", "40"))
        )
        assertEquals(240, focusBannerTopEdge(CurrentFocus.Stop(stop, stopRoute), 240))
        assertEquals(240, focusBannerTopEdge(CurrentFocus.Route(RouteTarget("route")), 240))
        assertEquals(240, focusBannerTopEdge(CurrentFocus.Stop(stop), 240))
        assertEquals(0, focusBannerTopEdge(CurrentFocus.None, 240))
        assertEquals(0, focusBannerTopEdge(CurrentFocus.BikeStation("bike"), 240))
    }

    @Test
    fun `directions takes its top edge from the form card`() {
        assertEquals(
            180,
            focusBannerTopEdge(CurrentFocus.Directions(), 240, directionsFormBottomPx = 180)
        )
    }

    // --- mapControlsBottomInset ---

    @Test
    fun `the map controls sit at the bottom edge with no sheet over the map`() {
        assertEquals(
            0.dp,
            mapControlsBottomInset(arrivalsPeek = 200.dp, arrivalsAtPeek = false, directionsSheet = 0.dp)
        )
    }

    @Test
    fun `a peeking arrivals sheet lifts the map controls, an expanded one does not`() {
        assertEquals(
            200.dp,
            mapControlsBottomInset(arrivalsPeek = 200.dp, arrivalsAtPeek = true, directionsSheet = 0.dp)
        )
        assertEquals(
            0.dp,
            mapControlsBottomInset(arrivalsPeek = 200.dp, arrivalsAtPeek = false, directionsSheet = 0.dp)
        )
    }

    @Test
    fun `the directions drawer lifts the map controls in both of its resting positions`() {
        // Expanded (a fraction of the window) and collapsed to its handle-only peek.
        assertEquals(
            320.dp,
            mapControlsBottomInset(arrivalsPeek = 0.dp, arrivalsAtPeek = false, directionsSheet = 320.dp)
        )
        assertEquals(
            48.dp,
            mapControlsBottomInset(arrivalsPeek = 0.dp, arrivalsAtPeek = false, directionsSheet = 48.dp)
        )
    }

    @Test
    fun `with both sheets reporting a height the controls clear the taller one`() {
        assertEquals(
            320.dp,
            mapControlsBottomInset(arrivalsPeek = 200.dp, arrivalsAtPeek = true, directionsSheet = 320.dp)
        )
        assertEquals(
            200.dp,
            mapControlsBottomInset(arrivalsPeek = 200.dp, arrivalsAtPeek = true, directionsSheet = 48.dp)
        )
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
        assertEquals(SheetBackAction.NAVIGATE_BACK, sheetBackAction(ArrivalsSheetState.Collapsed))
        assertEquals(SheetBackAction.NONE, sheetBackAction(ArrivalsSheetState.Hidden))
    }
}
