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
import org.junit.Test
import org.onebusaway.android.ui.arrivals.routeRowKey
import org.onebusaway.android.ui.home.RouteLeg
import org.onebusaway.android.ui.home.StopRouteSelection

class ArrivalsSheetSelectionTest {

    @Test
    fun routeFocus_selectsRowByDirectionId() {
        val selection = StopRouteSelection(
            originHeadsign = null,
            legs = listOf(RouteLeg("route-8", "8", directionId = 1)),
        )

        assertEquals(routeRowKey("route-8", 1, null), selection.selectedArrivalRowKey())
    }

    @Test
    fun routeFocus_withoutDirectionId_fallsBackToOriginHeadsign() {
        val selection = StopRouteSelection(
            originHeadsign = "Northgate",
            legs = listOf(RouteLeg("route-8", "8")),
        )

        assertEquals(routeRowKey("route-8", "Northgate"), selection.selectedArrivalRowKey())
    }

    @Test
    fun continuation_keepsOriginalDrawerRowSelected() {
        val selection = StopRouteSelection(
            originHeadsign = "Downtown",
            legs = listOf(RouteLeg("route-65", "65"), RouteLeg("route-75", "75")),
        )

        assertEquals(
            routeRowKey("route-65", "Downtown"),
            selection.selectedArrivalRowKey(),
        )
    }
}
