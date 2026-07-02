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
 * Unit tests for [SituationUtils.isActiveWindowForSituation]. The point of these (beyond the
 * boundary behaviour) is the #1612 guardrail: the check is a **pure function of the passed
 * `currentTime`** and reads no clock of its own, so a caller feeding the response's server
 * `currentTime` gets a result that is immune to device clock skew.
 */
class SituationUtilsTest {

    // A realistic epoch-seconds anchor and the same instant in millis (the "server currentTime").
    private val fromSec = 1_700_000_000L
    private val toSec = 1_700_003_600L // +1h
    private val insideMs = 1_700_001_800_000L // +30min, in millis
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
        val s = situation(windows = arrayOf(window(fromSec, toSec)))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, insideMs))
    }

    @Test
    fun `server time before the window is inactive`() {
        val s = situation(windows = arrayOf(window(fromSec, toSec)))
        assertFalse(SituationUtils.isActiveWindowForSituation(s, beforeMs))
    }

    @Test
    fun `server time after the window is inactive`() {
        val s = situation(windows = arrayOf(window(fromSec, toSec)))
        assertFalse(SituationUtils.isActiveWindowForSituation(s, afterMs))
    }

    @Test
    fun `zero end time is an open-ended window`() {
        // to == 0 means "no end" (see #990): active at and after the start, unbounded above.
        val s = situation(windows = arrayOf(window(fromSec, 0L)))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, insideMs))
        assertTrue(SituationUtils.isActiveWindowForSituation(s, afterMs))
    }

    // The #1612 property — the verdict is a function of the passed (server) time alone, so device
    // clock skew can't flip an alert — is what the boundary tests above already demonstrate: each
    // passes a caller-supplied time and gets a deterministic answer.

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
