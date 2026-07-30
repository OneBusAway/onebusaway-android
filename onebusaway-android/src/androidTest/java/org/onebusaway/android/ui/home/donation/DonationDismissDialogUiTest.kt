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
package org.onebusaway.android.ui.home.donation

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The dismiss dialog's outcome mapping: "maybe later" reports [DonationDismissChoice.REMIND_LATER]
 * while the "remind me" box stays checked, and [DonationDismissChoice.STOP_ASKING] once it's cleared
 * — the permanent opt-out that replaced the old "I don't want to help" button.
 *
 * [DonationOverlay] takes plain booleans and a [DonationCallbacks] holder, so the dialog composes
 * standalone: no activity, no `DonationsManager` gating, no prefs staging.
 */
class DonationDismissDialogUiTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val remindMe = context.getString(R.string.donation_dismiss_dialog_remind_me_checkbox)
    private val maybeLater = context.getString(R.string.donation_dismiss_dialog_maybe_later_button)
    private val back = context.getString(R.string.donation_dismiss_dialog_back_button)

    private fun showDialog(onMaybeLater: (DonationDismissChoice) -> Unit = {}, onCancel: () -> Unit = {}) {
        composeRule.setContent {
            DonationOverlay(
                cardVisible = false,
                dismissDialogVisible = true,
                callbacks = DonationCallbacks(
                    onClose = {},
                    onLearnMore = {},
                    onDonate = {},
                    onMaybeLater = onMaybeLater,
                    onCancelDismiss = onCancel
                )
            )
        }
    }

    @Test
    fun remindMeIsCheckedByDefaultAndMaybeLaterSnoozes() {
        var choice: DonationDismissChoice? = null
        showDialog(onMaybeLater = { choice = it })

        composeRule.onNodeWithText(remindMe).assertIsOn()
        composeRule.onNodeWithText(maybeLater).performClick()

        composeRule.runOnIdle { assertEquals(DonationDismissChoice.REMIND_LATER, choice) }
    }

    @Test
    fun clearingRemindMeMakesMaybeLaterStopAsking() {
        var choice: DonationDismissChoice? = null
        showDialog(onMaybeLater = { choice = it })

        composeRule.onNodeWithText(remindMe).performClick()
        composeRule.onNodeWithText(remindMe).assertIsOff()
        composeRule.onNodeWithText(maybeLater).performClick()

        composeRule.runOnIdle { assertEquals(DonationDismissChoice.STOP_ASKING, choice) }
    }

    @Test
    fun backCancelsWithoutChoosing() {
        var choice: DonationDismissChoice? = null
        var cancelled = false
        showDialog(onMaybeLater = { choice = it }, onCancel = { cancelled = true })

        composeRule.onNodeWithText(back).performClick()

        composeRule.runOnIdle {
            assertEquals(true, cancelled)
            assertEquals(null, choice)
        }
    }
}
