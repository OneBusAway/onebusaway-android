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
import androidx.core.net.toUri
import com.google.android.gms.common.GoogleApiAvailability
import org.onebusaway.android.R
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.region.Region

/**
 * Utility object containing launchers for external Intents (browser, dialer, email, payment apps).
 */
object ExternalIntents {

    fun goToUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.browser_error), Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * The URI used to enumerate the device's browsers.
     *
     * It carries a **host**, deliberately. A scheme-only `https:` URI is *opaque* — `getHost()` is null —
     * and `IntentFilter.AuthorityEntry.match` fails any filter that declares a host, wildcard included.
     * So a browser registering `<data android:scheme="https" android:host="*"/>` rather than a bare
     * scheme would be missed entirely, and on a device where that was the only browser [openInBrowser]
     * would find nothing to hand the link to.
     *
     * `example.com` is the IANA-reserved example domain: nothing is ever fetched (this URI only ever
     * reaches `PackageManager`), no real site can claim it, and — the property that matters — it is not
     * one of `ExternalDeepLinks.WEB_HOSTS`, so this app's own web-link filter cannot match it. That is
     * what keeps [openInBrowser] from resolving back to us and looping.
     */
    private val BROWSER_PROBE_URI = "https://example.com".toUri()

    /**
     * Opens [uri] in a browser, explicitly excluding this app, and reports whether it could.
     *
     * A plain `ACTION_VIEW` (as [goToUrl] fires) is wrong for a URL this app was *launched* for: it
     * would match our own web-link intent-filter again and bounce straight back. So the target browser
     * is resolved up front and set as the intent's package. Used to hand back an App Link the manifest
     * filter claims but the parser can't route — see `ExternalDeepLinks.isUnhandledWebLink`.
     *
     * Browsers are enumerated rather than assumed so the user's default is honoured; when the device has
     * browsers but no default among them, the chooser is restricted to those explicit intents (a plain
     * `createChooser` over the URL would list this app again).
     */
    fun openInBrowser(context: Context, uri: Uri): Boolean {
        val manager = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, BROWSER_PROBE_URI)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val browsers = manager.browserPackages(probe)
            .filterNot { it == context.packageName }
            .distinct()
        if (browsers.isEmpty()) return false

        val open = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val preferred = manager.defaultBrowserPackage(probe)?.takeIf { it in browsers }
        val launch = if (preferred != null) {
            Intent(open).setPackage(preferred)
        } else {
            val explicit = browsers.map { Intent(open).setPackage(it) }
            Intent.createChooser(explicit.first(), null).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, explicit.drop(1).toTypedArray())
            }
        }
        return try {
            context.startActivity(launch)
            true
        } catch (e: ActivityNotFoundException) {
            // Nothing to log or surface: the browser was resolved a moment ago, so this is the narrow
            // race where it was uninstalled/disabled in between. Reporting false is the whole contract —
            // the caller falls back to opening the app normally.
            false
        }
    }

    /** Package names of every activity that handles [probe] (see [BROWSER_PROBE_URI]). */
    private fun PackageManager.browserPackages(probe: Intent): List<String> {
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            // The ResolveInfoFlags overload is API 33+ and minSdk is 23, so the pre-33 path has to use
            // the int overload that API 33 deprecated.
            @Suppress("DEPRECATION")
            queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return matches.map { it.activityInfo.packageName }
    }

    /** The user's default browser, or null if the device has none set (the chooser would show). */
    private fun PackageManager.defaultBrowserPackage(probe: Intent): String? {
        val match = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveActivity(probe, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            // See browserPackages: the typed-flags overload needs API 33.
            @Suppress("DEPRECATION")
            resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return match?.activityInfo?.packageName
    }

    /**
     * Hands the rider to a rental operator's app (#2150): its launcher activity when it's installed,
     * else its Play Store page so they can get it.
     *
     * The package has to be declared in the manifest's `<queries>` block for the installed case to be
     * reachable at all — without it, Android 11+ package visibility makes `getLaunchIntentForPackage`
     * return null for an app that is right there. The store fallback keeps that a degradation rather
     * than a dead end, but a catalog entry whose package isn't declared silently never opens the app;
     * see `RentalOperators.KnownOperator.appPackage`.
     */
    fun openRentalApp(context: Context, packageName: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            try {
                context.startActivity(launch)
                return
            } catch (e: ActivityNotFoundException) {
                // Uninstalled/disabled between the resolve and the launch — fall through to the store.
            }
        }
        goToUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
    }

    /**
     * Opens an operator-supplied rental deep link, reporting whether anything handled it.
     *
     * Unlike [goToUrl] this doesn't toast on failure: the URI comes from a transit feed rather than
     * from the rider, and "no app for `lime://…`" is the app's problem to fall back from, not an error
     * to put in front of them.
     */
    fun openRentalDeepLink(context: Context, uri: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun goToPhoneDialer(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.setData(url.toUri())
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
        context: Context,
        email: String,
        location: String?,
        tripPlanUrl: String?,
        tripPlanFail: Boolean
    ) {
        val obaRegionName = RegionUtils.getObaRegionName(context)
        val autoRegion = PreferenceUtils
            .getBoolean(context.getString(R.string.preference_key_auto_select_region), true)
        val regionSelectionMethod: String
        if (autoRegion) {
            regionSelectionMethod = context.getString(R.string.region_selected_auto)
        } else {
            regionSelectionMethod = context.getString(R.string.region_selected_manually)
        }

        sendEmail(
            context,
            email,
            location,
            obaRegionName,
            regionSelectionMethod,
            tripPlanUrl,
            tripPlanFail
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
        context: Context,
        email: String,
        location: String?,
        regionName: String?,
        regionSelectionMethod: String?,
        tripPlanUrl: String?,
        tripPlanFail: Boolean
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

        val hasWarning = !TextUtils.isEmpty(region.paymentWarningTitle) ||
            !TextUtils.isEmpty(region.paymentWarningBody)
        if (hasWarning &&
            !PreferenceUtils.getBoolean(
                activity.getString(R.string.preference_key_never_show_payment_warning_dialog),
                false
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
            AnalyticsEntryPoint.get(activity).reportUiEvent(
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                activity.getString(R.string.analytics_label_button_fare_payment),
                activity.getString(R.string.analytics_label_open_app)
            )
        } else {
            // Go to Play Store listing to download app
            intent = Intent(Intent.ACTION_VIEW)
            intent.setData(activity.getString(R.string.google_play_listing_prefix, paymentAndroidAppId).toUri())
            activity.startActivity(intent)
            AnalyticsEntryPoint.get(activity).reportUiEvent(
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                activity.getString(R.string.analytics_label_button_fare_payment),
                activity.getString(R.string.analytics_label_download_app)
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
            AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                context.getString(R.string.analytics_label_button_bike_share),
                context.getString(R.string.analytics_label_open_app)
            )
        } else {
            // Go to Play Store listing to download app
            intent = Intent(Intent.ACTION_VIEW)
            intent.setData(context.getString(R.string.google_play_listing_prefix, context.getString(R.string.hopr_android_app_id)).toUri())
            context.startActivity(intent)
            AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_FARE_PAYMENT_EVENT_URL,
                context.getString(R.string.analytics_label_button_bike_share),
                context.getString(R.string.analytics_label_download_app)
            )
        }
    }
}
