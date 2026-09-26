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
package org.onebusaway.android.ondemand

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility

class OnDemandAvailabilityTest {

    // Tuesday 2026-03-10, Eastern Daylight Time.
    private val tuesdayTwoPm = instant("2026-03-10T14:00:00-04:00")

    @Test
    fun `running inside today's window with same-day booking is open now until the window ends`() {
        val availability = computeAvailability(service(), tuesdayTwoPm)
        assertTrue(availability.runningNow)
        assertEquals(instant("2026-03-10T16:40:00-04:00"), availability.runningUntil)
        assertTrue(availability.bookableNow)
        assertEquals(BookingTier.SAME_DAY, availability.bookingTier)
        assertEquals(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), availability.status)
        assertEquals(setOf(OnDemandTag.SAME_DAY_BOOKING), availability.tags)
        assertEquals(TIER_OPEN_NOW, availability.usabilityTier)
        // The same-day cutoff (16:40 − 60 min) comes before the window end.
        assertEquals(instant("2026-03-10T15:40:00-04:00"), availability.nextChangeInstant)
    }

    @Test
    fun `a window past midnight is found from the previous service day`() {
        // Fifteen minutes' notice: at 00:30 the 01:00 window end is still bookable for yesterday's service date.
        val night = service(rules = listOf(rule(start = "20:00:00", end = "25:00:00")), bookingRules = mapOf("CC_b1" to bookingRule(durationMin = 15)))
        val availability = computeAvailability(night, instant("2026-03-11T00:30:00-04:00"))
        assertTrue(availability.runningNow)
        assertEquals(instant("2026-03-11T01:00:00-04:00"), availability.runningUntil)
        assertTrue("bookableNow evaluates for yesterday's service date", availability.bookableNow)
    }

    @Test
    fun `an all-hours service on consecutive days runs continuously`() {
        val allDay = service(rules = listOf(rule(start = null, end = null)), calendars = mapOf("CC_cal" to calendar(days = ALL_WEEK)))
        val availability = computeAvailability(allDay, instant("2026-03-10T03:00:00-04:00"))
        assertTrue(availability.runningNow)
        assertNull("abutting windows read as one continuous service", availability.runningUntil)
        assertEquals(OnDemandStatus.OpenNow(null), availability.status)
    }

    @Test
    fun `an all-hours service whose next day is inactive ends at midnight`() {
        val monTue = service(rules = listOf(rule(start = null, end = null)), calendars = mapOf("CC_cal" to calendar(days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY))))
        val availability = computeAvailability(monTue, instant("2026-03-10T03:00:00-04:00"))
        assertEquals(instant("2026-03-11T00:00:00-04:00"), availability.runningUntil)
    }

    @Test
    fun `an excepted date removes today and the next run is tomorrow`() {
        val excepted = service(calendars = mapOf("CC_cal" to calendar(excepted = setOf(LocalDate.of(2026, 3, 10)))))
        val availability = computeAvailability(excepted, tuesdayTwoPm)
        assertFalse(availability.runningNow)
        assertEquals(instant("2026-03-11T07:20:00-04:00"), availability.nextRunStart)
        assertEquals(OnDemandStatus.OpensAt(instant("2026-03-11T07:20:00-04:00")), availability.status)
        assertEquals(instant("2026-03-11T07:20:00-04:00"), availability.nextChangeInstant)
    }

    @Test
    fun `an added calendar day counts like any other calendar`() {
        val sunday = LocalDate.of(2026, 3, 15)
        val added = service(
            rules = listOf(rule(calendarIds = listOf("CC_cal", "CC_cal_added_20260315"))),
            calendars = mapOf("CC_cal" to calendar(), "CC_cal_added_20260315" to calendar(id = "CC_cal_added_20260315", days = ALL_WEEK, start = sunday, end = sunday))
        )
        assertTrue(computeAvailability(added, instant("2026-03-15T10:00:00-04:00")).runningNow)
    }

    @Test
    fun `after the window ends the next run starts tomorrow and the tier is advance`() {
        val availability = computeAvailability(service(), instant("2026-03-10T17:00:00-04:00"))
        assertFalse(availability.runningNow)
        assertEquals(instant("2026-03-11T07:20:00-04:00"), availability.nextRunStart)
        // Spec §2.6: the next bookable date is tomorrow, not today, so an after-hours same-day service reads as tier 3.
        assertEquals(LocalDate.of(2026, 3, 11), availability.nextBookableServiceDate)
        assertEquals(TIER_ADVANCE, availability.usabilityTier)
    }

    @Test
    fun `a same-day service not yet running but bookable for today is tier 2`() {
        // Window 16:30–18:00 with an hour's notice, now 16:00: not running yet, but today's cutoff
        // (17:00) is still ahead — tier 2, and "Opens" at 16:30.
        val later = service(rules = listOf(rule(start = "16:30:00", end = "18:00:00")))
        val availability = computeAvailability(later, instant("2026-03-10T16:00:00-04:00"))
        assertFalse(availability.runningNow)
        assertEquals(LocalDate.of(2026, 3, 10), availability.nextBookableServiceDate)
        assertEquals(TIER_SAME_DAY, availability.usabilityTier)
        assertEquals(OnDemandStatus.OpensAt(instant("2026-03-10T16:30:00-04:00")), availability.status)
    }

    @Test
    fun `the booking tier is the least demanding rule active on the next active date`() {
        val mixed = service(
            rules = listOf(rule(bookingRuleId = "CC_prior"), rule(bookingRuleId = null)),
            bookingRules = mapOf("CC_prior" to bookingRule(id = "CC_prior", type = BookingType.PRIOR_DAYS, lastDay = 1, lastTime = "15:10:00"))
        )
        val availability = computeAvailability(mixed, tuesdayTwoPm)
        assertEquals(BookingTier.REAL_TIME, availability.bookingTier)
        assertEquals(setOf(OnDemandTag.NO_NOTICE_NEEDED), availability.tags)
    }

    @Test
    fun `a dangling booking rule id is skipped for the tier`() {
        val dangling = service(rules = listOf(rule(bookingRuleId = "missing"), rule(bookingRuleId = "CC_b1")))
        assertEquals(BookingTier.SAME_DAY, computeAvailability(dangling, tuesdayTwoPm).bookingTier)
    }

    @Test
    fun `an advance service promotes its deadline even while running`() {
        val advance = service(bookingRules = mapOf("CC_b1" to bookingRule(type = BookingType.PRIOR_DAYS, durationMin = null, lastDay = 1, lastTime = "15:10:00")))
        val availability = computeAvailability(advance, tuesdayTwoPm)
        assertTrue(availability.runningNow)
        assertEquals(OnDemandStatus.BookBy(instant("2026-03-10T15:10:00-04:00"), LocalDate.of(2026, 3, 11)), availability.status)
        assertEquals(LocalDate.of(2026, 3, 11), availability.nextBookableServiceDate)
        assertEquals(TIER_ADVANCE, availability.usabilityTier)
        assertEquals(setOf(OnDemandTag.ADVANCE_BOOKING), availability.tags)
    }

    @Test
    fun `an advance service whose booking has not opened reads booking opens`() {
        // Booking opens two days ahead at 18:00 and closes one day ahead at 15:10, so it is genuinely
        // open for a two-hour-fifty-minute window each day — unlike a same-day open/close pair, this
        // one never collapses to an instantly-closed window.
        val opensLater = bookingRule(type = BookingType.PRIOR_DAYS, durationMin = null, lastDay = 1, lastTime = "15:10:00")
            .copy(priorNoticeStartDay = 2, priorNoticeStartTime = org.onebusaway.android.models.ServiceDayTime.parse("18:00:00"))
        val advance = service(bookingRules = mapOf("CC_b1" to opensLater))
        // Tuesday 16:00: today (opened Sun 18:00, cutoff Mon 15:10) and Wednesday (opened Mon 18:00,
        // cutoff Tue 15:10) are both already closed. Thursday is the first date not yet open — it
        // opens Tuesday 18:00 — so the service has no next bookable date at all.
        val availability = computeAvailability(advance, instant("2026-03-10T16:00:00-04:00"))
        assertEquals(OnDemandStatus.BookingOpens(instant("2026-03-10T18:00:00-04:00"), LocalDate.of(2026, 3, 12)), availability.status)
        assertNull(availability.nextBookableServiceDate)
        assertEquals(TIER_UNKNOWN_OR_CLOSED, availability.usabilityTier)
    }

    @Test
    fun `eligibility required adds the tag and tier 4 over open now`() {
        val restricted = service(eligibility = OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, null))
        val availability = computeAvailability(restricted, tuesdayTwoPm)
        assertEquals(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), availability.status)
        assertEquals(setOf(OnDemandTag.SAME_DAY_BOOKING, OnDemandTag.ELIGIBILITY_REQUIRED), availability.tags)
        assertEquals(TIER_ELIGIBILITY, availability.usabilityTier)
    }

    @Test
    fun `open eligibility shows nothing`() {
        val open = service(eligibility = OnDemandEligibility(EligibilityRequirement.OPEN, null))
        assertEquals(setOf(OnDemandTag.SAME_DAY_BOOKING), computeAvailability(open, tuesdayTwoPm).tags)
    }

    @Test
    fun `a missing timezone is unknown at tier 5`() {
        val availability = computeAvailability(service(timezone = null), tuesdayTwoPm)
        assertEquals(OnDemandAvailability.UNKNOWN, availability)
        assertEquals(TIER_UNKNOWN_OR_CLOSED, availability.usabilityTier)
        assertTrue(availability.tags.isEmpty())
    }

    @Test
    fun `no rules is unknown`() {
        assertEquals(OnDemandStatus.Unknown, computeAvailability(service(rules = emptyList()), tuesdayTwoPm).status)
    }

    @Test
    fun `same-day cutoff passed while still running opens again the next active day`() {
        // 90 minutes' notice pushes the cutoff to 15:10, ahead of the 16:40 window end: the service
        // is still running but no longer bookable for today (spec §2.5 step 3, I4).
        val closedForToday = service(bookingRules = mapOf("CC_b1" to bookingRule(durationMin = 90)))
        val availability = computeAvailability(closedForToday, instant("2026-03-10T15:30:00-04:00"))
        assertTrue(availability.runningNow)
        assertFalse(availability.bookableNow)
        assertEquals(OnDemandStatus.OpensAt(instant("2026-03-11T07:20:00-04:00")), availability.status)
        assertEquals(TIER_ADVANCE, availability.usabilityTier)
    }

    @Test
    fun `same-day booking is still open just before the cutoff`() {
        val closedForToday = service(bookingRules = mapOf("CC_b1" to bookingRule(durationMin = 90)))
        val availability = computeAvailability(closedForToday, instant("2026-03-10T15:00:00-04:00"))
        assertTrue(availability.bookableNow)
        assertEquals(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), availability.status)
        assertEquals(TIER_OPEN_NOW, availability.usabilityTier)
        // The 15:10 cutoff is the next thing to happen, ahead of the 16:40 window end.
        assertEquals(instant("2026-03-10T15:10:00-04:00"), availability.nextChangeInstant)
    }

    @Test
    fun `after the window ends the cutoff fix leaves behaviour unchanged`() {
        val closedForToday = service(bookingRules = mapOf("CC_b1" to bookingRule(durationMin = 90)))
        val availability = computeAvailability(closedForToday, instant("2026-03-10T16:50:00-04:00"))
        assertFalse(availability.runningNow)
        assertEquals(OnDemandStatus.OpensAt(instant("2026-03-11T07:20:00-04:00")), availability.status)
        assertEquals(TIER_ADVANCE, availability.usabilityTier)
    }

    @Test
    fun `a midnight-crossing window's cutoff lands in the next change instant`() {
        // Same window as the previous-service-day test, but pinning that its own 00:45 cutoff — not
        // just the 01:00 window end — surfaces so the dock re-checks in time (Task 2 deferred minor).
        val night = service(rules = listOf(rule(start = "20:00:00", end = "25:00:00")), bookingRules = mapOf("CC_b1" to bookingRule(durationMin = 15)))
        val availability = computeAvailability(night, instant("2026-03-11T00:30:00-04:00"))
        assertEquals(instant("2026-03-11T00:45:00-04:00"), availability.nextChangeInstant)
    }

    @Test
    fun `every calendar ended is closed, tier 5, and walks no further`() {
        val ended = service(calendars = mapOf("CC_cal" to calendar(end = LocalDate.of(2026, 2, 28))))
        val availability = computeAvailability(ended, tuesdayTwoPm)
        assertEquals(OnDemandStatus.Closed, availability.status)
        assertEquals(TIER_UNKNOWN_OR_CLOSED, availability.usabilityTier)
        assertNull(availability.nextRunStart)
        assertNull(availability.nextChangeInstant)
    }
}
