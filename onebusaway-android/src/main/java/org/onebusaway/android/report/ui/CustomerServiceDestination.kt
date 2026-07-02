/*
 * Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com),
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.analytics.FirebaseAnalytics
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.report.ReportContext
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.report.customerservice.CustomerServiceRoute
import org.onebusaway.android.util.ExternalIntents

/**
 * The customer-service NavHost destination (former CustomerServiceActivity content). Hosts the
 * Hilt-scoped [CustomerServiceRoute]; the email/web/phone handlers issue the platform contact intents
 * and analytics here (reading the optional location string off the forwarded [reportContext] nav-arg).
 */
@Composable
fun CustomerServiceDestination(navController: NavController, reportContext: ReportContext) {
    val activity = LocalContext.current.findActivity()
    val firebaseAnalytics = remember { FirebaseAnalytics.getInstance(activity) }

    fun reportContactEvent(agencyName: String, labelRes: Int) {
        ObaAnalytics.reportUiEvent(
            firebaseAnalytics,
            Application.get().plausibleInstance,
            PlausibleAnalytics.REPORT_MORE_EVENT_URL,
            agencyName + "_" + activity.getString(R.string.analytics_customer_service),
            activity.getString(labelRes)
        )
    }

    CustomerServiceRoute(
        viewModel = hiltViewModel(),
        onBack = { navController.popBackStack() },
        onEmail = { agency ->
            val email = agency.email ?: return@CustomerServiceRoute
            val locationString = reportContext.locationString
            ExternalIntents.sendEmail(activity, email, locationString)
            reportContactEvent(agency.name, R.string.analytics_label_customer_service_email)
            if (locationString == null) {
                reportContactEvent(
                    agency.name, R.string.analytics_label_customer_service_email_without_location
                )
            }
        },
        onWeb = { agency ->
            val url = agency.url ?: return@CustomerServiceRoute
            ExternalIntents.goToUrl(activity, url)
            reportContactEvent(agency.name, R.string.analytics_label_customer_service_web)
        },
        onPhone = { agency ->
            val phone = agency.phone ?: return@CustomerServiceRoute
            ExternalIntents.goToPhoneDialer(activity, "tel:$phone")
            reportContactEvent(agency.name, R.string.analytics_label_customer_service_phone)
        }
    )
}
