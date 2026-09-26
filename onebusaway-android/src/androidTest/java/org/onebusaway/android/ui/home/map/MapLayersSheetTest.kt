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
package org.onebusaway.android.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

class MapLayersSheetTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tappingATileFlipsItsSubtitleAndResetAppears() {
        composeRule.setContent {
            var prefs by remember { mutableStateOf(LayerPrefs.DEFAULTS) }
            val state = layersUiState(prefs, onDemandTileVisible = true, rentalsEnabled = true, hasBasemapChoice = false)
            ObaTheme {
                MapLayersSheet(state = state, onToggle = { prefs = prefs.toggled(it) }, onBasemap = {}, onReset = { prefs = LayerPrefs.DEFAULTS }, onDismiss = {})
            }
        }
        val zones = composeRule.onNodeWithTag(MapLayersTestTags.tile(LayerTileId.ON_DEMAND_ZONES))
        zones.assertTextContains(context.getString(R.string.map_layers_state_on))
        zones.performClick()
        zones.assertTextContains(context.getString(R.string.map_layers_state_off))
        composeRule.onNodeWithText(context.getString(R.string.map_layers_reset)).assertIsDisplayed().performClick()
        zones.assertTextContains(context.getString(R.string.map_layers_state_on))
    }
}
