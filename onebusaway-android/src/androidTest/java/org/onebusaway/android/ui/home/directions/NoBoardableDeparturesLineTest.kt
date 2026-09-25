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
package org.onebusaway.android.ui.home.directions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The one-line stand-in for a Board row's ETA strip when the feed has nothing the rider can board
 * (#2228): it says so, and its "Show" hands over to the strip.
 */
class NoBoardableDeparturesLineTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun saysSo_andShowRevealsTheStrip() {
        var reveals = 0
        composeRule.setContent {
            NoBoardableDeparturesLine(onReveal = { reveals++ })
        }

        composeRule.onNodeWithText(context.getString(R.string.directions_stop_eta_none_boardable)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.directions_stop_eta_show_anyway)).performClick()
        assertEquals(1, reveals)
    }
}
