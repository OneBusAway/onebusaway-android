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
package org.onebusaway.android.ui.home

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.time.WallTime

class FocusTimeoutTest {

    private val now = WallTime(1_700_000_000_000L)

    @Test
    fun `expires once the idle gap reaches the timeout`() {
        val timeout = FocusTimeout.FOUR_HOURS
        assertFalse(timeout.hasExpired(now - 4.hours + 1.milliseconds, now))
        assertTrue(timeout.hasExpired(now - 4.hours, now))
        assertTrue(timeout.hasExpired(now - 30.hours, now))
    }

    @Test
    fun `always never expires`() {
        assertFalse(FocusTimeout.ALWAYS.hasExpired(now - 30.hours, now))
    }

    @Test
    fun `an unmeasured focus is kept`() {
        assertFalse(FocusTimeout.THIRTY_MINUTES.hasExpired(null, now))
    }

    @Test
    fun `a stamp from the future is not expired`() {
        // A device clock set back: the gap is negative, which is "no idle time", not "forever".
        assertFalse(FocusTimeout.THIRTY_MINUTES.hasExpired(now + 1.hours, now))
    }

    @Test
    fun `preference values round-trip and unknown values fall back to the default`() {
        for (timeout in FocusTimeout.entries) {
            assertEquals(timeout, FocusTimeout.fromPreference(timeout.value))
        }
        assertEquals(FocusTimeout.FOUR_HOURS, FocusTimeout.fromPreference(null))
        assertEquals(FocusTimeout.FOUR_HOURS, FocusTimeout.fromPreference("3 hours"))
    }

    @Test
    fun `reads the setting through the preferences repository`() {
        val prefs = FakePreferencesRepository()
        assertEquals(FocusTimeout.DEFAULT, prefs.focusTimeout())
        prefs.setString(FocusTimeout.PREFERENCE_KEY, FocusTimeout.EIGHT_HOURS.value)
        assertEquals(FocusTimeout.EIGHT_HOURS, prefs.focusTimeout())
    }
}
