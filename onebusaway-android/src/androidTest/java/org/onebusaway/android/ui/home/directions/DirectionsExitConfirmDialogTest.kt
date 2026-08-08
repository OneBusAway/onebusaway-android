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

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * On-device checks on the leave-directions confirmation (#2140). Its whole job is to make discarding a
 * planned trip a deliberate act, so what matters is that the two answers stay wired to opposite
 * outcomes — a swap here would silently restore the accidental erasure the dialog exists to prevent,
 * while still looking correct.
 */
class DirectionsExitConfirmDialogTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val discardLabel = context.getString(R.string.directions_exit_confirm_discard)
    private val pinLabel = context.getString(R.string.directions_exit_confirm_pin)
    private val cancelLabel = context.getString(R.string.cancel)

    @Test
    fun itNamesTheTripAndOnlyDiscardConfirmsTheExit() {
        var confirmed = 0
        var dismissed = 0
        renderDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ })

        composeRule.onNodeWithText(context.getString(R.string.directions_exit_confirm_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.directions_exit_confirm_message))
            .assertIsDisplayed()

        composeRule.onNodeWithText(cancelLabel).performClick()
        assertEquals(0, confirmed)
        assertEquals(1, dismissed)

        composeRule.onNodeWithText(discardLabel).performClick()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun pinAndLeavePinsTheTripAndStillLeaves() {
        // Both halves matter: a "Pin & leave" that pinned without leaving would strand the rider on the
        // trip they asked to leave, and one that left without pinning is the erasure it exists to avoid.
        var pinned = 0
        var confirmed = 0
        var dismissed = 0
        renderDialog(
            onPinAndLeave = { pinned++ },
            onConfirm = { confirmed++ },
            onDismiss = { dismissed++ }
        )

        composeRule.onNodeWithText(pinLabel).performClick()

        assertEquals(1, pinned)
        assertEquals(0, dismissed)
    }

    @Test
    fun discardAndCancelDoNotPin() {
        var pinned = 0
        renderDialog(onPinAndLeave = { pinned++ })

        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.onNodeWithText(discardLabel).performClick()

        assertEquals(0, pinned)
    }

    @Test
    fun aTripWithNoRequestBehindItOffersNoPin() {
        // A plan restored from a trip-update notification carries no request, so there is no trip to
        // park; the button is absent rather than present-and-inert.
        renderDialog(onPinAndLeave = null)

        composeRule.onAllNodesWithText(pinLabel).assertCountEquals(0)
        composeRule.onNodeWithText(discardLabel).assertIsDisplayed()
    }

    private fun renderDialog(
        onPinAndLeave: (() -> Unit)? = {},
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeRule.setContent {
            ObaTheme {
                DirectionsExitConfirmDialog(
                    onPinAndLeave = onPinAndLeave,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss
                )
            }
        }
    }
}
