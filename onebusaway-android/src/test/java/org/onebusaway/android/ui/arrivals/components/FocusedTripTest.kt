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
package org.onebusaway.android.ui.arrivals.components

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.ui.arrivals.RouteRowGroup

/**
 * Which trip a row's "Show trip status" button opens (#2428). The button's presence and its click are
 * covered on-device by `RouteArrivalRowTripStatusTest`; this pins the selection rule it draws from.
 */
class FocusedTripTest {

    private val soonest = previewArrival("40", "Northgate", etaMinutes = 3, tripId = "trip_1")
    private val later = previewArrival("40", "Northgate", etaMinutes = 11, tripId = "trip_2")
    private val group = RouteRowGroup(listOf(soonest, later))

    @Test
    fun `the drilled-into trip of the selected row is offered`() {
        assertSame(later, group.focusedTrip(selected = true, selectedTripId = "trip_2"))
    }

    @Test
    fun `a scheduled-only trip is offered too`() {
        // No live vehicle: the pill tap still drills into the trip, so trip status is still one tap away.
        val scheduled = previewArrival("40", "Northgate", etaMinutes = 24, predicted = false, tripId = "trip_3")
        assertSame(scheduled, RouteRowGroup(listOf(scheduled)).focusedTrip(selected = true, selectedTripId = "trip_3"))
    }

    @Test
    fun `an unselected row offers nothing, even holding the trip id`() {
        // The id is one map-wide value; only the selected row may act on it.
        assertNull(group.focusedTrip(selected = false, selectedTripId = "trip_2"))
    }

    @Test
    fun `a selected row with no trip drilled into offers nothing`() {
        // A row-body tap selects the route without naming a trip.
        assertNull(group.focusedTrip(selected = true, selectedTripId = null))
    }

    @Test
    fun `a trip the row no longer holds offers nothing`() {
        // E.g. the trip has dropped out of the stop's window since it was tapped.
        assertNull(group.focusedTrip(selected = true, selectedTripId = "gone"))
    }
}
