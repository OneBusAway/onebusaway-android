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
package org.onebusaway.android.ui.home.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.components.MigrationChoice
import org.onebusaway.android.ui.compose.components.PhonePreview
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.searchresults.SearchResultMode

class SearchWorkflowChoiceTest {
    @get:Rule val compose = createUnconfinedComposeRule()

    @Test
    fun continueSavesThePreselectedListsChoice() {
        var saved: SearchResultMode? = null
        compose.setContent { ObaTheme { SearchWorkflowChoiceDialog({ saved = it }, {}) } }
        compose.onNodeWithContentDescription("Page 1 of 1").assertIsDisplayed()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.onNode(hasText("List navigation") and hasText("Classic layout")).assertIsSelected()
        compose.onNode(hasText("Map navigation") and hasText("New layout")).assertIsDisplayed()
        assertTrue(
            compose.onNodeWithText("List navigation").getUnclippedBoundsInRoot().top <
                compose.onNodeWithText("Map navigation").getUnclippedBoundsInRoot().top
        )
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(SearchResultMode.LISTS, saved) }
    }

    @Test
    fun mapChoiceSurvivesRecreationBeforeContinue() {
        var saved: SearchResultMode? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ObaTheme { SearchWorkflowChoiceDialog({ saved = it }, {}) } }
        compose.onNodeWithText("Map navigation").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(SearchResultMode.MAP, saved) }
    }

    @Test
    fun previewCallbackHandlesTapWithoutParentOrSampleActions() {
        var previewTaps = 0
        var parentTaps = 0
        var sampleTaps = 0
        compose.setContent {
            ObaTheme {
                MigrationChoice(
                    title = "Choice",
                    description = "Preview tap target",
                    previous = true,
                    selected = false,
                    onSelect = { parentTaps++ }
                ) {
                    PhonePreview(onSelect = { previewTaps++ }, modifier = Modifier.testTag("phone-preview")) {
                        Box(Modifier.fillMaxWidth().height(120.dp).clickable { sampleTaps++ })
                    }
                }
            }
        }
        // Target physical preview bounds: performClick would invoke semantics instead of hit testing.
        compose.onNodeWithTag("phone-preview", useUnmergedTree = true).performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, previewTaps)
            assertEquals(0, parentTaps)
            assertEquals(0, sampleTaps)
        }
    }

    @Test
    fun previewTapSelectsOnlyTheEnclosingChoice() {
        var saved: SearchResultMode? = null
        compose.setContent { ObaTheme { SearchWorkflowChoiceDialog({ saved = it }, {}) } }
        compose.onNodeWithText("Map navigation").performScrollTo().performClick()
        compose.onNodeWithText("List navigation").performScrollTo().performTouchInput {
            click(Offset(center.x, height * .8f))
        }
        compose.onNodeWithText("List navigation").assertIsSelected()
        compose.runOnIdle { assertEquals(null, saved) }
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(SearchResultMode.LISTS, saved) }
    }
}
