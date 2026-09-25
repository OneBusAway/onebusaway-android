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

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.ServiceDayTime

/** Whether a ride on a given travel date can be booked right now (wiki §2.5, spec §6). */
enum class BookingState {
    /** Booking has not opened yet ([BookingEvaluation.openInstant] is in the future). */
    NOT_YET_OPEN,
    OPEN,

    /** The cutoff for this travel date has passed. */
    CLOSED_FOR_DATE,

    /**
     * No deadline can be stated: the feed omits a field the booking type needs (spec §6.2), or a
     * prior-notice count-back cannot complete (spec §6.3). Client-only; never on the wire.
     */
    UNKNOWN
}

data class BookingEvaluation(
    val state: BookingState,
    val cutoffInstant: Instant?,
    val openInstant: Instant?
)

/**
 * The client-side booking deadline algorithm, implemented once here and verified against the vectors
 * every client shares. Everything is in **service days in the agency timezone**: a service day is
 * anchored at local noon minus twelve hours (GTFS's DST-safe convention), and a [ServiceDayTime] is
 * added to that anchor — so `18:00:00` stays 18:00 local on a spring-forward day. `now` is the device
 * wall clock (never the envelope `currentTime`, which is cached for hours), handed in as an `Instant`
 * so no epoch-millis arithmetic happens here.
 */
object BookingDeadlineEvaluator {

    private val MIDNIGHT = ServiceDayTime(0)
    private val END_OF_SERVICE_DAY = ServiceDayTime(24 * 3600)

    /** A backstop against calendars too sparse for any real prior-notice window (spec §6.3). */
    private const val MAX_COUNT_BACK_DAYS = 400

    /** How far ahead [nextActiveServiceDate] looks, matching the other clients' lookahead cap. */
    private const val MAX_LOOKAHEAD_DAYS = 400

    /** Local noon of [date] in [zone], minus twelve hours. */
    fun serviceDayAnchor(date: LocalDate, zone: ZoneId): Instant = date.atTime(LocalTime.NOON).atZone(zone).toInstant().minus(Duration.ofHours(12))

    fun instantOf(date: LocalDate, time: ServiceDayTime, zone: ZoneId): Instant = serviceDayAnchor(date, zone).plusSeconds(time.seconds.toLong())

    /**
     * [date] minus [days]: calendar days when [calendar] is null, otherwise days [calendar] is active
     * on (holidays on it push the result earlier). Zero days is [date] itself. Null when the count
     * cannot complete — it would step before the calendar's start date, the calendar has no active
     * days, or it would walk more than [MAX_COUNT_BACK_DAYS] calendar days (spec §6.3).
     */
    fun countBack(date: LocalDate, days: Int, calendar: FlexCalendar?): LocalDate? {
        if (calendar == null) return date.minusDays(days.toLong())
        if (days == 0) return date
        if (calendar.days.isEmpty()) return null
        var current = date
        var counted = 0
        repeat(MAX_COUNT_BACK_DAYS) {
            current = current.minusDays(1)
            // No service day precedes the calendar's start, so the count can't be consumed there.
            if (current.isBefore(calendar.startDate)) return null
            if (calendar.isActiveOn(current)) counted++
            if (counted == days) return current
        }
        return null
    }

    /**
     * Evaluates one (rule, travel date) pair. The pickup side governs booking, so [bookingRule] is the
     * rule's pickup booking rule; null means no notice is required.
     */
    fun evaluate(
        rule: AvailabilityRule,
        bookingRule: BookingRule?,
        travelDate: LocalDate,
        now: Instant,
        zone: ZoneId,
        calendars: Map<String, FlexCalendar>
    ): BookingEvaluation {
        if (bookingRule == null) return BookingEvaluation(BookingState.OPEN, cutoffInstant = null, openInstant = null)
        val window = when (bookingRule.bookingType) {
            // Real-time: booked at ride time. Any prior-notice fields the feed carries are forbidden
            // for this type and ignored (Charlevoix's booking_rule_CC4).
            BookingType.REAL_TIME -> BookingWindow(cutoff = latestPickup(rule, travelDate, zone), open = null)
            BookingType.SAME_DAY -> sameDayWindow(rule, bookingRule, travelDate, zone)
            BookingType.PRIOR_DAYS -> priorDaysWindow(bookingRule, travelDate, zone, calendars)
        } ?: return BookingEvaluation(BookingState.UNKNOWN, cutoffInstant = null, openInstant = null)
        val state = when {
            window.open != null && now.isBefore(window.open) -> BookingState.NOT_YET_OPEN
            now.isAfter(window.cutoff) -> BookingState.CLOSED_FOR_DATE
            else -> BookingState.OPEN
        }
        return BookingEvaluation(state, window.cutoff, window.open)
    }

