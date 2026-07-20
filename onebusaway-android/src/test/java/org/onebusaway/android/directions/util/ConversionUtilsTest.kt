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
import java.time.ZoneOffset

/**
 * JVM unit tests for the pure date helpers extracted during the java.time migration (#1690).
 * These pin the round-to-minute (>= 30s rounds up) and today/tomorrow classification, in an explicit
 * zone (production reads in the device's zone), so a future refactor can't silently regress the
 * rounding boundary.
 */
class ConversionUtilsTest {

    private fun epochMs(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun wallClock_belowThreshold_doesNotRound() {
        val zdt = ConversionUtils.wallClock(epochMs("2024-01-15T10:23:29Z"), ZoneOffset.UTC)
        assertEquals(10, zdt.hour)
        assertEquals(23, zdt.minute)
        assertEquals(LocalDate.of(2024, 1, 15), zdt.toLocalDate())
    }

    @Test
    fun wallClock_atThreshold_roundsUp() {
        val zdt = ConversionUtils.wallClock(epochMs("2024-01-15T10:23:30Z"), ZoneOffset.UTC)
        assertEquals(10, zdt.hour)
        assertEquals(24, zdt.minute)
    }

    @Test
    fun wallClock_roundingCrossesMidnight() {
        val zdt = ConversionUtils.wallClock(epochMs("2024-01-15T23:59:30Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.of(2024, 1, 16), zdt.toLocalDate())
        assertEquals(0, zdt.hour)
        assertEquals(0, zdt.minute)
    }

    @Test
    fun wallClock_readInZone_shiftsWallClockAndDate() {
        // 02:00Z read in UTC-3 is 23:00 on the previous day.
        val zdt = ConversionUtils.wallClock(epochMs("2024-01-15T02:00:00Z"), ZoneOffset.ofHours(-3))
        assertEquals(LocalDate.of(2024, 1, 14), zdt.toLocalDate())
        assertEquals(23, zdt.hour)
        assertEquals(0, zdt.minute)
    }

    @Test
    fun wallClock_roundDisabled_keepsSubMinute() {
        val zdt = ConversionUtils.wallClock(epochMs("2024-01-15T10:23:59Z"), ZoneOffset.UTC, round = false)
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
