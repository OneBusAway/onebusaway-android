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
import org.onebusaway.android.R
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
        hasPlottableVehicle: Boolean = false
    ): ArrivalData = FakeArrivalData(
        predicted = predicted,
        // Mirror the adapter's wire→domain mint: a non-positive predicted instant decodes to null.
        predictedArrivalTime = predictedArrivalTime.takeIf { it > 0L }?.let { ServerTime(it) },
        scheduledArrivalTime = ServerTime(scheduledArrivalTime),
        hasPlottableVehicle = hasPlottableVehicle
    )

    private fun infoFor(data: ArrivalData) = ArrivalInfo(
        context = null,
        data = data,
        now = now,
        includeArrivalDepartureInStatusLabel = false
    )

    /** A predicted arrival ~50min out (slightly later than [scheduledArrival]), shared by the
     *  liveEta tests below — they only care about eta/liveEta, not this scenario's specifics. */
    private val genuinePrediction
        get() = infoFor(arrival(predicted = true, predictedArrivalTime = 1_783_119_500_000L))

    @Test
    fun `predicted true with a minus-one sentinel falls back to the scheduled time`() {
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = -1L))

        assertFalse("a -1 predicted sentinel is not a real prediction", info.predicted)
        assertEquals("displayTime falls back to the scheduled instant", scheduledArrival, info.displayTime.epochMs)
        // ETA is scheduled - now (~50 min), never the ~ -29,718,596 min garbage.
        assertEquals(scheduledArrival / 60_000 - now.epochMs / 60_000, info.eta)
        assertTrue("ETA is a sane near-future value", info.eta in 0..120)
    }

    @Test
    fun `predicted true with a zero sentinel falls back to the scheduled time`() {
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = 0L))

        assertFalse("a 0 predicted sentinel is not a real prediction", info.predicted)
        assertEquals(scheduledArrival, info.displayTime.epochMs)
        assertEquals(scheduledArrival / 60_000 - now.epochMs / 60_000, info.eta)
        assertTrue("ETA is a sane near-future value", info.eta in 0..120)
    }

    @Test
    fun `a genuine positive prediction is still honored`() {
        val predictedArrival = 1_783_119_500_000L // slightly later than scheduled
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = predictedArrival))

        assertTrue(info.predicted)
        assertEquals(predictedArrival, info.displayTime.epochMs)
        assertEquals(predictedArrival / 60_000 - now.epochMs / 60_000, info.eta)
    }

    @Test
    fun `an unpredicted arrival uses the scheduled time`() {
        val info = infoFor(arrival(predicted = false, predictedArrivalTime = 0L))

        assertFalse(info.predicted)
        assertEquals(scheduledArrival, info.displayTime.epochMs)
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

    @Test
    fun `vehicleOnMap is true only when a genuine prediction has a plottable vehicle (#1992)`() {
        // The pin is a strict refinement of the rss cue: a drawable vehicle AND a real prediction.
        assertTrue(
            "genuine prediction + plottable vehicle shows the pin",
            infoFor(arrival(predicted = true, predictedArrivalTime = 1_783_119_500_000L, hasPlottableVehicle = true))
                .vehicleOnMap
        )
        assertFalse(
            "no plottable vehicle stays on the rss glyph",
            infoFor(arrival(predicted = true, predictedArrivalTime = 1_783_119_500_000L, hasPlottableVehicle = false))
                .vehicleOnMap
        )
    }

    @Test
    fun `vehicleOnMap is false when the prediction is suppressed even with a plottable vehicle (#1992)`() {
        // A closed-stop -1 sentinel normalizes predicted to false, so the pin can't outlive the rss cue
        // it refines — no indicator at all here, not a pin.
        assertFalse(
            infoFor(arrival(predicted = true, predictedArrivalTime = -1L, hasPlottableVehicle = true)).vehicleOnMap
        )
    }

    @Test
    fun `liveEta matches eta at the constructor's own now (issue #1781)`() {
        val info = genuinePrediction

        assertEquals(info.eta, info.liveEta(now))
    }

    @Test
    fun `liveEta counts down as the ticking now advances past a minute boundary`() {
        val info = genuinePrediction

        assertEquals(info.eta - 1, info.liveEta(ServerTime(now.epochMs + 60_000L)))
    }

    @Test
    fun `liveEta does not move within the same minute as now`() {
        val info = genuinePrediction

        assertEquals(info.eta, info.liveEta(ServerTime(now.epochMs + 30_000L)))
    }

    // --- Schedule-deviation color, end to end through the arrivals path (#2043) -------------------
    //
    // The band itself is pinned in ScheduleDeviationTest; these assert that ArrivalInfo actually
    // *reaches* it at full precision. The old code floored each instant to whole minutes and
    // subtracted, so a sub-minute deviation that straddled a minute boundary was reported as a full
    // minute late — which is why a 60 s-late bus could never render on-time green.

    /** An arrival [deviationSeconds] off its scheduled time, real-time unless told otherwise. */
    private fun deviating(deviationSeconds: Long, predicted: Boolean = true) = infoFor(
        arrival(
            predicted = predicted,
            predictedArrivalTime = scheduledArrival + deviationSeconds * 1_000L
        )
    )

    @Test
    fun `a deviation just inside the band renders on time`() {
        assertEquals(R.color.stop_info_ontime, deviating(-89).color)
        assertEquals(R.color.stop_info_ontime, deviating(89).color)
    }

    @Test
    fun `a deviation just outside the band renders early or late`() {
        assertEquals(R.color.stop_info_early, deviating(-91).color)
        assertEquals(R.color.stop_info_delayed, deviating(91).color)
    }

    /**
     * The headline acceptance case for #2043. A minute late used to land on the late color because
     * the two epoch-minute floors differed by one; it is now comfortably inside the band.
     */
    @Test
    fun `a bus one minute late is on time and a bus two minutes late is not`() {
        assertEquals(R.color.stop_info_ontime, deviating(60).color)
        assertEquals(R.color.stop_info_delayed, deviating(120).color)
    }

    /**
     * A deviation straddling a minute boundary: the predicted instant lands in the next epoch-minute
     * from the scheduled one despite being only a second late. Flooring first made this "late".
     */
    @Test
    fun `a one-second deviation across a minute boundary is still on time`() {
        val scheduled = 1_783_119_119_000L // :59 within its epoch-minute
        val info = infoFor(
            arrival(
                predicted = true,
                predictedArrivalTime = scheduled + 1_000L, // 1 s later, but the next epoch-minute
                scheduledArrivalTime = scheduled
            )
        )

        assertEquals(R.color.stop_info_ontime, info.color)
    }

    @Test
    fun `without a prediction the color is scheduled regardless of deviation`() {
        assertEquals(R.color.stop_info_scheduled_time, deviating(600, predicted = false).color)
        assertEquals(R.color.stop_info_scheduled_time, deviating(-600, predicted = false).color)
    }

    /** The pill/badge tier tracks the same state, so the two can't disagree about what's on time. */
    @Test
    fun `the fill color tracks the same state as the foreground color`() {
        assertEquals(R.color.stop_info_ontime_fill, deviating(89).fillColor)
        assertEquals(R.color.stop_info_delayed_fill, deviating(91).fillColor)
        assertEquals(R.color.stop_info_early_fill, deviating(-91).fillColor)
        assertEquals(R.color.stop_info_scheduled_fill, deviating(600, predicted = false).fillColor)
    }

    // --- The timetable time behind displayTime (#2167) --------------------------------------------
    //
    // The clock surfaces print the correction — the struck-through scheduled time over the expected
    // one — so scheduledTime has to be the *same* arrival-vs-departure choice displayTime made, and
    // has to survive a prediction rather than being overwritten by it.

    @Test
    fun `scheduledTime keeps the timetable time a prediction moved`() {
        val info = infoFor(arrival(predicted = true, predictedArrivalTime = 1_783_119_500_000L))

        assertEquals(scheduledArrival, info.scheduledTime.epochMs)
        assertEquals(1_783_119_500_000L, info.displayTime.epochMs)
    }

    @Test
    fun `scheduledTime equals displayTime when there is no usable prediction`() {
        // Including the closed-stop sentinel, which normalizes to no prediction: the two agree, so
        // nothing is struck through.
        assertEquals(
            infoFor(arrival(predicted = false, predictedArrivalTime = 0L)).displayTime,
            infoFor(arrival(predicted = false, predictedArrivalTime = 0L)).scheduledTime
        )
        val suppressed = infoFor(arrival(predicted = true, predictedArrivalTime = -1L))
        assertEquals(suppressed.displayTime, suppressed.scheduledTime)
    }

    @Test
    fun `at the first stop scheduledTime is the scheduled departure`() {
        // stopSequence 0 makes this a departure, and displayTime follows the departure pair — the
        // timetable time has to follow it there, not stay on the (unused) arrival time.
        val info = infoFor(
            FakeArrivalData(
                predicted = true,
                predictedArrivalTime = ServerTime(scheduledArrival),
                scheduledArrivalTime = ServerTime(scheduledArrival),
                stopSequence = 0,
                scheduledDepartureTime = ServerTime(scheduledArrival + 120_000L),
                predictedDepartureTime = ServerTime(scheduledArrival + 300_000L)
            )
        )

        assertEquals(scheduledArrival + 120_000L, info.scheduledTime.epochMs)
        assertEquals(scheduledArrival + 300_000L, info.displayTime.epochMs)
    }
}

/** Minimal [ArrivalData] stub; only the arrival-time fields matter for these ETA assertions. */
private data class FakeArrivalData(
    override val predicted: Boolean,
    override val predictedArrivalTime: ServerTime?,
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
    override val predictedDepartureTime: ServerTime? = null,
    override val status: Status? = null,
    override val frequency: FrequencyWindow? = null,
    override val situationIds: List<String> = emptyList(),
    override val historicalOccupancy: Occupancy? = null,
    override val predictedOccupancy: Occupancy? = null,
    override val hasTripStatus: Boolean = false,
    override val scheduleDeviation: Long = 0L,
    override val lastKnownLat: Double? = null,
    override val lastKnownLon: Double? = null,
    override val hasPlottableVehicle: Boolean = false
) : ArrivalData
