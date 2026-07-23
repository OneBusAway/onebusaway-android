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
package org.onebusaway.android.api.adapters

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.Position
import org.onebusaway.android.api.contract.TripStatus

/**
 * [DtoArrivalData.hasPlottableVehicle] — the "the map can draw a vehicle for THIS trip right now" predicate
 * behind the ETA pill's "on the map" pin (#1992). It must mirror the map's own draw condition: the trip
 * status's active trip is this arrival's trip AND it carries a location (last-known or current position).
 */
class ArrivalAdaptersTest {

    private fun arrival(tripId: String = "trip", tripStatus: TripStatus? = null) = ArrivalDeparture(routeId = "route", tripId = tripId, stopId = "stop", tripStatus = tripStatus)
        .asArrivalData(directionId = null)

    @Test
    fun `no trip status is not plottable`() {
        assertFalse(arrival(tripStatus = null).hasPlottableVehicle)
    }

    @Test
    fun `active on this trip with a last-known location is plottable`() {
        val status = TripStatus(activeTripId = "trip", lastKnownLocation = Position(47.6, -122.3))
        assertTrue(arrival(tripId = "trip", tripStatus = status).hasPlottableVehicle)
    }

    @Test
    fun `active on this trip with only a current position is plottable`() {
        val status = TripStatus(activeTripId = "trip", position = Position(47.6, -122.3))
        assertTrue(arrival(tripId = "trip", tripStatus = status).hasPlottableVehicle)
    }

    @Test
    fun `vehicle upstream on an earlier block trip is not plottable for this trip`() {
        // The block's vehicle is still serving an earlier trip, so the map would draw a marker keyed to
        // that trip, not this one — tapping this pill wouldn't reframe, so it must be false here.
        val status = TripStatus(activeTripId = "earlier_trip", lastKnownLocation = Position(47.6, -122.3))
        assertFalse(arrival(tripId = "trip", tripStatus = status).hasPlottableVehicle)
    }

    @Test
    fun `active on this trip but with no location is not plottable`() {
        // A schedule-deviation-only status (no GPS) is real-time but has nothing to draw.
        val status = TripStatus(activeTripId = "trip", scheduleDeviation = 120L)
        assertFalse(arrival(tripId = "trip", tripStatus = status).hasPlottableVehicle)
    }
}
