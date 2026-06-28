/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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
package org.onebusaway.android.report.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.report.ReportContext
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.feedback.FeedbackLauncher
import org.onebusaway.android.ui.feedback.FeedbackScreen
import org.onebusaway.android.ui.feedback.FeedbackSubmitter
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * The report / feedback navigation cluster: the report chooser ([NavRoutes.REPORT]) and its customer
 * service + infrastructure-issue sub-screens (all already self-wiring via
 * [org.onebusaway.android.ui.compose.findActivity]), plus the
 * post-trip destination-reminder feedback screen ([NavRoutes.FEEDBACK]).
 */
fun NavGraphBuilder.reportGraph(navController: NavHostController) {
    // Report flow (former ReportActivity / CustomerServiceActivity /
    // InfrastructureIssueActivity). The chooser ([REPORT]) shows the region-validate dialog
    // (if needed) then the type list; a tapped type navigates in-NavHost to customer service
    // or the infrastructure-issue screen, so back returns to the chooser (today's behavior).
    // The stop/location/trip context rides one nav-arg (an encoded [ReportContext]) the chooser
    // forwards to its sub-screens, so each destination reads its own (process-death-safe)
    // back-stack args. Non-exported; no aliases.
    composable(
        NavRoutes.REPORT,
        arguments = listOf(reportContextArg()),
    ) { backStackEntry ->
        ObaTheme {
            ReportDestination(
                navController = navController,
                reportContext = backStackEntry.decodeReportContext(),
            )
        }
    }
    composable(
        NavRoutes.CUSTOMER_SERVICE,
        arguments = listOf(reportContextArg()),
    ) { backStackEntry ->
        ObaTheme {
            CustomerServiceDestination(
                navController = navController,
                reportContext = backStackEntry.decodeReportContext(),
            )
        }
    }
    composable(
        NavRoutes.INFRASTRUCTURE_ISSUE,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SELECTED_SERVICE) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            reportContextArg(),
        ),
    ) { backStackEntry ->
        val selectedService =
            backStackEntry.arguments?.getString(NavRoutes.ARG_SELECTED_SERVICE)
        ObaTheme {
            InfrastructureIssueDestination(
                navController = navController,
                selectedService = selectedService,
                reportContext = backStackEntry.decodeReportContext(),
            )
        }
    }
    // Feedback destination: the post-trip destination-reminder feedback screen.
    // Reached only from the post-trip notification's Yes/No actions (NavigationService →
    // FeedbackActivity facade → HomeActivity → translator). On send it runs the submit/log
    // glue (FeedbackSubmitter) then pops back. Non-exported; no alias.
    composable(
        NavRoutes.FEEDBACK,
        arguments = listOf(
            navArgument(NavRoutes.ARG_FEEDBACK_RESPONSE) {
                type = NavType.IntType; defaultValue = 0
            },
            navArgument(NavRoutes.ARG_LOG_FILE) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_TRIP_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_NOTIFICATION_ID) {
                type = NavType.IntType; defaultValue = 0
            },
        ),
    ) { backStackEntry ->
        val context = LocalContext.current
        val response =
            backStackEntry.arguments?.getInt(NavRoutes.ARG_FEEDBACK_RESPONSE) ?: 0
        val logFile = backStackEntry.arguments?.getString(NavRoutes.ARG_LOG_FILE)
        val submitter = remember(logFile) {
            FeedbackSubmitter(context.applicationContext, PreferencesEntryPoint.get(context), logFile)
        }
        ObaTheme {
            FeedbackScreen(
                initialLiked = response == FeedbackLauncher.FEEDBACK_YES,
                initialSendLogs = submitter.shareLogsPref(),
                onBack = { navController.popBackStack() },
                onSendLogsChanged = submitter::setShareLogs,
                onSend = { liked, text ->
                    submitter.submit(liked, text)
                    navController.popBackStack()
                },
            )
        }
    }
}

/** The optional, nullable encoded-[ReportContext] nav-arg shared by every report destination. */
private fun reportContextArg() = navArgument(NavRoutes.ARG_REPORT_CONTEXT) {
    type = NavType.StringType; nullable = true; defaultValue = null
}

/** Decodes this entry's [NavRoutes.ARG_REPORT_CONTEXT] arg into a [ReportContext] (empty if absent). */
private fun NavBackStackEntry.decodeReportContext(): ReportContext =
    ReportContext.decode(arguments?.getString(NavRoutes.ARG_REPORT_CONTEXT))
