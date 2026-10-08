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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.ui.arrivals.components.previewArrival

/** The per-stop route filter's pure rules (#2366): which rows the board shows, and what a row's menu offers. */
class RouteFilterTest {

    private fun row(routeId: String, headsign: String = "Downtown") = RouteRowGroup(listOf(previewArrival(routeId, headsign, 5, routeId = routeId)))

    private val groups = listOf(row("A"), row("B"), row("C"))

    @Test
    fun hiddenRoutesDropOut_keepingTheRestInOrder() {
        assertEquals(listOf("A", "C"), visibleRouteGroups(groups, setOf("B"), selectedKey = null).map { it.routeId })
    }

    @Test
    fun hidingOneRouteHidesBothItsDirections() {
        val twoWay = listOf(row("A", "North"), row("A", "South"), row("B"))

        assertEquals(listOf("B"), visibleRouteGroups(twoWay, setOf("A"), selectedKey = null).map { it.routeId })
    }

    @Test
    fun aRowSelectedOnTheMapShowsEvenWhenItsRouteIsHidden() {
        val selected = row("B").key

        assertEquals(listOf("A", "B", "C"), visibleRouteGroups(groups, setOf("B"), selected).map { it.routeId })
    }

    @Test
    fun nothingHidden_showsEveryRow() {
        assertEquals(groups, visibleRouteGroups(groups, emptySet(), selectedKey = null))
    }

    /** Which items [RouteFilterCallbacks.forRoute] offers, as (show only, hide, show all). */
    private fun offered(routeId: String, stopRouteIds: Set<String>, hiddenRouteIds: Set<String>): Triple<Boolean, Boolean, Boolean> {
        val actions = RouteFilterCallbacks({}, {}, {}).forRoute(routeId, stopRouteIds, hiddenRouteIds)
        return Triple(actions.onShowOnly != null, actions.onHide != null, actions.onShowAll != null)
    }

    @Test
    fun unfilteredStop_offersShowOnlyAndHide_butNotShowAll() {
        assertEquals(Triple(true, true, false), offered("A", setOf("A", "B"), emptySet()))
    }

    @Test
    fun lastRouteShowing_offersNeitherShowOnlyNorHide() {
        // "Only this one" would change nothing, and hiding it would blank the board.
        assertEquals(Triple(false, false, true), offered("A", setOf("A", "B", "C"), setOf("B", "C")))
    }

    @Test
    fun singleRouteStop_offersNothing() {
        assertEquals(Triple(false, false, false), offered("A", setOf("A"), emptySet()))
    }

    @Test
    fun hiddenRouteShownBySelection_offersShowOnly_butNotHide() {
        assertEquals(Triple(true, false, true), offered("B", setOf("A", "B"), setOf("B")))
    }

    @Test
    fun offeredItemsActOnTheRowsRoute() {
        val calls = mutableListOf<String>()
        val actions = RouteFilterCallbacks({ calls += "only:$it" }, { calls += "hide:$it" }, { calls += "all" })
            .forRoute("A", setOf("A", "B"), setOf("C"))

        actions.onShowOnly!!()
        actions.onHide!!()
        actions.onShowAll!!()

        assertEquals(listOf("only:A", "hide:A", "all"), calls)
    }
}
