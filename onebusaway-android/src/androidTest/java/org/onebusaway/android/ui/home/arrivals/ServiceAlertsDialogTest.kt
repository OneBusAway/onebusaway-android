/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.ui.home.arrivals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.arrivals.AlertItem
import org.onebusaway.android.ui.arrivals.AlertSeverity
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.StopHeader
import org.onebusaway.android.time.ServerTime

class ServiceAlertsDialogTest {

    // See EtaStripJustifyTest for why the v1 (Unconfined) rule is used here (issue #1792).
    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryOpensTheSelectedAlert() {
        var selected: String? = null
        val alert = AlertItem("content", "situation", setOf("situation"), "Route detour", AlertSeverity.WARNING)
        val content = ArrivalsUiState.Content(
            header = StopHeader("stop", "Main St", "N", false),
            arrivals = emptyList(),
            routeGroups = emptyList(),
            minutesAfter = 60,
            windowEnd = ServerTime(0),
            isStale = false,
            alerts = listOf(alert),
        )
        composeRule.setContent {
            ServiceAlertsDialog(
                content = content,
                onShowAlert = { selected = it },
                onHideAlert = {},
                onShowHiddenAlerts = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithTag("focus_alert_panel").assertIsDisplayed()
        composeRule.onNodeWithText("Route detour").performClick()
        composeRule.runOnIdle { assertEquals("situation", selected) }
    }
}
