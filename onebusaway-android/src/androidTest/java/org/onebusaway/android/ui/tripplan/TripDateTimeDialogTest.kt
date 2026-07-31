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
package org.onebusaway.android.ui.tripplan

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The combined date/time picker (#2117), which replaced the two menu rows that each set the whole
 * instant from one of its halves.
 *
 * What's asserted here is the seam the merge created: the day and the time are settled separately but
 * reported as one instant, so moving the day must carry the time across untouched and vice versa. The
 * dial itself is Material's and isn't re-tested; the time is left at whatever the dialog opened on,
 * which is exactly the half a day-only change must not disturb.
 */
class TripDateTimeDialogTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)

    /** Local wall-clock [date] at 15:45, as the epoch millis the form carries. */
    private fun atQuarterToFour(date: LocalDate): Long = date.atTime(15, 45).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun theDayDropdownOpensOnTheDayItWasGiven() {
        showDialog(atQuarterToFour(today))

        composeRule.onNodeWithTag(TripPlanTestTags.PICKER_DAY).assertTextContains("Today")
    }

    /** Naming tomorrow moves only the day: the time the dialog opened on rides across with it. */
    @Test
    fun namingTomorrowKeepsTheTimeAndReportsOneInstant() {
        var confirmed: Long? = null
        showDialog(atQuarterToFour(today), onConfirm = { confirmed = it })

        composeRule.onNodeWithTag(TripPlanTestTags.PICKER_DAY).performClick()
        composeRule.onNodeWithText("Tomorrow").performClick()
        composeRule.onNodeWithTag(TripPlanTestTags.PICKER_DAY).assertTextContains("Tomorrow")
        composeRule.onNodeWithText("OK").performClick()

        composeRule.runOnIdle {
            assertEquals(atQuarterToFour(today.plusDays(1)), confirmed)
        }
    }

    /** Any other day is the date itself, so the button always states the day it holds. */
    @Test
    fun aDayBeyondTomorrowShowsItsOwnDate() {
        val later = today.plusDays(5)
        showDialog(atQuarterToFour(later))

        composeRule.onNodeWithTag(TripPlanTestTags.PICKER_DAY).assertTextContains(
            later.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        )
    }

    /** Cancelling pins nothing — the trip keeps whatever anchor it had. */
    @Test
    fun cancellingReportsNoInstant() {
        var confirmed: Long? = null
        var dismissed = false
        showDialog(
            atQuarterToFour(today),
            onConfirm = { confirmed = it },
            onDismiss = { dismissed = true }
        )

        composeRule.onNodeWithTag(TripPlanTestTags.PICKER_DAY).performClick()
        composeRule.onNodeWithText("Tomorrow").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle {
            assertNull("cancelling should report no instant", confirmed)
            assertEquals(true, dismissed)
        }
    }

    /** The instant a confirmed pick reports is the chosen day at the chosen wall-clock time. */
    @Test
    fun theConfirmedInstantIsTheDayAndTimeTogether() {
        var confirmed: Long? = null
        val start = LocalDateTime.of(today, LocalTime.of(7, 5))
        showDialog(start.atZone(zone).toInstant().toEpochMilli(), onConfirm = { confirmed = it })

        composeRule.onNodeWithText("OK").performClick()

        composeRule.runOnIdle {
            assertEquals(start.atZone(zone).toInstant().toEpochMilli(), confirmed)
        }
    }

    private fun showDialog(
        initialMillis: Long,
        onDismiss: () -> Unit = {},
        onConfirm: (Long) -> Unit = {}
    ) {
        composeRule.setContent {
            ObaTheme {
                TripDateTimeDialog(
                    initialMillis = initialMillis,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm
                )
            }
        }
    }
}
