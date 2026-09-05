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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.AlertItem
import org.onebusaway.android.ui.arrivals.ArrivalActionHandler
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.ArrivalsList
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.arrivals.StopHeader
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.home.nearby.NearbyArrivalsSheetHost
import org.onebusaway.android.ui.home.nearby.NearbyBay
import org.onebusaway.android.ui.home.nearby.NearbyRouteRow
import org.onebusaway.android.util.GeoPoint

/** Real lists inside Material's sheet: assert visible content and anchors, not height arithmetic. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalMaterial3Api::class)
class ArrivalsSheetContentTest(private val nearby: Boolean) {
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun shortListShowsItsFirstAndLastRowsWithoutEmptyExpansion() {
        composeRule.setContent { Harness(rowCount = 2) }
        assertShortList(2)
    }

    @Test
    fun sameListGrowsToCeilingScrollsAndShrinksBackToContent() {
        var count by mutableIntStateOf(1)
        composeRule.setContent { Harness(rowCount = count) }
        assertShortList(1)

        composeRule.runOnIdle { count = 20 }
        assertAtCeiling(400)
        composeRule.onNodeWithText("Destination 0").assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(19)
        composeRule.onNodeWithText("Destination 19").assertIsDisplayed()
        assertAtCeiling(400)

        // Shrink while scrolled, retaining the same LazyListState, as a refreshed response can do.
        composeRule.runOnIdle { count = 2 }
        assertShortList(2)
    }

    @Test
    fun ceilingChangesRemeasureTheListAndKeepTheFirstRowVisible() {
        var ceiling by mutableStateOf(400.dp)
        composeRule.setContent { Harness(rowCount = 20, maxHeight = ceiling) }
        assertAtCeiling(400)
        composeRule.runOnIdle { ceiling = 280.dp }
        assertAtCeiling(280)
        composeRule.onNodeWithText("Destination 0").assertIsDisplayed()
    }

    private fun assertShortList(count: Int) {
        composeRule.waitForIdle()
        val sheet = composeRule.onNodeWithTag("sheet-content").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithText("Destination 0").assertIsDisplayed().getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithText("Destination ${count - 1}").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("First row must not be above the visible sheet", first.top >= sheet.top)
        assertTrue("Last row must fit inside the sheet", last.bottom <= sheet.bottom)
        assertTrue("Short content must not expand to the ceiling", sheet.height < 400.dp)
        // The actual lazy viewport is the sheet, not a larger hidden measurement surface.
        val list = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).getUnclippedBoundsInRoot()
        assertEquals(sheet.top, list.top)
        assertEquals(sheet.bottom, list.bottom)
        if (!nearby) composeRule.onNodeWithText("Load more").assertIsDisplayed()
    }

    private fun assertAtCeiling(height: Int) {
        composeRule.waitForIdle()
        val sheet = composeRule.onNodeWithTag("sheet-content").getUnclippedBoundsInRoot()
        val container = composeRule.onNodeWithTag("scaffold").getUnclippedBoundsInRoot()
        assertEquals(height.dp, sheet.height)
        assertEquals(container.bottom, sheet.bottom)
        assertEquals(container.bottom - height.dp, sheet.top)
    }

    @Composable
    private fun Harness(rowCount: Int, maxHeight: androidx.compose.ui.unit.Dp = 400.dp) {
        val listState = rememberLazyListState()
        val groups = remember(rowCount) {
            List(rowCount) { index ->
                RouteRowGroup(listOf(previewArrival("$index", "Destination $index", 5)))
            }
        }
        var panelPx by remember { mutableIntStateOf(0) }
        val peek = minOf(with(LocalDensity.current) { panelPx.toDp() } + 24.dp, 120.dp)
        BottomSheetScaffold(
            modifier = Modifier.size(360.dp, 560.dp).testTag("scaffold"),
            scaffoldState = rememberBottomSheetScaffoldState(
                bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded)
            ),
            sheetPeekHeight = peek,
            sheetDragHandle = { Box(Modifier.height(24.dp)) },
            sheetContent = {
                ArrivalsSheetContent(maxHeight, { panelPx = it }, Modifier.testTag("sheet-content")) {
                    ListContent(groups, listState)
                }
            }
        ) {}
    }

    @Composable
    private fun ListContent(groups: List<RouteRowGroup>, listState: LazyListState) {
        if (nearby) {
            NearbyArrivalsSheetHost(
                rows = groups.map { group ->
                    NearbyRouteRow(group, NearbyBay("stop", "Main St", null, null, GeoPoint(47.6, -122.3)))
                },
                actionsFor = { null },
                favoriteRouteIds = emptySet(),
                callbacks = previewRowCallbacks(),
                limitExceeded = false,
                listState = listState
            )
        } else {
            ArrivalsList(
                content = ArrivalsUiState.Content(
                    header = StopHeader("stop", "Main St", null, false),
                    arrivals = groups.flatMap { it.trips },
                    routeGroups = groups,
                    minutesAfter = 60,
                    windowEnd = ServerTime(0),
                    isStale = false
                ),
                rowCallbacks = previewRowCallbacks(),
                handler = unusedActions,
                onShowHiddenAlerts = {},
                onLoadMore = {},
                loadingMore = remember { MutableStateFlow(false) },
                listState = listState,
                showDirection = false,
                showAlerts = false,
                contentPadding = PaddingValues(bottom = 24.dp)
            )
        }
    }

    private val unusedActions = object : ArrivalActionHandler {
        override fun onRouteFavorite(actions: ArrivalActions) = error("Unexpected action")
        override fun onShowVehiclesOnMap(arrival: ArrivalInfo) = error("Unexpected action")
        override fun onShowRouteOnMap(arrival: ArrivalInfo) = error("Unexpected action")
        override fun onFocusVehicleOnMap(arrival: ArrivalInfo) = error("Unexpected action")
        override fun onShowTripStatus(arrival: ArrivalInfo) = error("Unexpected action")
        override fun onSetReminder(arrival: ArrivalInfo) = error("Unexpected action")
        override fun onToggleTracking(arrival: ArrivalInfo) = error("Unexpected action")
        override fun onShowRouteSchedule(scheduleUrl: String) = error("Unexpected action")
        override fun onReportArrivalProblem(actions: ArrivalActions) = error("Unexpected action")
        override fun onShowAlert(alertId: String) = error("Unexpected action")
        override fun onHideAlert(alert: AlertItem) = error("Unexpected action")
        override fun onReportStopProblem() = error("Unexpected action")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "nearby={0}")
        fun parameters() = listOf(arrayOf(false), arrayOf(true))
    }
}
