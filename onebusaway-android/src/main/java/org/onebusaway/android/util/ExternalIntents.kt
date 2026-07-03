/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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

package org.onebusaway.android.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.widget.Toast

import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.analytics.FirebaseAnalytics

import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.region.Region

/**
 * Utility object containing launchers for external Intents (browser, dialer, email, payment apps).
 */
object ExternalIntents {

    fun goToUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.browser_error), Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun goToPhoneDialer(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.setData(Uri.parse(url))
        context.startActivity(intent)
    }

    /**
     * Opens email apps based on the given email address
     * @param email address
     * @param location string that shows the current location
     */
    fun sendEmail(context: Context, email: String, location: String?) {
        sendEmail(context, email, location, null, false)
    }

    /**
     * Opens email apps based on the given email address
     * @param email address
     * @param location string that shows the current location
     * @param tripPlanUrl trip planning URL that failed, if this is a trip problem error report, or null if it's not
     */
    fun sendEmail(
        context: Context, email: String, location: String?,
        tripPlanUrl: String?, tripPlanFail: Boolean
    ) {
        val obaRegionName = RegionUtils.getObaRegionName()
        val autoRegion = PreferenceUtils
            .getBoolean(context.getString(R.string.preference_key_auto_select_region), true)
        val regionSelectionMethod: String
        if (autoRegion) {
            regionSelectionMethod = context.getString(R.string.region_selected_auto)
        } else {
            regionSelectionMethod = context.getString(R.string.region_selected_manually)
        }

        sendEmail(
            context, email, location, obaRegionName, regionSelectionMethod,
            tripPlanUrl, tripPlanFail
        )
    }

    /**
     * Opens email apps based on the given email address
     * @param email address
     * @param location string that shows the current location
     * @param regionName name of the current api region
     * @param regionSelectionMethod string that shows if the current api region selected manually or
     *                              automatically
     * @param tripPlanUrl trip planning URL that failed, if this is a trip problem error report, or null if it's not
     */
    private fun sendEmail(
        context: Context, email: String, location: String?, regionName: String?,
        regionSelectionMethod: String?, tripPlanUrl: String?, tripPlanFail: Boolean
    ) {
        val pm = context.packageManager
        val appInfoOba: PackageInfo
        val appInfoGps: PackageInfo
        var obaVersion: String? = ""
        var googlePlayServicesAppVersion: String? = ""
        try {
            appInfoOba = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            obaVersion = appInfoOba.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            // Leave version as empty string
        }
        try {
            appInfoGps = pm.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0)
            googlePlayServicesAppVersion = appInfoGps.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            // Leave version as empty string
        }
        val body: String
        if (location != null) {
            // Have location
            if (tripPlanUrl == null) {
                // No trip plan
                body = context.getString(
                    R.string.bug_report_body,
                    obaVersion,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                    googlePlayServicesAppVersion,
                    GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE,
                    regionName,
                    regionSelectionMethod,
                    location
                )
            } else {
                // Trip plan
                if (tripPlanFail) {
                    body = context.getString(
                        R.string.bug_report_body_trip_plan_fail,
                        obaVersion,
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT,
                        googlePlayServicesAppVersion,
                        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE,
                        regionName,
                        regionSelectionMethod,
                        location,
                        tripPlanUrl
                    )
                } else {
                    body = context.getString(
                        R.string.bug_report_body_trip_plan,
                        obaVersion,
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT,
                        googlePlayServicesAppVersion,
                        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE,
                        regionName,
                        regionSelectionMethod,
                        location,
                        tripPlanUrl
                    )
                }
            }
        } else {
            // No location
            if (tripPlanUrl == null) {
                // No trip plan
                body = context.getString(
                    R.string.bug_report_body_without_location,
                    obaVersion,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                )
            } else {
                // Trip plan
                if (tripPlanFail) {
                    body = context.getString(
                        R.string.bug_report_body_trip_plan_without_location_fail,
                        obaVersion,
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT,
                        tripPlanUrl
                    )
                } else {
                    body = context.getString(
                        R.string.bug_report_body_trip_plan_without_location,
                        obaVersion,
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT,
                        tripPlanUrl
                    )
                }
            }
        }

        val send = Intent(Intent.ACTION_SEND)
        send.putExtra(
            Intent.EXTRA_EMAIL,
            arrayOf(email)
        )
        // Show trip planner subject line if we have a trip planning URL
        val appName = context.getString(R.string.app_name)
        val subject: String
        if (tripPlanUrl == null) {
            if (tripPlanFail) {
                subject = context.getString(R.string.bug_report_subject_trip_plan, appName)
            } else {
                subject = context.getString(R.string.bug_report_subject, appName)
            }
        } else {
            if (tripPlanFail) {
                subject = context.getString(R.string.bug_report_subject_trip_plan_fail, appName)
            } else {
                subject = context.getString(R.string.bug_report_subject_trip_plan, appName)
            }
        }
        send.putExtra(Intent.EXTRA_SUBJECT, subject)
        send.putExtra(Intent.EXTRA_TEXT, body)
        send.setType("message/rfc822")
        try {
            context.startActivity(Intent.createChooser(send, subject))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.bug_report_error, Toast.LENGTH_LONG)
                .show()
        }
    }

    /**
     * Begins the fare-payment flow for the currently selected region. If the region has a fare
     * payment-app warning the user hasn't opted out of, returns that region so the caller can show
     * the warning dialog and then call [startPaymentIntent]; otherwise launches the payment
     * intent directly (installed app, else the Google Play listing) and returns null. Returns null
     * when there is no current region (e.g. a custom API URL is set).
     * @param activity activity to launch the fare payment app or Google Play store from
     * @return the region whose payment warning must be shown first, or null if already handled
     */
    fun payFareOrWarningRegion(activity: Activity): Region? {
        val region = RegionEntryPoint.get(activity).currentRegion()
        if (region == null) {
            // If a custom API URL is set (i.e., no region), then no op
            return null
        }

        val hasWarning = !TextUtils.isEmpty(region.paymentWarningTitle)
                || !TextUtils.isEmpty(region.paymentWarningBody)
        if (hasWarning && !PreferenceUtils.getBoolean(
                activity.getString(R.string.preference_key_never_show_payment_warning_dialog), false
            )
        ) {
            // Caller shows the warning dialog, then calls startPaymentIntent on confirm.
            return region
        }
        // No warning (or opted out) - start the Intent directly.
        startPaymentIntent(activity, region)
        return null
    }

    /**
     * Launches the payment app for the provided region if it's already installed, and if not
     * directs the user to the listing in Google Play where it can be downloaded
     * @param activity Activity to use to launch the Intent
     * @param region region to launch a payment Intent for
     */
    fun startPaymentIntent(activity: Activity, region: Region) {
        val manager = activity.packageManager
        val paymentAndroidAppId = region.paymentAndroidAppId.orEmpty()
        var intent = manager.getLaunchIntentForPackage(paymentAndroidAppId)
        if (intent != null) {
            // Launch installed app
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            activity.startActivity(intent)
            ObaAnalytics.reportUiEvent(
                FirebaseAnalytics.getInstance(activity),
                Application.get().plausibleInstance,
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                Application.get().getString(R.string.analytics_label_button_fare_payment),
                Application.get().getString(R.string.analytics_label_open_app)
            )
        } else {
            // Go to Play Store listing to download app
            intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(Application.get().getString(R.string.google_play_listing_prefix, paymentAndroidAppId)))
            activity.startActivity(intent)
            ObaAnalytics.reportUiEvent(
                FirebaseAnalytics.getInstance(activity),
                Application.get().plausibleInstance,
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                Application.get().getString(R.string.analytics_label_button_fare_payment),
                Application.get().getString(R.string.analytics_label_download_app)
            )
        }
    }

    /**
     * Launches the HOPR bikeshare app for Tampa if the app is installed, otherwise directs the user
     * to the Google Play store listing to download it.
     *
     * @param context context to launch the fare payment app or Google Play store from
     */
    fun launchTampaHoprApp(context: Context) {
        val manager = context.packageManager
        var intent = manager.getLaunchIntentForPackage(context.getString(R.string.hopr_android_app_id))
        if (intent != null) {
            // Launch installed app
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            context.startActivity(intent)
            ObaAnalytics.reportUiEvent(
                FirebaseAnalytics.getInstance(context),
                Application.get().plausibleInstance,
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                Application.get().getString(R.string.analytics_label_button_bike_share),
                Application.get().getString(R.string.analytics_label_open_app)
            )
        } else {
            // Go to Play Store listing to download app
            intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(Application.get().getString(R.string.google_play_listing_prefix, context.getString(R.string.hopr_android_app_id))))
            context.startActivity(intent)
            ObaAnalytics.reportUiEvent(
                FirebaseAnalytics.getInstance(context),
                Application.get().plausibleInstance,
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                Application.get().getString(R.string.analytics_label_button_bike_share),
                Application.get().getString(R.string.analytics_label_download_app)
            )
        }
    }
}
