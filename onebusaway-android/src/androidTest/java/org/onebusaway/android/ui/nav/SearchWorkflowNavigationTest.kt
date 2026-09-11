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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.routeinfo.RouteDirection
import org.onebusaway.android.ui.routeinfo.RouteInfo
import org.onebusaway.android.ui.routeinfo.RouteInfoScreen
import org.onebusaway.android.ui.routeinfo.RouteInfoUiState
import org.onebusaway.android.ui.routeinfo.RouteStopItem
import org.onebusaway.android.ui.searchresults.SearchResultMode

class SearchWorkflowNavigationTest {
    @get:Rule val compose = createUnconfinedComposeRule()
    private lateinit var nav: NavHostController

    @Test
    fun listSearchRetainsDirectionAndScrollAfterArrivalsAndSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Harness() }
        compose.runOnIdle {
            nav.navigate(NavRoutes.search("8"))
            nav.openRoute("route8", SearchResultMode.LISTS, prefersMap = true)
        }
        compose.onNodeWithText("Downtown").performClick()
        compose.onAllNodes(hasScrollAction())[0].performScrollToIndex(28)
        compose.onNodeWithText("Stop 27").performClick()
        compose.onNodeWithText("Arrivals board").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Stop 27").assertIsDisplayed()
        compose.runOnIdle {
            nav.showRouteMapFromArrivals(ShowRouteRequest("route8"))
            assertEquals(NavRoutes.HOME, nav.currentDestination?.route)
            nav.popBackStack()
        }
        compose.onNodeWithText("Stop 27").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Search results").assertIsDisplayed()
    }

    @Test
    fun stopSearchGoesDirectlyToArrivalsAndMapModeKeepsBothMapReveals() {
        compose.setContent { Harness() }
        compose.runOnIdle {
            nav.navigate(NavRoutes.search("123"))
            nav.openStop(StopReveal("123", "My stop"), SearchResultMode.LISTS, prefersMap = true)
            assertEquals(NavRoutes.ARRIVALS, nav.currentDestination?.route)
            nav.popBackStack()
            assertEquals(NavRoutes.SEARCH, nav.currentDestination?.route)
            nav.openStop(StopReveal("123", "My stop"), SearchResultMode.MAP, prefersMap = true)
            assertEquals(NavRoutes.HOME, nav.currentDestination?.route)
            assertEquals("123", nav.currentBackStackEntry!!.savedStateHandle.consumeStopReveal()?.stopId)
            nav.navigate(NavRoutes.search("8"))
            nav.openRoute("route8", SearchResultMode.MAP, prefersMap = true)
            assertEquals(NavRoutes.HOME, nav.currentDestination?.route)
            assertEquals("route8", nav.currentBackStackEntry!!.savedStateHandle.consumeRouteReveal()?.routeId)
        }
    }

    /** A saved-stop row prefers the board even in Map mode (#2297); Lists mode overrides a map preference (#2319). */
    @Test
    fun modeHasTheLastWordOverARequestersMapPreference() {
        compose.setContent { Harness() }
        compose.runOnIdle {
            nav.openStop(StopReveal("123", "My stop"), SearchResultMode.MAP, prefersMap = false)
            assertEquals(NavRoutes.ARRIVALS, nav.currentDestination?.route)
            nav.popBackStack()
            nav.openStop(StopReveal("123", "My stop"), SearchResultMode.LISTS, prefersMap = true)
            assertEquals(NavRoutes.ARRIVALS, nav.currentDestination?.route)
            assertEquals(null, nav.getBackStackEntry(NavRoutes.HOME).savedStateHandle.consumeStopReveal())
            nav.popBackStack()
            nav.openRoute("route8", SearchResultMode.LISTS, prefersMap = true)
            assertEquals(NavRoutes.ROUTE_INFO, nav.currentDestination?.route)
        }
    }

    @Composable
    private fun Harness() {
        nav = rememberNavController()
        ObaTheme {
            NavHost(nav, startDestination = NavRoutes.HOME) {
                composable(NavRoutes.HOME) { Text("Map") }
                composable(NavRoutes.SEARCH, arguments = listOf(navArgument(NavRoutes.ARG_QUERY) { type = NavType.StringType })) { Text("Search results") }
                composable(NavRoutes.ROUTE_INFO, arguments = listOf(navArgument(NavRoutes.ARG_ROUTE_ID) { type = NavType.StringType })) {
                    RouteInfoScreen(
                        RouteInfoUiState.Success(RouteInfo("route8", "8", "Route eight", "Metro", null, listOf(RouteDirection("Downtown", (0..60).map { RouteStopItem("s$it", "Stop $it", "N", 0.0, 0.0) })))),
                        onBack = { nav.popBackStack() },
                        onShowRouteOnMap = { nav.showRouteMapFromArrivals(ShowRouteRequest("route8")) },
                        onStopClick = { nav.openStop(StopReveal(it.id, it.name), SearchResultMode.LISTS, prefersMap = true) }
                    )
                }
                composable(
                    NavRoutes.ARRIVALS,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_STOP_ID) { type = NavType.StringType },
                        navArgument(NavRoutes.ARG_STOP_NAME) {
                            type = NavType.StringType
                            nullable = true
                        }
                    )
                ) { Text("Arrivals board") }
            }
        }
    }
}
