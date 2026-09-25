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

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingDeadlineEvaluator
import org.onebusaway.android.ondemand.BookingEvaluation
import org.onebusaway.android.ondemand.BookingState

/** One "When" line: the days of [calendarId] and the pickup window (null ends = all hours). */
data class WhenRow(val calendarId: String, val days: Set<DayOfWeek>, val start: ServiceDayTime?, val end: ServiceDayTime?)

/**
 * The "How to book" section. [travelDate] is the next bookable service day — by construction a date
 * on which some rule evaluates [BookingState.OPEN] — and [evaluation] its verdict: the earliest cutoff
 * among the rules that evaluate OPEN on that date. The earliest known deadline is the safest
 * instruction a rider can be given (wiki §2.5). Only OPEN rules supply it: a rule already closed for
 * that date has a deadline in the past, a rule not yet open would say "booking opens …" while another
 * rule is bookable now, and a rule whose deadline is unknown adds no bound the client can state. An
 * OPEN rule with no booking rule has no deadline (a null cutoff); it supplies the line only when it
 * is the sole OPEN rule, which then reads "no notice required".
 *
 * Only when no rule is bookable on any date: each rule is taken on the first of its service days
 * whose booking is [BookingState.NOT_YET_OPEN] (not merely its next service day, which may already be
 * closed while a later one is still to open), and if any rule has one, [travelDate] and [evaluation]
 * are those of the rule whose booking opens earliest, so the page says when booking opens. Otherwise
 * both are null: no date can be promised (every rule's notice is unknown, the calendars have ended,
 * or [zone] is null).
 *
 * [zone] is the agency timezone every deadline is computed in; null when the feed's id cannot be
 * resolved, in which case no deadline is computed at all.
 */
data class BookingSummary(
    val travelDate: LocalDate?,
    val evaluation: BookingEvaluation?,
    val zone: ZoneId?,
    val phoneNumber: String?,
    val bookingUrl: String?,
    val infoUrl: String?,
    val messages: List<String>
)

sealed interface OnDemandServiceUiState {
    data object Loading : OnDemandServiceUiState
    data object NotFound : OnDemandServiceUiState
    data object Error : OnDemandServiceUiState
    data class Content(val service: OnDemandService, val whenRows: List<WhenRow>, val booking: BookingSummary?) : OnDemandServiceUiState
}

private val UNKNOWN_EVALUATION = BookingEvaluation(BookingState.UNKNOWN, cutoffInstant = null, openInstant = null)

/**
 * Projects a service for the page at [now] (the device wall clock, minted by the caller). Pure, so
 * the deadline line is JVM-tested with a fixed instant.
 */
internal fun presentService(service: OnDemandService, now: Instant): OnDemandServiceUiState.Content {
    // Several rules can share the same days and pickup window (e.g. Charlevoix's CC_CC1); collapse
    // those into one row, keeping the first occurrence's position.
    val whenRows = service.rules.flatMap { rule ->
        rule.calendarIds.mapNotNull { id -> service.calendars[id]?.let { WhenRow(id, it.days, rule.startPickupTime, rule.endPickupTime) } }
    }.distinctBy { Triple(it.days, it.start, it.end) }
    val bookingRules = service.rules.mapNotNull(service::pickupBookingRule).distinctBy { it.id }
    val booking = if (service.rules.isEmpty()) {
        null
    } else {
        val zone = agencyZone(service.agencyTimezone)
        val line = zone?.let { service.bookingLine(now, it) }
        BookingSummary(
            travelDate = line?.travelDate,
            evaluation = line?.evaluation,
            zone = zone,
            phoneNumber = bookingRules.firstNotNullOfOrNull { it.phoneNumber },
            bookingUrl = bookingRules.firstNotNullOfOrNull { it.bookingUrl },
            infoUrl = bookingRules.firstNotNullOfOrNull { it.infoUrl },
            messages = bookingRules.flatMap { listOfNotNull(it.message, it.pickupMessage, it.dropOffMessage) }.distinct()
        )
    }
    return OnDemandServiceUiState.Content(service, whenRows, booking)
}

