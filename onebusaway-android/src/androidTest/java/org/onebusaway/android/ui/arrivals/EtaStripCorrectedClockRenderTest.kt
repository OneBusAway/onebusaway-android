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

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.DisplayFormat

/**
 * The ETA pill's struck-through timetable time (#2167), end to end: a real [EtaStrip] over real
 * [org.onebusaway.android.ui.arrivals.ArrivalInfo]s, so the whole path — the model's `scheduledTime`,
 * the formatted-string rule, the pill's two clock lines — is exercised rather than the composable in
 * isolation.
 *
 * The two things worth pinning on a device: a corrected pill really draws *both* times (its extra line
 * has to fit the height the strip measured for it, or it would be clipped away), and the correction is
 * spoken, since a strikethrough says nothing to a screen reader.
 */
class EtaStripCorrectedClockRenderTest {

    // Unconfined composition — see createUnconfinedComposeRule (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** previewArrival anchors serverNow at 0, so a pill's clock time is just its ETA in minutes past
     *  the epoch — formatted through the same helper the pill uses, so this matches whatever locale and
     *  12h/24h setting the test device is in. */
    private fun clockAt(minutes: Long) = DisplayFormat.formatTime(context, minutes * 60_000L)

    /** A strip holding one trip: [etaMinutes] out, running [deviationMinutes] behind its timetable. */
    private fun setContent(etaMinutes: Long, deviationMinutes: Long) = composeRule.setContent {
        Box(Modifier.width(320.dp)) {
            EtaStrip(
                trips = listOf(
                    previewArrival(
                        "8",
                        "Rainier Beach",
                        etaMinutes = etaMinutes,
                        scheduleDeviationMinutes = deviationMinutes
                    )
                ),
                actionsFor = { null },
                callbacks = previewRowCallbacks()
            )
        }
    }

    @Test
    fun latePillDrawsItsTimetableTimeAboveTheTimeNowExpected() {
        setContent(etaMinutes = 6, deviationMinutes = 4)

        composeRule.onNodeWithText(clockAt(2)).assertIsDisplayed()
        composeRule.onNodeWithText(clockAt(6)).assertIsDisplayed()
    }

    @Test
    fun theCorrectionIsSpokenSinceItsStrikethroughIsNot() {
        setContent(etaMinutes = 6, deviationMinutes = 4)

        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.stop_info_clock_corrected, clockAt(2), clockAt(6))
            )
            .assertExists()
    }

    @Test
    fun aPillOnItsTimetableTimeDrawsOneClockLine() {
        setContent(etaMinutes = 6, deviationMinutes = 0)

        // Exactly one line carrying that time, not two identical ones — the failure mode of striking a
        // time through and replacing it with itself, which is why the rule compares formatted strings.
        // The unmerged tree, since the clickable pill merges its children's text into one node.
        composeRule.onAllNodesWithText(clockAt(6), useUnmergedTree = true).assertCountEquals(1)
    }
}
