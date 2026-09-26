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
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.util.GeoPoint

/** The JVM fixtures in `src/test` are not visible here; this is the subset the Compose tests need. */
internal val TUESDAY_TWO_PM: Instant = OffsetDateTime.parse("2026-03-10T14:00:00-04:00").toInstant()

internal val SQUARE = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))

internal fun sampleService(id: String = "CC_CC1", name: String = "Dial-a-Ride", phone: String? = "231-582-6900", url: String? = null, inside: Boolean = true): OnDemandService {
    val calendar = FlexCalendar("CC_cal", DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.toSet(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), emptySet())
    val booking = BookingRule("CC_b1", BookingType.SAME_DAY, 60, null, null, null, null, null, null, null, null, null, phone, null, url)
    val rule = AvailabilityRule(listOf("CC_area"), listOf("CC_area"), ServiceDayTime.parse("07:20:00"), ServiceDayTime.parse("16:40:00"), null, listOf("CC_cal"), 2, 2, "CC_b1", "CC_b1", null, null)
    val area = ServiceArea("CC_area", "Charlevoix County", null, GeoPoint(45.0, -85.2), GeoPoint(45.1, -85.0), listOf(listOf(SQUARE)), if (inside) 0.0 else 700.0, if (inside) null else GeoPoint(45.056, -85.1))
    return OnDemandService(
        id = id, agencyId = "CC", routeId = id, name = name, kind = OnDemandServiceKind.ZONE, rules = listOf(rule),
        matchReason = if (inside) OnDemandMatchReason.AREA_CONTAINS_POINT else OnDemandMatchReason.AREA_NEARBY,
        areas = listOf(area), bookingRules = mapOf("CC_b1" to booking), calendars = mapOf("CC_cal" to calendar), agencyTimezone = "America/Detroit"
    )
}

internal fun sampleMatch(service: OnDemandService = sampleService()): OnDemandMatch = matchFor(service, TUESDAY_TWO_PM)
