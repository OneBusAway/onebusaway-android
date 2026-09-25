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
package org.onebusaway.android.ui.ondemand

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingState

class OnDemandServicePresentationTest {

    private val weekdays = FlexCalendar(
        "5088_c_63",
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
        LocalDate.of(2025, 12, 1),
        LocalDate.of(2026, 12, 1),
        emptySet()
    )
    private val sunday = FlexCalendar("5088_c_64", setOf(DayOfWeek.SUNDAY), LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1), emptySet())
    private val booking = BookingRule(
        "5088_booking", BookingType.PRIOR_DAYS, null, null, priorNoticeLastDay = 1, priorNoticeLastTime = ServiceDayTime.parse("17:00:00"),
        priorNoticeStartDay = 14, priorNoticeStartTime = ServiceDayTime(0), priorNoticeCalendarId = null,
        message = "DOT is the City of Alexandria's paratransit program", pickupMessage = null, dropOffMessage = null,
        phoneNumber = "703-746-5222", infoUrl = "https://www.alexandriava.gov/Paratransit", bookingUrl = null
    )

    private fun rule(calendarId: String, start: String, bookingRuleId: String? = "5088_booking") = AvailabilityRule(
        listOf("5088_area_1449"), listOf("5088_area_1449"), ServiceDayTime.parse(start), ServiceDayTime.parse("24:50:00"), ServiceDayTime.parse("25:00:00"),
        listOf(calendarId), 2, 2, bookingRuleId, bookingRuleId, 1.0, 0.0
    )

    private val alexandria = OnDemandService(
        id = "5088_77652", agencyId = "5088", routeId = "5088_77652", name = "DOT Paratransit", kind = OnDemandServiceKind.ZONE,
        rules = listOf(rule("5088_c_63", "05:00:00"), rule("5088_c_64", "07:00:00")),
        bookingRules = mapOf("5088_booking" to booking), calendars = mapOf("5088_c_63" to weekdays, "5088_c_64" to sunday),
        agencyTimezone = "America/Los_Angeles"
    )

    // Tuesday 2026-03-10 at 16:00 Pacific: tomorrow's 17:00 cutoff is still an hour away.
    private val tuesdayAfternoon = OffsetDateTime.parse("2026-03-10T16:00:00-07:00").toInstant()

    @Test
    fun `when rows follow rule order and booking names the next bookable date`() {
        val content = presentService(alexandria, tuesdayAfternoon)
        assertEquals(2, content.whenRows.size)
        assertEquals(weekdays.days, content.whenRows[0].days)
        assertEquals(ServiceDayTime.parse("05:00:00"), content.whenRows[0].start)
        val summary = requireNotNull(content.booking)
        assertEquals(LocalDate.of(2026, 3, 11), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertEquals(OffsetDateTime.parse("2026-03-10T17:00:00-07:00").toInstant(), summary.evaluation?.cutoffInstant)
        assertEquals("703-746-5222", summary.phoneNumber)
        assertEquals(listOf("DOT is the City of Alexandria's paratransit program"), summary.messages)
    }

    @Test
    fun `after the cutoff the next bookable date moves on`() {
        val now = OffsetDateTime.parse("2026-03-10T17:30:00-07:00").toInstant()
        assertEquals(LocalDate.of(2026, 3, 12), presentService(alexandria, now).booking?.travelDate)
    }

    @Test
    fun `identical days and hours collapse into one row`() {
        val service = alexandria.copy(
            rules = listOf(rule("5088_c_63", "05:00:00"), rule("5088_c_63", "05:00:00"), rule("5088_c_63", "07:00:00"))
        )
        val content = presentService(service, tuesdayAfternoon)
        assertEquals(2, content.whenRows.size)
        assertEquals(ServiceDayTime.parse("05:00:00"), content.whenRows[0].start)
        assertEquals(ServiceDayTime.parse("07:00:00"), content.whenRows[1].start)
    }

    @Test
    fun `same days but different hours stay separate`() {
        val service = alexandria.copy(rules = listOf(rule("5088_c_63", "05:00:00"), rule("5088_c_63", "09:00:00")))
        val content = presentService(service, tuesdayAfternoon)
        assertEquals(2, content.whenRows.size)
        assertEquals(ServiceDayTime.parse("05:00:00"), content.whenRows[0].start)
        assertEquals(ServiceDayTime.parse("09:00:00"), content.whenRows[1].start)
    }

    @Test
    fun `a degenerate service with no rules still presents`() {
        val content = presentService(alexandria.copy(rules = emptyList()), tuesdayAfternoon)
        assertTrue(content.whenRows.isEmpty())
        assertNull(content.booking)
    }

    @Test
    fun `a same-day rule without a minimum notice yields no deadline but keeps the contact`() {
        val unknownNotice = booking.copy(bookingType = BookingType.SAME_DAY, priorNoticeLastDay = null, priorNoticeLastTime = null, priorNoticeStartDay = null, priorNoticeStartTime = null)
        val service = alexandria.copy(bookingRules = mapOf("5088_booking" to unknownNotice))
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertNull(summary.travelDate)
        assertNull(summary.evaluation)
        assertEquals("703-746-5222", summary.phoneNumber)
    }

    @Test
    fun `a booking rule id the references cannot resolve is unknown, not open`() {
        // The feed names a booking rule (so notice is required) but its reference was absent or had a
        // booking_type this build can't read; promising "book any time" would be the unrecoverable error.
        val service = alexandria.copy(bookingRules = emptyMap())
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertNull(summary.travelDate)
        assertNull(summary.evaluation)
    }

    @Test
    fun `no booking rule id means no notice is required, so today is bookable`() {
        val service = alexandria.copy(rules = listOf(rule("5088_c_63", "05:00:00", bookingRuleId = null)))
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertEquals(LocalDate.of(2026, 3, 10), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertNull(summary.evaluation?.cutoffInstant)
    }

    // A same-day rule with an hour's notice: on 2026-03-10 its cutoff is 23:50 that evening.
    private val sameDayHour = booking.copy(
        id = "same_day",
        bookingType = BookingType.SAME_DAY,
        priorNoticeDurationMin = 60,
        priorNoticeLastDay = null,
        priorNoticeLastTime = null,
        priorNoticeStartDay = null,
        priorNoticeStartTime = null
    )
    private val sameDayCutoff = OffsetDateTime.parse("2026-03-10T23:50:00-07:00").toInstant()

    @Test
    fun `a rule already closed for the travel date never supplies the deadline`() {
        // Both rules run today. The prior-day rule closed yesterday at 17:00; the same-day one is open.
        val service = alexandria.copy(
            rules = listOf(rule("5088_c_63", "05:00:00"), rule("5088_c_63", "05:00:00", bookingRuleId = "same_day")),
            bookingRules = mapOf("5088_booking" to booking, "same_day" to sameDayHour)
        )
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertEquals(LocalDate.of(2026, 3, 10), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertEquals(sameDayCutoff, summary.evaluation?.cutoffInstant)
    }

    @Test
    fun `a rule with an unknown deadline does not displace a known one`() {
        val unknownNotice = sameDayHour.copy(id = "unknown", priorNoticeDurationMin = null)
        val service = alexandria.copy(
            rules = listOf(rule("5088_c_63", "05:00:00", bookingRuleId = "unknown"), rule("5088_c_63", "05:00:00", bookingRuleId = "same_day")),
            bookingRules = mapOf("unknown" to unknownNotice, "same_day" to sameDayHour)
        )
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertEquals(LocalDate.of(2026, 3, 10), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertEquals(sameDayCutoff, summary.evaluation?.cutoffInstant)
    }

    @Test
    fun `a not-yet-open rule on the travel date never supplies the deadline`() {
        // Both rules run today. The evening rule's booking opens at 21:00 for a 23:05 cutoff, earlier
        // than the open rule's 23:50: stating it would say "booking opens" while a ride is bookable now.
        val evening = sameDayHour.copy(id = "evening", priorNoticeDurationMin = 5, priorNoticeDurationMax = 120)
        val eveningRule = rule("5088_c_63", "23:00:00", bookingRuleId = "evening").copy(endPickupTime = ServiceDayTime.parse("23:10:00"))
        val service = alexandria.copy(
            rules = listOf(eveningRule, rule("5088_c_63", "05:00:00", bookingRuleId = "same_day")),
            bookingRules = mapOf("evening" to evening, "same_day" to sameDayHour)
        )
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertEquals(LocalDate.of(2026, 3, 10), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertEquals(sameDayCutoff, summary.evaluation?.cutoffInstant)
    }

    @Test
    fun `an agency timezone java time cannot resolve states no deadline but keeps the when rows`() {
        // The device zone is not the agency's, so a deadline computed in it could be hours late.
        for (timezone in listOf("Mars/Olympus_Mons", null)) {
            val content = presentService(alexandria.copy(agencyTimezone = timezone), tuesdayAfternoon)
            assertEquals(2, content.whenRows.size)
            val summary = requireNotNull(content.booking)
            assertNull(summary.travelDate)
            assertNull(summary.evaluation)
            assertNull(summary.zone)
            assertEquals("703-746-5222", summary.phoneNumber)
        }
    }

    // A seasonal service: every day from 2026-04-09, thirty days after tuesdayAfternoon, booked from
    // 09:00 fourteen days ahead until 17:00 the day before. Nothing is bookable yet.
    private val season = FlexCalendar("season", DayOfWeek.entries.toSet(), LocalDate.of(2026, 4, 9), LocalDate.of(2026, 12, 1), emptySet())
    private val seasonBooking = booking.copy(id = "season", priorNoticeStartDay = 14, priorNoticeStartTime = ServiceDayTime.parse("09:00:00"))
    private val seasonal = alexandria.copy(
        rules = listOf(rule("season", "05:00:00", bookingRuleId = "season")),
        bookingRules = mapOf("season" to seasonBooking),
        calendars = mapOf("season" to season)
    )

    @Test
    fun `when nothing is bookable yet the page says when booking opens`() {
        val summary = requireNotNull(presentService(seasonal, tuesdayAfternoon).booking)
        assertEquals(LocalDate.of(2026, 4, 9), summary.travelDate)
        assertEquals(BookingState.NOT_YET_OPEN, summary.evaluation?.state)
        assertEquals(OffsetDateTime.parse("2026-03-26T09:00:00-07:00").toInstant(), summary.evaluation?.openInstant)
    }

    @Test
    fun `a rule bookable now wins over one whose booking has not opened`() {
        val service = seasonal.copy(
            rules = seasonal.rules + rule("5088_c_63", "05:00:00", bookingRuleId = "same_day"),
            bookingRules = seasonal.bookingRules + ("same_day" to sameDayHour),
            calendars = seasonal.calendars + ("5088_c_63" to weekdays)
        )
        val summary = requireNotNull(presentService(service, tuesdayAfternoon).booking)
        assertEquals(LocalDate.of(2026, 3, 10), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertEquals(sameDayCutoff, summary.evaluation?.cutoffInstant)
    }

    @Test
    fun `a service unknown on every rule still publishes no deadline`() {
        val unknownNotice = sameDayHour.copy(id = "season", priorNoticeDurationMin = null)
        val summary = requireNotNull(presentService(seasonal.copy(bookingRules = mapOf("season" to unknownNotice)), tuesdayAfternoon).booking)
        assertNull(summary.travelDate)
        assertNull(summary.evaluation)
    }
}