/** The verdict the booking line states, and the ride date it is for. */
private data class DatedEvaluation(val travelDate: LocalDate, val evaluation: BookingEvaluation)

/** What the booking line states; see [BookingSummary]. Null when no date can be promised. */
private fun OnDemandService.bookingLine(now: Instant, zone: ZoneId): DatedEvaluation? {
    val bookableDate = nextBookableServiceDate(now, zone) ?: return earliestOpening(now, zone)
    return earliestOpenEvaluation(bookableDate, now, zone)?.let { DatedEvaluation(bookableDate, it) }
}

/**
 * For a service with nothing bookable on any date: each rule on the first of its service days whose
 * booking has not opened yet, and of those the one whose booking opens earliest. The walk has to go
 * past the rule's next service day: with a one-day notice window that day is already closed by the
 * evening before, while the day after it is still to open.
 */
private fun OnDemandService.earliestOpening(now: Instant, zone: ZoneId): DatedEvaluation? {
    val today = now.atZone(zone).toLocalDate()
    return rules
        .filterNot(::hasUnresolvedPickupBookingRule)
        .mapNotNull { rule ->
            BookingDeadlineEvaluator.nextServiceDateInState(rule, pickupBookingRule(rule), BookingState.NOT_YET_OPEN, today, now, zone, calendars)
                ?.let { date -> DatedEvaluation(date, evaluateBooking(rule, date, now, zone)) }
        }
        .minByOrNull { it.evaluation.openInstant ?: Instant.MAX }
}

/** The earliest date any rule can be booked for right now, or null when none can. */
private fun OnDemandService.nextBookableServiceDate(now: Instant, zone: ZoneId): LocalDate? {
    val today = now.atZone(zone).toLocalDate()
    return rules
        .filterNot(::hasUnresolvedPickupBookingRule)
        .mapNotNull { rule -> BookingDeadlineEvaluator.nextBookableServiceDate(rule, pickupBookingRule(rule), today, now, zone, calendars) }
        .minOrNull()
}

/** The verdict with the earliest cutoff among the rules that evaluate OPEN on [date]; see [BookingSummary]. */
private fun OnDemandService.earliestOpenEvaluation(date: LocalDate, now: Instant, zone: ZoneId): BookingEvaluation? = rules
    .filter { rule -> rule.calendarIds.any { calendars[it]?.isActiveOn(date) == true } }
    .map { rule -> evaluateBooking(rule, date, now, zone) }
    .filter { it.state == BookingState.OPEN }
    .minByOrNull { it.cutoffInstant ?: Instant.MAX }

/**
 * The rule names a pickup booking rule the references don't resolve (absent, or dropped at the
 * adapter for a `booking_type` this build can't read). Notice *is* required, we just can't say how
 * much — so it is [BookingState.UNKNOWN], never the evaluator's null-rule "no notice, book any time".
 */
private fun OnDemandService.hasUnresolvedPickupBookingRule(rule: AvailabilityRule): Boolean = rule.pickupBookingRuleId != null && pickupBookingRule(rule) == null

private fun OnDemandService.evaluateBooking(rule: AvailabilityRule, date: LocalDate, now: Instant, zone: ZoneId): BookingEvaluation = if (hasUnresolvedPickupBookingRule(rule)) {
    UNKNOWN_EVALUATION
} else {
    BookingDeadlineEvaluator.evaluate(rule, pickupBookingRule(rule), date, now, zone, calendars)
}

/**
 * The agency timezone, required by GTFS and always in the references; null when it is missing or an
 * id `java.time` doesn't know. Never the device zone: a deadline computed in the rider's zone rather
 * than the agency's could be hours late, the one error a rider can't recover from.
 */
private fun agencyZone(timezone: String?): ZoneId? = try {
    timezone?.let(ZoneId::of)
} catch (_: DateTimeException) {
    null
}
