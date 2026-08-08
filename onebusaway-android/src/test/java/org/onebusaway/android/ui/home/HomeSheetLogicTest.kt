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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.showsNearbyArrivals
import org.onebusaway.android.util.GeoPoint

/**
 * Decision-table tests for the pure arrivals-sheet logic extracted from [HomeScreen]. These cover
 * the parity-sensitive behavior the on-device smoke test would otherwise be the only check on: which
 * focus/tab states show the sheet, when a reconcile peeks it open vs. leaves the user's position, and
 * how the chevron toggle + back-press unwind it.
 */
class HomeSheetLogicTest {

    private val stop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))

    // --- homeSheetContent ---

    /** The nearby drawer's preconditions, so each test below varies one thing away from them. */
    private fun content(
        focus: CurrentFocus = CurrentFocus.None,
        band: StopBand = StopBand.ROUTES,
        nearbyRowsReady: Boolean = true
    ) = homeSheetContent(focus, band, nearbyRowsReady)

    @Test
    fun `a focused stop shows its own panel at any zoom`() {
        assertEquals(HomeSheetContent.Stop("1"), content(CurrentFocus.Stop(stop), StopBand.ROUTES))
        assertEquals(HomeSheetContent.Stop("1"), content(CurrentFocus.Stop(stop), StopBand.DOT))
    }

    /** A focused stop is a deliberate choice about one bay; it outranks the ambient nearby list. */
    @Test
    fun `a focused stop wins over the nearby list`() {
        assertEquals(
            HomeSheetContent.Stop("1"),
            content(focus = CurrentFocus.Stop(stop), nearbyRowsReady = true)
        )
    }

    @Test
    fun `nearby routes show unfocused at transit-centre zoom with rows`() {
        assertEquals(HomeSheetContent.NearbyRoutes, content())
    }

    /** Widening bands: a band added above ROUTES must keep the drawer, not switch it off. */
    @Test
    fun `nearby routes read the band as an ordering`() {
        assertEquals(
            HomeSheetContent.NearbyRoutes,
            content(band = StopBand.entries.last())
        )
    }

    @Test
    fun `nothing shows below the transit-centre band`() {
        assertEquals(HomeSheetContent.None, content(band = StopBand.FULL))
        assertEquals(HomeSheetContent.None, content(band = StopBand.DOT))
    }

    /** Never open an empty drawer: no rows means no sheet, whatever the zoom. */
    @Test
    fun `nothing shows without nearby rows`() {
        assertEquals(HomeSheetContent.None, content(nearbyRowsReady = false))
    }

    @Test
    fun `route, bike, and directions focus show no sheet`() {
        assertEquals(HomeSheetContent.None, content(focus = CurrentFocus.Route(RouteTarget("route"))))
        assertEquals(HomeSheetContent.None, content(focus = CurrentFocus.BikeStation("bike")))
        assertEquals(HomeSheetContent.None, content(focus = CurrentFocus.Directions()))
    }

    // --- sheetKey ---

    /**
     * The reveal effect keys off this, so it must NOT change as the rider pans within the nearby mode
     * — otherwise every settled camera would re-run the reveal and fight a drag in progress.
     */
    @Test
    fun `the nearby key is stable while the stop key is per stop`() {
        assertEquals("nearby", HomeSheetContent.NearbyRoutes.sheetKey)
        assertEquals("stop:1", HomeSheetContent.Stop("1").sheetKey)
        assertEquals("stop:2", HomeSheetContent.Stop("2").sheetKey)
        assertNull(HomeSheetContent.None.sheetKey)
    }

    /**
     * The sheet decision and `NearbyArrivalsViewModel`'s query gate must read the same predicate, or
     * the drawer can engage on a band the query never asked for — so pin the predicate itself, not
     * each caller's copy of `>= ROUTES`.
     */
    @Test
    fun `only the transit-centre band shows nearby arrivals`() {
        assertFalse(StopBand.DOT.showsNearbyArrivals)
        assertFalse(StopBand.FULL.showsNearbyArrivals)
        assertTrue(StopBand.ROUTES.showsNearbyArrivals)
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

    /** An expanded sheet collapses to peek whatever it holds — including the nearby list. */
    @Test
    fun `back collapses an expanded nearby list`() {
        assertEquals(
            SheetBackAction.COLLAPSE,
            sheetBackAction(ArrivalsSheetState.Expanded, HomeSheetContent.NearbyRoutes)
        )
    }

    /**
     * The nearby drawer is ambient, not a focus: at peek there is nothing behind it to go back to, so
     * back must reach the system rather than being swallowed into a focus pop.
     */
    @Test
    fun `back at peek passes through for the nearby list but pops a focused stop`() {
        assertEquals(
            SheetBackAction.NONE,
            sheetBackAction(ArrivalsSheetState.Collapsed, HomeSheetContent.NearbyRoutes)
        )
        assertEquals(
            SheetBackAction.NAVIGATE_BACK,
            sheetBackAction(ArrivalsSheetState.Collapsed, HomeSheetContent.Stop("1"))
        )
    }
}
