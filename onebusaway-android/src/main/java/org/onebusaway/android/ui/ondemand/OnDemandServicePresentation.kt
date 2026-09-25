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
 * is the sole OPEN rule, which then reads "no notice required". Both null when no date can be
 * promised (every rule's notice is unknown, or the calendars have ended).
 */
data class BookingSummary(
    val travelDate: LocalDate?,
    val evaluation: BookingEvaluation?,
    val zone: ZoneId,
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
    val zone = agencyZone(service.agencyTimezone)
    val whenRows = service.rules.flatMap { rule ->
        rule.calendarIds.mapNotNull { id -> service.calendars[id]?.let { WhenRow(id, it.days, rule.startPickupTime, rule.endPickupTime) } }
    }
    val bookingRules = service.rules.mapNotNull(service::pickupBookingRule).distinctBy { it.id }
    val booking = if (service.rules.isEmpty()) {
        null
    } else {
        val today = now.atZone(zone).toLocalDate()
        val travelDate = service.rules
            .filterNot(service::hasUnresolvedPickupBookingRule)
            .mapNotNull { rule -> BookingDeadlineEvaluator.nextBookableServiceDate(rule, service.pickupBookingRule(rule), today, now, zone, service.calendars) }
            .minOrNull()
        val evaluation = travelDate?.let { date ->
            service.rules
                .filter { rule -> rule.calendarIds.any { service.calendars[it]?.isActiveOn(date) == true } }
                .map { rule -> service.evaluateBooking(rule, date, now, zone) }
                .filter { it.state == BookingState.OPEN }
                .minByOrNull { it.cutoffInstant ?: Instant.MAX }
        }
        BookingSummary(
            travelDate = travelDate,
            evaluation = evaluation,
            zone = zone,
            phoneNumber = bookingRules.firstNotNullOfOrNull { it.phoneNumber },
            bookingUrl = bookingRules.firstNotNullOfOrNull { it.bookingUrl },
            infoUrl = bookingRules.firstNotNullOfOrNull { it.infoUrl },
            messages = bookingRules.flatMap { listOfNotNull(it.message, it.pickupMessage, it.dropOffMessage) }.distinct()
        )
    }
    return OnDemandServiceUiState.Content(service, whenRows, booking)
}

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
 * The agency timezone is required by GTFS and always in the references; the device zone is only a
 * last resort for a feed that published an id `java.time` doesn't know, so the page still renders.
 */
private fun agencyZone(timezone: String?): ZoneId = try {
    timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
