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
package org.onebusaway.android.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.testing.FakePreferencesRepository

/**
 * Unit tests for [ArrivalTutorial]'s pure gating: which onboarding steps are still owed given the
 * opt-out flag and each step's "already shown" preference.
 */
class ArrivalTutorialTest {

    @Test
    fun pendingSteps_freshInstall_returnsAllStepsInOrder() {
        val prefs = FakePreferencesRepository()

        val pending = ArrivalTutorial.pendingSteps(prefs)

        assertEquals(
            listOf(
                ArrivalTutorial.KEY_ETA,
                ArrivalTutorial.KEY_PANEL,
                ArrivalTutorial.KEY_STAR,
                ArrivalTutorial.KEY_MORE_MENU,
            ),
            pending.map { it.id },
        )
    }

    @Test
    fun pendingSteps_tutorialsTurnedOff_returnsNothing() {
        val prefs = FakePreferencesRepository()
        prefs.setBoolean(R.string.preference_key_show_tutorial_screens, false)

        assertTrue(ArrivalTutorial.pendingSteps(prefs).isEmpty())
    }

    @Test
    fun pendingSteps_skipsAlreadyShownSteps() {
        val prefs = FakePreferencesRepository()
        prefs.setBoolean(ArrivalTutorial.KEY_ETA, true)

        assertEquals(
            listOf(ArrivalTutorial.KEY_PANEL, ArrivalTutorial.KEY_STAR, ArrivalTutorial.KEY_MORE_MENU),
            ArrivalTutorial.pendingSteps(prefs).map { it.id },
        )
    }

    @Test
    fun markShown_makesThoseStepsNoLongerPending() {
        val prefs = FakePreferencesRepository()

        ArrivalTutorial.markShown(prefs, ArrivalTutorial.pendingSteps(prefs))

        assertTrue(ArrivalTutorial.pendingSteps(prefs).isEmpty())
    }

    @Test
    fun resetKeys_coverEveryStep() {
        assertEquals(ArrivalTutorial.steps.map { it.id }, ArrivalTutorial.resetKeys())
    }
}
