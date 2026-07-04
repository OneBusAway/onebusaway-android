/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers for the reminder feature: FCM payload parsing, the reminder-availability check, and the
 * valid-lead-time list. The reminder storage (create/delete/exists over the Trips table) moved to
 * {@code org.onebusaway.android.reminders.ReminderRepository} at the storage-modernization cutover.
 */
public class ReminderUtils {

    private static final String TAG = "ReminderUtils";

    /**
     * Key for the arrival-and-departure reminder payload (JSON): the FCM message data key on receipt
     * and the intent extra it is forwarded as. Shared by the FCM service, the route translator, and
     * the payload parsers ({@link #getStopIdFromPayload}, {@link #getTripIdFromPayload}).
     */
    public static final String ARRIVAL_PAYLOAD_KEY = "arrival_and_departure";

    /**
     * Extracts the stop_id from an FCM arrival_and_departure JSON payload.
     *
     * @param arrivalJson the JSON string from the arrival_and_departure FCM data field
     * @return the stop ID, or null if not present or unparseable
     */
    public static String getStopIdFromPayload(String arrivalJson) {
        return stringFromPayload(arrivalJson, "stop_id");
    }

    /**
     * Extracts the trip_id from an FCM arrival_and_departure JSON payload.
     *
     * @param arrivalJson the JSON string from the arrival_and_departure FCM data field
     * @return the trip ID, or null if not present or unparseable
     */
    public static String getTripIdFromPayload(String arrivalJson) {
        return stringFromPayload(arrivalJson, "trip_id");
    }

    private static String stringFromPayload(String arrivalJson, String key) {
        if (arrivalJson == null) return null;
        try {
            JSONObject arrival = new JSONObject(arrivalJson);
            String value = arrival.optString(key, "");
            return value.isEmpty() ? null : value;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing arrival_and_departure JSON", e);
            return null;
        }
    }

    /**
     * Checks if reminders should be available by verifying an FCM push token has been obtained.
     * Returns false if the token has not yet been fetched or registration failed.
     */
    public static boolean shouldShowReminders() {
        String pushId = Application.getUserPushID();
        return pushId != null && !pushId.isEmpty();
    }

    /**
     * Returns the valid reminder times based on the provided departure time.
     * @param context the application context
     * @param departTime the departure time in milliseconds
     * @return the valid reminder times
     */
    public static String[] getReminderTimes(Context context, long departTime) {
        Integer[] times = {3, 5, 10, 15, 20, 25, 30};
        // Convert milliseconds to minutes and calculate the time until departure
        long departTimeInMinutes = (long) Math.ceil((departTime - System.currentTimeMillis()) / 60000.0);
        String[] allTimes = context.getResources().getStringArray(R.array.reminder_time);
        List<String> validTimes = new ArrayList<>();

        // Add at least 1 minute to the list of valid times
        validTimes.add(allTimes[0]);

        int index = 1;
        for (Integer time : times) {
            if (time <= departTimeInMinutes) {
                validTimes.add(allTimes[index]);
            } else {
                break;
            }
            ++index;
        }

        return validTimes.toArray(new String[0]);
    }
}
