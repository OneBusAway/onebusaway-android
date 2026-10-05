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
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.toJson
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.home.CurrentFocus
import org.onebusaway.android.ui.home.HomeViewModel
import org.onebusaway.android.ui.home.focusedStop
import org.onebusaway.android.ui.nav.DeepLinkUris
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.tripplan.PlanResult
import org.onebusaway.android.ui.tripplan.TripPlanViewModel

/** Exercise the real Activity/Hilt/Navigation path, including the outgoing blank HOME anchor. */
@RunWith(AndroidJUnit4::class)
class HomeFeatureInitializationTest {
    // Use the standard dispatcher for the real map SDK: an unconfined test dispatcher can resume
    // map rendering on a background flow producer's thread, violating the SDK's main-thread contract.
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun listLaunchAndRecreationLeaveMapFeaturesUninitialized() {
        assertMaplessLaunch(HomeActivity.navIntent(context, NavRoutes.HOME_STARRED_STOPS))
    }

    @Test
    fun boardLaunchAndRecreationLeaveMapFeaturesUninitialized() {
        assertMaplessLaunch(HomeActivity.navIntent(context, NavRoutes.arrivals("test-stop")))
    }

    @Test
    fun shortcutLaunchAndRecreationLeaveMapFeaturesUninitialized() {
        assertMaplessLaunch(
            Intent(Intent.ACTION_VIEW, DeepLinkUris.STOPS.buildUpon().appendPath("test-stop").build())
                .setClass(context, HomeActivity::class.java)
        )
    }

    @Test
    fun mapLaunchResolvesFeaturesAndRetainsThemAcrossRecreation() {
        ActivityScenario.launch<HomeActivity>(HomeActivity.navIntent(context, NavRoutes.HOME)).use { scenario ->
            awaitFeature(scenario, "weatherViewModel")
            assertMapFeaturesRetained(scenario)
        }
    }

    @Test
    fun enteringMapAfterListLaunchResolvesFeaturesAndDeliversStopFocus() {
        ActivityScenario.launch<HomeActivity>(HomeActivity.navIntent(context, NavRoutes.HOME_STARRED_STOPS)).use { scenario ->
            awaitFeature(scenario, "helpViewModel")
            scenario.onActivity { activity ->
                assertMapFeaturesAbsent(activity)
                instrumentation.callActivityOnNewIntent(
                    activity,
                    HomeActivity.navIntent(context, NavRoutes.HOME)
                        .putExtra(MapParams.STOP_ID, "revealed-stop")
                )
            }
            awaitFeature(scenario, "weatherViewModel")
            scenario.onActivity { activity ->
                val home = activity.feature("viewModel").value as HomeViewModel
                assertTrue(home.currentFocus.value is CurrentFocus.Stop)
                assertEquals("revealed-stop", home.currentFocus.value.focusedStop?.id)
            }
            assertMapFeaturesRetained(scenario)
        }
    }

    @Test
    fun coldTripNotificationSeedsTheActivityOwnedPlanner() {
        ActivityScenario.launch<HomeActivity>(tripNotification()).use { scenario ->
            assertNotificationPlanRetained(scenario)
        }
    }

    @Test
    fun warmTripNotificationAfterListLaunchSeedsTheActivityOwnedPlanner() {
        ActivityScenario.launch<HomeActivity>(HomeActivity.navIntent(context, NavRoutes.HOME_STARRED_STOPS)).use { scenario ->
            awaitFeature(scenario, "helpViewModel")
            scenario.onActivity { activity ->
                assertMapFeaturesAbsent(activity)
                instrumentation.callActivityOnNewIntent(activity, tripNotification())
            }
            assertNotificationPlanRetained(scenario)
        }
    }

    private fun assertNotificationPlanRetained(scenario: ActivityScenario<HomeActivity>) {
        awaitFeature(scenario, "weatherViewModel")
        lateinit var planner: TripPlanViewModel
        var result: PlanResult? = null
        scenario.onActivity { activity ->
            planner = activity.feature("tripPlanViewModel").value as TripPlanViewModel
            result = planner.planState.value
            assertEquals(notificationItineraries, (result as PlanResult.Success).itineraries)
            val home = activity.feature("viewModel").value as HomeViewModel
            assertTrue(home.currentFocus.value is CurrentFocus.Directions)
        }
        scenario.recreate()
        awaitFeature(scenario, "weatherViewModel")
        scenario.onActivity { activity ->
            assertSame(planner, activity.feature("tripPlanViewModel").value)
            // Replaying the notification would create a new plan generation.
            assertSame(result, planner.planState.value)
        }
    }

    private fun tripNotification() = Intent(context, HomeActivity::class.java)
        .putExtra(OTPConstants.INTENT_SOURCE, OTPConstants.Source.NOTIFICATION)
        .putExtra(OTPConstants.ITINERARIES, notificationItineraries.toJson())

    private val notificationItineraries = listOf(
        TripItinerary(
            startTime = ServerTime(1_800_000_000_000),
            legs = listOf(
                TripLeg(
                    mode = TripMode.WALK,
                    startTime = ServerTime(1_800_000_000_000),
                    endTime = ServerTime(1_800_000_060_000),
                    from = TripPlace(name = "Start", lat = 47.61, lon = -122.33),
                    to = TripPlace(name = "End", lat = 47.62, lon = -122.33)
                )
            )
        )
    )

    private fun assertMaplessLaunch(intent: Intent) {
        ActivityScenario.launch<HomeActivity>(intent).use { scenario ->
            // Help is reached only after launch routing has completed. Waiting for idle also lets the
            // outgoing HOME transition finish; the old eager bundle fails the assertions below.
            awaitFeature(scenario, "helpViewModel")
            scenario.onActivity(::assertMapFeaturesAbsent)
            scenario.recreate()
            awaitFeature(scenario, "helpViewModel")
            scenario.onActivity(::assertMapFeaturesAbsent)
        }
    }

    private fun assertMapFeaturesRetained(scenario: ActivityScenario<HomeActivity>) {
        val instances = mutableMapOf<String, Any?>()
        scenario.onActivity { activity ->
            mapFeatures.forEach { name ->
                val feature = activity.feature(name)
                assertTrue("$name was not initialized on the map", feature.isInitialized())
                instances[name] = feature.value
            }
        }
        scenario.recreate()
        awaitFeature(scenario, "weatherViewModel")
        scenario.onActivity { activity ->
            instances.forEach { (name, instance) -> assertSame(name, instance, activity.feature(name).value) }
        }
    }

    private fun awaitFeature(scenario: ActivityScenario<HomeActivity>, name: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            var initialized = false
            scenario.onActivity { initialized = it.feature(name).isInitialized() }
            initialized
        }
        compose.waitForIdle()
    }

    private fun assertMapFeaturesAbsent(activity: HomeActivity) {
        mapFeatures.forEach { name ->
            assertFalse("$name was initialized for a mapless launch", activity.feature(name).isInitialized())
        }
    }

    // Inspect without resolving the delegates; ViewModelProvider.get would create the very VM whose
    // absence this regression test checks. No test hooks or counters are needed in production code.
    private fun HomeActivity.feature(name: String): Lazy<*> = HomeActivity::class.java
        .getDeclaredField("$name\$delegate")
        .apply { isAccessible = true }
        .get(this) as Lazy<*>

    private val mapFeatures = listOf(
        "mapViewModel",
        "surveyViewModel",
        "donationViewModel",
        "weatherViewModel",
        "tripPlanViewModel",
        "tripResultsViewModel",
        "pinnedTripViewModel"
    )
}
