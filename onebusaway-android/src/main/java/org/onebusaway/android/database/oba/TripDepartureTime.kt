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
package org.onebusaway.android.database.oba

import androidx.annotation.VisibleForTesting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Encodes/decodes the legacy `trips.departure` column, which stores a reminder's departure as
 * "minutes after midnight" (in the device's local time zone) rather than an absolute time — carried
 * over from the ObaProvider schema.
 *
 * Implemented on `java.time` (was the deprecated `android.text.format.Time`) so the math is pure JVM
 * and unit-testable off-device; the public methods use the system default zone, matching the original
 * "current day, local time" behavior. `java.time` resolves the two annual DST-transition days via the
 * zone rules (`atStartOfDay`), which is at worst more correct than the legacy `Time.toMillis` there.
 */
object TripDepartureTime {

    /** Decodes a stored "minutes after midnight" value into an epoch-millis time in the current day. */
    fun toEpochMillis(minutesAfterMidnight: Int): Long =
        toEpochMillis(minutesAfterMidnight, ZoneId.systemDefault())

    /** Encodes an epoch-millis time into the stored "minutes after midnight" value. */
    fun fromEpochMillis(epochMillis: Long): Int =
        fromEpochMillis(epochMillis, ZoneId.systemDefault())

    /** [toEpochMillis] with an explicit [zone] (test seam; avoids DST flakiness from the device zone). */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun toEpochMillis(minutesAfterMidnight: Int, zone: ZoneId): Long =
        LocalDate.now(zone)
            .atStartOfDay(zone)
            .plusMinutes(minutesAfterMidnight.toLong())
            .toInstant()
            .toEpochMilli()

    /** [fromEpochMillis] with an explicit [zone] (test seam). */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun fromEpochMillis(epochMillis: Long, zone: ZoneId): Int {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }
}
