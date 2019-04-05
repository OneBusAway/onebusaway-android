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
package org.onebusaway.android.io;

import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import static android.text.TextUtils.isEmpty;

/**
 * Analytics class for tracking the app
 *
 * @author Cagri Cetin, Sean Barbeau
 */

public class ObaAnalytics {

    /**
     * Users location accuracy should be less then 50f
     */
    private static final float LOCATION_ACCURACY_THRESHOLD = 50f;

    /**
     * To measure the distance when the bus stop tapped.
     */
    public enum ObaStopDistance {
        DISTANCE_1("User Distance: 00000-00050m", 50),
        DISTANCE_2("User Distance: 00050-00100m", 100),
        DISTANCE_3("User Distance: 00100-00200m", 200),
        DISTANCE_4("User Distance: 00200-00400m", 400),
        DISTANCE_5("User Distance: 00400-00800m", 800),
        DISTANCE_6("User Distance: 00800-01600m", 1600),
        DISTANCE_7("User Distance: 01600-03200m", 3200),
        DISTANCE_8("User Distance: 03200-INFINITY", 0);

        private final String stringValue;
        private final int distanceInMeters;

        ObaStopDistance(final String s, final int i) {
            stringValue = s;
            distanceInMeters = i;
        }

        public String toString() {
            return stringValue;
        }

        public int getDistanceInMeters() {
            return distanceInMeters;
        }
    }

