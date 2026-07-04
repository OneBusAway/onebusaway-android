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

import android.content.Context
import android.location.Location
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Analytics orchestrator for the app: fans each tracked event out to Firebase, Plausible
 * ([PlausibleAnalytics]) and Umami ([UmamiAnalyticsReporter]). An injected app-singleton — it holds the
 * Firebase emitter, the region-derived [AnalyticsProvider] (Plausible/Umami), and the analytics-enabled
 * preference itself, so call sites just call `obaAnalytics.reportXxx(...)` instead of threading a
 * `FirebaseAnalytics` + `Plausible` in. Injectable classes inject it directly; the context-less static /
 * Java / composable call sites reach it via [org.onebusaway.android.app.di.AnalyticsEntryPoint].
 *
 * @author Cagri Cetin, Sean Barbeau
 */
@Singleton
class ObaAnalytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analyticsProvider: AnalyticsProvider,
    private val preferences: PreferencesRepository,
    private val firebase: FirebaseAnalytics,
) {

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
    fun reportUiEvent(pageUrl: String, id: String, state: String?) {
        if (!isAnalyticsActive()) return
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, id)
            if (!state.isNullOrEmpty()) putString(FirebaseAnalytics.Param.ITEM_VARIANT, state)
        }
        firebase.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
        PlausibleAnalytics.reportUiEvent(analyticsProvider.plausible, pageUrl, id, state)
        UmamiAnalyticsReporter.reportUiEvent(analyticsProvider.umami, pageUrl, id, state)
    }

    /**
     * Reports a login event to Firebase.
     *
     * @param signUpMethod sign-up method of the login, or null if unknown
     */
    fun reportLoginEvent(signUpMethod: String?) {
        if (!isAnalyticsActive()) return
        val bundle = signUpMethod?.takeIf { it.isNotEmpty() }?.let {
            Bundle().apply { putString(FirebaseAnalytics.Param.METHOD, it) }
        }
        firebase.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
    }

    /**
     * Reports a search event to Firebase + Plausible + Umami.
     *
     * @param searchTerm search term used, or null if unknown
     */
    fun reportSearchEvent(searchTerm: String?) {
        if (!isAnalyticsActive()) return
        val bundle = searchTerm?.takeIf { it.isNotEmpty() }?.let {
            Bundle().apply { putString(FirebaseAnalytics.Param.SEARCH_TERM, it) }
        }
        firebase.logEvent(FirebaseAnalytics.Event.SEARCH, bundle)
        searchTerm?.let {
            PlausibleAnalytics.reportSearchEvent(analyticsProvider.plausible, it)
            UmamiAnalyticsReporter.reportSearchEvent(analyticsProvider.umami, it)
        }
    }

    /**
     * Reports the user viewing a stop, bucketing the distance between the device and the stop (only
     * when the device fix is accurate enough — under [LOCATION_ACCURACY_THRESHOLD]).
     */
    fun reportViewStopEvent(
        stopId: String,
        stopName: String?,
        myLocation: Location?,
        stopLocation: Location,
    ) {
        if (!isAnalyticsActive() || myLocation == null) return
        if (myLocation.accuracy < LOCATION_ACCURACY_THRESHOLD) {
            val stopDistance = ObaStopDistance.forDistance(myLocation.distanceTo(stopLocation))
            reportViewStopEvent(stopId, stopName, stopDistance.toString())
        }
    }

    private fun reportViewStopEvent(
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
        firebase.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle)
        PlausibleAnalytics.reportViewStopEvent(analyticsProvider.plausible, stopId, proximityToStopCategory)
        UmamiAnalyticsReporter.reportViewStopEvent(analyticsProvider.umami, stopId, proximityToStopCategory)
    }

    /** Sets the current region as a Firebase user property and on the Umami emitter. */
    fun setRegion(regionName: String?) {
        if (!isAnalyticsActive()) return
        firebase.setUserProperty(string(R.string.analytics_label_region_name), regionName)
        analyticsProvider.umami?.setRegionName(regionName)
    }

    /** Records (and toggles Firebase collection for) the send-anonymous-usage-data preference. */
    fun setSendAnonymousData(isAnalyticsActive: Boolean) {
        firebase.setUserProperty(
            string(R.string.analytics_label_analytics_property),
            isAnalyticsActive.toYesNo(),
        )
        firebase.setAnalyticsCollectionEnabled(isAnalyticsActive)
    }

    /** Records the left-handed-mode preference as a Firebase user property. */
    fun setLeftHanded(isLeftHanded: Boolean) {
        if (!isAnalyticsActive()) return
        firebase.setUserProperty(string(R.string.analytics_label_left_hand_property), isLeftHanded.toYesNo())
    }

    /** Records the hide-departed-vehicles preference as a Firebase user property. */
    fun setShowDepartedVehicles(showDepartedVehicles: Boolean) {
        if (!isAnalyticsActive()) return
        firebase.setUserProperty(
            string(R.string.analytics_label_show_departed_vehicles_property),
            showDepartedVehicles.toYesNo(),
        )
    }

    /** Records whether the device has touch exploration (accessibility) enabled. */
    fun setAccessibility(isAccessibilityActive: Boolean) {
        if (!isAnalyticsActive()) return
        firebase.setUserProperty(string(R.string.analytics_accessibility), isAccessibilityActive.toYesNo())
    }

    /**
     * Reports destination-reminder feedback to Firebase.
     *
     * @param wasGoodReminder true if the reminder fired at the right time
     * @param feedbackText free-text feedback, or null if none was entered
     * @param fileName name of the uploaded trip-data file, or null if nothing was uploaded
     */
    fun reportDestinationReminderFeedback(
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
        firebase.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
    }

    /** Whether the user has left analytics collection enabled in settings. */
    private fun isAnalyticsActive(): Boolean =
        preferences.getBoolean(R.string.preferences_key_analytics, true)

    private fun string(resId: Int): String = context.getString(resId)

    private fun Boolean.toYesNo(): String = if (this) "YES" else "NO"

    private companion object {
        /** A device fix is only accurate enough to bucket stop distance when below this (meters). */
        const val LOCATION_ACCURACY_THRESHOLD = 50f
    }
}
