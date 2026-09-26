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

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteLineColors
import org.onebusaway.android.ui.tripplan.TripDay

/**
 * JVM tests for [rowDays] — which trip-log times name their day (#2337). A trip on another day used to
 * read as bare clock times, indistinguishable from one leaving within the hour.
 */
class TripDaysTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 9, 24)
    private val tomorrow = today.plusDays(1)

    private val colors = RouteLineColors(Color.Gray, Color.White)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = ServerTime(LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli())

    private fun trip(start: ServerTime, board: ServerTime, exit: ServerTime, arrive: ServerTime) = listOf(
        TripLogEntry.Terminal(TerminalKind.START, start, "Renton Technical College"),
        TripLogEntry.Transit(
            routeShortName = "111",
            routeDisplayName = "111",
            mode = TransitMode.BUS,
            routeColorHex = null,
            headsign = "Seattle",
            reachStop = ReachStop.OnArrival(board),
            boardTime = board,
            exitTime = exit,
            durationMinutes = 24,
            rideEvents = listOf(RideEvent.Stop(LogStop("Intermediate"))),
            routeLeg = RouteLegRef("1_111", "Seattle", null, null)
        ),
        TripLogEntry.Terminal(TerminalKind.ARRIVE, arrive, "Exhibition Hall")
    )

    private fun days(entries: List<TripLogEntry>, expanded: Set<Int> = emptySet()): List<Pair<String?, LocalDate>> {
        val rows = flattenLog(entries, expanded, colors) { colors }
        val days = rowDays(rows, today, zone)
        return rows.filter { it.key in days }.map { it.content::class.simpleName to days.getValue(it.key) }
    }

    @Test
    fun aTripLaterTodayNamesNoDay() {
        val entries = trip(at(today, 9, 15), at(today, 9, 23), at(today, 9, 47), at(today, 9, 55))
        assertTrue(days(entries).isEmpty())
    }

    @Test
    fun aTripTomorrowNamesItsDayOnce_atTheStart() {
        val entries = trip(at(tomorrow, 9, 15), at(tomorrow, 9, 23), at(tomorrow, 9, 47), at(tomorrow, 9, 55))
        assertEquals(listOf("Terminal" to tomorrow), days(entries))
    }

    /** The screenshot in #2337: a trip whose day has already passed is not today's either. */
    @Test
    fun aTripOnAnEarlierDayNamesThatDay() {
        val earlier = today.minusDays(2)
        val entries = trip(at(earlier, 9, 15), at(earlier, 9, 23), at(earlier, 9, 47), at(earlier, 9, 55))
        assertEquals(listOf("Terminal" to earlier), days(entries))
    }

    @Test
    fun aTripPastMidnightNamesTheNewDayWhereItBegins() {
        val entries = trip(at(today, 23, 30), at(today, 23, 45), at(tomorrow, 0, 10), at(tomorrow, 0, 20))
        assertEquals(listOf("ExitNode" to tomorrow), days(entries))
    }

    /** Expanding a ride inserts untimed stop rows, which must not disturb which times name a day. */
    @Test
    fun expandingALegDoesNotMoveTheDay() {
        val entries = trip(at(tomorrow, 9, 15), at(tomorrow, 9, 23), at(tomorrow, 9, 47), at(tomorrow, 9, 55))
        assertEquals(days(entries), days(entries, expanded = setOf(1)))
    }

    @Test
    fun theDayIsTheDeviceZonesCalendarDay() {
        // 23:30 in Los Angeles is already the next day in UTC; the day named is the local one.
        assertEquals(today, at(today, 23, 30).localDate(zone))
    }

    @Test
    fun tripDayNamesTodayAndTomorrowAndNothingElse() {
        assertEquals(TripDay.TODAY, TripDay.of(today, today))
        assertEquals(TripDay.TOMORROW, TripDay.of(tomorrow, today))
        assertEquals(TripDay.OTHER, TripDay.of(today.minusDays(1), today))
        assertEquals(TripDay.OTHER, TripDay.of(today.plusDays(2), today))
    }
}
