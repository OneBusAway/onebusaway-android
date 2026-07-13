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
package org.onebusaway.android.ui

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.SmokeTest

/**
 * The core "**does the app boot?**" check for the API-23 floor smoke leg (#1818). `minSdk` is 23 but no
 * CI job otherwise *launches* the app below API 33, so a crash-on-boot at the floor — a desugaring gap,
 * an unguarded API-26+ platform call (e.g. a `NotificationChannel`), a Compose-on-old-runtime failure —
 * would ship unseen. Booting the real [HomeActivity] with its production Hilt graph exercises exactly
 * that surface; this test is where the nightly leg's canary (a deliberate API-26 break) is caught.
 *
 * [ActivityScenarioRule] drives the activity through its lifecycle in `@Before` and tears it down after;
 * a crash anywhere in `onCreate`/`onResume` (or the initial composition of the setContent tree) fails
 * the launch before the body ever runs, which is the signal we want. The body then confirms the process
 * actually settled at [Lifecycle.State.RESUMED] rather than immediately finishing or bouncing.
 *
 * Deliberately assertion-light: this is a smoke test of startup, not a functional test of the home
 * screen. It doesn't touch the network, location, or the region picker — reaching RESUMED is the bar.
 */
@SmokeTest
@RunWith(AndroidJUnit4::class)
class HomeActivitySmokeTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(HomeActivity::class.java)

    @Test
    fun homeActivity_launchesAndReachesResumed() {
        assertEquals(Lifecycle.State.RESUMED, scenarioRule.scenario.state)
        scenarioRule.scenario.onActivity { activity ->
            assertFalse("HomeActivity finished during launch", activity.isFinishing)
        }
    }
}