    /**
     * Reports UI events using Firebase
     * @param analytics Firebase singleton
     * @param id ID of the UI element to report
     * @param state the state or variant of the UI item, or null if the item doesn't have a state or variant
     */
    public static void reportUiEvent(FirebaseAnalytics analytics, String id, String state) {
        if (!isAnalyticsActive()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id);
        if (!isEmpty(state)) {
            bundle.putString(FirebaseAnalytics.Param.ITEM_VARIANT, state);
        }
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

    /**
     * Reports Login events using Firebase
     * @param analytics Firebase singleton
     * @param signUpMethod Sign up method of the login, or null if unknown
     */
    public static void reportLoginEvent(FirebaseAnalytics analytics, String signUpMethod) {
        if (!isAnalyticsActive()) {
            return;
        }
        Bundle bundle = null;
        if (!isEmpty(signUpMethod)) {
            bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.METHOD, signUpMethod);
        }
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);
    }

    /**
     * Reports Search events using Firebase
     * @param analytics Firebase singleton
     * @param searchTerm search term used, or null if unknown
     */
    public static void reportSearchEvent(FirebaseAnalytics analytics, String searchTerm) {
        if (!isAnalyticsActive()) {
            return;
        }
        Bundle bundle = null;
        if (!isEmpty(searchTerm)) {
            bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.SEARCH_TERM, searchTerm);
        }
        analytics.logEvent(FirebaseAnalytics.Event.SEARCH, bundle);
    }

    /**
     * Reports the distance between bus stop location and device current location
     *
     * @param analytics Firebase singleton
     * @param stopId       bus stop ID
     * @param stopName bus stop name
     * @param myLocation   the device location
     * @param stopLocation bus stop location
     */
    public static void reportViewStopEvent(FirebaseAnalytics analytics, String stopId, String stopName, Location myLocation, Location stopLocation) {
        if (!isAnalyticsActive() || myLocation == null) {
            return;
        }
        if (myLocation.getAccuracy() < LOCATION_ACCURACY_THRESHOLD) {
            float distanceInMeters = myLocation.distanceTo(stopLocation);
            ObaStopDistance stopDistance;

            if (distanceInMeters < ObaStopDistance.DISTANCE_1.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_1;
            } else if (distanceInMeters < ObaStopDistance.DISTANCE_2.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_2;
            } else if (distanceInMeters < ObaStopDistance.DISTANCE_3.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_3;
            } else if (distanceInMeters < ObaStopDistance.DISTANCE_4.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_4;
            } else if (distanceInMeters < ObaStopDistance.DISTANCE_5.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_5;
            } else if (distanceInMeters < ObaStopDistance.DISTANCE_6.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_6;
            } else if (distanceInMeters < ObaStopDistance.DISTANCE_7.getDistanceInMeters()) {
                stopDistance = ObaStopDistance.DISTANCE_7;
            } else {
                stopDistance = ObaStopDistance.DISTANCE_8;
            }

            reportViewStopEvent(analytics, stopId, stopName, stopDistance.toString());
        }
    }

    /**
     * Reports the user viewing a particular bus stop, as well as a categorized distance from that stop
     *
     * @param analytics       Firebase singleton
     * @param stopId          ID of the stop
     * @param stopName        Name of the stop
     * @param proximityToStopCategory a label indicating the proximity of the user to the stop
     */
    private static void reportViewStopEvent(FirebaseAnalytics analytics, String stopId, String stopName, String proximityToStopCategory) {
        if (!isAnalyticsActive()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, stopId);
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, stopName);
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, Application.get().getString(R.string.analytics_label_stop_category));
        bundle.putString(FirebaseAnalytics.Param.ITEM_LOCATION_ID, proximityToStopCategory);
        analytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
    }

    /**
     * Sets the current region as a user property in Firebase Analytics
     * @param analytics Firebase singleton
     * @param regionName name of the region that was selected
     */
    public static void setRegion(FirebaseAnalytics analytics, String regionName) {
        if (!isAnalyticsActive()) {
            return;
        }
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_region_name), regionName);
    }

    /**
     * Sets if the user has set the preference to send anonymous usage data
     * @param analytics Firebase singleton
     * @param isAnalyticsActive true if the user has enabled the preference, or false if they have disabled it
     */
    public static void setSendAnonymousData(FirebaseAnalytics analytics, boolean isAnalyticsActive) {
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_analytics_property), isAnalyticsActive ? "YES" : "NO");
        analytics.setAnalyticsCollectionEnabled(isAnalyticsActive);
    }

    /**
     * Sets if the user has set the preference for left handed mode
     * @param analytics Firebase singleton
     * @param isLeftHanded true if the user has enabled the left handed preference, or false if they have disabled it (default)
     */
    public static void setLeftHanded(FirebaseAnalytics analytics, boolean isLeftHanded) {
        if (!isAnalyticsActive()) {
            return;
        }
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_left_hand_property), isLeftHanded ? "YES" : "NO");
    }

    /**
     * Sets if the user has chosen to hide departed vehicles (i.e, negative prediction times)
     * @param analytics Firebase singleton
     * @param showDepartedVehicles true if the user has the preference enabled to see departed vehicles (default), or false if they have it disabled
     */
    public static void setShowDepartedVehicles(FirebaseAnalytics analytics, boolean showDepartedVehicles) {
        if (!isAnalyticsActive()) {
            return;
        }
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_show_departed_vehicles_property), showDepartedVehicles ? "YES" : "NO");
    }

    /**
     * Sets if the user has enabled touch exploration (accessibility) on their device
     * @param analytics Firebase singleton
     * @param isAccessibilityActive true if the user has enabled touch exploration (accessibility) on their device, and false if they have not
     */
    public static void setAccessibility(FirebaseAnalytics analytics, boolean isAccessibilityActive) {
        if (!isAnalyticsActive()) {
            return;
        }
        analytics.setUserProperty(Application.get().getString(R.string.analytics_accessibility), isAccessibilityActive ? "YES" : "NO");
    }

    /**
     * @return is GA enabled or disabled from settings
     */
    private static Boolean isAnalyticsActive() {
        SharedPreferences settings = Application.getPrefs();
        return settings.getBoolean(Application.get().getString(R.string.preferences_key_analytics), Boolean.TRUE);
    }

    /**
     * Reports destination reminder feedback using Firebase
     * @param analytics Firebase singleton
     * @param wasGoodReminder true if the user responded that they got the reminder at the right time, or false if they responded that they did not get the reminder at the right time
     * @param feedbackText plain text feedback submitted by the user, or null if the user didn't enter any feedback text
     * @param fileName the name of the file that was uploaded that contains the data for this particular trip, or null if the user didn't upload data
     */
    public static void reportDestinationReminderFeedback(FirebaseAnalytics analytics, boolean wasGoodReminder, String feedbackText, String fileName) {
        if (!isAnalyticsActive()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, Application.get().getString(R.string.analytics_label_button_press_destination_reminder_feedback));
        if (wasGoodReminder) {
            bundle.putString(FirebaseAnalytics.Param.ITEM_VARIANT, Application.get().getString(R.string.analytics_label_destination_reminder_yes));
        } else {
            bundle.putString(FirebaseAnalytics.Param.ITEM_VARIANT, Application.get().getString(R.string.analytics_label_destination_reminder_no));
        }
        if (!isEmpty(feedbackText)) {
            bundle.putString(FirebaseAnalytics.Param.CONTENT, feedbackText);
        }
        if (!isEmpty(fileName)) {
            bundle.putString(FirebaseAnalytics.Param.ITEM_LOCATION_ID, fileName);
        }
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }
}
