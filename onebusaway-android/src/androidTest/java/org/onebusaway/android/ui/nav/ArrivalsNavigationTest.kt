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
package org.onebusaway.android.ui.nav

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.ArrivalsIntents
import org.onebusaway.android.ui.arrivals.components.rememberArrivalDisplayMode
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.GeoPoint

/** Exercise real Navigation-Compose entries and saved state, without a network or map SDK. */
class ArrivalsNavigationTest {
    @get:Rule val compose = createUnconfinedComposeRule()
    private lateinit var nav: NavHostController
    private lateinit var activity: Activity

    @Test
    fun legacyShortcutAliasesAndNamesResolveToTheBoardWhileExplicitMapExtrasStayMapActions() {
        val id = "agency_stop/with space"
        val name = "Pike & 3rd / north"
        for (alias in listOf("org.onebusaway.android.ui.ArrivalsListActivity", "com.joulespersecond.seattlebusbot.ArrivalsListActivity")) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName("com.joulespersecond.seattlebusbot", alias)
                data = DeepLinkUris.STOPS.buildUpon().appendPath(id).build()
                putExtra(ArrivalsIntents.STOP_NAME, name)
            }
            assertEquals(NavRoutes.arrivals(id, name), IntentRouteMapper.routeForIntent(intent))
        }
        assertNull(IntentRouteMapper.routeForIntent(Intent().putExtra(MapParams.STOP_ID, id)))
    }

    @Test
    fun mapVisitKeepsTheBoardEntryScrollAndDisplayChoiceThenBackReturnsToTheList() {
        compose.setContent { Harness() }
        openBoard()
        val board = nav.currentBackStackEntry
        compose.onNodeWithText("Route").performClick()
        compose.onNodeWithTag("board_rows").performScrollToIndex(25)
        compose.onNodeWithText("Map").performClick()
        compose.runOnIdle {
            assertEquals(NavRoutes.HOME, nav.currentDestination?.route)
            assertSame(board, nav.previousBackStackEntry)
            assertEquals(StopReveal("stop/1", "My stop", GeoPoint(0.0, 0.0), useDefaultZoom = true), nav.currentBackStackEntry!!.savedStateHandle.consumeStopReveal())
            nav.popBackStack()
        }
        compose.onNodeWithText("Route selected").assertIsDisplayed()
        compose.onNodeWithText("Arrival 25").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Starred stops").assertIsDisplayed()
    }

    @Test
    fun savedMapVisitRestoresTheBoardUnderneathWithItsState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness() }
        openBoard()
        compose.onNodeWithTag("board_rows").performScrollToIndex(20)
        compose.onNodeWithText("Route").performClick()
        compose.onNodeWithText("Map").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(NavRoutes.HOME, nav.currentDestination?.route)
            nav.popBackStack()
        }
        compose.onNodeWithText("Route selected").assertIsDisplayed()
        compose.onNodeWithText("Arrival 20").assertIsDisplayed()
    }

    @Test
    fun explicitRouteMapActionPreservesTheBoardAndTheWholeRequest() {
        compose.setContent { Harness() }
        openBoard()
        compose.runOnIdle {
            val board = nav.currentBackStackEntry
            val request = ShowRouteRequest("route", "stop/1", "trip", 1)
            nav.showRouteMapFromArrivals(request)
            assertSame(board, nav.previousBackStackEntry)
            assertEquals(request, nav.currentBackStackEntry!!.savedStateHandle.consumeRouteReveal())
        }
    }

    @Test
    fun aBoardRouteCarriesTheRouteRowToSelectAndStaysOptional() {
        compose.setContent { Harness() }
        compose.runOnIdle {
            nav.navigate(NavRoutes.arrivals("stop/1", "My stop", routeId = "1_40/x", routeHeadsign = "Pike & 3rd"))
            val args = nav.currentBackStackEntry!!.arguments!!
            assertEquals(NavRoutes.ARRIVALS, nav.currentDestination?.route)
            assertEquals("stop/1", args.getString(NavRoutes.ARG_STOP_ID))
            assertEquals("My stop", args.getString(NavRoutes.ARG_STOP_NAME))
            assertEquals("1_40/x", args.getString(NavRoutes.ARG_ROUTE_ID))
            assertEquals("Pike & 3rd", args.getString(NavRoutes.ARG_ROUTE_HEADSIGN))

            // Every other board request names no row.
            nav.navigate(NavRoutes.arrivals("stop/2"))
            val plain = nav.currentBackStackEntry!!.arguments!!
            assertEquals("stop/2", plain.getString(NavRoutes.ARG_STOP_ID))
            assertNull(plain.getString(NavRoutes.ARG_ROUTE_ID))
            assertNull(plain.getString(NavRoutes.ARG_ROUTE_HEADSIGN))
        }
    }

    @Test
    fun shortcutUpOpensStarredStopsAndOpeningAnotherStopDoesNotInheritExitBehavior() {
        compose.setContent { Harness() }
        compose.runOnIdle {
            nav.navigateFromHome(NavRoutes.arrivals("shortcut-stop"))
            nav.currentBackStackEntry!!.savedStateHandle[LAUNCH_ROOT] = true
        }
        compose.onNodeWithText("Board up").performClick()
        compose.onNodeWithText("Starred stops").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(activity.isFinishing)
            assertEquals(true, nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>(LAUNCH_ROOT))
            assertEquals(NavRoutes.HOME, nav.previousBackStackEntry?.destination?.route)
        }
        compose.onNodeWithText("Open stop").performClick()
        compose.runOnIdle {
            assertNull(nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>(LAUNCH_ROOT))
        }
        compose.onNodeWithText("Board up").performClick()
        compose.onNodeWithText("Starred stops").assertIsDisplayed()
        compose.onNodeWithText("List up").performClick()
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        compose.runOnIdle { assertFalse(activity.isFinishing) }
    }

    @Test
    fun appIconLaunchIntoStarredStopsCanOpenAStopAndNavigateUpWithoutClosing() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness() }
        compose.runOnIdle {
            nav.navigateFromHome(NavRoutes.HOME_STARRED_STOPS)
            nav.currentBackStackEntry!!.savedStateHandle[LAUNCH_ROOT] = true
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Open stop").performClick()
        compose.runOnIdle { nav.navigateBackOrFinish() }
        compose.onNodeWithText("Starred stops").assertIsDisplayed()
        compose.onNodeWithText("List up").performClick()
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(activity.isFinishing)
            assertNull(nav.previousBackStackEntry)
        }
    }

    @Test
    fun boardMapTransitionsResumeWithinFourFramesInBothDirections() {
        compose.setContent { Harness() }
        openBoard()
        // Finish the ordinary list-to-board fade before timing the separate board/map hop.
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, nav.currentBackStackEntry!!.lifecycle.currentState)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.showStopMapFromArrivals(StopReveal("stop/1", "My stop")) }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals(NavRoutes.HOME, nav.currentDestination?.route)
            assertEquals(Lifecycle.State.RESUMED, nav.currentBackStackEntry!!.lifecycle.currentState)
            nav.popBackStack()
        }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals(NavRoutes.ARRIVALS, nav.currentDestination?.route)
            assertEquals(Lifecycle.State.RESUMED, nav.currentBackStackEntry!!.lifecycle.currentState)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun otherDestinationsKeepTheirNormalTransition() {
        compose.setContent { Harness() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.navigate(NavRoutes.HOME_STARRED_STOPS) }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals(Lifecycle.State.STARTED, nav.currentBackStackEntry!!.lifecycle.currentState)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, nav.currentBackStackEntry!!.lifecycle.currentState)
        }
        compose.mainClock.autoAdvance = true
    }

    private fun openBoard() {
        compose.runOnIdle { nav.navigate(NavRoutes.HOME_STARRED_STOPS) }
        compose.onNodeWithText("Open stop").performClick()
    }

    @Composable
    private fun Harness() {
        activity = requireNotNull(LocalActivity.current)
        nav = rememberNavController()
        NavHost(nav, startDestination = NavRoutes.HOME, modifier = Modifier.fillMaxSize()) {
            composable(
                NavRoutes.HOME,
                enterTransition = { arrivalsMapEnterTransition() },
                exitTransition = { arrivalsMapExitTransition() }
            ) { Text("Map screen", Modifier.fillMaxSize()) }
            composable(NavRoutes.HOME_STARRED_STOPS) {
                Column(Modifier.fillMaxSize()) {
                    Text("Starred stops")
                    Button(onClick = { nav.popBackStack() }) { Text("List up") }
                    Button(onClick = { nav.showArrivals(StopReveal("stop/1", "My stop")) }) { Text("Open stop") }
                }
            }
            composable(
                NavRoutes.ARRIVALS,
                enterTransition = { arrivalsMapEnterTransition() },
                exitTransition = { arrivalsMapExitTransition() },
                arguments = listOf(navArgument(NavRoutes.ARG_STOP_ID) { type = NavType.StringType }) +
                    listOf(NavRoutes.ARG_STOP_NAME, NavRoutes.ARG_ROUTE_ID, NavRoutes.ARG_ROUTE_HEADSIGN).map { name ->
                        navArgument(name) {
                            type = NavType.StringType
                            nullable = true
                        }
                    }
            ) { entry ->
                val id = requireNotNull(entry.arguments?.getString(NavRoutes.ARG_STOP_ID))
                var mode by rememberArrivalDisplayMode(id) { ArrivalDisplayMode.TIME }
                Column(Modifier.fillMaxSize()) {
                    Button(onClick = { nav.navigateUpFromArrivals(NavRoutes.HOME_STARRED_STOPS) }) { Text("Board up") }
                    Text(if (mode == ArrivalDisplayMode.ROUTE) "Route selected" else "Time selected")
                    Button(onClick = { mode = ArrivalDisplayMode.ROUTE }) { Text("Route") }
                    Button(onClick = { nav.showStopMapFromArrivals(StopReveal(id, "My stop", GeoPoint(0.0, 0.0))) }) { Text("Map") }
                    LazyColumn(Modifier.testTag("board_rows"), state = rememberLazyListState()) {
                        items((0..50).toList()) { Text("Arrival $it", Modifier.height(50.dp)) }
                    }
                }
            }
        }
    }
}
