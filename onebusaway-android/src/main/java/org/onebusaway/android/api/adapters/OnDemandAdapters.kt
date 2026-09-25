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
package org.onebusaway.android.api.adapters

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import org.onebusaway.android.api.contract.AvailabilityRuleDto
import org.onebusaway.android.api.contract.BookingRuleDto
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.FlexCalendarDto
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.ServiceAreaDto
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.LocationGroup
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.util.GeoPoint

/**
 * The entry response holds exactly one service, so every reference in its pool belongs to it — which
 * is what lets a zero-rule (degenerate, wiki §2.3) service still carry its areas.
 */
internal fun EntryWithReferences<OnDemandServiceDto>.toOnDemandService(): OnDemandService = entry.toOnDemandService(references, allReferencesBelongToService = true)

/**
 * A list response shares one pool across services, so each takes only what its rules reference. A
 * service that fails to adapt (a malformed time, date or bbox in what it references) is dropped on
 * its own: one bad record must not blank every other zone in the viewport. The single-service entry
 * above stays strict, since there the malformed service is the whole answer.
 */
internal fun ListWithReferences<OnDemandServiceDto>.toOnDemandServices(): List<OnDemandService> = list.mapNotNull { dto ->
    try {
        dto.toOnDemandService(references, allReferencesBelongToService = false)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeException) {
        null
    }
}

private fun OnDemandServiceDto.toOnDemandService(references: References, allReferencesBelongToService: Boolean): OnDemandService {
    val rules = this.rules.map { it.toAvailabilityRule() }
    val placeIds = rules.flatMapTo(mutableSetOf()) { it.fromIds + it.toIds }
    val bookingRules = rules.flatMap { listOfNotNull(it.pickupBookingRuleId, it.dropOffBookingRuleId) }
        .toSet()
        .mapNotNull { references.bookingRule(it)?.toBookingRule() }
        .associateBy { it.id }
    val calendarIds = rules.flatMapTo(mutableSetOf()) { it.calendarIds } +
        bookingRules.values.mapNotNull { it.priorNoticeCalendarId }
    val areas = references.serviceAreas.filter { allReferencesBelongToService || it.id in placeIds }
    val groups = references.locationGroups.filter { allReferencesBelongToService || it.id in placeIds }
    return OnDemandService(
        id = id,
        agencyId = agencyId,
        routeId = routeId,
        name = name,
        kind = OnDemandServiceKind.fromWire(serviceKind),
        description = description,
        url = url,
        rules = rules,
        matchReason = matchReason?.let(OnDemandMatchReason::fromWire),
        areas = areas.map { it.toServiceArea() },
        locationGroups = groups.map { LocationGroup(it.id, it.name, it.stopIds) },
        bookingRules = bookingRules,
        calendars = calendarIds.mapNotNull { references.calendar(it)?.toFlexCalendar() }.associateBy { it.id },
        agencyTimezone = references.agency(agencyId)?.timezone,
        routeColor = routeId?.let { references.route(it)?.colorArgb() }
    )
}

internal fun AvailabilityRuleDto.toAvailabilityRule(): AvailabilityRule = AvailabilityRule(
    fromIds = fromIds,
    toIds = toIds,
    startPickupTime = startPickupTime?.let(ServiceDayTime::parse),
    endPickupTime = endPickupTime?.let(ServiceDayTime::parse),
    endDropOffTime = endDropOffTime?.let(ServiceDayTime::parse),
    calendarIds = calendarIds,
    pickupType = pickupType,
    dropOffType = dropOffType,
    pickupBookingRuleId = pickupBookingRuleId,
    dropOffBookingRuleId = dropOffBookingRuleId,
    safeDurationFactor = safeDurationFactor,
    safeDurationOffset = safeDurationOffset
)

/** Null for a booking type outside `0..2`: a rule this build cannot evaluate is better absent than misread. */
internal fun BookingRuleDto.toBookingRule(): BookingRule? {
    val type = BookingType.fromWire(bookingType) ?: return null
    return BookingRule(
        id = id,
        bookingType = type,
        priorNoticeDurationMin = priorNoticeDurationMin,
        priorNoticeDurationMax = priorNoticeDurationMax,
        priorNoticeLastDay = priorNoticeLastDay,
        priorNoticeLastTime = priorNoticeLastTime?.let(ServiceDayTime::parse),
        priorNoticeStartDay = priorNoticeStartDay,
        priorNoticeStartTime = priorNoticeStartTime?.let(ServiceDayTime::parse),
        priorNoticeCalendarId = priorNoticeCalendarId,
        message = message,
        pickupMessage = pickupMessage,
        dropOffMessage = dropOffMessage,
        phoneNumber = phoneNumber,
        infoUrl = infoUrl,
        bookingUrl = bookingUrl
    )
}

private val WIRE_DAYS = mapOf(
    "mon" to DayOfWeek.MONDAY,
    "tue" to DayOfWeek.TUESDAY,
    "wed" to DayOfWeek.WEDNESDAY,
    "thu" to DayOfWeek.THURSDAY,
    "fri" to DayOfWeek.FRIDAY,
    "sat" to DayOfWeek.SATURDAY,
    "sun" to DayOfWeek.SUNDAY
)

internal fun FlexCalendarDto.toFlexCalendar(): FlexCalendar = FlexCalendar(
    id = id,
    days = days.mapNotNullTo(mutableSetOf()) { WIRE_DAYS[it] },
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    exceptedDates = exceptedDates.mapTo(mutableSetOf()) { LocalDate.parse(it) }
)

internal fun ServiceAreaDto.toServiceArea(): ServiceArea {
    require(bbox.size == 4) { "serviceArea $id bbox must be [minLon, minLat, maxLon, maxLat]" }
    return ServiceArea(
        id = id,
        name = name,
        description = description,
        southWest = GeoPoint(latitude = bbox[1], longitude = bbox[0]),
        northEast = GeoPoint(latitude = bbox[3], longitude = bbox[2]),
        polygons = polygons(),
        distanceToAreaMeters = distanceToArea,
        nearestPointOnBoundary = nearestPointOnBoundary?.let { GeoPoint(latitude = it[1], longitude = it[0]) }
    )
}
