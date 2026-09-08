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

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.nav.DeepLinkUris
import org.onebusaway.android.ui.nav.LAUNCH_ROOT
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.navigateFromHome

/** Saves a real launch effect and NavHost while collection is gated by the Activity lifecycle. */
class LaunchIntentEffectTest {
    @get:Rule val compose = createUnconfinedComposeRule()
    private lateinit var owner: LaunchLifecycleOwner
    private lateinit var channel: LaunchIntentChannel<Intent>
    private lateinit var nav: NavHostController
    private var ready = false
    private val handled = mutableListOf<Intent>()

    @Test
    fun shortcutSavedBeforeCollectionIsRoutedAfterRecreation() {
        val intent = Intent(Intent.ACTION_VIEW, DeepLinkUris.STOPS.buildUpon().appendPath("shortcut-stop").build())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness(intent) }
        compose.runOnIdle {
            assertFalse(ready)
            assertTrue(handled.isEmpty())
        }
        val oldChannel = channel
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertNotSame(oldChannel, channel)
            assertFalse(ready)
            assertTrue(handled.isEmpty())
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Arrivals board").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(ready)
            assertEquals(listOf(intent), handled)
            assertEquals("shortcut-stop", nav.currentBackStackEntry!!.arguments!!.getString(NavRoutes.ARG_STOP_ID))
            assertEquals(true, nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>(LAUNCH_ROOT))
        }
    }

    @Test
    fun mapLaunchSavedBeforeCollectionReleasesTheMapGateAfterRecreation() {
        // An explicit HOME route avoids depending on the device's remembered drawer section.
        val intent = Intent().putExtra(NavRoutes.EXTRA_NAV_ROUTE, NavRoutes.HOME)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness(intent) }
        compose.onNodeWithText("Map screen").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(ready)
            assertEquals(listOf(intent), handled)
        }
    }

    @Test
    fun handledLaunchRestoresTheCurrentDestinationWithoutRepeatingSideEffects() {
        val intent = Intent(Intent.ACTION_VIEW, DeepLinkUris.STOPS.buildUpon().appendPath("shortcut-stop").build())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness(intent) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Arrivals board").assertIsDisplayed()
        compose.runOnIdle { nav.navigateFromHome(NavRoutes.HOME) }
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(ready)
            assertEquals(listOf(intent), handled)
        }
    }

    @Test
    fun warmLaunchesWaitUntilStartedAndAreHandledInOrderWithoutBecomingLaunchRoots() {
        val initial = Intent().putExtra(NavRoutes.EXTRA_NAV_ROUTE, NavRoutes.HOME)
        compose.setContent { Harness(initial) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        val first = Intent(Intent.ACTION_VIEW, DeepLinkUris.STOPS.buildUpon().appendPath("first").build())
        val second = Intent(Intent.ACTION_VIEW, DeepLinkUris.STOPS.buildUpon().appendPath("second").build())
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            channel.submit(first)
            channel.submit(second)
        }
        compose.runOnIdle {
            assertEquals(listOf(initial), handled)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Arrivals board").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(initial, first, second), handled)
            assertEquals("second", nav.currentBackStackEntry!!.arguments!!.getString(NavRoutes.ARG_STOP_ID))
            assertEquals(null, nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>(LAUNCH_ROOT))
        }
    }

    @Composable
    private fun Harness(intent: Intent) {
        // Both objects are recreated with the composition, as in a new HomeActivity instance.
        owner = remember { LaunchLifecycleOwner() }
        channel = remember { LaunchIntentChannel() }
        nav = rememberNavController()
        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            val launchReady = LaunchIntentEffect(nav, intent, channel.items) { handled.add(it) }
            ready = launchReady
            NavHost(nav, startDestination = NavRoutes.HOME) {
                composable(NavRoutes.HOME) {
                    if (launchReady) Text("Map screen")
                }
                composable(NavRoutes.ARRIVALS) { Text("Arrivals board") }
            }
        }
    }

    private class LaunchLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }
}