    /**
     * The earliest active service day of the rule's calendars, searching from [from] (the agency-local
     * today, spec §6.4) to the latest calendar end date, on which [evaluate] is [BookingState.OPEN];
     * null when there is none. A date that evaluates [BookingState.UNKNOWN] is not bookable and is
     * skipped, so a rule that is unknown on every date yields null.
     */
    fun nextBookableServiceDate(
        rule: AvailabilityRule,
        bookingRule: BookingRule?,
        from: LocalDate,
        now: Instant,
        zone: ZoneId,
        calendars: Map<String, FlexCalendar>
    ): LocalDate? {
        val ruleCalendars = rule.calendarIds.mapNotNull(calendars::get)
        val end = ruleCalendars.maxOfOrNull { it.endDate } ?: return null
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .filter { date -> ruleCalendars.any { it.isActiveOn(date) } }
            .firstOrNull { evaluate(rule, bookingRule, it, now, zone, calendars).state == BookingState.OPEN }
    }

    /**
     * The earliest date on or after [from] (the agency-local today) that is active on at least one of
     * the rule's calendars, bounded by the latest calendar end date and [MAX_LOOKAHEAD_DAYS]; null when
     * there is none. Unlike [nextBookableServiceDate] it ignores booking, so the caller can evaluate a
     * date that cannot be booked yet.
     */
    fun nextActiveServiceDate(rule: AvailabilityRule, from: LocalDate, calendars: Map<String, FlexCalendar>): LocalDate? {
        val ruleCalendars = rule.calendarIds.mapNotNull(calendars::get)
        val end = ruleCalendars.maxOfOrNull { it.endDate } ?: return null
        return generateSequence(from) { it.plusDays(1) }
            .take(MAX_LOOKAHEAD_DAYS)
            .takeWhile { !it.isAfter(end) }
            .firstOrNull { date -> ruleCalendars.any { it.isActiveOn(date) } }
    }

    private class BookingWindow(val cutoff: Instant, val open: Instant?)

    private fun latestPickup(rule: AvailabilityRule, travelDate: LocalDate, zone: ZoneId): Instant = instantOf(travelDate, rule.endPickupTime ?: END_OF_SERVICE_DAY, zone)

    /**
     * Cutoff is the latest pickup less the minimum notice; booking opens [BookingRule.priorNoticeDurationMax]
     * before the earliest pickup, or else on the civil start day. Null (unknown) without a minimum
     * notice: defaulting it to zero would state the latest possible deadline, the one failure a rider
     * can't recover from (spec §6.2).
     */
    private fun sameDayWindow(rule: AvailabilityRule, bookingRule: BookingRule, travelDate: LocalDate, zone: ZoneId): BookingWindow? {
        val durationMin = bookingRule.priorNoticeDurationMin ?: return null
        val cutoff = latestPickup(rule, travelDate, zone).minus(Duration.ofMinutes(durationMin.toLong()))
        val durationMax = bookingRule.priorNoticeDurationMax
        val startDay = bookingRule.priorNoticeStartDay
        val open = when {
            durationMax != null -> instantOf(travelDate, rule.startPickupTime ?: MIDNIGHT, zone).minus(Duration.ofMinutes(durationMax.toLong()))
            // The prior-notice calendar is forbidden for this type, so the start day is civil days.
            startDay != null -> instantOf(travelDate.minusDays(startDay.toLong()), bookingRule.priorNoticeStartTime ?: MIDNIGHT, zone)
            else -> null
        }
        return BookingWindow(cutoff, open)
    }

    /**
     * Both the last day and the start day are counted on the prior-notice calendar when it resolves
     * (spec §6.1). A missing last time is the start of the last day — never later than any deadline
     * the feed could have meant (spec §6.2). Null (unknown) without a last day, or when either
     * count-back cannot complete (spec §6.3).
     */
    private fun priorDaysWindow(bookingRule: BookingRule, travelDate: LocalDate, zone: ZoneId, calendars: Map<String, FlexCalendar>): BookingWindow? {
        val lastDay = bookingRule.priorNoticeLastDay ?: return null
        val noticeCalendar = bookingRule.priorNoticeCalendarId?.let(calendars::get)
        val lastDayDate = countBack(travelDate, lastDay, noticeCalendar) ?: return null
        val cutoff = instantOf(lastDayDate, bookingRule.priorNoticeLastTime ?: MIDNIGHT, zone)
        val startDay = bookingRule.priorNoticeStartDay ?: return BookingWindow(cutoff, open = null)
        val startDayDate = countBack(travelDate, startDay, noticeCalendar) ?: return null
        return BookingWindow(cutoff, instantOf(startDayDate, bookingRule.priorNoticeStartTime ?: MIDNIGHT, zone))
    }
}
