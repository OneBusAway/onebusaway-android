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
import org.onebusaway.android.api.contract.TripStatus
import org.onebusaway.android.models.isVehicleServingTrip

/**
 * The #1681 tracking gate: OBA marks `predicted:true` block-wide, so a stop served by a later trip in
 * a tracked vehicle's block inherits the flag. `isVehicleServingTrip` / `ArrivalData.isTracked` narrow
 * "predicted" to "the vehicle is on *this* trip". This lives at the wire→model seam, so it's tested
 * here rather than in the display projection ([ArrivalInfo]).
 */
class ArrivalTrackingTest {

    @Test
    fun `isVehicleServingTrip is strict — only the named active trip counts`() {
        assertTrue(isVehicleServingTrip("1_a", "1_a"))
        assertFalse("a different trip in the block is not served", isVehicleServingTrip("1_a", "1_b"))
        // Absent/blank active trip (no status, older server) reads as not-serving — realtime is shown
        // only when the server explicitly names the active trip.
        assertFalse(isVehicleServingTrip("1_a", null))
        assertFalse(isVehicleServingTrip("1_a", ""))
    }

    private fun arrival(predicted: Boolean, tripId: String, activeTripId: String?) = ArrivalDeparture(
        tripId = tripId,
        predicted = predicted,
        tripStatus = activeTripId?.let { TripStatus(activeTripId = it) },
    )

    @Test
    fun `isTracked requires predicted AND the vehicle serving this trip`() {
        assertTrue(
            "predicted and on this trip",
            arrival(predicted = true, tripId = "1_a", activeTripId = "1_a").asArrivalData().isTracked
        )
        assertFalse(
            "predicted but serving another trip in the block (#1681)",
            arrival(predicted = true, tripId = "1_a", activeTripId = "1_b").asArrivalData().isTracked
        )
        assertFalse(
            "predicted but no trip status at all",
            arrival(predicted = true, tripId = "1_a", activeTripId = null).asArrivalData().isTracked
        )
        assertFalse(
            "on this trip but not predicted",
            arrival(predicted = false, tripId = "1_a", activeTripId = "1_a").asArrivalData().isTracked
        )
    }
}
