/*
 * Copyright (C) 2014-2019 University of South Florida
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
package org.onebusaway.android.analytics

import android.location.Location
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.onebusaway.plausible.android.Plausible
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.util.PreferenceUtils

/**
 * Analytics orchestrator for the app: fans each tracked event out to Firebase, Plausible
 * ([PlausibleAnalytics]) and Umami ([UmamiAnalyticsReporter]). Exposed as `@JvmStatic` methods so the
 * many Java + Kotlin call sites keep calling `ObaAnalytics.xxx(...)` unchanged.
 *
 * @author Cagri Cetin, Sean Barbeau
 */
object ObaAnalytics {

    /** A device fix is only accurate enough to bucket stop distance when below this (meters). */
    private const val LOCATION_ACCURACY_THRESHOLD = 50f

    /** Distance buckets reported when a stop is tapped. */
    enum class ObaStopDistance(private val label: String, val distanceInMeters: Int) {
        DISTANCE_1("User Distance: 00000-00050m", 50),
        DISTANCE_2("User Distance: 00050-00100m", 100),
        DISTANCE_3("User Distance: 00100-00200m", 200),
        DISTANCE_4("User Distance: 00200-00400m", 400),
        DISTANCE_5("User Distance: 00400-00800m", 800),
        DISTANCE_6("User Distance: 00800-01600m", 1600),
        DISTANCE_7("User Distance: 01600-03200m", 3200),
        DISTANCE_8("User Distance: 03200-INFINITY", 0);

        override fun toString(): String = label

        companion object {
            /** The first bucket whose ceiling exceeds [meters], or [DISTANCE_8] (infinity) past 3200m. */
            fun forDistance(meters: Float): ObaStopDistance =
                entries.firstOrNull { meters < it.distanceInMeters } ?: DISTANCE_8
        }
    }

