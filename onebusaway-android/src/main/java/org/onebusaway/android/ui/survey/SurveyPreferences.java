package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class SurveyPreferences {

    private static final String PREFS_NAME = "survey_pref";
    private static final String UUID_KEY = "my_uuid";

    /**
     * Saves the user's UUID to the shared preferences.
     *
     * @param uuid the UUID to be saved in shared preferences
     */
    private static void saveUserUUIDHelper(Context context, UUID uuid) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(UUID_KEY, uuid.toString());
        editor.apply();
    }

    /**
     * Retrieves the user's UUID from shared preferences.
     *
     * @return the saved UUID as a string, or null if not found
     */
    private static String getUserUUIDHelper(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uuidString = prefs.getString(UUID_KEY, null);
        return uuidString != null ? UUID.fromString(uuidString).toString() : null;
    }


    /**
     * Retrieves the user's UUID. If the UUID is not already stored, generates a new UUID and saves it.
     *
     * @return The user's UUID as a String.
     */
    public static String getUserUUID(Context context) {
        if (SurveyPreferences.getUserUUIDHelper(context) == null) {
            UUID uuid = UUID.randomUUID();
            SurveyPreferences.saveUserUUIDHelper(context, uuid);
        }
        return SurveyPreferences.getUserUUIDHelper(context);
    }
}
