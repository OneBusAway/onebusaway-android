package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class SurveyPreferences {

    private static final String PREFS_NAME = "survey_pref";
    private static final String UUID_KEY = "my_uuid";

    public static void saveUserUUID(Context context, UUID uuid) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(UUID_KEY, uuid.toString());
        editor.apply();
    }

    public static String getUserUUID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uuidString = prefs.getString(UUID_KEY, null);
        return uuidString != null ? UUID.fromString(uuidString).toString() : null;
    }

}
