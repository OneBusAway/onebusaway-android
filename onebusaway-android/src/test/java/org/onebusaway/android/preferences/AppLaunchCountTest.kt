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
package org.onebusaway.android.preferences

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.testing.FakePreferencesRepository

/**
 * Unit tests for [PreferencesRepository]'s app-launch counter — the read/increment pair that moved
 * off `Application` (slice 6 of #1659). [PreferencesRepository.incrementAppLaunchCount] is the one
 * genuinely new piece: a shared default-method read-modify-write feeding the survey gate and the
 * donations manager. Exercised through [FakePreferencesRepository], which round-trips getInt/setInt.
 */
class AppLaunchCountTest {

    @Test
    fun `count starts at zero`() {
        assertEquals(0, FakePreferencesRepository().getAppLaunchCount())
    }

    @Test
    fun `one increment from cold yields one`() {
        val prefs = FakePreferencesRepository()
        prefs.incrementAppLaunchCount()
        assertEquals(1, prefs.getAppLaunchCount())
    }

    @Test
    fun `N increments yield N`() {
        val prefs = FakePreferencesRepository()
        repeat(4) { prefs.incrementAppLaunchCount() }
        assertEquals(4, prefs.getAppLaunchCount())
    }

    @Test
    fun `increment builds on a pre-seeded value`() {
        val prefs = FakePreferencesRepository()
        prefs.setInt(PreferencesRepository.APP_LAUNCH_COUNT_KEY, 5)
        prefs.incrementAppLaunchCount()
        assertEquals(6, prefs.getAppLaunchCount())
    }
}
