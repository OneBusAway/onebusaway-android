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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.Frequency
import org.onebusaway.android.api.contract.Position
import org.onebusaway.android.api.contract.TripStatus

/**
 * The wire→domain decisions in `ArrivalAdapters`:
 *  - [DtoArrivalData.hasPlottableVehicle] — the "the map can draw a vehicle for THIS trip right now"
 *    predicate behind the ETA pill's "on the map" pin (#1992). It must mirror the map's own draw
 *    condition: the trip status's active trip is this arrival's trip AND it carries a location.
 *  - [predictedServerTimeOrNull] — decoding the server's no-prediction sentinel in both the spellings
 *    it reaches us in.
 */
class ArrivalAdaptersTest {

    private fun arrival(tripId: String = "trip", tripStatus: TripStatus? = null) = ArrivalDeparture(routeId = "route", tripId = tripId, stopId = "stop", tripStatus = tripStatus)
        .asArrivalData(directionId = null)
        .let(::requireNotNull)

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

    // ---- predictedServerTimeOrNull: the no-prediction sentinel -----------------------------------
    //
    // OBA's TimepointPredictionRecord defaults both predicted fields to -1 ("the feed gave no
    // prediction here"). It reaches the client two ways, and both must decode to "no prediction".

    @Test
    fun `the bare sentinel decodes to no prediction`() {
        assertNull(predictedServerTimeOrNull(-1L, scheduledDwellMs = 0L))
        assertNull(predictedServerTimeOrNull(0L, scheduledDwellMs = 0L))
    }

    /**
     * The live 1 Line record that motivated this: stop `40_99603`, trip `..._100479_1058`, a platform
     * with a 30 s scheduled dwell. The server ran `arrivalTime + slack * 1000` on the -1 sentinel and
     * sent `29999` for *both* predicted fields — positive, so the old non-positive check passed it, and
     * as an epoch it is half a minute past 1970. Rendered ETA: -496029 hours.
     */
    @Test
    fun `the sentinel with slack added decodes to no prediction`() {
        val dwell = 30_000L // scheduledDepartureTime - scheduledArrivalTime, i.e. the server's slackTime

        assertNull(predictedServerTimeOrNull(29_999L, scheduledDwellMs = dwell))
    }

    /** The same bug at any other dwell — the artifact tracks the stop's slack, it is not one constant. */
    @Test
    fun `the sentinel with slack added is decoded at any dwell`() {
        assertNull(predictedServerTimeOrNull(59_999L, scheduledDwellMs = 60_000L))
        assertNull(predictedServerTimeOrNull(89_999L, scheduledDwellMs = 90_000L))
        // A stop that doesn't dwell reduces to the bare sentinel, which is why this hid for so long.
        assertNull(predictedServerTimeOrNull(-1L, scheduledDwellMs = 0L))
    }

    /**
     * The artifact is only the artifact *for its own stop*: `29999` at a stop with a 60 s dwell is not
     * what that stop's slack arithmetic would have produced. Reconstruction is exact, so this is a real
     * (if absurd) instant rather than a value in a suspicious range — the check has no magnitude band.
     */
    @Test
    fun `a value matching another stops artifact is not discarded`() {
        assertEquals(29_999L, predictedServerTimeOrNull(29_999L, scheduledDwellMs = 60_000L)?.epochMs)
    }

    @Test
    fun `a real prediction is kept`() {
        // The healthy sibling of the record above, from the same response.
        assertEquals(
            1_785_706_260_000L,
            predictedServerTimeOrNull(1_785_706_260_000L, scheduledDwellMs = 30_000L)?.epochMs
        )
    }

    @Test
    fun `a frequency row whose scheduled times were collapsed onto the sentinel is discarded`() {
        val artifact = 29_999L
        val data = ArrivalDeparture(
            routeId = "route",
            tripId = "trip",
            stopId = "stop",
            predicted = true,
            scheduledArrivalTime = artifact,
            predictedArrivalTime = artifact,
            scheduledDepartureTime = artifact,
            predictedDepartureTime = artifact,
            frequency = Frequency(
                startTime = 1_785_706_000_000L,
                endTime = 1_785_709_600_000L,
                headway = 600L
            )
        ).asArrivalData(directionId = null)

        assertNull(data)
    }

    @Test
    fun `a frequency row collapsed onto the bare sentinel is discarded`() {
        val data = ArrivalDeparture(
            routeId = "route",
            tripId = "trip",
            stopId = "stop",
            predicted = true,
            scheduledArrivalTime = -1L,
            predictedArrivalTime = -1L,
            scheduledDepartureTime = -1L,
            predictedDepartureTime = -1L,
            frequency = Frequency(
                startTime = 1_785_706_000_000L,
                endTime = 1_785_709_600_000L,
                headway = 600L
            )
        ).asArrivalData(directionId = null)

        assertNull(data)
    }

    @Test
    fun `a real frequency prediction with collapsed scheduled times is kept`() {
        val prediction = 1_785_706_260_123L
        val data = ArrivalDeparture(
            routeId = "route",
            tripId = "trip",
            stopId = "stop",
            predicted = true,
            scheduledArrivalTime = prediction,
            predictedArrivalTime = prediction,
            scheduledDepartureTime = prediction,
            predictedDepartureTime = prediction,
            frequency = Frequency(
                startTime = 1_785_706_000_000L,
                endTime = 1_785_709_600_000L,
                headway = 600L
            )
        ).asArrivalData(directionId = null)

        assertEquals(prediction, data?.predictedArrivalTime?.epochMs)
        assertEquals(prediction, data?.predictedDepartureTime?.epochMs)
    }
}
