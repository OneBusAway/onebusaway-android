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
package org.onebusaway.android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.util.DisplayFormat.EtaPart

/**
 * JVM unit tests for [DisplayFormat.formatEtaParts]'s minutes/hours split (issue #1777): one
 * consistent "Xhr Ymin" shape of alternating bold-number/small-unit parts for every non-zero ETA,
 * with the "Xhr" segment simply omitted under an hour, rather than switching between a bare-minute
 * format and an hours+minutes format depending on magnitude.
 */
class DisplayFormatTest {

    private fun format(minutes: Long): List<EtaPart> = DisplayFormat.formatEtaParts(minutes, minutesAbbrev = "min", hoursAbbrev = "hr")

    @Test
    fun `zero minutes omits the hour segment`() {
        assertEquals(
            listOf(EtaPart("0", emphasized = true), EtaPart("min", emphasized = false)),
            format(0)
        )
    }

    @Test
    fun `under an hour omits the hour segment`() {
        assertEquals(
            listOf(EtaPart("23", emphasized = true), EtaPart("min", emphasized = false)),
            format(23)
        )
    }

    @Test
    fun `fifty nine minutes still omits the hour segment`() {
        assertEquals(
            listOf(EtaPart("59", emphasized = true), EtaPart("min", emphasized = false)),
            format(59)
        )
    }

    @Test
    fun `exactly one hour includes a zero leftover-minutes segment`() {
        assertEquals(
            listOf(
                EtaPart("1", emphasized = true),
                EtaPart("hr", emphasized = false),
                EtaPart(" 0", emphasized = true),
                EtaPart("min", emphasized = false)
            ),
            format(60)
        )
    }

    @Test
    fun `an hour and change splits into bold hour and bold leftover minutes, with a gap between the halves`() {
        assertEquals(
            listOf(
                EtaPart("1", emphasized = true),
                EtaPart("hr", emphasized = false),
                EtaPart(" 23", emphasized = true),
                EtaPart("min", emphasized = false)
            ),
            format(83)
        )
    }

    @Test
    fun `multiple hours split correctly`() {
        assertEquals(
            listOf(
                EtaPart("2", emphasized = true),
                EtaPart("hr", emphasized = false),
                EtaPart(" 5", emphasized = true),
                EtaPart("min", emphasized = false)
            ),
            format(125)
        )
    }

    @Test
    fun `a recent-past eta under an hour keeps its sign on the number`() {
        assertEquals(
            listOf(EtaPart("-5", emphasized = true), EtaPart("min", emphasized = false)),
            format(-5)
        )
    }

    @Test
    fun `a recent-past eta over an hour keeps its sign on the leading hour number`() {
        assertEquals(
            listOf(
                EtaPart("-1", emphasized = true),
                EtaPart("hr", emphasized = false),
                EtaPart(" 23", emphasized = true),
                EtaPart("min", emphasized = false)
            ),
            format(-83)
        )
    }

    // --- formatTimeParts: a clock time with its AM/PM marker split out, for the widget to shrink ---

    private val at2024Utc = 1_767_299_040_000L // 2026-01-01T20:24:00Z

    private fun timeParts(pattern: String, locale: Locale) = DisplayFormat.formatTimeParts(at2024Utc, pattern, locale, TimeZone.getTimeZone("UTC"))

    @Test
    fun `a 12-hour time splits off its trailing marker`() {
        assertEquals(
            listOf(EtaPart("8:24 ", emphasized = true), EtaPart("PM", emphasized = false)),
            timeParts("h:mm a", Locale.US)
        )
    }

    @Test
    fun `a 24-hour time has no marker to split`() {
        assertEquals(listOf(EtaPart("20:24", emphasized = true)), timeParts("HH:mm", Locale.US))
    }

    @Test
    fun `a marker the locale puts first is found there, not assumed to trail`() {
        val parts = timeParts("a h:mm", Locale.KOREAN)

        assertEquals(listOf(false, true), parts.map { it.emphasized })
        assertEquals(" 8:24", parts[1].text)
    }

    @Test
    fun `the parts join back into the whole formatted time`() {
        val whole = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(at2024Utc))

        assertEquals(whole, timeParts("h:mm a", Locale.US).joinToString("") { it.text })
    }
}
