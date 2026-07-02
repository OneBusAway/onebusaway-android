/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com),
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
package org.onebusaway.android.report.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.appcompat.app.AppCompatActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.analytics.FirebaseAnalytics
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.region.Region
import org.onebusaway.android.report.ReportContext
import org.onebusaway.android.report.constants.ReportConstants
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.report.types.ReportAction
import org.onebusaway.android.ui.report.types.ReportTypeListRoute
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.PreferenceUtils

/**
 * Launcher facade for the report flow (former Activity). The screen is now the
 * [NavRoutes.REPORT] NavHost destination ([ReportDestination]); [start] encodes the stop/location
 * context (plus the last-known-location string) into a single [ReportContext] nav-arg on the route,
 * which the destination — and the customer-service / infrastructure-issue sub-screens it forwards to —
 * read from their own (process-death-safe) back-stack args.
 */
object ReportLauncher {

    @JvmStatic
    fun start(
        context: Context,
        focusId: String?,
        stopName: String?,
        stopCode: String?,
        lat: Double,
        lon: Double
    ) {
        context.startActivity(makeIntent(context, focusId, stopName, stopCode, lat, lon))
    }

    @JvmStatic
    fun start(context: Context, lat: Double, lon: Double) {
        context.startActivity(makeIntent(context, null, null, null, lat, lon))
    }

    @JvmStatic
    fun start(context: Context) {
        context.startActivity(makeIntent(context, null, null, null, 0.0, 0.0))
    }

    private fun makeIntent(
        context: Context,
        focusId: String?,
        stopName: String?,
        stopCode: String?,
        lat: Double,
        lon: Double
    ): Intent {
        val locationString = LocationEntryPoint.get(context).lastKnownLocation()
            ?.let { LocationUtils.printLocationDetails(it) }
        val reportContext = ReportContext(
            stopId = focusId,
            stopName = stopName,
            stopCode = stopCode,
            lat = lat,
            lon = lon,
            locationString = locationString,
        )
        return HomeActivity.navIntent(context, NavRoutes.report(reportContext.encode()))
    }
}

/**
 * The report-type chooser NavHost destination (former ReportActivity content). On first composition
 * it shows the [RegionValidateDialog] (over the host activity's `supportFragmentManager`) when the
 * region needs validation; otherwise it hosts the [ReportTypeListRoute]. A tapped type navigates
 * in-NavHost to customer service or the infrastructure-issue screen, or sends app feedback.
 */
@Composable
fun ReportDestination(navController: NavController, reportContext: ReportContext) {
    val activity = LocalContext.current.findActivity()
    val firebaseAnalytics = remember { FirebaseAnalytics.getInstance(activity) }

    // Whether the region needs validating: false → show the type list straight away. Computed once.
    val needsValidation = remember { showValidateRegionDialog(activity) }

    // The region-validate gate flips this when confirmed; destination-local since both the writer (the
    // dialog's Yes button) and reader live here. A fresh report entry starts false, so no reset is needed.
    var regionValidated by rememberSaveable { mutableStateOf(false) }
    val showTypeList = !needsValidation || regionValidated

    // "Is this your region?" gate (Tier 1: was RegionValidateDialog, a DialogFragment). Not cancelable
    // on outside touch; back leaves the report flow.
    if (needsValidation && !regionValidated) {
        val region = remember { RegionEntryPoint.get(activity).region.value }
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            properties = DialogProperties(dismissOnClickOutside = false),
            text = { Text(stringResource(R.string.region_dialog_message, region?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    region?.id?.let {
                        PreferenceUtils.saveLong(ReportConstants.PREF_VALIDATED_REGION_ID, it)
                    }
                    regionValidated = true
                }) { Text(stringResource(R.string.rt_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Open settings to pick a different region, then leave the report flow.
                    activity.startActivity(
                        HomeActivity.navIntent(activity, NavRoutes.SETTINGS)
                            .putExtra(HomeActivity.EXTRA_SHOW_CHECK_REGION_DIALOG, true)
                    )
                    navController.popBackStack()
                }) { Text(stringResource(R.string.rt_no)) }
            },
        )
    }

    if (showTypeList) {
        ReportTypeListRoute(
            viewModel = hiltViewModel(),
            onBack = { navController.popBackStack() },
            onActionSelected = { action ->
                onReportActionSelected(activity, firebaseAnalytics, navController, action, reportContext)
            }
        )
    }
}

