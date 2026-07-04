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
package org.onebusaway.android.api.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [situationEpochToMillis], the wire→domain normalization that makes active-window
 * `from`/`to` unambiguously epoch millis (they arrive as seconds OR millis depending on the server;
 * see the function doc). Mirrors the OBA server's own `toMillis` threshold (1e12).
 */
class SituationEpochToMillisTest {

    @Test
    fun `epoch seconds are scaled to millis`() {
        // 1_700_000_000 s (Nov 2023) → millis.
        assertEquals(1_700_000_000_000L, situationEpochToMillis(1_700_000_000L))
    }

    @Test
    fun `epoch millis pass through unchanged`() {
        assertEquals(1_700_000_000_000L, situationEpochToMillis(1_700_000_000_000L))
    }

    @Test
    fun `zero (unset from, or to== no end) passes through`() {
        assertEquals(0L, situationEpochToMillis(0L))
    }

    @Test
    fun `negative passes through unchanged`() {
        assertEquals(-1L, situationEpochToMillis(-1L))
    }

    @Test
    fun `boundary at the threshold is treated as millis`() {
        // Exactly 1e12 (year 2001 in millis) is at/above the threshold → already millis.
        assertEquals(1_000_000_000_000L, situationEpochToMillis(1_000_000_000_000L))
        // Just below → treated as seconds and scaled.
        assertEquals(999_999_999_999_000L, situationEpochToMillis(999_999_999_999L))
    }
}
