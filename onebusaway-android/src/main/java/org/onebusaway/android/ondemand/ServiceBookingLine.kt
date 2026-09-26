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

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.OnDemandService

/** The verdict the booking line states, and the ride date it is for. */
internal data class DatedEvaluation(val travelDate: LocalDate, val evaluation: BookingEvaluation)

internal val UNKNOWN_EVALUATION = BookingEvaluation(BookingState.UNKNOWN, cutoffInstant = null, openInstant = null)

/**
 * What the booking line states; see `BookingSummary` in the service presentation. Null when no date
 * can be promised. Shared by the service page and [computeAvailability] so the two never disagree.
 */
internal fun OnDemandService.bookingLine(now: Instant, zone: ZoneId): DatedEvaluation? {
    val bookableDate = serviceNextBookableDate(now, zone) ?: return earliestOpening(now, zone)
    return earliestOpenEvaluation(bookableDate, now, zone)?.let { DatedEvaluation(bookableDate, it) }
}

/**
 * For a service with nothing bookable on any date: each rule on the first of its service days whose
 * booking has not opened yet, and of those the one whose booking opens earliest. The walk has to go
 * past the rule's next service day: with a one-day notice window that day is already closed by the
 * evening before, while the day after it is still to open.
 */
internal fun OnDemandService.earliestOpening(now: Instant, zone: ZoneId): DatedEvaluation? {
    val today = now.atZone(zone).toLocalDate()
    return rules
        .filterNot(::hasUnresolvedPickupBookingRule)
        .mapNotNull { rule ->
            BookingDeadlineEvaluator.nextServiceDateInState(rule, pickupBookingRule(rule), BookingState.NOT_YET_OPEN, today, now, zone, calendars)
                ?.let { date -> DatedEvaluation(date, evaluateBooking(rule, date, now, zone)) }
        }
        .minByOrNull { it.evaluation.openInstant ?: Instant.MAX }
}

/** The earliest date any rule can be booked for right now, or null when none can (spec §2.5, service level). */
internal fun OnDemandService.serviceNextBookableDate(now: Instant, zone: ZoneId): LocalDate? {
    val today = now.atZone(zone).toLocalDate()
    return rules
        .filterNot(::hasUnresolvedPickupBookingRule)
        .mapNotNull { rule -> BookingDeadlineEvaluator.nextBookableServiceDate(rule, pickupBookingRule(rule), today, now, zone, calendars) }
        .minOrNull()
}

/** The verdict with the earliest cutoff among the rules that evaluate OPEN on [date]. */
internal fun OnDemandService.earliestOpenEvaluation(date: LocalDate, now: Instant, zone: ZoneId): BookingEvaluation? = rules
    .filter { rule -> rule.calendarIds.any { calendars[it]?.isActiveOn(date) == true } }
    .map { rule -> evaluateBooking(rule, date, now, zone) }
    .filter { it.state == BookingState.OPEN }
    .minByOrNull { it.cutoffInstant ?: Instant.MAX }

/**
 * The rule names a pickup booking rule the references don't resolve (absent, or dropped at the
 * adapter for a `booking_type` this build can't read). Notice *is* required, we just can't say how
 * much — so it is [BookingState.UNKNOWN], never the evaluator's null-rule "no notice, book any time".
 */
internal fun OnDemandService.hasUnresolvedPickupBookingRule(rule: AvailabilityRule): Boolean = rule.pickupBookingRuleId != null && pickupBookingRule(rule) == null

internal fun OnDemandService.evaluateBooking(rule: AvailabilityRule, date: LocalDate, now: Instant, zone: ZoneId): BookingEvaluation = if (hasUnresolvedPickupBookingRule(rule)) {
    UNKNOWN_EVALUATION
} else {
    BookingDeadlineEvaluator.evaluate(rule, pickupBookingRule(rule), date, now, zone, calendars)
}

/**
 * The agency timezone, required by GTFS and always in the references; null when it is missing or an
 * id `java.time` doesn't know. Never the device zone: a deadline computed in the rider's zone rather
 * than the agency's could be hours late, the one error a rider can't recover from.
 */
internal fun agencyZone(timezone: String?): ZoneId? = try {
    timezone?.let(ZoneId::of)
} catch (_: DateTimeException) {
    null
}
