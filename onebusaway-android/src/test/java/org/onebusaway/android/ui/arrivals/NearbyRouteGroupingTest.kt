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

/**
 * JVM unit tests for [groupByRouteDirectionAndStop] — the transit-centre drawer's grouping (#2107),
 * which spans many bays and so keys the stop into a row's identity. Uses the same lightweight
 * [RouteDirectionItem] fake as [RouteRowGroupingTest], since building an [ArrivalInfo] needs a
 * `Context`.
 */
class NearbyRouteGroupingTest {

    private data class FakeItem(
        override val routeId: String,
        override val headsign: String?,
        override val eta: Long,
        val stopId: String,
        val stopSortKey: String? = stopId,
        override val directionId: Int? = null,
        override val lineName: String = routeId,
        val agencyName: String? = null
    ) : RouteDirectionItem

    private fun group(items: List<FakeItem>) = groupByRouteDirectionAndStop(
        items = items,
        agencyNameOf = { it.agencyName },
        stopIdOf = { it.stopId },
        stopSortKeyOf = { it.stopSortKey }
    )

    /**
     * The point of the whole function: one route+direction leaving two bays is two rows, so neither
     * row can send a rider to the wrong side of the street.
     */
    @Test
    fun `one route and direction at two bays produces two rows`() {
        val rows = group(
            listOf(
                FakeItem("r1", "Downtown", eta = 3, stopId = "bayA"),
                FakeItem("r1", "Downtown", eta = 9, stopId = "bayB"),
                FakeItem("r1", "Downtown", eta = 12, stopId = "bayA")
            )
        )
        assertEquals(2, rows.size)
        assertEquals(listOf("bayA", "bayA"), rows[0].map { it.stopId })
        assertEquals(listOf("bayB"), rows[1].map { it.stopId })
    }

    /** Same bay, one route, two directions — still split, exactly as the per-stop grouping does. */
    @Test
    fun `one route with two directions at one bay produces two rows`() {
        val rows = group(
            listOf(
                FakeItem("r1", "Downtown", eta = 3, stopId = "bayA", directionId = 0),
                FakeItem("r1", "Uptown", eta = 5, stopId = "bayA", directionId = 1)
            )
        )
        assertEquals(2, rows.size)
    }

    /** Different routes at the same bay stay separate rows, ordered by the shared line comparator. */
    @Test
    fun `rows order by line name with natural sort, not lexical`() {
        val rows = group(
            listOf(
                FakeItem("r40", "A", eta = 1, stopId = "bay", lineName = "40"),
                FakeItem("r8", "A", eta = 2, stopId = "bay", lineName = "8"),
                FakeItem("r550", "A", eta = 3, stopId = "bay", lineName = "550")
            )
        )
        assertEquals(listOf("8", "40", "550"), rows.map { it.first().lineName })
    }

    @Test
    fun `agency orders ahead of line name`() {
        val rows = group(
            listOf(
                FakeItem("r1", "A", eta = 1, stopId = "bay", lineName = "1", agencyName = "Zed Transit"),
                FakeItem("r9", "A", eta = 2, stopId = "bay", lineName = "9", agencyName = "Ace Transit")
            )
        )
        assertEquals(listOf("Ace Transit", "Zed Transit"), rows.map { it.first().agencyName })
    }

    /** The bay is the last tiebreak, so the two-bay case is deterministic rather than input-ordered. */
    @Test
    fun `two bays for one route order by their stop sort key`() {
        val rows = group(
            listOf(
                FakeItem("r1", "Downtown", eta = 3, stopId = "z", stopSortKey = "Bay 9"),
                FakeItem("r1", "Downtown", eta = 4, stopId = "a", stopSortKey = "Bay 2")
            )
        )
        assertEquals(listOf("Bay 2", "Bay 9"), rows.map { it.first().stopSortKey })
    }

    /** A bay with no sort key sorts last rather than dominating the top. */
    @Test
    fun `a bay with no sort key sorts last`() {
        val rows = group(
            listOf(
                FakeItem("r1", "Downtown", eta = 3, stopId = "z", stopSortKey = null),
                FakeItem("r1", "Downtown", eta = 4, stopId = "a", stopSortKey = "Bay 2")
            )
        )
        assertEquals(listOf("Bay 2", null), rows.map { it.first().stopSortKey })
    }

    /**
     * Ordering is by the stable (agency, line, headsign, bay) key and never by ETA, so a row does not
     * jump around the list as its countdown ticks or a poll lands.
     */
    @Test
    fun `row order does not depend on eta`() {
        val soonestFirst = group(
            listOf(
                FakeItem("r8", "A", eta = 1, stopId = "bay", lineName = "8"),
                FakeItem("r40", "A", eta = 2, stopId = "bay", lineName = "40")
            )
        )
        val soonestLast = group(
            listOf(
                FakeItem("r40", "A", eta = 1, stopId = "bay", lineName = "40"),
                FakeItem("r8", "A", eta = 99, stopId = "bay", lineName = "8")
            )
        )
        assertEquals(listOf("8", "40"), soonestFirst.map { it.first().lineName })
        assertEquals(listOf("8", "40"), soonestLast.map { it.first().lineName })
    }

    /**
     * The stop id is joined into the key with the same NUL the route key uses, so a bay whose id
     * happens to contain that separator still reads as its own bay rather than merging with another.
     */
    @Test
    fun `a stop id containing the key separator stays its own row`() {
        val rows = group(
            listOf(
                FakeItem("r1", null, eta = 1, stopId = "a"),
                FakeItem("r1", null, eta = 2, stopId = "a\u0000r1"),
                FakeItem("r1", null, eta = 3, stopId = "a")
            )
        )
        assertEquals(2, rows.size)
        assertEquals(2, rows.first { it.first().stopId == "a" }.size)
    }
}
