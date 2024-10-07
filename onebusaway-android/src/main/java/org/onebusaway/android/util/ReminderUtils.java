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

import com.onesignal.notifications.INotification;

import org.json.JSONObject;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalsListActivity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
/**
 * Utilities to assist in the registering of reminder alarms for arriving/departing buses
 */
public class ReminderUtils {

    /**
     * Retrieves the short name of a bus route based on the provided route ID.
     *
     * @param context the application context
     * @param id the ID of the route
     * @return the short name of the route
     */
    public static String getRouteShortName(Context context, String id) {
        return UIUtils.stringForQuery(context, Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, id), ObaContract.Routes.SHORTNAME);
    }

    /**
     * Opens the stop information activity when a notification is received.
     *
     * @param context the application context
     * @param notification the notification containing additional data
     */
    public static void openStopInfo(Context context, INotification notification) {
        JSONObject data = notification.getAdditionalData();
        if (data != null) {
            try {
                JSONObject arrivalAndDeparture = data.getJSONObject("arrival_and_departure");
                String stopId = arrivalAndDeparture.optString("stop_id");
                Intent intent = new ArrivalsListActivity.Builder(context, stopId).getIntent();
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Deletes a saved reminder for a specific trip and stop ID.
     *
     * @param context the application context
     * @param tripId the ID of the trip
     * @param stopId the ID of the stop
     */
    public static void deleteSavedReminder(Context context, String tripId, String stopId) {
        String selection = ObaContract.Trips.TRIP_ID + " = ? AND " + ObaContract.Trips.STOP_ID + " = ?";
        String[] selectionArgs = new String[]{tripId, stopId};

        Uri uri = ObaContract.Trips.CONTENT_URI;

        try {
            context.getContentResolver().delete(uri, selection, selectionArgs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the delete path for an alarm based on the provided trip URI.
     *
     * @param context the application context
     * @param tripUri the URI of the trip
     * @return the delete path for the alarm, or null if not found
     */
    public static String getAlarmDeletePath(Context context, Uri tripUri) {
        ContentResolver cr = context.getContentResolver();
        String alarmDeletePath = null;

        try (Cursor cursor = cr.query(tripUri, new String[]{ObaContract.Trips.ALARM_DELETE_PATH}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                alarmDeletePath = cursor.getString(cursor.getColumnIndexOrThrow(ObaContract.Trips.ALARM_DELETE_PATH));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return alarmDeletePath;
    }

    /**
     * Checks if an alarm exists for the specified trip URI.
     *
     * @param context the application context
     * @param tripURI the URI of the trip
     * @return true if the alarm exists, false otherwise
     */
    public static boolean isAlarmExist(Context context, Uri tripURI) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(tripURI, new String[]{ObaContract.Trips._ID}, null, null, null);
        return (c != null && c.getCount() > 0);
    }
}
