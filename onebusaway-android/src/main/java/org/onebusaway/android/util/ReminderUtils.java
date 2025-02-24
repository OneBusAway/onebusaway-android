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

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.provider.ObaContract;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * This is not useless it's checking if the app is configured to show reminders
     * Checks if reminders should be shown to the user
     * @return true if reminders should be shown, false otherwise
     */

    public static boolean shouldShowReminders(){
        return Application.getUserPushID() != null && !Application.getUserPushID().isEmpty();
    }

    /**
     * Returns the valid reminder times based on the provided departure time.
     * @param departTime the departure time in milliseconds
     * @return the valid reminder times
     */

    public static String[] getReminderTimes(long departTime) {
        Integer[] times = {3,5,10,15,20,25,30};
        // Convert milliseconds to minutes and calculate the time until departure
        long departTimeInMinutes = (long) Math.ceil((departTime - System.currentTimeMillis()) / 60000.0);
        String[] allTimes = Application.get().getResources().getStringArray(R.array.reminder_time);
        List<String> validTimes = new ArrayList<>();

        // Add at least 1 minute to the list of valid times
        validTimes.add(allTimes[0]);

        int index = 1;
        for (Integer time : times) {
            if (time <= departTimeInMinutes) {
                validTimes.add(allTimes[index]);
            }else{
                break;
            }
            ++index;
        }

        return validTimes.toArray(new String[0]);
    }

    /**
     * Returns the departure time of the reminder based on the provided arrival information.
     * @param info the arrival information
     * @return the departure time of the reminder
     */
    public static long getReminderDepartureTime(ObaArrivalInfo info){
        return info.getPredictedDepartureTime() != 0 ? info.getPredictedDepartureTime() : info.getScheduledDepartureTime();
    }
}
