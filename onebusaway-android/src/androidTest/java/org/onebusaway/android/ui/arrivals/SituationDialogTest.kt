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

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.arrivals.dialogs.showSituationDialog

/**
 * The service alert dialog shows a long headline in full, and lets the rider select its text
 * (#2448). The headline used to be the dialog's title, which stops at two lines and ellipsizes.
 */
@RunWith(AndroidJUnit4::class)
class SituationDialogTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(HomeActivity::class.java)

    private val longHeadline =
        "Route 40 is rerouted between Fremont and Ballard through Sunday night because of road " +
            "work on Leary Way NW. Use the temporary stops on NW 46th St and Shilshole Ave NW, " +
            "and allow extra travel time, since buses are delayed up to 15 minutes in both directions"

    @Test
    fun longHeadlineIsShownWholeAndSelectable() {
        val message = showAlert(AlertDetails("1_alert", longHeadline, "Stops at 3rd Ave NW are closed.", null))

        assertTrue(message.text.startsWith(longHeadline))
        assertTrue(message.text.contains("Stops at 3rd Ave NW are closed."))
        val layout = checkNotNull(message.layout) { "message not laid out" }
        for (line in 0 until layout.lineCount) {
            assertEquals("line $line is ellipsized", 0, layout.getEllipsisCount(line))
        }
        assertTrue("the alert text should be selectable", message.isTextSelectable)
    }

    @Test
    fun headlineWithoutDescriptionIsShownOnce() {
        val message = showAlert(AlertDetails("1_alert", longHeadline, longHeadline, null))

        assertEquals(longHeadline, message.text.toString())
    }

    /** Shows the dialog over the home screen and returns its laid-out message view. */
    private fun showAlert(alert: AlertDetails): TextView {
        lateinit var dialog: AlertDialog
        scenarioRule.scenario.onActivity { activity ->
            dialog = showSituationDialog(
                activity = activity,
                alert = alert,
                onMarkRead = {},
                onHide = {},
                onUnhide = {},
                onHideAll = {},
                onDismiss = {},
                showUndoSnackbar = { _, _ -> }
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        lateinit var message: TextView
        scenarioRule.scenario.onActivity {
            val title = dialog.findViewById<View>(androidx.appcompat.R.id.alertTitle)
            assertFalse("the headline should not be in the dialog title", title?.isShown == true)
            message = checkNotNull(dialog.findViewById(android.R.id.message))
        }
        return message
    }
}
