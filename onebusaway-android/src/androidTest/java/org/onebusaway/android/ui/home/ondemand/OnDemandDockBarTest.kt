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
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.TUESDAY_TWO_PM
import org.onebusaway.android.ondemand.sampleMatch
import org.onebusaway.android.ondemand.sampleService
import org.onebusaway.android.ui.compose.LocalUnitsAreMetric
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.GeoPoint

class OnDemandDockBarTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.Rider)

    private fun render(vararg matches: OnDemandMatch, onCall: (String) -> Unit = {}, onZoomOut: () -> Unit = {}, onPageShown: (DockBarPage) -> Unit = {}) {
        val state = OnDemandDockState.Bar(matches.toList(), probe)
        val colors = matches.associate { it.service.id to 0xFF78AA36.toInt() }
        composeRule.setContent {
            ObaTheme {
                OnDemandDockBar(
                    pages = dockBarPages(state, emptyMap(), colors, TUESDAY_TWO_PM, Locale.US, metric = false),
                    probe = probe,
                    geometry = emptyMap(),
                    onPageShown = onPageShown,
                    onOpenDetail = {},
                    onOpenPicker = {},
                    onCall = onCall,
                    onOpenUrl = {},
                    onZoomOut = onZoomOut,
                    onPanTo = {}
                )
            }
        }
    }

    @Test
    fun anInsidePageShowsTheInsideTitleAndCallsTheService() {
        var called: String? = null
        render(sampleMatch(), onCall = { called = it })
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_inside)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.ondemand_bar_a11y_call, "Dial-a-Ride")).performClick()
        assertEquals("231-582-6900", called)
        // The badge sits inside the clickable thumbnail, whose merged node absorbs its tag.
        composeRule.onNodeWithTag(OnDemandDockTestTags.BADGE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theThumbnailIsLabelledAndZoomsOut() {
        var zoomed = 0
        render(sampleMatch(), onZoomOut = { zoomed++ })
        composeRule.onNodeWithContentDescription(context.getString(R.string.ondemand_bar_a11y_zoom_out)).performClick()
        assertEquals(1, zoomed)
    }

    @Test
    fun twoPagesShowTheBadgeAndAnOutsidePageShowsTheEdgeChevron() {
        val shown = mutableListOf<String>()
        render(sampleMatch(), sampleMatch(sampleService(id = "CC_CC2", name = "Medical Trips", inside = false)), onPageShown = { shown += it.match.service.id })
        // Spec §2.3: the first page is not highlighted just for appearing.
        assertEquals(emptyList<String>(), shown)
        composeRule.onNodeWithTag(OnDemandDockTestTags.BADGE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_badge, 1, 2), useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(OnDemandDockTestTags.BAR).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_badge, 2, 2), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("CC_CC2"), shown)
        composeRule.onNode(hasTestTag(OnDemandDockTestTags.TRAILING) and hasContentDescription(context.getString(R.string.ondemand_bar_a11y_show_edge))).assertIsDisplayed()
    }

    @Test
    fun aReprobeKeepsTheSwipedPage() {
        val matches = listOf(sampleMatch(), sampleMatch(sampleService(id = "CC_CC2", name = "Medical Trips", inside = false)))
        val state = mutableStateOf<OnDemandDockState>(OnDemandDockState.Bar(matches, probe))
        val actions = OnDemandDockActions(
            openDetail = { _, _ -> },
            openPicker = { _, _, _ -> },
            call = {},
            openUrl = {},
            zoomOut = { _, _ -> },
            panTo = {},
            highlight = {}
        )
        composeRule.setContent {
            ObaTheme {
                CompositionLocalProvider(LocalUnitsAreMetric provides false) {
                    OnDemandDockFeature(
                        state = state.value,
                        allMatches = matches,
                        edges = emptyMap(),
                        geometry = emptyMap(),
                        colors = matches.associate { it.service.id to 0xFF78AA36.toInt() },
                        now = TUESDAY_TWO_PM,
                        actions = actions
                    )
                }
            }
        }
        composeRule.onNodeWithTag(OnDemandDockTestTags.BAR).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_badge, 2, 2), useUnmergedTree = true).assertIsDisplayed()

        // A street-level reprobe (the rider moved) produces a new Bar with the same stack.
        state.value = OnDemandDockState.Bar(matches, ProbePoint(GeoPoint(45.052, -85.1), ProbeSource.Rider))
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_badge, 2, 2), useUnmergedTree = true).assertIsDisplayed()
    }
}
