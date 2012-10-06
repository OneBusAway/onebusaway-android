package com.joulespersecond.seattlebusbot;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
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
}