private fun onReportActionSelected(
    activity: AppCompatActivity,
    firebaseAnalytics: FirebaseAnalytics,
    navController: NavController,
    action: ReportAction,
    reportContext: ReportContext
) {
    // Forward the same stop/location context to the sub-screens so a report started with a focused
    // stop keeps it through customer service / the infrastructure-issue form.
    val encodedContext = reportContext.encode()
    when (action) {
        ReportAction.CUSTOMER_SERVICE -> {
            navController.navigate(NavRoutes.customerService(encodedContext))
            reportEvent(
                activity, firebaseAnalytics,
                PlausibleAnalytics.REPORT_MORE_EVENT_URL, R.string.analytics_label_customer_service
            )
        }

        ReportAction.STOP_PROBLEM -> {
            navController.navigate(
                NavRoutes.infrastructureIssue(
                    activity.getString(R.string.ri_selected_service_stop), encodedContext
                )
            )
            reportEvent(
                activity, firebaseAnalytics,
                PlausibleAnalytics.REPORT_STOP_PROBLEM_EVENT_URL, R.string.analytics_label_stop_problem
            )
        }

        ReportAction.ARRIVAL_PROBLEM -> {
            navController.navigate(
                NavRoutes.infrastructureIssue(
                    activity.getString(R.string.ri_selected_service_trip), encodedContext
                )
            )
            reportEvent(
                activity, firebaseAnalytics,
                PlausibleAnalytics.REPORT_VEHICLE_PROBLEM_EVENT_URL, R.string.analytics_label_trip_problem
            )
        }

        ReportAction.APP_FEEDBACK -> sendAppFeedback(activity, firebaseAnalytics, reportContext.locationString)
    }
}

private fun sendAppFeedback(
    activity: AppCompatActivity,
    firebaseAnalytics: FirebaseAnalytics,
    locationString: String?
) {
    ExternalIntents.sendEmail(activity, activity.getString(R.string.ri_app_feedback_email), locationString)
    reportEvent(
        activity, firebaseAnalytics,
        PlausibleAnalytics.REPORT_MORE_EVENT_URL, R.string.analytics_label_app_feedback
    )
    if (locationString == null) {
        reportEvent(
            activity, firebaseAnalytics,
            PlausibleAnalytics.REPORT_VEHICLE_PROBLEM_EVENT_URL,
            R.string.analytics_label_app_feedback_without_location
        )
    }
}

private fun reportEvent(
    activity: AppCompatActivity,
    firebaseAnalytics: FirebaseAnalytics,
    eventUrl: String,
    labelRes: Int
) {
    ObaAnalytics.reportUiEvent(
        firebaseAnalytics,
        Application.get().plausibleInstance,
        eventUrl,
        activity.getString(R.string.analytics_problem),
        activity.getString(labelRes)
    )
}

/** Don't re-validate a region the user already confirmed (skipped for the single-region brand). */
private fun showValidateRegionDialog(activity: AppCompatActivity): Boolean {
    val currentRegion: Region = RegionEntryPoint.get(activity).region.value ?: return false
    val validatedRegionId = PreferenceUtils.getLong(ReportConstants.PREF_VALIDATED_REGION_ID, -1)
    val needsValidation = validatedRegionId == -1L || currentRegion.id != validatedRegionId
    if (!needsValidation) return false
    // Agency Y is locked to a single region, so there's nothing to validate.
    return !BuildConfig.FLAVOR_brand.equals(BuildFlavorUtils.AGENCYY_FLAVOR_BRAND, ignoreCase = true)
}
