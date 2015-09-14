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

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;

public class PreferenceHelp {

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
}
