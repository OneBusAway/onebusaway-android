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
package org.onebusaway.android.ui.home.directions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.testing.FakePreferencesRepository

/**
 * Unit tests for the pure half of the directions safety notice (#2218): whether the acknowledgement
 * is still owed, and that recording it sticks.
 */
class DirectionsSafetyNoticeTest {

    @Test
    fun pending_onFreshInstall() {
        // FakePreferencesRepository seeds *observed* booleans to true; the synchronous getters this
        // gate uses fall through to the caller's default, so an unset key reads as unacknowledged.
        assertTrue(isSafetyNoticePending(FakePreferencesRepository()))
    }

    @Test
    fun notPending_onceAcknowledged() {
        val prefs = FakePreferencesRepository()

        markSafetyNoticeAcknowledged(prefs)

        assertFalse(isSafetyNoticePending(prefs))
    }

    @Test
    fun acknowledging_isIdempotent() {
        val prefs = FakePreferencesRepository()

        markSafetyNoticeAcknowledged(prefs)
        markSafetyNoticeAcknowledged(prefs)

        assertFalse(isSafetyNoticePending(prefs))
    }

    @Test
    fun pending_whenTheFlagIsExplicitlyCleared() {
        val prefs = FakePreferencesRepository()
        prefs.setBoolean(R.string.preference_key_directions_safety_acknowledged, false)

        assertTrue(isSafetyNoticePending(prefs))
    }
}
