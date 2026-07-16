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
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.continuationBadgeText

/**
 * JVM unit tests for the pure route-row grouping/ordering functions. Grouping and the between-row
 * (agency, line, headsign) sort (#1822) key off the [RouteDirectionItem] interface (which carries
 * [RouteDirectionItem.lineName] intrinsically) plus an external agency-name accessor, so a
 * lightweight fake stands in for [ArrivalInfo] (which needs a `Context`); [ArrivalInfo.lineName]'s
 * own fallback chain is covered separately using [previewArrival]. Within-row trip order is
 * untouched by any of this and stays whatever order the caller fed in (upstream: ETA-sorted,
 * recent-past first).
 */
class RouteRowGroupingTest {

    @Test
    fun `continuation badge shows one two or collapsed route names`() {
        assertEquals("65", continuationBadgeText(listOf("65"), "fallback"))
        assertEquals("65 › 75", continuationBadgeText(listOf("65", "75"), "fallback"))
        assertEquals(
            "65 » 75",
            continuationBadgeText(listOf("65", "67", "75"), "fallback"),
        )
    }

    private data class FakeItem(
        override val routeId: String,
        override val headsign: String?,
        override val eta: Long,
        override val lineName: String = routeId,
        val agencyName: String? = null,
    ) : RouteDirectionItem

    /** [groupByRouteDirection] with the agency name read off [FakeItem] itself. */
    private fun group(items: List<FakeItem>) = groupByRouteDirection(items) { it.agencyName }

    // Grouping (route, direction) into rows ---------------------------------------------------------

    @Test
    fun grouping_sameRouteTwoHeadsigns_producesTwoGroups() {
        val items = listOf(
            FakeItem("A", "North", 3),
            FakeItem("A", "South", 6),
            FakeItem("A", "North", 15),
        )
        val groups = group(items)

        assertEquals(2, groups.size)
        assertEquals(listOf("North", "North"), groups.first { it.first().headsign == "North" }.map { it.headsign })
        assertEquals(listOf("South"), groups.first { it.first().headsign == "South" }.map { it.headsign })
    }

    @Test
    fun grouping_frequencyDuplicatesCollapseIntoOneGroup() {
        // Same route+headsign appearing twice (e.g. frequency-based service) → one group.
        val items = listOf(
            FakeItem("A", "North", 4),
            FakeItem("A", "North", 4),
        )
        val groups = group(items)

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
    }

    @Test
    fun grouping_nullAndBlankHeadsignGroupTogether() {
        val items = listOf(
            FakeItem("A", null, 1),
            FakeItem("A", "", 9),
        )
        assertEquals(1, group(items).size)
    }

    @Test
    fun grouping_empty_returnsEmpty() {
        assertEquals(emptyList<List<FakeItem>>(), group(emptyList()))
    }

    @Test
    fun grouping_keepsTripsInIncomingOrder() {
        // Within-row trip order is untouched by the between-row sort (#1822) — it stays whatever order
        // the caller fed in (upstream: ETA-sorted by ArrivalInfoUtils.InfoComparator).
        val items = listOf(
            FakeItem("A", "North", -2),
            FakeItem("A", "North", 20),
        )
        assertEquals(listOf(-2L, 20L), group(items).single().map { it.eta })
    }

    // Between-row ordering: (agency, line, headsign) — stable, not ETA (#1822) -----------------------

    @Test
    fun ordering_sortsByLineNameNaturally_withinSameAgency() {
        // Numeric-aware: "8" before "40" before "550", not lexicographic ("40" < "550" < "8").
        val items = listOf(
            FakeItem("r550", "X", eta = 1, agencyName = "Metro", lineName = "550"),
            FakeItem("r8", "X", eta = 1, agencyName = "Metro", lineName = "8"),
            FakeItem("r40", "X", eta = 1, agencyName = "Metro", lineName = "40"),
        )
        assertEquals(listOf("r8", "r40", "r550"), group(items).map { it.first().routeId })
    }

