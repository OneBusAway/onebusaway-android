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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime

/**
 * JVM unit tests for [ArrivalInfo]'s numeric ETA/display projection (issue #1687).
 *
 * A closed stop keeps `predicted:true` but suppresses the near-term prediction to a non-positive
 * sentinel (observed `-1` and `0`). The pre-#1687 logic keyed the ETA off the `predicted` boolean
 * alone, so it subtracted a ~0 timestamp from "now" and rendered a garbage `~ -29,718,596 min` ETA
 * (and a bogus "early" status for `-1` / a "Scheduled" label with a garbage time for `0`).
 *
 * `context` is null here so the resource-backed status/time strings collapse to "" — this exercises
 * the pure `eta`/`displayTime`/`predicted` computation, which is where the bug lived.
 */
class ArrivalInfoTest {

    /** ~2026-04-13, matching the issue's `currentTime ≈ 1783116081756`. */
    private val now = ServerTime(1_783_116_081_756L)

    /** A valid scheduled arrival ~50 min out, mirroring the issue's `scheduledArrivalTime`. */
    private val scheduledArrival = 1_783_119_110_000L

    private fun arrival(
        predicted: Boolean,
        predictedArrivalTime: Long,
        scheduledArrivalTime: Long = scheduledArrival,
    ): ArrivalData = FakeArrivalData(
        predicted = predicted,
        predictedArrivalTime = ServerTime(predictedArrivalTime),
        scheduledArrivalTime = ServerTime(scheduledArrivalTime),
    )

    private fun infoFor(data: ArrivalData) = ArrivalInfo(
        context = null,
        data = data,
        now = now,
        includeArrivalDepartureInStatusLabel = false,
        favorite = false,
    )

    @Test
    fun `predicted true with a minus-one sentinel falls back to the scheduled time`() {
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = -1L))

        assertFalse("a -1 predicted sentinel is not a real prediction", info.predicted)
        assertEquals("displayTime falls back to the scheduled instant", scheduledArrival, info.displayTime)
        // ETA is scheduled - now (~50 min), never the ~ -29,718,596 min garbage.
        assertEquals(scheduledArrival / 60_000 - now.epochMs / 60_000, info.eta)
        assertTrue("ETA is a sane near-future value", info.eta in 0..120)
    }

    @Test
    fun `predicted true with a zero sentinel falls back to the scheduled time`() {
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = 0L))

        assertFalse("a 0 predicted sentinel is not a real prediction", info.predicted)
        assertEquals(scheduledArrival, info.displayTime)
        assertEquals(scheduledArrival / 60_000 - now.epochMs / 60_000, info.eta)
        assertTrue("ETA is a sane near-future value", info.eta in 0..120)
    }

    @Test
    fun `a genuine positive prediction is still honored`() {
        val predictedArrival = 1_783_119_500_000L // slightly later than scheduled
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = predictedArrival))

        assertTrue(info.predicted)
        assertEquals(predictedArrival, info.displayTime)
        assertEquals(predictedArrival / 60_000 - now.epochMs / 60_000, info.eta)
    }

    @Test
    fun `an unpredicted arrival uses the scheduled time`() {
        val info = infoFor(arrival(predicted = false, predictedArrivalTime = 0L))

        assertFalse(info.predicted)
        assertEquals(scheduledArrival, info.displayTime)
        assertEquals(scheduledArrival / 60_000 - now.epochMs / 60_000, info.eta)
    }

    @Test
    fun `report context carries the normalized predicted flag for a suppressed prediction`() {
        // Closed stop: server keeps predicted:true but suppresses the instant to a sentinel. The
        // report builder branches on TripReportContext.predicted to pick predicted vs scheduled
        // times, so it must see false here — otherwise it formats Date(0) as a 1969 garbage time.
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = -1L))

        assertFalse("suppressed prediction is not real-time in the report", info.toTripReportContext().predicted)
    }

    @Test
    fun `report context carries the normalized predicted flag for a genuine prediction`() {
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = 1_783_119_500_000L))

        assertTrue("a genuine prediction stays real-time in the report", info.toTripReportContext().predicted)
    }
}

/** Minimal [ArrivalData] stub; only the arrival-time fields matter for these ETA assertions. */
private data class FakeArrivalData(
    override val predicted: Boolean,
    override val predictedArrivalTime: ServerTime,
    override val scheduledArrivalTime: ServerTime,
    override val routeId: String = "1_100",
    override val tripId: String = "1_trip",
    override val stopId: String = "1_82673",
    override val headsign: String? = "Downtown",
    override val shortName: String? = "230",
    override val routeLongName: String? = null,
    override val stopSequence: Int = 5,
    override val serviceDate: Long = 0L,
    override val vehicleId: String? = null,
    override val scheduledDepartureTime: ServerTime = ServerTime(0L),
    override val predictedDepartureTime: ServerTime = ServerTime(0L),
    override val status: Status? = null,
    override val frequency: FrequencyWindow? = null,
    override val situationIds: List<String> = emptyList(),
    override val historicalOccupancy: Occupancy? = null,
    override val predictedOccupancy: Occupancy? = null,
    override val hasTripStatus: Boolean = false,
    override val scheduleDeviation: Long = 0L,
    override val lastKnownLat: Double? = null,
    override val lastKnownLon: Double? = null,
) : ArrivalData
