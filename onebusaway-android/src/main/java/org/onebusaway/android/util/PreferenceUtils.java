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

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.app.di.PreferencesEntryPoint;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.preferences.PreferencesRepository;

import java.util.Locale;

/**
 * A class containing utility methods related to preferences.
 *
 * <p>This is a thin synchronous facade over the {@link PreferencesRepository} seam — every read/write
 * routes through the repository (resolved via {@link PreferencesEntryPoint}) rather than touching
 * {@code SharedPreferences} directly. That keeps these static helpers (and their many call sites)
 * working unchanged while the underlying store is free to move (e.g. to DataStore) behind the seam.
 */
public class PreferenceUtils {

    /**
     * The single point of contact with persisted prefs (same app-singleton every consumer shares).
     *
     * <p>This is the one deliberately-kept {@code Application.get()} reach in this hub (#1636): it's a
     * pure DI graph handle — a {@link Context} used only to resolve the {@link PreferencesEntryPoint},
     * never for its own state. The ~20 accessors here are static and reached from ~90 call sites, most
     * without a {@code Context} in scope, so threading one through purely to resolve the same
     * app-singleton would be churn with no behavioral gain. It can only be removed by making
     * {@code PreferenceUtils} itself injectable. The resource/string reaches that <em>did</em> need a
     * real {@code Context} (sort-order arrays, the location-denied key) now take one as a parameter.
     */
    private static PreferencesRepository repo() {
        return PreferencesEntryPoint.get(Application.get());
    }

    public static void saveString(String key, String value) {
        repo().setString(key, value);
    }

    public static void saveInt(String key, int value) {
        repo().setInt(key, value);
    }

    public static void saveLong(String key, long value) {
        repo().setLong(key, value);
    }

    public static void saveBoolean(String key, boolean value) {
        repo().setBoolean(key, value);
    }

    public static void saveFloat(String key, float value) {
        repo().setFloat(key, value);
    }

    public static void saveDouble(String key, double value) {
        repo().setLong(key, Double.doubleToRawLongBits(value));
    }

    /**
     * Gets a double for the provided key from preferences, or the default value if the preference
     * doesn't currently have a value.
     *
     * <p>Doubles are stored as the raw long bits of the value; passing the default's bits as the
     * long default makes an absent key round-trip back to {@code defaultValue} without a separate
     * {@code contains()} probe.
     *
     * @param key          key for the preference
     * @param defaultValue the default value to return if the key doesn't have a value
     * @return a double from preferences, or the default value if it doesn't exist
     */
    public static Double getDouble(String key, double defaultValue) {
        long bits = repo().getLong(key, Double.doubleToRawLongBits(defaultValue));
        return Double.longBitsToDouble(bits);
    }

    /**
     * Returns the currently selected stop sort order as the index in R.array.sort_stops
     *
     * @return the currently selected stop sort order as the index in R.array.sort_stops
     */
    public static int getStopSortOrderFromPreferences(Context context) {
        Resources r = context.getResources();
        String[] sortOptions = r.getStringArray(R.array.sort_stops);
        String sortPref = repo().getString(r.getString(
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
    public static int getReminderSortOrderFromPreferences(Context context){
        Resources resources = context.getResources();
        String[] sortOptions = resources.getStringArray(R.array.sort_reminders);
        String sortPref = repo().getString(resources.getString(
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
        return repo().getString(key, null);
    }

    public static String getString(String key, String defaultValue) {
        return repo().getString(key, defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        return repo().getLong(key, defaultValue);
    }

    public static float getFloat(String key, float defaultValue) {
        return repo().getFloat(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return repo().getInt(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return repo().getBoolean(key, defaultValue);
    }

    /**
     * Returns true if the user has previously indicated that they don't want to be prompted to provide
     * location permissions. Note that this means they haven't actually be prompted with the
     * system permission dialog.
     */
    public static boolean userDeniedLocationPermission(Context context) {
        Resources r = context.getResources();
        return getBoolean(r.getString(R.string.preferences_key_user_denied_location_permissions), false);
    }

    /**
     * Set value to true if the user has previously indicated that they don't want to be prompted to provide
     * location permissions, or false if they have indicated that they want to be prompted with
     * the system permission dialog.
     */
    public static void setUserDeniedLocationPermissions(Context context, boolean value) {
        Resources r = context.getResources();
        saveBoolean(r.getString(R.string.preferences_key_user_denied_location_permissions), value);
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

        String preferredUnits = repo()
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
