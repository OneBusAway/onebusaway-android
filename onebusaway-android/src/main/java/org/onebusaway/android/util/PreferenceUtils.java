/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.util;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.map.MapParams;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;

import java.util.Locale;

/**
 * A class containing utility methods related to preferences
 */
public class PreferenceUtils {

    @TargetApi(9)
    public static void saveString(SharedPreferences prefs, String key, String value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public static void saveString(String key, String value) {
        saveString(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveInt(SharedPreferences prefs, String key, int value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public static void saveInt(String key, int value) {
        saveInt(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveLong(SharedPreferences prefs, String key, long value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public static void saveLong(String key, long value) {
        saveLong(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveBoolean(SharedPreferences prefs, String key, boolean value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public static void saveBoolean(String key, boolean value) {
        saveBoolean(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveFloat(SharedPreferences prefs, String key, float value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putFloat(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public static void saveFloat(String key, float value) {
        saveFloat(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveDouble(SharedPreferences prefs, String key, double value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, Double.doubleToRawLongBits(value));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    @TargetApi(9)
    public static void saveDouble(String key, double value) {
        saveDouble(Application.getPrefs(), key, value);
    }

    /**
     * Gets a double for the provided key from preferences, or the default value if the preference
     * doesn't currently have a value
     *
     * @param key          key for the preference
     * @param defaultValue the default value to return if the key doesn't have a value
     * @return a double from preferences, or the default value if it doesn't exist
     */
    public static Double getDouble(String key, double defaultValue) {
        if (!Application.getPrefs().contains(key)) {
            return defaultValue;
        }
        return Double.longBitsToDouble(Application.getPrefs().getLong(key, 0));
    }

    /**
     * Returns the currently selected stop sort order as the index in R.array.sort_stops
     *
     * @return the currently selected stop sort order as the index in R.array.sort_stops
     */
    public static int getStopSortOrderFromPreferences() {
        Resources r = Application.get().getResources();
        SharedPreferences settings = Application.getPrefs();
        String[] sortOptions = r.getStringArray(R.array.sort_stops);
        String sortPref = settings.getString(r.getString(
                R.string.preference_key_default_stop_sort), sortOptions[0]);
        if (sortPref.equalsIgnoreCase(sortOptions[0])) {
            return 0;
        } else if (sortPref.equalsIgnoreCase(sortOptions[1])) {
            return 1;
        }
        return 0;  // Default to the first option
    }

    /**
     * Returns current reminder sort order from the SharedPreferences
     *
     * @return the currently selected reminder sort order as the index in R.array.sort_stops
     */
    public static int getReminderSortOrderFromPreferences(){
        Resources resources = Application.get().getResources();
        SharedPreferences preferences = Application.getPrefs();
        String[] sortOptions = resources.getStringArray(R.array.sort_reminders);
        String sortPref = preferences.getString(resources.getString(
                R.string.preference_key_default_reminder_sort), sortOptions[0]);
        if (sortPref.equalsIgnoreCase(sortOptions[0])){
            return 0;
        } else if (sortPref.equalsIgnoreCase(sortOptions[1])){
            return 1;
        }
        return 0;  //Default to the first option
    }

    /**
     * Saves provided MapView center location and zoom level to preferences
     *
     * @param lat  latitude of map center
     * @param lon  longitude of map center
     * @param zoom zoom level of map
     */
    public static void saveMapViewToPreferences(double lat, double lon, float zoom) {
        saveDouble(MapParams.CENTER_LAT, lat);
        saveDouble(MapParams.CENTER_LON, lon);
        saveFloat(MapParams.ZOOM, zoom);
    }

    /**
     * Retrieves the map view location and zoom level from a preference and stores it in the
     * provided bundle, if a valid lat/long and zoom level has been previously saved to prefs
     *
     * @param b bundle to store the map view center and zoom level in
     */
    public static void maybeRestoreMapViewToBundle(Bundle b) {
        Double lat = PreferenceUtils.getDouble(MapParams.CENTER_LAT, 0.0d);
        Double lon = PreferenceUtils.getDouble(MapParams.CENTER_LON, 0.0d);
        float zoom = PreferenceUtils.getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);
        if (lat != 0.0 && lon != 0.0 && zoom != 0.0) {
            b.putDouble(MapParams.CENTER_LAT, lat);
            b.putDouble(MapParams.CENTER_LON, lon);
            b.putFloat(MapParams.ZOOM, zoom);
        }
    }

    public static String getString(String key) {
        return Application.getPrefs().getString(key, null);
    }

    public static long getLong(String key, long defaultValue) {
        return Application.getPrefs().getLong(key, defaultValue);
    }

    public static float getFloat(String key, float defaultValue) {
        return Application.getPrefs().getFloat(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return Application.getPrefs().getInt(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Application.getPrefs().getBoolean(key, defaultValue);
    }

    /**
     * Returns true if preferred units are metric, false if Imperial. If set to Automatic,
     * assume Imperial if the default locale is the US, metric otherwise.
     *
     * @param context context to get string resources from
     * @return returns true if preferred units are metric, false if Imperial
     */
    public static boolean getUnitsAreMetricFromPreferences(Context context) {

        // This implementation taken from RegionsFragment

        String IMPERIAL = context.getString(R.string.preferences_preferred_units_option_imperial);
        String METRIC = context.getString(R.string.preferences_preferred_units_option_metric);
        String AUTOMATIC = context.getString(R.string.preferences_preferred_units_option_automatic);

        SharedPreferences mSettings = Application.getPrefs();

        String preferredUnits = mSettings
                .getString(context.getString(R.string.preference_key_preferred_units),
                        AUTOMATIC);

        if (preferredUnits.equalsIgnoreCase(AUTOMATIC)) {
            // If the country is set to USA, assume imperial, otherwise metric
            // TODO - Method of guessing metric/imperial can definitely be improved
            return !Locale.getDefault().getISO3Country()
                    .equalsIgnoreCase(Locale.US.getISO3Country());
        } else {
            return preferredUnits.equalsIgnoreCase(METRIC);
        }
    }
}
