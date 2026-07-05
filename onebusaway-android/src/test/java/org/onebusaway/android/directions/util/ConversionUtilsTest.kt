/*
 * Copyright 2026 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * JVM unit tests for the pure date helpers extracted during the java.time migration (#1690).
 * These pin the behavior the legacy Calendar path had — GMT wall-clock shifting, round-to-minute
 * (>= 30s rounds up), and the today/tomorrow classification — so a future refactor can't silently
 * regress the rounding boundary.
 */
class ConversionUtilsTest {

    private val ONE_HOUR_MS = 3_600_000

    private fun epochMs(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun agencyWallClock_noOffset_belowThreshold_doesNotRound() {
        val zdt = ConversionUtils.agencyWallClock(epochMs("2024-01-15T10:23:29Z"), 0)
        assertEquals(10, zdt.hour)
        assertEquals(23, zdt.minute)
        assertEquals(LocalDate.of(2024, 1, 15), zdt.toLocalDate())
    }

    @Test
    fun agencyWallClock_noOffset_atThreshold_roundsUp() {
        val zdt = ConversionUtils.agencyWallClock(epochMs("2024-01-15T10:23:30Z"), 0)
        assertEquals(10, zdt.hour)
        assertEquals(24, zdt.minute)
    }

    @Test
    fun agencyWallClock_roundingCrossesMidnight() {
        val zdt = ConversionUtils.agencyWallClock(epochMs("2024-01-15T23:59:30Z"), 0)
        assertEquals(LocalDate.of(2024, 1, 16), zdt.toLocalDate())
        assertEquals(0, zdt.hour)
        assertEquals(0, zdt.minute)
    }

    @Test
    fun agencyWallClock_negativeOffset_shiftsWallClockAndDate() {
        // 02:00Z shifted back 3h reads as 23:00 on the previous day in GMT wall-clock.
        val zdt = ConversionUtils.agencyWallClock(epochMs("2024-01-15T02:00:00Z"), -3 * ONE_HOUR_MS)
        assertEquals(LocalDate.of(2024, 1, 14), zdt.toLocalDate())
        assertEquals(23, zdt.hour)
        assertEquals(0, zdt.minute)
    }

    @Test
    fun agencyWallClock_roundDisabled_keepsSubMinute() {
        val zdt = ConversionUtils.agencyWallClock(epochMs("2024-01-15T10:23:59Z"), 0, round = false)
        assertEquals(23, zdt.minute)
        assertEquals(59, zdt.second)
    }

    @Test
    fun isToday_and_isTomorrow() {
        val today = LocalDate.of(2024, 6, 30)
        assertTrue(ConversionUtils.isToday(LocalDate.of(2024, 6, 30), today))
        assertFalse(ConversionUtils.isToday(LocalDate.of(2024, 7, 1), today))

        // Crosses a month boundary — plain LocalDate arithmetic handles it.
        assertTrue(ConversionUtils.isTomorrow(LocalDate.of(2024, 7, 1), today))
        assertFalse(ConversionUtils.isTomorrow(LocalDate.of(2024, 6, 30), today))
    }
}
