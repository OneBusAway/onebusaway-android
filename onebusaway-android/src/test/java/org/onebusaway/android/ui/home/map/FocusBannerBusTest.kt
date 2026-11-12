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
package org.onebusaway.android.ui.home.map

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.data.serverTimeOrNever
import org.onebusaway.android.map.render.DataAgeUnit
import org.onebusaway.android.map.render.OccupancyBucket
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.ScheduleDeviation.Status

class FocusBannerBusTest {

    private val polledAt = ServerTime(1_800_000_000_000L)

    private fun bus(
        vehicleId: String? = "1_7213",
        isRealtime: Boolean = true,
        deviation: Duration = Duration.ZERO,
        occupancy: OccupancyBucket? = null
    ) = focusBannerBus(vehicleId, isRealtime, deviation, occupancy, ServerTime(polledAt.epochMs - 20_000L), polledAt)

    private fun FocusBannerBus.statusAndMinutes() = deviationStatus to deviationMinutes

    @Test
    fun `a bus without a prediction is scheduled whatever its deviation says`() {
        assertEquals(Status.SCHEDULED, bus(isRealtime = false, deviation = 5.minutes).deviationStatus)
    }

    @Test
    fun `the on-time band is ScheduleDeviation's, half-open like iOS`() {
        assertEquals(Status.ON_TIME, bus(deviation = 89.seconds).deviationStatus)
        assertEquals(Status.ON_TIME, bus(deviation = (-90).seconds).deviationStatus)
        assertEquals(Status.DELAYED to 2L, bus(deviation = 90.seconds).statusAndMinutes())
        assertEquals(Status.EARLY to 2L, bus(deviation = (-91).seconds).statusAndMinutes())
    }

    @Test
    fun `late and early carry rounded whole minutes, always positive`() {
        assertEquals(Status.DELAYED to 3L, bus(deviation = 3.minutes + 20.seconds).statusAndMinutes())
        assertEquals(Status.EARLY to 4L, bus(deviation = -(3.minutes + 40.seconds)).statusAndMinutes())
    }

    @Test
    fun `a vehicle never heard from has no report time rather than one in 1970`() {
        assertNull(serverTimeOrNever(0L))
        assertEquals(ServerTime(5L), serverTimeOrNever(5L))
    }

    @Test
    fun `the vehicle is named by its number, without the agency prefix`() {
        assertEquals("7213", bus().vehicleId)
    }

    @Test
    fun `only the first underscore separates the agency, as in OBA's id contract`() {
        assertEquals("bus_7213", bus(vehicleId = "1_bus_7213").vehicleId)
    }

    @Test
    fun `an id with no agency prefix is kept whole`() {
        assertEquals("7213", bus(vehicleId = "7213").vehicleId)
    }

    @Test
    fun `a blank vehicle id names no vehicle`() {
        assertNull(bus(vehicleId = " ").vehicleId)
        assertNull(bus(vehicleId = "1_").vehicleId)
        assertNull(bus(vehicleId = null).vehicleId)
    }

    @Test
    fun `fullness is its bucket, and absent when the feed reports none`() {
        assertEquals(OccupancyBucket.STANDING_ROOM, bus(occupancy = OccupancyBucket.STANDING_ROOM).occupancy)
        assertNull(bus().occupancy)
    }

    @Test
    fun `the data age reads in one whole unit, rounded down`() {
        assertEquals(DataAgeUnit.Seconds(0), DataAgeUnit.of((-5).seconds))
        assertEquals(DataAgeUnit.Seconds(59), DataAgeUnit.of(59.9.seconds))
        assertEquals(DataAgeUnit.Minutes(1), DataAgeUnit.of(60.seconds))
        assertEquals(DataAgeUnit.Minutes(59), DataAgeUnit.of(59.minutes + 59.seconds))
        assertEquals(DataAgeUnit.Hours(1), DataAgeUnit.of(60.minutes))
        assertEquals(DataAgeUnit.Hours(23), DataAgeUnit.of(23.hours + 59.minutes))
        assertNull(DataAgeUnit.of(24.hours))
    }
}
