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

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.util.GeoPoint

/** Charlevoix County, the fixture feed every manual check uses; Eastern time, on DST from 2026-03-08. */
internal const val CHARLEVOIX_TZ = "America/Detroit"

internal val MON_TO_SAT: Set<DayOfWeek> = DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.toSet()
internal val ALL_WEEK: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/** A 0.1° square (about 11 km tall) south of Charlevoix: lat 45.0–45.1, lon −85.2 to −85.0, closed ring. */
internal val SQUARE = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))

internal fun instant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

internal fun calendar(
    id: String = "CC_cal",
    days: Set<DayOfWeek> = MON_TO_SAT,
    start: LocalDate = LocalDate.of(2026, 1, 1),
    end: LocalDate = LocalDate.of(2026, 12, 31),
    excepted: Set<LocalDate> = emptySet()
) = FlexCalendar(id, days, start, end, excepted)

internal fun bookingRule(
    id: String = "CC_b1",
    type: BookingType = BookingType.SAME_DAY,
    durationMin: Int? = 60,
    lastDay: Int? = null,
    lastTime: String? = null,
    phone: String? = "231-582-6900",
    bookingUrl: String? = null,
    infoUrl: String? = null,
    message: String? = null
) = BookingRule(
    id = id,
    bookingType = type,
    priorNoticeDurationMin = durationMin,
    priorNoticeDurationMax = null,
    priorNoticeLastDay = lastDay,
    priorNoticeLastTime = lastTime?.let(ServiceDayTime::parse),
    priorNoticeStartDay = null,
    priorNoticeStartTime = null,
    priorNoticeCalendarId = null,
    message = message,
    pickupMessage = null,
    dropOffMessage = null,
    phoneNumber = phone,
    infoUrl = infoUrl,
    bookingUrl = bookingUrl
)

internal fun rule(
    calendarIds: List<String> = listOf("CC_cal"),
    start: String? = "07:20:00",
    end: String? = "16:40:00",
    bookingRuleId: String? = "CC_b1",
    fromIds: List<String> = listOf("CC_area"),
    toIds: List<String> = fromIds
) = AvailabilityRule(
    fromIds = fromIds,
    toIds = toIds,
    startPickupTime = start?.let(ServiceDayTime::parse),
    endPickupTime = end?.let(ServiceDayTime::parse),
    endDropOffTime = null,
    calendarIds = calendarIds,
    pickupType = 2,
    dropOffType = 2,
    pickupBookingRuleId = bookingRuleId,
    dropOffBookingRuleId = bookingRuleId,
    safeDurationFactor = null,
    safeDurationOffset = null
)

internal fun area(
    id: String = "CC_area",
    name: String? = "Charlevoix County",
    rings: List<List<GeoPoint>> = listOf(SQUARE),
    distance: Double? = null,
    nearest: GeoPoint? = null
): ServiceArea {
    val points = rings.flatten()
    return ServiceArea(
        id = id,
        name = name,
        description = null,
        southWest = GeoPoint(points.minOf { it.latitude }, points.minOf { it.longitude }),
        northEast = GeoPoint(points.maxOf { it.latitude }, points.maxOf { it.longitude }),
        polygons = listOf(rings),
        distanceToAreaMeters = distance,
        nearestPointOnBoundary = nearest
    )
}

internal fun service(
    id: String = "CC_CC1",
    name: String = "Dial-a-Ride",
    rules: List<AvailabilityRule> = listOf(rule()),
    calendars: Map<String, FlexCalendar> = mapOf("CC_cal" to calendar()),
    bookingRules: Map<String, BookingRule> = mapOf("CC_b1" to bookingRule()),
    areas: List<ServiceArea> = listOf(area()),
    timezone: String? = CHARLEVOIX_TZ,
    routeColor: Int? = null,
    matchReason: OnDemandMatchReason? = null,
    eligibility: OnDemandEligibility? = null,
    url: String? = null,
    description: String? = null,
    kind: OnDemandServiceKind = OnDemandServiceKind.ZONE
) = OnDemandService(
    id = id,
    agencyId = "CC",
    routeId = id,
    name = name,
    kind = kind,
    description = description,
    url = url,
    rules = rules,
    matchReason = matchReason,
    areas = areas,
    bookingRules = bookingRules,
    calendars = calendars,
    agencyTimezone = timezone,
    routeColor = routeColor,
    eligibility = eligibility
)
