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
package org.onebusaway.android.ui.tripresults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime

class ItineraryWinnersTest {

    private fun option(
        durationMinutes: Long,
        walkMeters: Double,
        departureMinutes: Long,
        arrivalMinutes: Long
    ) = ItineraryOption(
        symbols = emptyList(),
        durationMinutes = durationMinutes,
        startTime = ServerTime(departureMinutes * 60_000L),
        endTime = ServerTime(arrivalMinutes * 60_000L),
        walkDistanceMeters = walkMeters
    )

    @Test
    fun `shortest travel time is duration, not earliest arrival`() {
        val options = listOf(
            option(durationMinutes = 10, walkMeters = 500.0, departureMinutes = 30, arrivalMinutes = 40),
            option(durationMinutes = 20, walkMeters = 300.0, departureMinutes = 0, arrivalMinutes = 20)
        )

        val winners = itineraryWinnerCategories(options, ScheduleWinnerMode.EARLIEST_ARRIVAL)

        assertEquals(setOf(WinnerCategory.SHORTEST_TRAVEL_TIME), winners[0])
        assertEquals(
            setOf(WinnerCategory.LEAST_WALKING, WinnerCategory.EARLIEST_ARRIVAL),
            winners[1]
        )
    }

    @Test
    fun `ties award every best option while all-equal categories are suppressed`() {
        val options = listOf(
            option(20, 100.0, 0, 30),
            option(20, 200.0, 5, 30),
            option(30, 300.0, 10, 30)
        )

        val winners = itineraryWinnerCategories(options, ScheduleWinnerMode.EARLIEST_ARRIVAL)

        assertTrue(WinnerCategory.SHORTEST_TRAVEL_TIME in winners[0])
        assertTrue(WinnerCategory.SHORTEST_TRAVEL_TIME in winners[1])
        assertTrue(winners.none { WinnerCategory.EARLIEST_ARRIVAL in it })
        assertEquals(setOf(WinnerCategory.LEAST_WALKING, WinnerCategory.SHORTEST_TRAVEL_TIME), winners[0])
    }

    @Test
    fun `a single option has no winners`() {
        val winners = itineraryWinnerCategories(
            listOf(option(20, 0.0, 0, 20)),
            ScheduleWinnerMode.BOTH
        )

        assertEquals(listOf(emptySet<WinnerCategory>()), winners)
    }

    @Test
    fun `schedule mode chooses the useful endpoint`() {
        val options = listOf(
            option(20, 100.0, 0, 20),
            option(20, 100.0, 10, 30)
        )

        val leaveAt = itineraryWinnerCategories(options, ScheduleWinnerMode.EARLIEST_ARRIVAL)
        val arriveBy = itineraryWinnerCategories(options, ScheduleWinnerMode.LATEST_DEPARTURE)
        val restored = itineraryWinnerCategories(options, ScheduleWinnerMode.BOTH)

        assertEquals(setOf(WinnerCategory.EARLIEST_ARRIVAL), leaveAt[0])
        assertEquals(setOf(WinnerCategory.LATEST_DEPARTURE), arriveBy[1])
        assertEquals(setOf(WinnerCategory.EARLIEST_ARRIVAL), restored[0])
        assertEquals(setOf(WinnerCategory.LATEST_DEPARTURE), restored[1])
    }

    @Test
    fun `clock-time ties use the minute precision shown on the card`() {
        val options = listOf(
            option(20, 100.0, 0, 20).copy(endTime = ServerTime(20 * 60_000L + 1_000L)),
            option(20, 100.0, 0, 20).copy(endTime = ServerTime(20 * 60_000L + 59_000L)),
            option(20, 100.0, 0, 21)
        )

        val winners = itineraryWinnerCategories(options, ScheduleWinnerMode.EARLIEST_ARRIVAL)

        assertTrue(WinnerCategory.EARLIEST_ARRIVAL in winners[0])
        assertTrue(WinnerCategory.EARLIEST_ARRIVAL in winners[1])
        assertTrue(WinnerCategory.EARLIEST_ARRIVAL !in winners[2])
    }

    @Test
    fun `zero walking wins against positive distances`() {
        val options = listOf(
            option(20, 0.0, 0, 20),
            option(20, 250.0, 0, 20)
        )

        val winners = itineraryWinnerCategories(options, ScheduleWinnerMode.BOTH)

        assertEquals(setOf(WinnerCategory.LEAST_WALKING), winners[0])
        assertTrue(winners[1].isEmpty())
    }

    @Test
    fun `non-finite walking distance suppresses that category`() {
        val options = listOf(
            option(20, Double.NaN, 0, 20),
            option(30, 250.0, 0, 30)
        )

        val winners = itineraryWinnerCategories(options, ScheduleWinnerMode.EARLIEST_ARRIVAL)

        assertTrue(winners.none { WinnerCategory.LEAST_WALKING in it })
    }
}
