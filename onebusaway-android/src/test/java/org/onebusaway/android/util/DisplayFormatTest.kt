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
}
