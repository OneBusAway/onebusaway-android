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
 * is still owed, that recording it sticks, and — the distinction the scripted tutorial rests on
 * (#2164) — that declining to *show* it is not the same as having it acknowledged.
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
    fun pending_whenTheFlagIsExplicitlyCleared() {
        val prefs = FakePreferencesRepository()
        prefs.setBoolean(R.string.preference_key_directions_safety_acknowledged, false)

        assertTrue(isSafetyNoticePending(prefs))
    }

    @Test
    fun shown_whenOwedAndNotInDemoMode() {
        assertTrue(shouldShowSafetyNotice(pending = true, demoActive = false))
    }

    @Test
    fun notShown_onceAcknowledged() {
        assertFalse(shouldShowSafetyNotice(pending = false, demoActive = false))
    }

    @Test
    fun notShown_whileTheTutorialIsRunningOnDemoData() {
        assertFalse(shouldShowSafetyNotice(pending = true, demoActive = true))
    }

    /**
     * The point of suppressing rather than acknowledging: after the tutorial has walked the rider
     * through the planner on demo data, the notice is still owed, so their first *real* set of
     * directions still carries the disclosure.
     */
    @Test
    fun stillOwedAfterTheTutorialSuppressedIt() {
        val prefs = FakePreferencesRepository()

        // What the tutorial does: asks whether to show it, is told no, and writes nothing.
        assertFalse(shouldShowSafetyNotice(isSafetyNoticePending(prefs), demoActive = true))

        // Back on real data, the rider has still never seen it.
        assertTrue(isSafetyNoticePending(prefs))
        assertTrue(shouldShowSafetyNotice(isSafetyNoticePending(prefs), demoActive = false))
    }
}
