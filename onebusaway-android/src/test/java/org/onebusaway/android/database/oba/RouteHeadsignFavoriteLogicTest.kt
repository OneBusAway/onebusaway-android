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
package org.onebusaway.android.database.oba

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The legacy route/headsign favorite precedence, ported to a pure in-memory resolver. */
class RouteHeadsignFavoriteLogicTest {

    private fun row(routeId: String, headsign: String, stopId: String, exclude: Int) =
        RouteHeadsignFavoriteRecord(routeId = routeId, headsign = headsign, stopId = stopId, exclude = exclude)

    @Test
    fun `no rows is not a favorite`() {
        assertFalse(computeRouteHeadsignFavorite(emptyList(), "r1", "Downtown", "s1"))
    }

    @Test
    fun `favorited for this exact stop`() {
        val rows = listOf(row("r1", "Downtown", "s1", 0))
        assertTrue(computeRouteHeadsignFavorite(rows, "r1", "Downtown", "s1"))
    }

    @Test
    fun `favorited for all stops applies to any stop`() {
        val rows = listOf(row("r1", "Downtown", ALL_STOPS_FAVORITE, 0))
        assertTrue(computeRouteHeadsignFavorite(rows, "r1", "Downtown", "s1"))
        assertTrue(computeRouteHeadsignFavorite(rows, "r1", "Downtown", "s2"))
    }

    @Test
    fun `all-stops favorite excluded at this stop is not a favorite here`() {
        val rows = listOf(
            row("r1", "Downtown", ALL_STOPS_FAVORITE, 0),
            row("r1", "Downtown", "s1", 1), // exclusion for s1
        )
        assertFalse(computeRouteHeadsignFavorite(rows, "r1", "Downtown", "s1"))
        assertTrue(computeRouteHeadsignFavorite(rows, "r1", "Downtown", "s2"))
    }

    @Test
    fun `direct stop favorite wins over an exclusion row`() {
        val rows = listOf(
            row("r1", "Downtown", "s1", 0),
            row("r1", "Downtown", "s1", 1),
        )
        assertTrue(computeRouteHeadsignFavorite(rows, "r1", "Downtown", "s1"))
    }

    @Test
    fun `different route or headsign is not a favorite`() {
        val rows = listOf(row("r1", "Downtown", "s1", 0))
        assertFalse(computeRouteHeadsignFavorite(rows, "r2", "Downtown", "s1"))
        assertFalse(computeRouteHeadsignFavorite(rows, "r1", "Uptown", "s1"))
    }

    @Test
    fun `null headsign matches the empty-string headsign`() {
        val rows = listOf(row("r1", "", "s1", 0))
        assertTrue(computeRouteHeadsignFavorite(rows, "r1", null, "s1"))
    }
}