    @Test
    fun ordering_sortsAlphanumericLineNames_viaAlphanumComparator() {
        // Mixed numeric/alphanumeric short names ("8", "A Line", "RapidRide E") sort via
        // AlphanumComparator (the same comparator RouteDisplay already uses for route short names):
        // digit-led names sort before letter-led names, which then compare alphabetically.
        val items = listOf(
            FakeItem("rapidride", "X", eta = 1, agencyName = "Metro", lineName = "RapidRide E"),
            FakeItem("aline", "X", eta = 1, agencyName = "Metro", lineName = "A Line"),
            FakeItem("r8", "X", eta = 1, agencyName = "Metro", lineName = "8"),
        )
        assertEquals(listOf("r8", "aline", "rapidride"), group(items).map { it.first().routeId })
    }

    @Test
    fun ordering_sortsByAgencyBeforeLineName() {
        // Agency is the primary key: a numerically-later line in an earlier agency still sorts first.
        val items = listOf(
            FakeItem("r-zoo-8", "X", eta = 1, agencyName = "Zoo Transit", lineName = "8"),
            FakeItem("r-metro-40", "X", eta = 1, agencyName = "Metro", lineName = "40"),
        )
        assertEquals(listOf("r-metro-40", "r-zoo-8"), group(items).map { it.first().routeId })
    }

    @Test
    fun ordering_nullAgencySortsLast() {
        val items = listOf(
            FakeItem("named", "X", eta = 1, agencyName = "Metro", lineName = "1"),
            FakeItem("unnamed", "X", eta = 1, agencyName = null, lineName = "1"),
        )
        assertEquals(listOf("named", "unnamed"), group(items).map { it.first().routeId })
    }

    @Test
    fun ordering_blankAgencySortsLast_sameAsNull() {
        val items = listOf(
            FakeItem("blank", "X", eta = 1, agencyName = "", lineName = "1"),
            FakeItem("named", "X", eta = 1, agencyName = "Metro", lineName = "1"),
        )
        assertEquals(listOf("named", "blank"), group(items).map { it.first().routeId })
    }

    @Test
    fun ordering_tiesBrokenByHeadsign_deterministically() {
        // Same (agency, line) serving two directions at the stop — order must not depend on input order.
        val items = listOf(
            FakeItem("r8", "West", eta = 1, agencyName = "Metro", lineName = "8"),
            FakeItem("r8", "East", eta = 1, agencyName = "Metro", lineName = "8"),
        )
        assertEquals(listOf("East", "West"), group(items).map { it.first().headsign })
    }

    @Test
    fun ordering_doesNotDependOnEta() {
        // The whole point of #1822: a group with a far-off ETA can still sort above one with a near ETA,
        // as long as its (agency, line) key sorts first.
        val items = listOf(
            FakeItem("r40", "X", eta = 1, agencyName = "Metro", lineName = "40"),
            FakeItem("r8", "X", eta = 999, agencyName = "Metro", lineName = "8"),
        )
        assertEquals(listOf("r8", "r40"), group(items).map { it.first().routeId })
    }

    @Test
    fun ordering_isStableAmongEqualKeys() {
        // Equal (agency, line, headsign) keys keep first-seen order (frequency-style duplicate groups
        // aside, this covers hypothetical equal-but-distinct groups).
        val items = listOf(
            FakeItem("first", "X", eta = 1, agencyName = "Metro", lineName = "8"),
            FakeItem("second", "X", eta = 1, agencyName = "Metro", lineName = "8"),
        )
        assertEquals(listOf("first", "second"), group(items).map { it.first().routeId })
    }

    // groupArrivalsByRouteDirection: line-name fallback chain on real ArrivalInfo (#1822) -------------

    @Test
    fun arrivalsGrouping_lineNameFallsBackToLongName_whenShortNameBlank() {
        val arrivals = listOf(
            previewArrival(shortName = "", headsign = "X", etaMinutes = 1, routeId = "blank-short", routeLongName = "Zephyr Line"),
            previewArrival(shortName = "8", headsign = "X", etaMinutes = 1, routeId = "has-short"),
        )
        val groups = groupArrivalsByRouteDirection(arrivals) { null }
        // "8" (numeric) sorts before "Zephyr Line" (non-numeric fallback) under natural ordering.
        assertEquals(listOf("has-short", "blank-short"), groups.map { it.routeId })
    }

    @Test
    fun arrivalsGrouping_lineNameFallsBackToRouteId_whenShortAndLongNameBlank() {
        val arrivals = listOf(
            previewArrival(shortName = "", headsign = "X", etaMinutes = 1, routeId = "aaa-fallback", routeLongName = null),
        )
        val groups = groupArrivalsByRouteDirection(arrivals) { null }
        assertEquals("aaa-fallback", groups.single().representative.routeId)
    }

