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

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Boot the production Hilt graph with extras that cannot be unmarshalled (#2327). */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33) // The reported stack uses Android 13+'s lazy Bundle values.
class HomeActivityIntentTest {

    @Test
    fun foreignExtrasDoNotCrashColdLaunchOrRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val incoming = parcelledForeignIntent().setClass(context, HomeActivity::class.java)
        incoming.data = Uri.parse("geo:0,0?q=Clinic")
        ActivityScenario.launch<HomeActivity>(incoming).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                val extras = activity.defaultViewModelCreationExtras
                assertSame(activity, extras[SAVED_STATE_REGISTRY_OWNER_KEY])
                assertSame(activity, extras[VIEW_MODEL_STORE_OWNER_KEY])
                assertFalse(requireNotNull(extras[DEFAULT_ARGS_KEY]).containsKey(FOREIGN_PARAMETERS))
                assertEquals(incoming.data, activity.intent.data)
            }
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun foreignExtrasDoNotCrashWarmLaunchThenRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<HomeActivity>(Intent(context, HomeActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(
                    activity,
                    parcelledForeignIntent().setClass(context, HomeActivity::class.java)
                )
                assertFalse(requireNotNull(activity.defaultViewModelCreationExtras[DEFAULT_ARGS_KEY]).containsKey(FOREIGN_PARAMETERS))
            }
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
