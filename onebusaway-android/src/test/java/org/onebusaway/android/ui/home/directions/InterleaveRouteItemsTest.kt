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
package org.onebusaway.android.ui.home.directions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.tripresults.AlternativeRouteRef
import org.onebusaway.android.ui.tripresults.LegBadge
import org.onebusaway.android.ui.tripresults.RouteLegRef
import org.onebusaway.android.ui.tripresults.TransitMode

class InterleaveRouteItemsTest {

    private val route1 = RouteBadge("1 Line", 0xFF00AA00.toInt())
    private val route2 = RouteBadge("2 Line", 0xFF0000AA.toInt())

    @Test
    fun `interchangeable routes become one chronological sequence with their badges`() {
        val result = interleaveRouteItems(
            routes = listOf(route1 to listOf(3L, 12L), route2 to listOf(5L, 8L)),
            timeOf = { it }
        )

        assertEquals(listOf(3L, 5L, 8L, 12L), result.map { it.first })
        assertEquals(listOf(route1, route2, route2, route1), result.map { it.second })
    }

    @Test
    fun `equal arrival times keep planned route first`() {
        val result = interleaveRouteItems(
            routes = listOf(route1 to listOf(5L), route2 to listOf(5L)),
            timeOf = { it }
        )

        assertEquals(listOf(route1, route2), result.map { it.second })
    }

    @Test
    fun `same-named alternative does not erase planned route color`() {
        val planned = RouteBadge("A Line", 0xFF0066CC.toInt())
        val alternative = RouteBadge("A Line", 0xFFCC6600.toInt())
        val routeLeg = RouteLegRef(
            routeId = "1_planned",
            headsign = "Downtown",
            board = null,
            alight = null,
            alternatives = listOf(
                AlternativeRouteRef("1_alternative", "Downtown", alternative.shortName, alternative.routeColor)
            ),
            plannedBadge = planned,
            // The joined presentation deduplicates identical public names, so it cannot be used to
            // recover which route was planned.
            badge = LegBadge(listOf(alternative), TransitMode.RAIL, RouteBadgeJoin.ANY_OF)
        )

        assertEquals(planned, routeLeg.etaPlannedBadge("A Line"))
    }
}
