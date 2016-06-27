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

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.RegionUtils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.support.v4.app.Fragment;

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
     * Tracks stop tap distance between bus stop location and users current location
     *
     * @param stopId       for action
     * @param myLocation   the users location
     * @param stopLocation tapped stop location
     */
    public static void trackBusStopDistance(String stopId, Location myLocation, Location stopLocation) {
        if (isAnalyticsActive()) {
            if (myLocation == null) {
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

                reportEventWithCategory(ObaEventCategory.STOP_ACTION.toString(), "Stop Id: " + stopId,
                        stopDistance.toString());
            }
        }
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
