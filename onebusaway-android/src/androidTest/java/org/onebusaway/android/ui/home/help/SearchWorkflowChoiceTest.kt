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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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
        compose.onNodeWithText("Lists and arrivals").assertIsSelected()
        assertTrue(
            compose.onNodeWithText("Lists and arrivals").getUnclippedBoundsInRoot().top <
                compose.onNodeWithText("Map").getUnclippedBoundsInRoot().top
        )
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(SearchResultMode.LISTS, saved) }
    }

    @Test
    fun mapChoiceSurvivesRecreationBeforeContinue() {
        var saved: SearchResultMode? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ObaTheme { SearchWorkflowChoiceDialog({ saved = it }, {}) } }
        compose.onNodeWithText("Map").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(SearchResultMode.MAP, saved) }
    }

    @Test
    fun previewTapSelectsOnlyTheEnclosingChoice() {
        var saved: SearchResultMode? = null
        compose.setContent { ObaTheme { SearchWorkflowChoiceDialog({ saved = it }, {}) } }
        compose.onNodeWithText("Lists and arrivals").performScrollTo().performTouchInput {
            click(Offset(center.x, height * .8f))
        }
        compose.onNodeWithText("Lists and arrivals").assertIsSelected()
        compose.runOnIdle { assertEquals(null, saved) }
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(SearchResultMode.LISTS, saved) }
    }
}
