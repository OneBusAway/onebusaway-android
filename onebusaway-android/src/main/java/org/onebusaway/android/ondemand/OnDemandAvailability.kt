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

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceDayTime

/** How much notice a service's pickup booking rules demand, least demanding first (spec §2.5). */
enum class BookingTier { REAL_TIME, SAME_DAY, ADVANCE }

/** The chips a surface shows for a service: exactly one booking tag, plus eligibility when required. */
enum class OnDemandTag { NO_NOTICE_NEEDED, SAME_DAY_BOOKING, ADVANCE_BOOKING, ELIGIBILITY_REQUIRED }

/** The one line every surface leads with (spec §2.5 `status`), chosen in the order listed there. */
sealed interface OnDemandStatus {
    /** Running and bookable; [until] is null for a continuous service ("Open", not "until 12:00 AM"). */
    data class OpenNow(val until: Instant?) : OnDemandStatus

    /** Not running; the service starts running at [at]. */
    data class OpensAt(val at: Instant) : OnDemandStatus

    /** Advance booking whose window has not opened yet — the *booking* opens at [at], for [travelDate]. */
    data class BookingOpens(val at: Instant, val travelDate: LocalDate) : OnDemandStatus

    /** Advance booking that is open now and closes at [deadline] for a ride on [travelDate]. */
    data class BookBy(val deadline: Instant, val travelDate: LocalDate) : OnDemandStatus

    data object Closed : OnDemandStatus
    data object Unknown : OnDemandStatus
}

const val TIER_OPEN_NOW = 1
const val TIER_SAME_DAY = 2
const val TIER_ADVANCE = 3
const val TIER_ELIGIBILITY = 4
const val TIER_UNKNOWN_OR_CLOSED = 5

/**
 * Everything a surface says about whether a service can be used now, computed once from the
 * service, its rules, calendars and booking rules in the agency timezone at [now] (the device
 * clock). Surfaces read this; they never re-derive it.
 */
data class OnDemandAvailability(
    val runningNow: Boolean,
    val runningUntil: Instant?,
    val nextRunStart: Instant?,
    val bookingTier: BookingTier?,
    val bookableNow: Boolean,
    val nextBookableServiceDate: LocalDate?,
    val status: OnDemandStatus,
    val tags: Set<OnDemandTag>,
    val usabilityTier: Int,
    /** When the dock must re-evaluate (plus one second); null when nothing is scheduled to change. */
    val nextChangeInstant: Instant?,
    /** The agency timezone every instant above is formatted in; null only for [UNKNOWN]. */
    val zone: ZoneId?
) {
    companion object {
        /** The answer when the agency timezone is missing: every field null or false, tier 5. */
        val UNKNOWN = OnDemandAvailability(
            runningNow = false,
            runningUntil = null,
            nextRunStart = null,
            bookingTier = null,
            bookableNow = false,
            nextBookableServiceDate = null,
            status = OnDemandStatus.Unknown,
            tags = emptySet(),
            usabilityTier = TIER_UNKNOWN_OR_CLOSED,
            nextChangeInstant = null,
            zone = null
        )
    }
}

/** How far ahead the run/tier walks look, matching the evaluator's lookahead. */
private const val LOOKAHEAD_DAYS = 400L
private val MIDNIGHT = ServiceDayTime(0)
private val END_OF_SERVICE_DAY = ServiceDayTime(24 * 3600)

/** One rule's pickup window on one service date, as instants. */
private class PickupWindow(val rule: AvailabilityRule, val date: LocalDate, val start: Instant, val end: Instant)

