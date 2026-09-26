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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.GeoPoint

/**
 * Spec §6.2: the shared Canvas drawing must compose without crashing for both an empty zone (the bar
 * before a probe resolves) and a multi-ring zone with a probe dot (the common case for both the bar
 * and the detail page).
 */
class ZoneThumbnailTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun composesAndDisplaysWithNoShapesOrProbe() {
        composeRule.setContent {
            ObaTheme {
                ZoneThumbnail(
                    shapes = emptyList(),
                    centre = null,
                    probePoint = null,
                    probeStyle = ProbeDotStyle.RIDER,
                    modifier = Modifier.testTag(TAG).size(SIZE)
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG).assertIsDisplayed()
    }

    @Test
    fun composesAndDisplaysWithMultiRingShapesAndARiderProbe() {
        val square = ThumbnailShape(
            rings = listOf(
                listOf(GeoPoint(45.00, -85.20), GeoPoint(45.00, -85.00), GeoPoint(45.10, -85.00), GeoPoint(45.10, -85.20), GeoPoint(45.00, -85.20))
            ),
            color = 0xFF2196F3.toInt()
        )
        val holedPolygon = ThumbnailShape(
            rings = listOf(
                listOf(GeoPoint(44.80, -85.60), GeoPoint(44.80, -85.30), GeoPoint(45.00, -85.30), GeoPoint(45.00, -85.60), GeoPoint(44.80, -85.60)),
                listOf(GeoPoint(44.85, -85.55), GeoPoint(44.90, -85.55), GeoPoint(44.90, -85.35), GeoPoint(44.85, -85.35), GeoPoint(44.85, -85.55))
            ),
            color = 0xFF4CAF50.toInt()
        )
        val rider = GeoPoint(44.95, -85.35)

        composeRule.setContent {
            ObaTheme {
                ZoneThumbnail(
                    shapes = listOf(square, holedPolygon),
                    centre = shapesCentre(listOf(square, holedPolygon)),
                    probePoint = rider,
                    probeStyle = ProbeDotStyle.RIDER,
                    modifier = Modifier.testTag(TAG).size(SIZE)
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG).assertIsDisplayed()
    }

    private companion object {
        const val TAG = "zone_thumbnail"
        val SIZE = 56.dp
    }
}
