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
package org.onebusaway.android.api.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.contract.StopGroup
import org.onebusaway.android.api.contract.StopGroupName
import org.onebusaway.android.api.contract.StopGrouping
import org.onebusaway.android.models.RouteMapDirection

/** [directionsFrom] — building the route's selectable directions + stop membership from stop groups. */
class MapDataSourceDirectionsTest {

    private fun group(id: String?, names: List<String> = emptyList(), stopIds: List<String> = emptyList()) =
        StopGroup(id = id, name = StopGroupName(names), stopIds = stopIds)

    private fun grouping(vararg groups: StopGroup) = StopGrouping(groups.toList())

    private fun directionsOf(groupings: List<StopGrouping>) = directionsFrom(groupings).directions

    @Test
    fun numericGroupsBecomeDirectionsWithHeadsigns() {
        val result = directionsOf(listOf(grouping(group("0", listOf("to Downtown")), group("1", listOf("to Northgate")))))
        assertEquals(
            listOf(RouteMapDirection(0, "to Downtown"), RouteMapDirection(1, "to Northgate")),
            result,
        )
    }

    @Test
    fun nonNumericGroupIdsAreDropped() {
        val result = directionsOf(listOf(grouping(group("loop", listOf("Loop")), group("0", listOf("to Downtown")))))
        assertEquals(listOf(RouteMapDirection(0, "to Downtown")), result)
    }

    @Test
    fun nullGroupIdsAreDropped() {
        val result = directionsOf(listOf(grouping(group(null, listOf("Unnamed")), group("1", listOf("to Northgate")))))
        assertEquals(listOf(RouteMapDirection(1, "to Northgate")), result)
    }

    @Test
    fun duplicateIdsKeepTheFirstAndAreSorted() {
        // Same id in two groups (across groupings): first wins; results sorted by id.
        val result = directionsOf(
            listOf(
                grouping(group("1", listOf("to Northgate")), group("0", listOf("to Downtown"))),
                grouping(group("1", listOf("to Northgate Alt"))),
            )
        )
        assertEquals(
            listOf(RouteMapDirection(0, "to Downtown"), RouteMapDirection(1, "to Northgate")),
            result,
        )
    }

    @Test
    fun blankOrAbsentDisplayNameYieldsEmptyLabel() {
        val result = directionsOf(listOf(grouping(group("0"), group("1", listOf("")))))
        assertEquals(listOf(RouteMapDirection(0, ""), RouteMapDirection(1, "")), result)
    }

    @Test
    fun emptyGroupingsYieldNoDirections() {
        assertEquals(emptyList<RouteMapDirection>(), directionsOf(emptyList()))
    }

    @Test
    fun directionsByStopTagsEachStopAndSharesBothForOverlaps() {
        val result = directionsFrom(
            listOf(
                grouping(
                    group("0", listOf("to Downtown"), stopIds = listOf("a", "shared")),
                    group("1", listOf("to Northgate"), stopIds = listOf("b", "shared")),
                )
            )
        )
        assertEquals(setOf(0), result.directionsByStop["a"])
        assertEquals(setOf(1), result.directionsByStop["b"])
        assertEquals(setOf(0, 1), result.directionsByStop["shared"])
        assertEquals(null, result.directionsByStop["absent"])
    }
}
