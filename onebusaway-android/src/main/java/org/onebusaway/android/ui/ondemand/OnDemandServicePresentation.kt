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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingEvaluation
import org.onebusaway.android.ondemand.BookingState
import org.onebusaway.android.ondemand.LocationCheck
import org.onebusaway.android.ondemand.OnDemandAvailability
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.TIER_ADVANCE
import org.onebusaway.android.ondemand.TIER_OPEN_NOW
import org.onebusaway.android.ondemand.TIER_SAME_DAY
import org.onebusaway.android.ondemand.TIER_UNKNOWN_OR_CLOSED
import org.onebusaway.android.ondemand.agencyZone
import org.onebusaway.android.ondemand.bookingLine
import org.onebusaway.android.ondemand.computeAvailability

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

/**
 * The "Where" section (spec §3.6 item 5): the pickup areas, and the drop-off places when they differ.
 * [zoneCount] is the service's total area count, the fallback either row's name list resolves to when
 * every one of its areas is unnamed (spec gives no fallback of its own, so both rows share this one).
 */
data class WhereRows(val serviceAreaNames: List<String>, val dropOffNames: List<String>?, val zoneCount: Int)

/** Which fact the page promotes (spec §3.6 items 1–2): the status line, the deadline row, or neither. */
enum class DetailPromotion { STATUS_LINE, DEADLINE_ROW, NONE }

sealed interface OnDemandServiceUiState {
    data object Loading : OnDemandServiceUiState
    data object NotFound : OnDemandServiceUiState
    data object Error : OnDemandServiceUiState
    data class Content(
        val service: OnDemandService,
        val whenRows: List<WhenRow>,
        val booking: BookingSummary?,
        val availability: OnDemandAvailability,
        val whereRows: WhereRows?,
        /** Weekdays no live calendar serves, shown as "No service" so the absence is visible. */
        val noServiceDays: Set<DayOfWeek>,
        val locationCheck: LocationCheck?,
        /** The instant the page was presented at; relative copy ("tomorrow") is measured from it. */
        val presentedAt: Instant
    ) : OnDemandServiceUiState
}

/**
 * Projects a service for the page at [now] (the device wall clock, minted by the caller), with the
 * probe's [locationCheck] when the page was opened from one. Pure, so it is JVM-tested with a fixed instant.
 */
internal fun presentService(service: OnDemandService, now: Instant, locationCheck: LocationCheck? = null): OnDemandServiceUiState.Content {
    val availability = computeAvailability(service, now)
    val bookingRules = service.rules.mapNotNull(service::pickupBookingRule).distinctBy { it.id }
    // Spec §3.6 item 7: the booking rule's info URL, else the eligibility's, and never the agency
    // website again — the two rows must not open the same page.
    val infoUrl = (bookingRules.firstNotNullOfOrNull { it.infoUrl } ?: service.eligibility?.infoUrl)?.takeIf { it != service.url }
    val booking = if (service.rules.isEmpty()) {
        infoUrl?.let {
            BookingSummary(travelDate = null, evaluation = null, zone = null, phoneNumber = null, bookingUrl = null, infoUrl = it, messages = emptyList())
        }
    } else {
        val zone = agencyZone(service.agencyTimezone)
        val line = zone?.let { service.bookingLine(now, it) }
        BookingSummary(
            travelDate = line?.travelDate,
            evaluation = line?.evaluation,
            zone = zone,
            phoneNumber = bookingRules.firstNotNullOfOrNull { it.phoneNumber },
            bookingUrl = bookingRules.firstNotNullOfOrNull { it.bookingUrl },
            infoUrl = infoUrl,
            messages = bookingRules.flatMap { listOfNotNull(it.message, it.pickupMessage, it.dropOffMessage) }.distinct()
        )
    }
    val today = availability.zone?.let { now.atZone(it).toLocalDate() }
    return OnDemandServiceUiState.Content(
        service = service,
        whenRows = mergedWhenRows(service),
        booking = booking,
        availability = availability,
        whereRows = whereRows(service),
        noServiceDays = noServiceDays(service, today),
        locationCheck = locationCheck,
        presentedAt = now
    )
}

