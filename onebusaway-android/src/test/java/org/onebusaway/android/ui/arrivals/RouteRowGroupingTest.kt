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

/**
 * JVM unit tests for the pure route-row grouping/ordering functions. They key off the
 * [RouteDirectionItem] interface, so a lightweight fake stands in for [ArrivalInfo] (which needs a
 * `Context`). Inputs mirror the real pipeline: already ETA-sorted (ascending, recent-past first).
 */
class RouteRowGroupingTest {

    private data class FakeItem(
        override val routeId: String,
        override val headsign: String?,
        override val eta: Long
    ) : RouteDirectionItem

    // Grouping + departure ordering ---------------------------------------------------------------

    @Test
    fun grouping_ordersGroupsByDeparture_andKeepsTripsSorted() {
        // ETA-sorted input interleaving two routes.
        val items = listOf(
            FakeItem("A", "North", 2),
            FakeItem("B", "East", 5),
            FakeItem("A", "North", 12),
            FakeItem("B", "East", 20),
            FakeItem("A", "North", 30),
        )
        val groups = groupByRouteDirection(items)

        // Group order follows each group's soonest upcoming ETA: A (2) before B (5).
        assertEquals(listOf("A", "B"), groups.map { it.first().routeId })
        // Trips within a group stay ETA-sorted.
        assertEquals(listOf(2L, 12L, 30L), groups[0].map { it.eta })
        assertEquals(listOf(5L, 20L), groups[1].map { it.eta })
    }

    @Test
    fun grouping_negativeEtaDoesNotCountTowardOrder() {
        // Route A just departed (-2) but its next real arrival is far off (20); Route B is sooner (5).
        // A must sort BELOW B — the departed trip doesn't pull A to the top.
        val items = listOf(
            FakeItem("A", "North", -2),
            FakeItem("B", "East", 5),
            FakeItem("A", "North", 20),
        )
        val groups = groupByRouteDirection(items)

        assertEquals(listOf("B", "A"), groups.map { it.first().routeId })
        // A still keeps its recent-past trip, shown first within the row.
        assertEquals(listOf(-2L, 20L), groups.first { it.first().routeId == "A" }.map { it.eta })
    }

    @Test
    fun grouping_allNegativeGroupSortsLast() {
        // Route A has only departed trips; Route B has an upcoming one — B ranks first.
        val items = listOf(
            FakeItem("A", "North", -5),
            FakeItem("A", "North", -2),
            FakeItem("B", "East", 4),
        )
        val groups = groupByRouteDirection(items)

        assertEquals(listOf("B", "A"), groups.map { it.first().routeId })
    }

    @Test
    fun grouping_sameRouteTwoHeadsigns_producesTwoGroups() {
        val items = listOf(
            FakeItem("A", "North", 3),
            FakeItem("A", "South", 6),
            FakeItem("A", "North", 15),
        )
        val groups = groupByRouteDirection(items)

        assertEquals(2, groups.size)
        assertEquals(listOf("North", "North"), groups[0].map { it.headsign })
        assertEquals(listOf("South"), groups[1].map { it.headsign })
    }

    @Test
    fun grouping_frequencyDuplicatesCollapseIntoOneGroup() {
        // Same route+headsign appearing twice (e.g. frequency-based service) → one group.
        val items = listOf(
            FakeItem("A", "North", 4),
            FakeItem("A", "North", 4),
        )
        val groups = groupByRouteDirection(items)

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
    }

    @Test
    fun grouping_nullAndBlankHeadsignGroupTogether() {
        val items = listOf(
            FakeItem("A", null, 1),
            FakeItem("A", "", 9),
        )
        assertEquals(1, groupByRouteDirection(items).size)
    }

    @Test
    fun grouping_empty_returnsEmpty() {
        assertEquals(emptyList<List<FakeItem>>(), groupByRouteDirection(emptyList<FakeItem>()))
    }

    // Favorite-first ordering ---------------------------------------------------------------------

    private fun order(items: List<FakeItem>, favorites: Set<String>) =
        orderGroupsByFavorite(items, favorites) { it.routeId }.map { it.routeId }

    @Test
    fun ordering_liftsStarredRoutesToTop() {
        // Departure-ordered input; B and D are starred → they lead, the rest follow.
        val items = listOf(
            FakeItem("A", "North", 2),
            FakeItem("B", "East", 5),
            FakeItem("C", "West", 8),
            FakeItem("D", "South", 11),
        )
        assertEquals(listOf("B", "D", "A", "C"), order(items, setOf("B", "D")))
    }

    @Test
    fun ordering_preservesDepartureOrderWithinEachPartition() {
        // Starred keep their relative (departure) order; so do the non-starred.
        val items = listOf(
            FakeItem("A", "North", 2),
            FakeItem("B", "East", 5),
            FakeItem("C", "West", 8),
            FakeItem("D", "South", 11),
        )
        assertEquals(listOf("A", "C", "B", "D"), order(items, setOf("A", "C")))
    }

    @Test
    fun ordering_noFavorites_leavesOrderUnchanged() {
        val items = listOf(
            FakeItem("A", "North", 2),
            FakeItem("B", "East", 5),
            FakeItem("C", "West", 8),
        )
        assertEquals(listOf("A", "B", "C"), order(items, emptySet()))
    }

    @Test
    fun ordering_allFavorites_leavesOrderUnchanged() {
        val items = listOf(
            FakeItem("A", "North", 2),
            FakeItem("B", "East", 5),
        )
        assertEquals(listOf("A", "B"), order(items, setOf("A", "B")))
    }

    // Group-level active-alert resolution ---------------------------------------------------------

    /** A minimal [ArrivalActions] carrying just the alert id under test — the row derivation reads
     *  only [ArrivalActions.alertSituationId]. */
    private fun actions(alertSituationId: String?) = ArrivalActions(
        tripId = "trip",
        routeId = "A",
        routeShortName = "A",
        routeLongName = "A Line",
        scheduleUrl = null,
        agencyName = null,
        blockId = null,
        alertSituationId = alertSituationId,
    )

    @Test
    fun activeAlert_scansWholeGroup_notJustTheRepresentative() {
        // Representative (soonest) trip is unaffected; a later trip in the same group carries an alert.
        val group = RouteRowGroup(
            listOf(
                previewArrival("A", "North", etaMinutes = 3),   // representative, unaffected
                previewArrival("A", "North", etaMinutes = 20),  // affected
            )
        )
        val id = group.activeAlertSituationId { arrival ->
            actions(alertSituationId = "s-1".takeIf { arrival.eta >= 10 })
        }
        assertEquals("s-1", id)
    }

    @Test
    fun activeAlert_nullWhenNoTripAffected() {
        val group = RouteRowGroup(listOf(previewArrival("A", "North", etaMinutes = 3)))
        assertEquals(null, group.activeAlertSituationId { actions(alertSituationId = null) })
    }
}
