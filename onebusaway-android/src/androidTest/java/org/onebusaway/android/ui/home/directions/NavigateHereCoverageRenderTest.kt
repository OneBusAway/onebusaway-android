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
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.ondemand.OnDemandCoverageLine
import org.onebusaway.android.ondemand.sampleMatch
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** The dropped pin's third line (DRT UI spec §3.7): "Inside …" / "Outside …", opening the zone. */
class NavigateHereCoverageRenderTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theCoverageLineNamesTheZoneAndOpensIt() {
        var opened = 0
        composeRule.setContent {
            ObaTheme {
                NavigateHereOffer(
                    anchor = { ScreenOffset(200f, 500f) },
                    onNavigate = {},
                    onDismiss = {},
                    coverage = OnDemandCoverageLine("Dial-a-Ride", isInside = true, match = sampleMatch(), othersInside = 0),
                    onOpenCoverage = { opened++ }
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.ondemand_address_inside, "Dial-a-Ride")).assertIsDisplayed().performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithText(context.getString(R.string.map_navigate_here)).assertIsDisplayed()
    }
}
