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
package org.onebusaway.android.ui.mylists

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure merge/filter helpers behind the search-box recents dropdown. */
class SearchRecentsTest {

    private fun stop(id: String, name: String, accessTime: Long?) = RecentItem.Stop(
        StopListItem(
            id = id,
            name = name,
            rawDirection = "N",
            directionText = "Northbound",
            lat = 0.0,
            lon = 0.0,
            isFavorite = false
        ),
        accessTime
    )

    private fun route(id: String, shortName: String, longName: String?, accessTime: Long?) = RecentItem.Route(RouteListItem(id = id, shortName = shortName, longName = longName, url = null), accessTime)

    // --- mergeRecents ---

    @Test
    fun `merge interleaves stops and routes strictly by accessTime descending`() {
        val stops = listOf(stop("s1", "Alpha", 100), stop("s2", "Beta", 300))
        val routes = listOf(route("r1", "8", "Rainier", 200), route("r2", "40", "Ballard", 400))

        val merged = mergeRecents(stops, routes, limit = 10)

        // Sorted purely by accessTime desc: r2@400, s2@300, r1@200, s1@100.
        assertEquals(listOf("route:r2", "stop:s2", "route:r1", "stop:s1"), merged.map { it.key })
    }

    @Test
    fun `merge sorts null accessTime last`() {
        val stops = listOf(stop("s1", "Alpha", null))
        val routes = listOf(route("r1", "8", "Rainier", 50))

        val merged = mergeRecents(stops, routes, limit = 10)

        assertEquals(listOf("route:r1", "stop:s1"), merged.map { it.key })
    }

    @Test
    fun `merge respects the limit, keeping the newest`() {
        val stops = listOf(stop("s1", "Alpha", 100), stop("s2", "Beta", 500))
        val routes = listOf(route("r1", "8", "Rainier", 300))

        val merged = mergeRecents(stops, routes, limit = 2)

        assertEquals(listOf("stop:s2", "route:r1"), merged.map { it.key })
    }

    @Test
    fun `merge is stable for equal timestamps - stops keep their order before routes`() {
        val stops = listOf(stop("s1", "Alpha", 100), stop("s2", "Beta", 100))
        val routes = listOf(route("r1", "8", "Rainier", 100))

        val merged = mergeRecents(stops, routes, limit = 10)

        // sortedByDescending is stable, and stops are concatenated first.
        assertEquals(listOf("stop:s1", "stop:s2", "route:r1"), merged.map { it.key })
    }

    // --- filterRecents ---

    private val sample = listOf(
        stop("s1", "Broadway & E Denny Way", 400),
        route("r1", "8", "Rainier Beach", 300),
        stop("s2", "3rd Ave & Pine St", 200),
        route("r2", "40", "Downtown Ballard", 100)
    )

    @Test
    fun `filter with a blank query passes the list through unchanged`() {
        assertEquals(sample, filterRecents(sample, "   "))
    }

    @Test
    fun `filter matches stop names case-insensitively`() {
        assertEquals(listOf("stop:s1"), filterRecents(sample, "broadway").map { it.key })
    }

    @Test
    fun `filter matches route short name and long name`() {
        assertEquals(listOf("route:r1"), filterRecents(sample, "8").map { it.key })
        assertEquals(listOf("route:r2"), filterRecents(sample, "ballard").map { it.key })
    }

    @Test
    fun `filter returns empty when nothing matches`() {
        assertEquals(emptyList<String>(), filterRecents(sample, "zzz").map { it.key })
    }
}