/** One row per distinct pickup window, its weekdays the union of every calendar with those hours (spec §3.6 item 6). */
private fun mergedWhenRows(service: OnDemandService): List<WhenRow> {
    val rows = service.rules.flatMap { rule ->
        rule.calendarIds.mapNotNull { id -> service.calendars[id]?.let { WhenRow(id, it.days, rule.startPickupTime, rule.endPickupTime) } }
    }
    return rows.groupBy { it.start to it.end }.values.map { group -> group.first().copy(days = group.flatMap { it.days }.toSet()) }
}

/**
 * Weekdays absent from every rule calendar whose end date is [today] or later. Empty without a
 * date, and empty when no rule calendar resolves at all (spec §2.5: no rules is Unknown, not a
 * service that runs zero days) — only a rule with a *resolvable but expired* calendar reports
 * every weekday as unserved.
 */
internal fun noServiceDays(service: OnDemandService, today: LocalDate?): Set<DayOfWeek> {
    if (today == null) return emptySet()
    val calendars = service.rules.flatMap { it.calendarIds }.mapNotNull { service.calendars[it] }
    if (calendars.isEmpty()) return emptySet()
    val served = calendars.filter { !it.endDate.isBefore(today) }.flatMap { it.days }.toSet()
    return DayOfWeek.entries.toSet() - served
}

/** Present when the service has more than one area or any rule's drop-off places differ from its pickups. */
internal fun whereRows(service: OnDemandService): WhereRows? {
    val differs = service.rules.any { it.toIds.toSet() != it.fromIds.toSet() }
    if (service.areas.size <= 1 && !differs) return null
    val fromIds = service.rules.flatMapTo(mutableSetOf()) { it.fromIds }
    val toIds = service.rules.flatMapTo(mutableSetOf()) { it.toIds }
    return WhereRows(
        serviceAreaNames = service.placeNames(fromIds),
        dropOffNames = if (differs) service.placeNames(toIds) else null,
        zoneCount = service.areas.size
    )
}

// Areas first, then location groups; member stops are not carried on the service, so a bare stop id names nothing.
private fun OnDemandService.placeNames(ids: Set<String>): List<String> = ids
    .mapNotNull { id -> areas.firstOrNull { it.id == id }?.name ?: locationGroups.firstOrNull { it.id == id }?.name }
    .filter { it.isNotBlank() }
    .distinct()

internal fun detailPromotion(availability: OnDemandAvailability): DetailPromotion = when {
    availability.usabilityTier == TIER_ADVANCE || availability.status is OnDemandStatus.BookingOpens -> DetailPromotion.DEADLINE_ROW
    availability.usabilityTier == TIER_OPEN_NOW || availability.usabilityTier == TIER_SAME_DAY -> DetailPromotion.STATUS_LINE
    availability.usabilityTier == TIER_UNKNOWN_OR_CLOSED && availability.status == OnDemandStatus.Closed -> DetailPromotion.STATUS_LINE
    else -> DetailPromotion.NONE
}

/** "Mon–Sat", "Sat–Sun", "Sun", "Mon, Wed–Fri": consecutive runs joined with an en dash. */
internal fun formatDayRanges(days: Set<DayOfWeek>, locale: Locale): String {
    val ordered = DayOfWeek.entries.filter { it in days }
    val runs = mutableListOf<List<DayOfWeek>>()
    for (day in ordered) {
        val last = runs.lastOrNull()
        if (last != null && last.last().value + 1 == day.value) runs[runs.lastIndex] = last + day else runs += listOf(day)
    }
    return runs.joinToString(", ") { run ->
        val first = run.first().getDisplayName(TextStyle.SHORT, locale)
        if (run.size == 1) first else "$first–${run.last().getDisplayName(TextStyle.SHORT, locale)}"
    }
}
