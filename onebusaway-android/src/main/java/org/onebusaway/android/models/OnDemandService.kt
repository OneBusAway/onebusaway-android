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
package org.onebusaway.android.models

import java.time.DayOfWeek
import java.time.LocalDate
import org.onebusaway.android.util.GeoPoint

/**
 * A GTFS service-day time of day: seconds after the service day's anchor, allowed past 24:00:00
 * (`"25:00:00"` is 1 AM the following calendar day). It is a duration from an anchor, not a clock
 * instant; `BookingDeadlineEvaluator` turns it into a `java.time.Instant` for a date and zone.
 */
@JvmInline
value class ServiceDayTime(val seconds: Int) : Comparable<ServiceDayTime> {
    override fun compareTo(other: ServiceDayTime): Int = seconds.compareTo(other.seconds)

    val hours: Int get() = seconds / 3600
    val minutesOfHour: Int get() = (seconds % 3600) / 60

    companion object {
        /** Parses the wire `"HH:MM:SS"` form; hours may exceed 24. Throws on any other shape. */
        fun parse(hms: String): ServiceDayTime {
            val parts = hms.split(":")
            require(parts.size == 3) { "expected HH:MM:SS, got '$hms'" }
            val (h, m, s) = parts.map { it.toIntOrNull() ?: throw IllegalArgumentException("expected HH:MM:SS, got '$hms'") }
            require(h >= 0 && m in 0..59 && s in 0..59) { "expected HH:MM:SS, got '$hms'" }
            return ServiceDayTime(h * 3600 + m * 60 + s)
        }
    }
}

/** The shape of an on-demand service, classified by the server at import (wiki §2.3). Never inferred here. */
enum class OnDemandServiceKind(val wire: String) {
    ZONE("zone"),
    ZONE_TO_ZONE("zoneToZone"),
    STOP_GROUP("stopGroup"),
    DEVIATED_ROUTE("deviatedRoute"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(wire: String?): OnDemandServiceKind = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** Why a `services-for-location` element matched (wiki §3); [UNKNOWN] for a value this build doesn't know. */
enum class OnDemandMatchReason(val wire: String) {
    AREA_CONTAINS_POINT("areaContainsPoint"),
    STOP_WITHIN_RADIUS("stopWithinRadius"),
    AREA_NEARBY("areaNearby"),
    AREA_INTERSECTS_VIEWPORT("areaIntersectsViewport"),
    STOP_WITHIN_VIEWPORT("stopWithinViewport"),
    UNKNOWN("");

    companion object {
        fun fromWire(wire: String): OnDemandMatchReason = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** GTFS `booking_type`. */
enum class BookingType(val wire: Int) {
    REAL_TIME(0),
    SAME_DAY(1),
    PRIOR_DAYS(2);

    companion object {
        fun fromWire(wire: Int): BookingType? = entries.firstOrNull { it.wire == wire }
    }
}

/** One availability rule (wiki §2.2). All three window times null means the service runs all hours. */
data class AvailabilityRule(
    val fromIds: List<String>,
    val toIds: List<String>,
    val startPickupTime: ServiceDayTime?,
    val endPickupTime: ServiceDayTime?,
    val endDropOffTime: ServiceDayTime?,
    val calendarIds: List<String>,
    val pickupType: Int,
    val dropOffType: Int,
    val pickupBookingRuleId: String?,
    val dropOffBookingRuleId: String?,
    val safeDurationFactor: Double?,
    val safeDurationOffset: Double?
)

/**
 * A service area. [polygons] is `[polygon][ring][point]` (ring 0 exterior, then holes) at whatever
 * detail the request asked for — display only; containment is the server's `matchReason`.
 */
data class ServiceArea(
    val id: String,
    val name: String?,
    val description: String?,
    val southWest: GeoPoint,
    val northEast: GeoPoint,
    val polygons: List<List<List<GeoPoint>>>,
    val distanceToAreaMeters: Double?,
    val nearestPointOnBoundary: GeoPoint?
)

data class LocationGroup(val id: String, val name: String?, val stopIds: List<String>)

/** A booking rule (wiki §2.4). Conditionally-required fields may be null in real feeds (spec §6.2). */
data class BookingRule(
    val id: String,
    val bookingType: BookingType,
    val priorNoticeDurationMin: Int?,
    val priorNoticeDurationMax: Int?,
    val priorNoticeLastDay: Int?,
    val priorNoticeLastTime: ServiceDayTime?,
    val priorNoticeStartDay: Int?,
    val priorNoticeStartTime: ServiceDayTime?,
    val priorNoticeCalendarId: String?,
    val message: String?,
    val pickupMessage: String?,
    val dropOffMessage: String?,
    val phoneNumber: String?,
    val infoUrl: String?,
    val bookingUrl: String?
)

/** A compiled service calendar (wiki §2.4): weekdays inside a date range minus [exceptedDates]. */
data class FlexCalendar(
    val id: String,
    val days: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val exceptedDates: Set<LocalDate>
) {
    fun isActiveOn(date: LocalDate): Boolean = !date.isBefore(startDate) &&
        !date.isAfter(endDate) &&
        date.dayOfWeek in days &&
        date !in exceptedDates
}

/**
 * An on-demand service with the references it needs resolved onto it, so screens never touch the
 * wire pool. [agencyTimezone] is the agency's `timezone` — the zone every service-day time is in.
 * [routeColor] is the route's GTFS colour as ARGB, for the zone fill.
 */
data class OnDemandService(
    val id: String,
    val agencyId: String,
    val routeId: String?,
    val name: String,
    val kind: OnDemandServiceKind,
    val description: String? = null,
    val url: String? = null,
    val rules: List<AvailabilityRule> = emptyList(),
    val matchReason: OnDemandMatchReason? = null,
    val areas: List<ServiceArea> = emptyList(),
    val locationGroups: List<LocationGroup> = emptyList(),
    val bookingRules: Map<String, BookingRule> = emptyMap(),
    val calendars: Map<String, FlexCalendar> = emptyMap(),
    val agencyTimezone: String? = null,
    val routeColor: Int? = null
) {
    /** The booking rule that governs booking for [rule] — the pickup side (wiki §2.5). */
    fun pickupBookingRule(rule: AvailabilityRule): BookingRule? = rule.pickupBookingRuleId?.let(bookingRules::get)
}