fun computeAvailability(service: OnDemandService, now: Instant): OnDemandAvailability {
    val zone = agencyZone(service.agencyTimezone) ?: return OnDemandAvailability.UNKNOWN
    val today = now.atZone(zone).toLocalDate()
    val windows = ServiceWindows(service, zone)
    // Yesterday's windows are checked too because a window may pass 24:00.
    val containing = (windows.on(today.minusDays(1)) + windows.on(today)).filter { !now.isBefore(it.start) && now.isBefore(it.end) }
    val runningNow = containing.isNotEmpty()
    val latest = containing.maxByOrNull { it.end }
    val runningUntil = latest?.end?.takeUnless { end -> windows.on(latest.date.plusDays(1)).any { it.start == end } }
    val nextRunStart = if (runningNow) null else windows.nextStartAfter(now, today)
    val firstActiveDate = windows.firstActiveDate(today)
    val bookingTier = bookingTier(service, firstActiveDate)
    val bookableNow = containing.any { service.evaluateBooking(it.rule, it.date, now, zone).state == BookingState.OPEN }
    val nextBookable = service.serviceNextBookableDate(now, zone)
    val line = service.bookingLine(now, zone)
    val status = status(bookingTier, line, anyActive = firstActiveDate != null, hasRules = service.rules.isNotEmpty(), runningNow, bookableNow, runningUntil, nextRunStart)
    val tags = tags(bookingTier, service.eligibility)
    val bookingChange = line?.evaluation?.let { evaluation ->
        listOfNotNull(evaluation.openInstant, evaluation.cutoffInstant).filter { it.isAfter(now) }.minOrNull()
    }
    return OnDemandAvailability(
        runningNow = runningNow,
        runningUntil = runningUntil,
        nextRunStart = nextRunStart,
        bookingTier = bookingTier,
        bookableNow = bookableNow,
        nextBookableServiceDate = nextBookable,
        status = status,
        tags = tags,
        usabilityTier = usabilityTier(tags, status, nextBookable, today),
        nextChangeInstant = listOfNotNull(runningUntil, nextRunStart, bookingChange).minOrNull(),
        zone = zone
    )
}

/** The ordered decision of spec §2.6; the first matching line wins. */
internal fun usabilityTier(tags: Set<OnDemandTag>, status: OnDemandStatus, nextBookableServiceDate: LocalDate?, today: LocalDate): Int = when {
    OnDemandTag.ELIGIBILITY_REQUIRED in tags -> TIER_ELIGIBILITY
    status is OnDemandStatus.OpenNow -> TIER_OPEN_NOW
    status is OnDemandStatus.Closed || status is OnDemandStatus.Unknown || nextBookableServiceDate == null -> TIER_UNKNOWN_OR_CLOSED
    nextBookableServiceDate == today -> TIER_SAME_DAY
    else -> TIER_ADVANCE
}

private class ServiceWindows(private val service: OnDemandService, private val zone: ZoneId) {

    fun on(date: LocalDate): List<PickupWindow> = service.rules
        .filter { rule -> rule.calendarIds.any { service.calendars[it]?.isActiveOn(date) == true } }
        .map { rule ->
            PickupWindow(
                rule = rule,
                date = date,
                start = BookingDeadlineEvaluator.instantOf(date, rule.startPickupTime ?: MIDNIGHT, zone),
                end = BookingDeadlineEvaluator.instantOf(date, rule.endPickupTime ?: END_OF_SERVICE_DAY, zone)
            )
        }

    /** The earliest window start after [now] from yesterday through [LOOKAHEAD_DAYS] ahead, or null. */
    fun nextStartAfter(now: Instant, today: LocalDate): Instant? {
        val lastCalendarDay = latestCalendarEnd() ?: return null
        var best: Instant? = null
        var date = today.minusDays(1)
        val last = minOf(today.plusDays(LOOKAHEAD_DAYS), lastCalendarDay)
        while (!date.isAfter(last)) {
            // Every window on a day starts at or after that day's anchor, so once the anchor passes the
            // best candidate no later day can beat it.
            val current = best
            if (current != null && !BookingDeadlineEvaluator.serviceDayAnchor(date, zone).isBefore(current)) break
            val candidate = on(date).map { it.start }.filter { it.isAfter(now) }.minOrNull()
            if (candidate != null && (current == null || candidate.isBefore(current))) best = candidate
            date = date.plusDays(1)
        }
        return best
    }