    /**
     * Reports a UI selection event to Firebase + Plausible + Umami.
     *
     * @param pageUrl URL of the page where the UI element lives (for Plausible/Umami)
     * @param id ID of the UI element
     * @param state the state/variant of the UI item, or null if it has none
     */
    @JvmStatic
    fun reportUiEvent(
        analytics: FirebaseAnalytics,
        plausible: Plausible?,
        pageUrl: String,
        id: String,
        state: String?,
    ) {
        if (!isAnalyticsActive()) return
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, id)
            if (!state.isNullOrEmpty()) putString(FirebaseAnalytics.Param.ITEM_VARIANT, state)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
        PlausibleAnalytics.reportUiEvent(plausible, pageUrl, id, state)
        UmamiAnalyticsReporter.reportUiEvent(umami(), pageUrl, id, state)
    }

    /**
     * Reports a login event to Firebase.
     *
     * @param signUpMethod sign-up method of the login, or null if unknown
     */
    @JvmStatic
    fun reportLoginEvent(analytics: FirebaseAnalytics, signUpMethod: String?) {
        if (!isAnalyticsActive()) return
        val bundle = signUpMethod?.takeIf { it.isNotEmpty() }?.let {
            Bundle().apply { putString(FirebaseAnalytics.Param.METHOD, it) }
        }
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
    }

    /**
     * Reports a search event to Firebase + Plausible + Umami.
     *
     * @param searchTerm search term used, or null if unknown
     */
    @JvmStatic
    fun reportSearchEvent(plausible: Plausible?, analytics: FirebaseAnalytics, searchTerm: String?) {
        if (!isAnalyticsActive()) return
        val bundle = searchTerm?.takeIf { it.isNotEmpty() }?.let {
            Bundle().apply { putString(FirebaseAnalytics.Param.SEARCH_TERM, it) }
        }
        analytics.logEvent(FirebaseAnalytics.Event.SEARCH, bundle)
        searchTerm?.let {
            PlausibleAnalytics.reportSearchEvent(plausible, it)
            UmamiAnalyticsReporter.reportSearchEvent(umami(), it)
        }
    }

    /**
     * Reports the user viewing a stop, bucketing the distance between the device and the stop (only
     * when the device fix is accurate enough — under [LOCATION_ACCURACY_THRESHOLD]).
     */
    @JvmStatic
    fun reportViewStopEvent(
        plausible: Plausible?,
        analytics: FirebaseAnalytics,
        stopId: String,
        stopName: String?,
        myLocation: Location?,
        stopLocation: Location,
    ) {
        if (!isAnalyticsActive() || myLocation == null) return
        if (myLocation.accuracy < LOCATION_ACCURACY_THRESHOLD) {
            val stopDistance = ObaStopDistance.forDistance(myLocation.distanceTo(stopLocation))
            reportViewStopEvent(plausible, analytics, stopId, stopName, stopDistance.toString())
        }
    }

    private fun reportViewStopEvent(
        plausible: Plausible?,
        analytics: FirebaseAnalytics,
        stopId: String,
        stopName: String?,
        proximityToStopCategory: String,
    ) {
        if (!isAnalyticsActive()) return
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, stopId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, stopName)
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, string(R.string.analytics_label_stop_category))
            putString(FirebaseAnalytics.Param.LOCATION_ID, proximityToStopCategory)
        }
        analytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle)
        PlausibleAnalytics.reportViewStopEvent(plausible, stopId, proximityToStopCategory)
        UmamiAnalyticsReporter.reportViewStopEvent(umami(), stopId, proximityToStopCategory)
    }

    /** Sets the current region as a Firebase user property and on the Umami emitter. */
    @JvmStatic
    fun setRegion(analytics: FirebaseAnalytics, regionName: String?) {
        if (!isAnalyticsActive()) return
        analytics.setUserProperty(string(R.string.analytics_label_region_name), regionName)
        umami()?.setRegionName(regionName)
    }

    /** Records (and toggles Firebase collection for) the send-anonymous-usage-data preference. */
    @JvmStatic
    fun setSendAnonymousData(analytics: FirebaseAnalytics, isAnalyticsActive: Boolean) {
        analytics.setUserProperty(
            string(R.string.analytics_label_analytics_property),
            isAnalyticsActive.toYesNo(),
        )
        analytics.setAnalyticsCollectionEnabled(isAnalyticsActive)
    }

    /** Records the left-handed-mode preference as a Firebase user property. */
    @JvmStatic
    fun setLeftHanded(analytics: FirebaseAnalytics, isLeftHanded: Boolean) {
        if (!isAnalyticsActive()) return
        analytics.setUserProperty(string(R.string.analytics_label_left_hand_property), isLeftHanded.toYesNo())
    }

    /** Records the hide-departed-vehicles preference as a Firebase user property. */
    @JvmStatic
    fun setShowDepartedVehicles(analytics: FirebaseAnalytics, showDepartedVehicles: Boolean) {
        if (!isAnalyticsActive()) return
        analytics.setUserProperty(
            string(R.string.analytics_label_show_departed_vehicles_property),
            showDepartedVehicles.toYesNo(),
        )
    }

    /** Records whether the device has touch exploration (accessibility) enabled. */
    @JvmStatic
    fun setAccessibility(analytics: FirebaseAnalytics, isAccessibilityActive: Boolean) {
        if (!isAnalyticsActive()) return
        analytics.setUserProperty(string(R.string.analytics_accessibility), isAccessibilityActive.toYesNo())
    }

    /**
     * Reports destination-reminder feedback to Firebase.
     *
     * @param wasGoodReminder true if the reminder fired at the right time
     * @param feedbackText free-text feedback, or null if none was entered
     * @param fileName name of the uploaded trip-data file, or null if nothing was uploaded
     */
    @JvmStatic
    fun reportDestinationReminderFeedback(
        analytics: FirebaseAnalytics,
        wasGoodReminder: Boolean,
        feedbackText: String?,
        fileName: String?,
    ) {
        if (!isAnalyticsActive()) return
        val bundle = Bundle().apply {
            putString(
                FirebaseAnalytics.Param.ITEM_ID,
                string(R.string.analytics_label_button_press_destination_reminder_feedback),
            )
            putString(
                FirebaseAnalytics.Param.ITEM_VARIANT,
                if (wasGoodReminder) string(R.string.analytics_label_destination_reminder_yes)
                else string(R.string.analytics_label_destination_reminder_no),
            )
            if (!feedbackText.isNullOrEmpty()) putString(FirebaseAnalytics.Param.CONTENT, feedbackText)
            if (!fileName.isNullOrEmpty()) putString(FirebaseAnalytics.Param.LOCATION_ID, fileName)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
    }

    /** Whether the user has left analytics collection enabled in settings. */
    private fun isAnalyticsActive(): Boolean =
        PreferenceUtils.getBoolean(string(R.string.preferences_key_analytics), true)

    private fun string(resId: Int): String = Application.get().getString(resId)

    // The Umami emitter now lives on the injected AnalyticsProvider. ObaAnalytics is a context-less
    // static orchestrator, so it resolves the provider through the EntryPoint using the Application only
    // as a graph handle (a benign context reach) rather than reading app-global analytics *state* off it.
    private fun umami(): UmamiAnalytics? = AnalyticsEntryPoint.get(Application.get()).umami

    private fun Boolean.toYesNo(): String = if (this) "YES" else "NO"
}
