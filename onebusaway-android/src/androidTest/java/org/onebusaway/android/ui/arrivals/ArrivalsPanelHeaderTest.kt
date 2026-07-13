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

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.ArrivalsPanelHeader

/**
 * On-device regression test for the pinned arrivals-drawer header's service-alert toggle (PR #1812
 * review). The warning glyph is only a 24dp icon, but tapping it expands/collapses the alerts section,
 * so its tappable area must meet the 48dp accessibility minimum — enforced via
 * [androidx.compose.material3.minimumInteractiveComponentSize]. This locks that in: shrinking the
 * touch target back to the glyph's visual size would fail here.
 */
class ArrivalsPanelHeaderTest {

    // See EtaStripJustifyTest for why the v1 (Unconfined) rule is used here (issue #1792).
    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun alertToggleMeetsMinimumTouchTarget() {
        composeRule.setContent {
            ArrivalsPanelHeader(
                title = "Pine St & 3rd Ave",
                direction = "N",
                isFavorite = false,
                showActions = true,
                hasAlerts = true,
                alertsExpanded = false,
                onToggleAlerts = {},
                filtering = false,
                onToggleFavorite = {},
            )
        }

        val showAlerts = context.getString(R.string.stop_info_show_alerts)
        val bounds = composeRule.onNodeWithContentDescription(showAlerts)
            .assertHasClickAction()
            .getUnclippedBoundsInRoot()

        // minimumInteractiveComponentSize reserves at least 48dp in both axes for the touch target,
        // even though the glyph itself is 24dp.
        val width = (bounds.right - bounds.left).value
        val height = (bounds.bottom - bounds.top).value
        assertTrue("toggle width ${width}dp < 48dp", width >= 48f - 0.5f)
        assertTrue("toggle height ${height}dp < 48dp", height >= 48f - 0.5f)
    }

    @Test
    fun alertToggleContentDescriptionFollowsExpandedState() {
        // The same glyph carries a state-appropriate label so a screen reader announces the action it
        // will perform. Tapping is wired through onToggleAlerts (asserted clickable above); here we pin
        // the two labels the header selects between.
        composeRule.setContent {
            ArrivalsPanelHeader(
                title = "Pine St & 3rd Ave",
                direction = "N",
                isFavorite = false,
                showActions = true,
                hasAlerts = true,
                alertsExpanded = true,
                onToggleAlerts = {},
                filtering = false,
                onToggleFavorite = {},
            )
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_info_hide_alerts_toggle)
        ).assertHasClickAction().performClick()
    }
}
