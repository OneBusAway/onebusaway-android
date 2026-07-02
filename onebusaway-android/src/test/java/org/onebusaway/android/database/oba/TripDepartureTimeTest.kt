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

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the `trips.departure` "minutes after midnight" encoding. Uses a fixed non-DST zone
 * (UTC) so the round-trip is deterministic regardless of when/where the test runs.
 */
class TripDepartureTimeTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun roundTrips_everyBoundaryMinuteOfDay() {
        // Endpoints, hour rollover, noon, and the last minute of the day.
        for (m in listOf(0, 1, 59, 60, 90, 719, 720, 1439)) {
            val millis = TripDepartureTime.toEpochMillis(m, utc)
            assertEquals("round-trip for $m", m, TripDepartureTime.fromEpochMillis(millis, utc))
        }
    }

    @Test
    fun toEpochMillis_isMinutesAfterLocalMidnight() {
        val midnight = TripDepartureTime.toEpochMillis(0, utc)
        // 90 minutes-after-midnight is exactly 90 minutes past the day's start.
        assertEquals(90 * 60_000L, TripDepartureTime.toEpochMillis(90, utc) - midnight)
    }

    @Test
    fun fromEpochMillis_extractsLocalHourAndMinute() {
        val instant = Instant.parse("2024-06-15T13:45:00Z").toEpochMilli()
        assertEquals(13 * 60 + 45, TripDepartureTime.fromEpochMillis(instant, utc))
    }
}
