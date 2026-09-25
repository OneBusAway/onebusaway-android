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
package org.onebusaway.android.ui.arrivals

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.components.ArrivalDisplayChoiceDialog
import org.onebusaway.android.ui.arrivals.components.ArrivalDisplayModeSwitch
import org.onebusaway.android.ui.arrivals.components.ArrivalRowAnchors
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.components.arrivalClock
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.arrivals.components.rememberArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.components.sideBySideText
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

class ArrivalDisplayModeTest {
    @get:Rule val composeRule = createUnconfinedComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The node drawing [arrival]'s scheduled/expected pair on one line, asserted to be the only one —
     *  a second would mean the pill is still printing its own clock subline underneath. */
    private fun soleClockNode(arrival: ArrivalInfo): SemanticsNodeInteraction {
        val times = arrival.arrivalClock(context).sideBySideText()
        composeRule.onAllNodesWithText(times, useUnmergedTree = true).assertCountEquals(1)
        return composeRule.onNodeWithText(times, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun temporarySwitchSurvivesRefreshAndStateRestorationButNewStopUsesDefault() {
        var stop by mutableStateOf("first")
        var default by mutableStateOf(ArrivalDisplayMode.TIME)
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(composeRule)
        restoration.setContent {
            var mode by rememberArrivalDisplayMode(stop) { default }
            ArrivalDisplayModeSwitch(mode, { mode = it })
        }
        composeRule.onNodeWithText("Time").assertIsSelected()
        composeRule.onNodeWithText("Route").performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(ArrivalDisplayMode.TIME, default) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Route").assertIsSelected()
        composeRule.runOnIdle { stop = "second" }
        composeRule.onNodeWithText("Time").assertIsSelected()
        // A settings change affects the next session, not the one currently being read.
        composeRule.runOnIdle { default = ArrivalDisplayMode.ROUTE }
        composeRule.onNodeWithText("Time").assertIsSelected()
        composeRule.runOnIdle { stop = "third" }
        composeRule.onNodeWithText("Route").assertIsSelected()
    }

    @Test
    fun switchProjectsLoadedTripsChronologicallyWithoutFavoritePromotion() {
        val early = previewArrival("40", "Forty", 3, scheduleDeviationMinutes = 2, tripId = "forty", context = context)
        val middle = previewArrival("8", "Eight", 5, tripId = "eight-first")
        val late = previewArrival("8", "Eight", 12, tripId = "eight-second")
        val content = ArrivalsUiState.Content(
            header = StopHeader("stop", "Main St", null, false),
            arrivals = listOf(early, middle, late),
            routeGroups = listOf(RouteRowGroup(listOf(middle, late)), RouteRowGroup(listOf(early))),
            minutesAfter = 30,
            windowEnd = ServerTime(30 * 60_000L),
            isStale = false,
            favoriteRouteIds = setOf(middle.routeId)
        )
        var state by mutableStateOf(content)
        composeRule.setContent {
            var mode by rememberArrivalDisplayMode("stop") { ArrivalDisplayMode.ROUTE }
            Surface {
                ArrivalsList(
                    content = state, rowCallbacks = previewRowCallbacks(),
                    onShowAlert = {}, onHideAlert = {}, onShowHiddenAlerts = {}, onLoadMore = {},
                    loadingMore = MutableStateFlow(false), modifier = Modifier.width(360.dp),
                    showDirection = false, displayMode = mode, onDisplayModeChange = { mode = it }
                )
            }
        }
        composeRule.onAllNodesWithText("Eight").assertCountEquals(1)
        composeRule.onNodeWithText("Time").performClick()
        composeRule.onAllNodesWithText("Eight").assertCountEquals(2)
        composeRule.onNodeWithText(early.statusText, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(composeRule.onNodeWithText("Forty").getUnclippedBoundsInRoot().top < composeRule.onAllNodesWithText("Eight")[0].getUnclippedBoundsInRoot().top)
        val directionBounds = composeRule.onNodeWithText("Forty", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val timeNode = soleClockNode(early)
        assertEquals(directionBounds.left, timeNode.getUnclippedBoundsInRoot().left)
        assertTrue(timeNode.getUnclippedBoundsInRoot().top >= directionBounds.bottom)
        composeRule.runOnIdle { state = content.copy(isStale = true) }
        composeRule.onNodeWithText("Time").assertIsSelected()
        composeRule.onAllNodesWithText("Eight").assertCountEquals(2)
        composeRule.onNodeWithText("Route").performClick()
        composeRule.onAllNodesWithText("Eight").assertCountEquals(1)
    }

    @Test
    fun chronologicalRowRetainsRouteActionsAtLargeTextInDarkTheme() {
        var openedSchedule: String? = null
        val arrival = previewArrival("40", "Downtown via Main Street", 5, scheduleDeviationMinutes = 3, context = context)
        val actions = ArrivalActions("trip", arrival.routeId, "40", null, scheduleUrl = "https://example.com/schedule", agencyName = null, blockId = null)
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        RouteArrivalRow(
                            group = RouteRowGroup(listOf(arrival)),
                            actionsFor = { actions },
                            isFavorite = true,
                            callbacks = previewRowCallbacks(onShowRouteSchedule = { openedSchedule = it }),
                            chronological = true,
                            anchors = ArrivalRowAnchors(eta = Modifier.testTag("eta")),
                            stopLabel = "Main St (Northbound)"
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Downtown via Main Street").assertIsDisplayed()
        composeRule.onNodeWithText("Main St (Northbound)").assertIsDisplayed()
        composeRule.onNodeWithText(arrival.statusText, useUnmergedTree = true).assertIsDisplayed()
        val etaBounds = composeRule.onNodeWithTag("eta", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(soleClockNode(arrival).getUnclippedBoundsInRoot().bottom <= etaBounds.top)
        composeRule.onNodeWithText("Downtown via Main Street").performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithText("Show route schedule").performClick()
        composeRule.runOnIdle { assertEquals("https://example.com/schedule", openedSchedule) }
    }

    @Test
    fun migrationContinueSavesThePreselectedTimeChoice() {
        var saved: ArrivalDisplayMode? = null
        composeRule.setContent {
            ArrivalDisplayChoiceDialog(onSave = { saved = it }, onDismiss = {})
        }
        composeRule.onNodeWithText("Time").assertIsSelected()
        assertTrue(
            composeRule.onNodeWithText("Time").getUnclippedBoundsInRoot().top <
                composeRule.onNodeWithText("Route").getUnclippedBoundsInRoot().top
        )
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, saved) }
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.runOnIdle { assertEquals(ArrivalDisplayMode.TIME, saved) }
    }

    @Test
    fun migrationBackDoesNotSaveAnUnconfirmedChoice() {
        var saved: ArrivalDisplayMode? = null
        var backCount = 0
        composeRule.setContent {
            ArrivalDisplayChoiceDialog(
                onSave = { saved = it },
                onDismiss = {},
                page = 2,
                pageCount = 2,
                onBack = { backCount++ }
            )
        }
        composeRule.onNodeWithContentDescription("Page 2 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("Route").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle {
            assertEquals(1, backCount)
            assertEquals(null, saved)
        }
    }
}
