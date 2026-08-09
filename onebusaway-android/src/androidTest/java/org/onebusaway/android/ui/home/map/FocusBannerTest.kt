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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteMapDirection
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

class FocusBannerTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setStopBanner(
        hasAlerts: Boolean = true,
        favoriteEnabled: Boolean = true,
        direction: String? = "N",
        stopCode: String? = "12345"
    ) {
        composeRule.setContent {
            FocusBanner(
                state = FocusBannerState.Stop(
                    title = "Pine St & 3rd Ave",
                    direction = direction,
                    stopCode = stopCode,
                    isFavorite = false,
                    favoriteEnabled = favoriteEnabled,
                    hasAlerts = hasAlerts
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onRecenterStop = {},
                onSelectDirection = {},
                onFrameRoute = {},
                onShowSchedule = {},
                onHeight = {}
            )
        }
    }

    /**
     * The star draws small but must still be tappable: it's laid out at the icon's own size so it
     * doesn't bloat the rail, and relies on touch-target expansion for the accessible target. This
     * pins both halves — the drawn size *and* the touch bounds — so a layout change can't quietly
     * shrink the thing you actually have to hit.
     */
    @Test
    fun stopChromeUsesCompactStarAndAccessibleAlertTarget() {
        setStopBanner()
        val star = composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).assertHasClickAction()
        val starBounds = star.getUnclippedBoundsInRoot()
        assertTrue((starBounds.right - starBounds.left).value in 25.9f..26.9f)
        assertTrue((starBounds.bottom - starBounds.top).value in 25.9f..26.9f)

        val starTouch = star.fetchSemanticsNode().touchBoundsInRoot
        with(composeRule.density) {
            assertTrue(starTouch.width.toDp().value >= 47.5f)
            assertTrue(starTouch.height.toDp().value >= 47.5f)
        }

        val alertBounds = composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_info_show_alerts)
        ).assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue((alertBounds.right - alertBounds.left).value >= 47.5f)
        assertTrue((alertBounds.bottom - alertBounds.top).value >= 47.5f)
    }

    private fun setRouteBanner(
        scheduleUrl: String? = null,
        onShowSchedule: (String) -> Unit = {},
        onFrameRoute: () -> Unit = {},
        directions: List<RouteMapDirection> = emptyList(),
        currentDirectionId: Int? = null,
        onSelectDirection: (Int?) -> Unit = {}
    ) {
        composeRule.setContent {
            FocusBanner(
                state = FocusBannerState.Route(
                    header = org.onebusaway.android.map.RouteHeader(
                        loading = false,
                        shortName = "40",
                        longName = ROUTE_LONG_NAME,
                        agency = "Metro",
                        routeId = "1_40",
                        scheduleUrl = scheduleUrl,
                        directions = directions,
                        currentDirectionId = currentDirectionId
                    ),
                    isFavorite = false
                ),
                onClose = {},
                onToggleFavorite = {},
                onShowAlerts = {},
                onRecenterStop = {},
                onSelectDirection = onSelectDirection,
                onFrameRoute = onFrameRoute,
                onShowSchedule = onShowSchedule,
                onHeight = {}
            )
        }
    }

    private fun openDirectionMenu() = composeRule.onNodeWithContentDescription(
        context.getString(R.string.route_header_select_direction)
    ).assertIsDisplayed().performClick()

    /**
     * The whole point of #2033: the menu always offers "All directions", so a route narrowed to one
     * direction can be widened back. The old swap button could only cycle between directions.
     */
    @Test
    fun directionMenuOffersAllDirectionsAlongsideTheNamedOnes() {
        var selected: Int? = -1
        setRouteBanner(
            directions = TWO_DIRECTIONS,
            currentDirectionId = 1,
            onSelectDirection = { selected = it }
        )
        // The header states the menu's current value.
        composeRule.onNodeWithText(NORTHGATE).assertIsDisplayed()

        openDirectionMenu()
        composeRule.onNodeWithText(DOWNTOWN).assertIsDisplayed()
        // The shown direction is now on screen twice: the subtitle and its own (checked) menu row.
        composeRule.onAllNodesWithText(NORTHGATE).assertCountEquals(2)

        composeRule.onNodeWithText(context.getString(R.string.route_header_all_directions))
            .performClick()
        assertNull(selected)
    }

    /** Picking a named direction reports that direction's id. */
    @Test
    fun directionMenuSelectsANamedDirection() {
        var selected: Int? = null
        setRouteBanner(
            directions = TWO_DIRECTIONS,
            currentDirectionId = null,
            onSelectDirection = { selected = it }
        )
        // Shown whole, the subtitle reads as the menu's default rather than a headsign.
        composeRule.onNodeWithText(context.getString(R.string.route_header_all_directions))
            .assertIsDisplayed()

        openDirectionMenu()
        composeRule.onNodeWithText(DOWNTOWN).performClick()
        assertEquals(0, selected)
    }

    /** A route with nothing to choose between shows neither the menu nor a direction line. */
    @Test
    fun singleDirectionRouteHasNoDirectionMenu() {
        setRouteBanner(directions = listOf(RouteMapDirection(0, DOWNTOWN)))

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.route_header_select_direction)
        ).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.route_header_all_directions))
            .assertDoesNotExist()
    }

    /** The route banner's leading rail carries the star and nothing else (#2216). */
    @Test
    fun routeBannerRailIsJustTheStar() {
        setRouteBanner()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.route_shortcut)
        ).assertDoesNotExist()
    }

    @Test
    fun longPressingRouteBannerOpensTheSchedule() {
        var opened: String? = null
        setRouteBanner(scheduleUrl = SCHEDULE_URL, onShowSchedule = { opened = it })

        composeRule.onNodeWithText(ROUTE_LONG_NAME).performTouchInput { longClick() }

        composeRule.onNodeWithText(
            context.getString(R.string.bus_options_menu_show_route_schedule)
        ).performClick()

        assertEquals(SCHEDULE_URL, opened)
    }

    /** Tapping still frames the route, but with no schedule page there is nothing to long-press for. */
    @Test
    fun routeBannerHasNoLongPressWithoutASchedule() {
        var framed = false
        setRouteBanner(onFrameRoute = { framed = true })

        val row = composeRule.onNodeWithText(ROUTE_LONG_NAME)
        row.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        row.performClick()
        assertTrue(framed)
    }

    /**
     * The star is inset from the card edge by the rail and separated from the stop name by the
     * content's own start padding — nothing else. The rail used to center the star in a fixed-width
     * column, adding a trailing gutter on top of that padding and leaving the right-hand gap about
     * twice the left inset (#2216). Pinning the two against each other states the intent (balanced,
     * with the text side no looser than the edge side) without hardcoding either dp.
     */
    @Test
    fun starIsNoFurtherFromTheStopNameThanFromTheCardEdge() {
        setStopBanner()
        val star = composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).getUnclippedBoundsInRoot()
        val stopName = composeRule.onNodeWithText("Pine St & 3rd Ave").getUnclippedBoundsInRoot()

        val leadingInset = star.left.value
        val gapToStopName = stopName.left.value - star.right.value
        assertTrue("star should be inset from the card edge", leadingInset > 4f)
        assertTrue(
            "gap to the stop name ($gapToStopName) should not exceed the leading inset " +
                "($leadingInset)",
            gapToStopName <= leadingInset + 0.5f
        )
        assertTrue("star and stop name should not collide", gapToStopName > 4f)
    }

    @Test
    fun stopBannerShowsStopIdentity() {
        setStopBanner()
        composeRule.onNodeWithText("Pine St & 3rd Ave").assertIsDisplayed()
        val expectedSubtitle = "${context.getString(R.string.stop_details_code, "12345")} · " +
            context.getString(R.string.direction_n)
        composeRule.onNodeWithText(expectedSubtitle).assertIsDisplayed()
    }

    /**
     * A stop still loading its details shows a disabled star rather than an empty rail, and that
     * star sits on the stop name's line — the rail's only occupant since #2216.
     */
    @Test
    fun loadingStopBannerReservesTheFavoriteStar() {
        setStopBanner(
            hasAlerts = false,
            favoriteEnabled = false,
            direction = null,
            stopCode = null
        )

        val star = composeRule.onNodeWithContentDescription(
            context.getString(R.string.bus_options_menu_add_star)
        ).assertIsDisplayed().assertIsNotEnabled().getUnclippedBoundsInRoot()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.stop_shortcut)
        ).assertDoesNotExist()
        val stopName = composeRule.onNodeWithText("Pine St & 3rd Ave")
            .getUnclippedBoundsInRoot()

        val starCenter = (star.top.value + star.bottom.value) / 2f
        val stopNameCenter = (stopName.top.value + stopName.bottom.value) / 2f
        assertTrue(abs(stopNameCenter - starCenter) < 1f)
    }

    private companion object {
        const val ROUTE_LONG_NAME = "Downtown - Northgate"
        const val SCHEDULE_URL = "https://example.org/route/40/schedule"
        const val DOWNTOWN = "to Downtown"
        const val NORTHGATE = "to Northgate"
        val TWO_DIRECTIONS = listOf(RouteMapDirection(0, DOWNTOWN), RouteMapDirection(1, NORTHGATE))
    }
}