    @Test
    fun arrivalsGrouping_usesSuppliedAgencyNameAccessor() {
        val arrivals = listOf(
            previewArrival(shortName = "8", headsign = "X", etaMinutes = 1, routeId = "zoo-route"),
            previewArrival(shortName = "8", headsign = "X", etaMinutes = 1, routeId = "metro-route"),
        )
        val groups = groupArrivalsByRouteDirection(arrivals) { arrival ->
            if (arrival.routeId == "metro-route") "Metro" else "Zoo Transit"
        }
        assertEquals(listOf("metro-route", "zoo-route"), groups.map { it.routeId })
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

    // Selected-row promotion ----------------------------------------------------------------------

    @Test
    fun selectedRow_movesExactRouteDirectionToTop_withoutMutatingSourceOrder() {
        val groups = listOf(
            RouteRowGroup(listOf(previewArrival("A", "North", etaMinutes = 2, routeId = "A"))),
            RouteRowGroup(listOf(previewArrival("B", "East", etaMinutes = 5, routeId = "B"))),
            RouteRowGroup(listOf(previewArrival("A", "South", etaMinutes = 8, routeId = "A"))),
        )
        val originalKeys = groups.map(RouteRowGroup::key)

        val promoted = promoteSelectedRouteGroup(groups, routeRowKey("A", "South"))

        assertEquals(
            listOf(routeRowKey("A", "South"), routeRowKey("A", "North"), routeRowKey("B", "East")),
            promoted.map(RouteRowGroup::key),
        )
        assertEquals(originalKeys, groups.map(RouteRowGroup::key))
    }

    @Test
    fun selectedRow_usesSoleRouteRow_whenMapAndArrivalHeadsignsDiffer() {
        val groups = listOf(
            RouteRowGroup(listOf(previewArrival("11", "Madison Park", 2, routeId = "11"))),
            RouteRowGroup(listOf(previewArrival("12", "Downtown", 5, routeId = "12"))),
        )

        assertEquals(
            routeRowKey("11", "Madison Park"),
            resolveSelectedRouteGroupKey(groups, routeRowKey("11", "Madison Park via Pine"), "11"),
        )
    }

    @Test
    fun selectedRow_doesNotGuess_whenRouteHasMultipleDirectionRows() {
        val groups = listOf(
            RouteRowGroup(listOf(previewArrival("11", "Madison Park", 2, routeId = "11"))),
            RouteRowGroup(listOf(previewArrival("11", "Downtown", 5, routeId = "11"))),
        )

        assertEquals(
            null,
            resolveSelectedRouteGroupKey(groups, routeRowKey("11", "Different label"), "11"),
        )
    }

    @Test
    fun selectedRow_matchesNumericDirection_whenHeadsignsDiffer() {
        val groups = listOf(
            RouteRowGroup(
                listOf(previewArrival("11", "Madison Park", 2, routeId = "11", directionId = 0))
            ),
            RouteRowGroup(
                listOf(previewArrival("11", "Downtown", 5, routeId = "11", directionId = 1))
            ),
        )

        assertEquals(
            routeRowKey("11", 1, "Downtown"),
            resolveSelectedRouteGroupKey(
                groups,
                routeRowKey("11", 1, "A completely different label"),
                "11",
            ),
        )
    }

    @Test
    fun grouping_usesNumericDirectionRatherThanDisplayHeadsign() {
        val arrivals = listOf(
            previewArrival("11", "Same label", 2, routeId = "11", directionId = 0),
            previewArrival("11", "Same label", 5, routeId = "11", directionId = 1),
        )

        assertEquals(2, groupArrivalsByRouteDirection(arrivals) { "Metro" }.size)
    }

    @Test
    fun noActiveSelection_returnsOriginalOrderingInstance() {
        val groups = listOf(
            RouteRowGroup(listOf(previewArrival("A", "North", etaMinutes = 2, routeId = "A"))),
            RouteRowGroup(listOf(previewArrival("B", "East", etaMinutes = 5, routeId = "B"))),
        )

        assertSame(groups, promoteSelectedRouteGroup(groups, selectedKey = null))
        assertSame(groups, promoteSelectedRouteGroup(groups, selectedKey = "missing"))
        assertSame(groups, promoteSelectedRouteGroup(groups, selectedKey = groups.first().key))
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
