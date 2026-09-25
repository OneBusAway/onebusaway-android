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
package org.onebusaway.android.ui.tripresults

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The results name a trip's day whenever it isn't today (#2337). They used to show clock times alone,
 * so a trip planned for tomorrow read exactly like one leaving within the hour — and a screenshot of it
 * kept for later said nothing about which day it was for.
 */
class TripDayLabelTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.systemDefault()

    @Test
    fun aTripTomorrowSaysSoOnItsCardAndAtTheHeadOfItsLog() {
        show(dayOffset = 1)

        val word = context.getString(R.string.trip_plan_date_tomorrow)
        composeRule.onNodeWithTag(OPTION_DAY_TEST_TAG, useUnmergedTree = true).assertTextEquals(word)
        // Once, at the start — not repeated on every time of a trip that stays on one day.
        composeRule.onNodeWithTag(TRIP_LOG_DAY_TEST_TAG, useUnmergedTree = true).assertTextEquals(word)
    }

    @Test
    fun aTripTodayNamesNoDay() {
        show(dayOffset = 0)

        assertEquals(0, composeRule.onAllNodesWithTag(OPTION_DAY_TEST_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(TRIP_LOG_DAY_TEST_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // ---- fixtures -----------------------------------------------------------------------------------

    /** Noon, [dayOffset] days from today — clear of midnight whatever the device zone. */
    private fun noon(dayOffset: Long, plusMinutes: Long = 0) = ServerTime(
        deviceToday(zone).plusDays(dayOffset).atTime(LocalTime.NOON).plusMinutes(plusMinutes)
            .atZone(zone).toInstant().toEpochMilli()
    )

    private fun show(dayOffset: Long) {
        val start = noon(dayOffset)
        val end = noon(dayOffset, plusMinutes = 20)
        val state = TripResultsUiState.Success(
            options = listOf(
                ItineraryOption(
                    symbols = listOf(ModeSymbol.Street(StreetMode.WALK)),
                    durationMinutes = 20L,
                    startTime = start,
                    endTime = end,
                    streetDistanceMeters = mapOf(StreetMode.WALK to 1500.0)
                )
            ),
            selectedIndex = 0,
            directions = listOf(
                TripLogEntry.Terminal(TerminalKind.START, start, "Renton Technical College"),
                TripLogEntry.Terminal(TerminalKind.ARRIVE, end, "Exhibition Hall")
            )
        )
        composeRule.setContent { ObaTheme { TripResultsList(state = state) } }
        composeRule.waitForIdle()
    }
}
