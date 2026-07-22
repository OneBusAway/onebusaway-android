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

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

class FocusBannerTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setStopBanner(
        hasAlerts: Boolean = true,
        onClearSubordinateRoute: () -> Unit = {}
    ) {
        composeRule.setContent {
            FocusBanner(
                state = FocusBannerState.Stop(
                    title = "Pine St & 3rd Ave",
                    direction = "N",
                    stopCode = "12345",
                    isFavorite = false,
                    favoriteEnabled = true,
                    hasAlerts = hasAlerts,
                    subordinateRoutes = listOf(
                        FocusBannerState.SubordinateRoute("65"),
                        FocusBannerState.SubordinateRoute("75"),
                        FocusBannerState.SubordinateRoute("40")
                    ),
                    subordinateHeadsign = "Downtown"
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onClearSubordinateRoute = onClearSubordinateRoute,
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {}
            )
        }
    }

    @Test
    fun stopChromeUsesCompactStarAndAccessibleAlertTarget() {
        setStopBanner()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_shortcut)
        ).assertIsDisplayed()
        val starBounds = composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue((starBounds.right - starBounds.left).value in 25.9f..26.9f)
        assertTrue((starBounds.bottom - starBounds.top).value in 25.9f..26.9f)

        val alertBounds = composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_info_show_alerts)
        ).assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue((alertBounds.right - alertBounds.left).value >= 47.5f)
        assertTrue((alertBounds.bottom - alertBounds.top).value >= 47.5f)
    }

    @Test
    fun routeBannerUsesRouteOrientationIcon() {
        composeRule.setContent {
            FocusBanner(
                state = FocusBannerState.Route(
                    header = org.onebusaway.android.map.RouteHeader(
                        loading = false,
                        shortName = "40",
                        longName = "Downtown - Northgate",
                        agency = "Metro",
                        routeId = "1_40"
                    ),
                    isFavorite = false
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onClearSubordinateRoute = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {}
            )
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.route_shortcut)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun stopBannerShowsIdentityAndFullSubordinateRouteChain() {
        setStopBanner()
        composeRule.onNodeWithText("Pine St & 3rd Ave").assertIsDisplayed()
        val expectedSubtitle = "${context.getString(R.string.stop_details_code, "12345")} · " +
            context.getString(R.string.direction_n)
        composeRule.onNodeWithText(expectedSubtitle).assertIsDisplayed()
        listOf("65", "75", "40", "Downtown").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun subordinateRouteDismissIsACompactExactSizeTarget() {
        var cleared = false
        setStopBanner(onClearSubordinateRoute = { cleared = true })

        val dismiss = composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_info_unselect_route)
        ).assertIsDisplayed().assertHasClickAction()
        val bounds = dismiss.getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left).value in 21.5f..22.5f)
        assertTrue((bounds.bottom - bounds.top).value in 21.5f..22.5f)
        dismiss.performClick()
        assertTrue(cleared)
    }

    @Test
    fun loadingStopBannerReservesTheFavoriteStar() {
        composeRule.setContent {
            FocusBanner(
                state = FocusBannerState.Stop(
                    title = "Pine St & 3rd Ave",
                    direction = null,
                    stopCode = null,
                    isFavorite = false,
                    favoriteEnabled = false,
                    hasAlerts = false
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onClearSubordinateRoute = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onHeight = {}
            )
        }

        val star = composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).assertIsDisplayed().assertIsNotEnabled().getUnclippedBoundsInRoot()
        val typeIcon = composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_shortcut)
        ).getUnclippedBoundsInRoot()
        val stopName = composeRule.onNodeWithText("Pine St & 3rd Ave")
            .getUnclippedBoundsInRoot()

        val typeIconCenter = (typeIcon.top.value + typeIcon.bottom.value) / 2f
        val starCenter = (star.top.value + star.bottom.value) / 2f
        val stopNameCenter = (stopName.top.value + stopName.bottom.value) / 2f
        val railCenter = (typeIconCenter + starCenter) / 2f
        assertTrue(abs(stopNameCenter - railCenter) < 1f)
    }
}
