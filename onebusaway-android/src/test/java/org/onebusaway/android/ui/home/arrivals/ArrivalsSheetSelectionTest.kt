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
package org.onebusaway.android.ui.home.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.ui.arrivals.routeRowKey

class ArrivalsSheetSelectionTest {

    @Test
    fun routeFocus_selectsRowMatchingResolvedDirection() {
        val header = RouteHeader(
            loading = false,
            shortName = "8",
            longName = "",
            agency = "Metro",
            routeId = "route-8",
            directions = listOf(
                RouteMapDirection(0, "Downtown"),
                RouteMapDirection(1, "Northgate"),
            ),
            currentDirectionId = 1,
        )

        assertEquals(routeRowKey("route-8", "Northgate"), header.selectedArrivalRowKey())
    }

    @Test
    fun routeFocus_withoutResolvedDirection_selectsNoRow() {
        val header = RouteHeader(
            loading = true,
            shortName = "",
            longName = "",
            agency = "",
            routeId = "route-8",
        )

        assertNull(header.selectedArrivalRowKey())
    }
}
