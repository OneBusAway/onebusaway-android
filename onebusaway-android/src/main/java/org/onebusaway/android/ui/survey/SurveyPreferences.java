package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Date;
import java.util.UUID;

/**
 * Utility class for managing survey-related preferences using SharedPreferences.
 * This class handles storing and retrieving user UUIDs and survey reminder dates.
 */
public class SurveyPreferences {

    private static final String PREFS_NAME = "survey_pref";
    private static final String UUID_KEY = "my_uuid";
    private static final String SURVEY_REMINDER_DATE_KEY = "survey_reminder_day";


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
     * Saves the given survey reminder date in SharedPreferences.
     *
     * @param date The date to be saved as a reminder
     */
    public static void setSurveyReminderDate(Context context,Date date) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(SURVEY_REMINDER_DATE_KEY, date.getTime());
        editor.apply();
    }

    /**
     * Retrieves the survey reminder date from SharedPreferences.
     *
     * @return The stored reminder date as a long (milliseconds since epoch), or -1 if not set
     */
    public static Long getSurveyReminderDate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(SURVEY_REMINDER_DATE_KEY, -1);
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
