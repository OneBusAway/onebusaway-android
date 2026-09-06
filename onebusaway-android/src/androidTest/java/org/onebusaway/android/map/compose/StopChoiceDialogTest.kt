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
package org.onebusaway.android.map.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.GeoPoint

class StopChoiceDialogTest {
    @get:Rule val composeRule = createUnconfinedComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val selected = mutableListOf<String>()
    private var dismissals = 0

    @Test
    fun eachRowNamesItsOwnRoutesAndSelectsItsOriginalStop() {
        render()
        composeRule.onNodeWithText(context.getString(R.string.map_stop_routes, "5, 40")).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.map_stop_routes, "597")).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.stop_details_code, "590")).performClick()
        composeRule.onNodeWithText(context.getString(R.string.stop_details_code, "4720")).performClick()
        assertEquals(listOf("1_590", "3_2479"), selected)
        assertEquals(0, dismissals)
    }

    @Test
    fun cancelDoesNotSelectEitherStop() {
        render()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertTrue(selected.isEmpty())
        assertEquals(1, dismissals)
    }

    private fun render() {
        val stops = listOf(
            marker("1_590", "590", listOf("5", "40")),
            marker("3_2479", "4720", listOf("597"))
        )
        composeRule.setContent {
            ObaTheme {
                StopChoiceDialog(stops, onSelect = { selected += it.id }, onDismiss = { dismissals++ })
            }
        }
    }

    private fun marker(id: String, code: String, routes: List<String>) = StopMarker(
        id,
        GeoPoint(47.611137, -122.338951),
        "NW",
        3,
        ObaStopElement(id = id, code = code, name = "3rd Ave & Pine St"),
        routes = routes.map { StopRoute(it, null) }
    )
}