    /** The first date from [today] on which any rule is active, bounded by the calendars and the lookahead. */
    fun firstActiveDate(today: LocalDate): LocalDate? {
        val lastCalendarDay = latestCalendarEnd() ?: return null
        val last = minOf(today.plusDays(LOOKAHEAD_DAYS), lastCalendarDay)
        return generateSequence(today) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .firstOrNull { date -> on(date).isNotEmpty() }
    }

    private fun latestCalendarEnd(): LocalDate? = service.rules
        .flatMap { it.calendarIds }
        .mapNotNull { service.calendars[it]?.endDate }
        .maxOrNull()
}

/**
 * The least demanding pickup booking rule among the rules active on [date]: a null id is real-time,
 * a dangling id is skipped. Null when no date is active or nothing resolves.
 */
private fun bookingTier(service: OnDemandService, date: LocalDate?): BookingTier? {
    if (date == null) return null
    return service.rules
        .filter { rule -> rule.calendarIds.any { service.calendars[it]?.isActiveOn(date) == true } }
        .mapNotNull { rule ->
            val id = rule.pickupBookingRuleId ?: return@mapNotNull BookingTier.REAL_TIME
            when (service.bookingRules[id]?.bookingType) {
                BookingType.REAL_TIME -> BookingTier.REAL_TIME
                BookingType.SAME_DAY -> BookingTier.SAME_DAY
                BookingType.PRIOR_DAYS -> BookingTier.ADVANCE
                null -> null
            }
        }
        .minOrNull()
}

private fun status(
    tier: BookingTier?,
    line: DatedEvaluation?,
    anyActive: Boolean,
    hasRules: Boolean,
    runningNow: Boolean,
    bookableNow: Boolean,
    runningUntil: Instant?,
    nextRunStart: Instant?
): OnDemandStatus = when {
    tier == BookingTier.ADVANCE -> advanceStatus(line, anyActive)
    runningNow && bookableNow -> OnDemandStatus.OpenNow(runningUntil)
    nextRunStart != null -> OnDemandStatus.OpensAt(nextRunStart)
    // Rules that never activate again are closed; no rules at all is not knowable (spec §2.5 steps 4–5).
    !anyActive && hasRules -> OnDemandStatus.Closed
    else -> OnDemandStatus.Unknown
}

/** An advance service leads with its booking window, not with whether vehicles are running. */
private fun advanceStatus(line: DatedEvaluation?, anyActive: Boolean): OnDemandStatus {
    if (line == null) return if (anyActive) OnDemandStatus.Unknown else OnDemandStatus.Closed
    val evaluation = line.evaluation
    val opens = evaluation.openInstant
    val cutoff = evaluation.cutoffInstant
    return when {
        evaluation.state == BookingState.NOT_YET_OPEN && opens != null -> OnDemandStatus.BookingOpens(opens, line.travelDate)
        evaluation.state == BookingState.OPEN && cutoff != null -> OnDemandStatus.BookBy(cutoff, line.travelDate)
        else -> OnDemandStatus.Unknown
    }
}

private fun tags(tier: BookingTier?, eligibility: OnDemandEligibility?): Set<OnDemandTag> = buildSet {
    when (tier) {
        BookingTier.REAL_TIME -> add(OnDemandTag.NO_NOTICE_NEEDED)
        BookingTier.SAME_DAY -> add(OnDemandTag.SAME_DAY_BOOKING)
        BookingTier.ADVANCE -> add(OnDemandTag.ADVANCE_BOOKING)
        null -> Unit
    }
    if (eligibility?.requirement == EligibilityRequirement.CERTIFICATION_REQUIRED) add(OnDemandTag.ELIGIBILITY_REQUIRED)
}
