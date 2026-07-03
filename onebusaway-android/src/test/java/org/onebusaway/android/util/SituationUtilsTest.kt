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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.ObaSituation

/**
 * Unit tests for [SituationUtils.isActiveWindowForSituation]. It's a pure function of the passed
 * `currentTime`, immune to device clock skew (#1612). Active-window `from`/`to` reach it already in
 * epoch **millis** (the wire→domain adapter normalizes the seconds-or-millis wire value; that
 * normalization is covered separately by `SituationEpochToMillisTest`), so these fixtures use millis.
 */
class SituationUtilsTest {

    // A window [from, to] and three "server currentTime" probes, all epoch millis.
    private val fromMs = 1_700_000_000_000L
    private val toMs = 1_700_003_600_000L // +1h
    private val insideMs = 1_700_001_800_000L // +30min
    private val beforeMs = 1_699_999_000_000L
    private val afterMs = 1_700_004_000_000L

    @Test
    fun `no active windows means always active`() {
        val s = situation(windows = emptyArray())
        assertTrue(SituationUtils.isActiveWindowForSituation(s, beforeMs))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, afterMs))
    }

    @Test
    fun `server time inside the window is active`() {
        val s = situation(windows = arrayOf(window(fromMs, toMs)))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, insideMs))
    }

    @Test
    fun `server time before the window is inactive`() {
        val s = situation(windows = arrayOf(window(fromMs, toMs)))
        assertFalse(SituationUtils.isActiveWindowForSituation(s, beforeMs))
    }

    @Test
    fun `server time after the window is inactive`() {
        val s = situation(windows = arrayOf(window(fromMs, toMs)))
        assertFalse(SituationUtils.isActiveWindowForSituation(s, afterMs))
    }

    @Test
    fun `zero end time is an open-ended window`() {
        // to == 0 means "no end" (see #990): active at and after the start, unbounded above, but NOT
        // before the start — an open-ended window with no upper bound must still respect its start.
        val s = situation(windows = arrayOf(window(fromMs, 0L)))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, insideMs))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, afterMs))
        assertFalse(SituationUtils.isActiveWindowForSituation(s, beforeMs))
    }

    @Test
    fun `future start with no end is inactive`() {
        // A window that starts in the future with no end must not read as active now.
        val futureFromMs = 1_800_000_000_000L
        val s = situation(windows = arrayOf(window(futureFromMs, 0L)))
        assertFalse(SituationUtils.isActiveWindowForSituation(s, insideMs))
    }

    // --- fixtures ---

    private fun window(from: Long, to: Long): ObaSituation.ActiveWindow =
        object : ObaSituation.ActiveWindow {
            override val from = from
            override val to = to
        }

    private fun situation(windows: Array<ObaSituation.ActiveWindow>): ObaSituation =
        object : ObaSituation {
            override val id = "test"
            override val summary: String? = null
            override val description: String? = null
            override val severity: String? = null
            override val advice: String? = null
            override val reason: String? = null
            override val url: String? = null
            override val creationTime = 0L
            override val allAffects: Array<ObaSituation.AllAffects> = emptyArray()
            override val consequences: Array<ObaSituation.Consequence> = emptyArray()
            override val activeWindows = windows
        }
}
