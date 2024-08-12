package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

public class SurveyLocalData {

    private static final String PREFS_NAME = "survey_pref";
    private static final String HASH_MAP_KEY = "survey_hash_map";
    private static final String UUID_KEY = "my_uuid";

    /**
     * Saves the answered surveys to SharedPreferences.
     * The HashMap contains survey IDs as keys and their completion status as boolean values.
     *
     * @param context The context used to access SharedPreferences.
     * @param hashMap A HashMap where the keys are survey IDs and the values are booleans indicating completion status.
     */
    public static void setCompletedSurveys(Context context, HashMap<String, Boolean> hashMap) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        JSONObject jsonObject = new JSONObject(hashMap);
        String jsonString = jsonObject.toString();
        editor.putString(HASH_MAP_KEY, jsonString);
        editor.apply();
    }

    /**
     * Retrieves the answered surveys from SharedPreferences as a HashMap.
     * The map contains survey IDs as keys and their completion status as boolean values.
     *
     * @param context The context used to access SharedPreferences.
     * @return A HashMap where the keys are survey IDs and the values are booleans indicating completion status.
     */
    public static HashMap<String, Boolean> getCompletedSurveys(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(HASH_MAP_KEY, "{}");
        HashMap<String, Boolean> hashMap = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                boolean value = jsonObject.getBoolean(key);
                hashMap.put(key, value);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return hashMap;
    }

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
