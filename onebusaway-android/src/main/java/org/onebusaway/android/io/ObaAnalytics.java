/*
 * Copyright (C) 2014-2015 University of South Florida
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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.RegionUtils;

import androidx.fragment.app.Fragment;

import static android.text.TextUtils.isEmpty;

/**
 * Analytics class for tracking the app
 *
 * @author Cagri Cetin
 */

public class ObaAnalytics {

    /**
     * Users location accuracy should be less then 50f
     */
    private static final float LOCATION_ACCURACY_THRESHOLD = 50f;

    /**
     * Amazon devices manufacturer name
     */
    public static final String mAmazonManufacturer = "amazon";

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
     * Event categories for segmentation
     * app_settings, ui_action, submit is similar with OBA IOS
     */
    public enum ObaEventCategory {
        APP_SETTINGS("app_settings"), UI_ACTION("ui_action"),
        SUBMIT("submit"), STOP_ACTION("stop_metrics"),
        ACCESSIBILITY("accessibility");

        private final String stringValue;

        ObaEventCategory(final String s) {
            stringValue = s;
        }

        public String toString() {
            return stringValue;
        }
    }

    /**
     * Reports events with categories. Helps segmentation in GA admin console.
     *
     * @param category category name
     * @param action   action name
     * @param label    label name
     */
    public static void reportEventWithCategory(String category, String action, String label) {
        if (isAnalyticsActive()) {
            Tracker tracker = Application.get().getTracker(Application.TrackerName.APP_TRACKER);
            Tracker tracker2 = Application.get().getTracker(Application.TrackerName.GLOBAL_TRACKER);
            String obaRegionName = RegionUtils.getObaRegionName();

            tracker.send(new HitBuilders.EventBuilder()
                    .setCategory(category)
                    .setAction(action)
                    .setLabel(label)
                    .setCustomDimension(1, obaRegionName)
                    .build());
            tracker2.send(new HitBuilders.EventBuilder()
                    .setCategory(category)
                    .setAction(action)
                    .setLabel(label)
                    .setCustomDimension(1, obaRegionName)
                    .build());
        }
    }

    /**
     * Reports UI events using Firebase
     * @param analytics Firebase singleton
     * @param id ID of the UI element to report
     * @param state the state or variant of the UI item, or null if the item doesn't have a state or variant
     */
    public static void reportFirebaseUiEvent(FirebaseAnalytics analytics, String id, String state) {
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
    public static void reportFirebaseLoginEvent(FirebaseAnalytics analytics, String signUpMethod) {
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
    public static void reportFirebaseSearchEvent(FirebaseAnalytics analytics, String searchTerm) {
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
     * Sets the current region as a user property in Firebase Analytics
     * @param analytics Firebase singleton
     * @param regionName name of the region that was selected
     */
    public static void setFirebaseRegion(FirebaseAnalytics analytics, String regionName) {
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
    public static void setFirebaseSendAnonymousData(FirebaseAnalytics analytics, boolean isAnalyticsActive) {
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_firebase_analytics_property), isAnalyticsActive ? "YES" : "NO");
        analytics.setAnalyticsCollectionEnabled(isAnalyticsActive);
    }

    /**
     * Sets if the user has set the preference for left handed mode
     * @param analytics Firebase singleton
     * @param isLeftHanded true if the user has enabled the left handed preference, or false if they have disabled it (default)
     */
    public static void setFirebaseLeftHanded(FirebaseAnalytics analytics, boolean isLeftHanded) {
        if (!isAnalyticsActive()) {
            return;
        }
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_firebase_left_hand_property), isLeftHanded ? "YES" : "NO");
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
        analytics.setUserProperty(Application.get().getString(R.string.analytics_firebase_accessibility), isAccessibilityActive ? "YES" : "NO");
    }

    /**
     * Tracks distance between bus stop location and device current location
     *
     * @param analytics Firebase singleton
     * @param stopId       bus stop ID
     * @param myLocation   the device location
     * @param stopLocation bus stop location
     */
    public static void trackBusStopDistance(FirebaseAnalytics analytics, String stopId, Location myLocation, Location stopLocation) {
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

            reportFirebaseViewStop(analytics, stopId, stopDistance.toString());
        }
    }

    /**
     * Reports the user viewing a particular bus stop, as well as a categorized distance from that stop
     *
     * @param analytics       Firebase singleton
     * @param stopId          ID of the stop
     * @param proximityToStop a label indicating the proximity of the user to the stop
     */
    private static void reportFirebaseViewStop(FirebaseAnalytics analytics, String stopId, String proximityToStop) {
        if (!isAnalyticsActive()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, stopId);
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, Application.get().getString(R.string.analytics_label_stop_category));
        bundle.putString(FirebaseAnalytics.Param.ITEM_LOCATION_ID, proximityToStop);
        analytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
    }

    /**
     * For reporting activities on Start
     *
     * @param activity The activity being reported
     */
    public static void reportActivityStart(Activity activity) {
        if (isAnalyticsActive()) {
            Tracker tracker = Application.get().getTracker(Application.TrackerName.APP_TRACKER);
            tracker.setScreenName(activity.getClass().getSimpleName());
            tracker.send(new HitBuilders.ScreenViewBuilder().build());
            Tracker tracker2 = Application.get().getTracker(Application.TrackerName.GLOBAL_TRACKER);
            tracker2.setScreenName(activity.getClass().getSimpleName());
            tracker2.send(new HitBuilders.ScreenViewBuilder().build());
        }
    }


    /**
     * For reporting fragments on Start
     *
     * @param fragment The fragment being reported
     */
    public static void reportFragmentStart(Fragment fragment) {
        if (isAnalyticsActive()) {
            Tracker tracker = Application.get().getTracker(Application.TrackerName.APP_TRACKER);
            tracker.setScreenName(fragment.getClass().getSimpleName());
            tracker.send(new HitBuilders.ScreenViewBuilder().build());
            Tracker tracker2 = Application.get().getTracker(Application.TrackerName.GLOBAL_TRACKER);
            tracker2.setScreenName(fragment.getClass().getSimpleName());
            tracker2.send(new HitBuilders.ScreenViewBuilder().build());
        }
    }

    public static void initAnalytics(Context context) {
        if (BuildConfig.DEBUG) {
            //Disables reporting when app runs on debug
            GoogleAnalytics.getInstance(context).setDryRun(true);

            // Workaround for #243- setDryRun(true) doesn't work on Fire Phone
            if (android.os.Build.MANUFACTURER.toLowerCase().contains(mAmazonManufacturer)) {
                GoogleAnalytics.getInstance(context).setAppOptOut(true);
            }
        }
    }

    /**
     * @return is GA enabled or disabled from settings
     */
    private static Boolean isAnalyticsActive() {
        SharedPreferences settings = Application.getPrefs();
        return settings.getBoolean(Application.get().getString(R.string.preferences_key_analytics), Boolean.TRUE);
    }
}
