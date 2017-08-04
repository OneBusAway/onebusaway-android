/*
 * Copyright (C) 2016 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.tripservice;

import org.onebusaway.android.provider.ObaContract;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.format.Time;

/**
 * This is the runnable that implements scheduling of trips (for the reminder feature).
 * It can schedule one or many trips, depending on the URI.
 *
 * @author paulw
 */
public final class SchedulerTask implements Runnable {
    //private static final String TAG = "SchedulerTask";

    private static final long ONE_MINUTE = 60 * 1000;

    private static final long LOOKAHEAD_DURATION_MS = 5 * ONE_MINUTE;

    private static final String[] PROJECTION = {
            ObaContract.Trips._ID,
            ObaContract.Trips.STOP_ID,
            ObaContract.Trips.REMINDER,
            ObaContract.Trips.DEPARTURE,
            ObaContract.Trips.DAYS
    };

    private static final int COL_ID = 0;

    private static final int COL_STOP_ID = 1;

    private static final int COL_REMINDER = 2;

    private static final int COL_DEPARTURE = 3;

    private static final int COL_DAYS = 4;

    private final Context mContext;

    private final ContentResolver mCR;

    private final TaskContext mTaskContext;

    private final Uri mUri;

    public SchedulerTask(Context context, TaskContext taskContext, Uri uri) {
        mContext = context;
        mCR = mContext.getContentResolver();
        mTaskContext = taskContext;
        mUri = uri;
    }

    @Override
    public void run() {
        cleanupOldAlerts();
        Cursor c = mCR.query(mUri, PROJECTION, null, null, null);

        try {
            Time tNow = new Time();
            tNow.setToNow();
            final long now = tNow.toMillis(false);

            if (c != null) {
                while (c.moveToNext()) {
                    schedule1(c, tNow, now);
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
            mTaskContext.taskComplete();
        }
    }

    // This schedules an alarm to go off when we need it to start polling,
    // and instantiates an TripAlert in the database if needed.

    private void schedule1(Cursor c, Time tNow, long now) {
        final String tripId = c.getString(COL_ID);
        final String stopId = c.getString(COL_STOP_ID);
        final Uri tripUri = ObaContract.Trips.buildUri(tripId, stopId);

        final int departureMins = c.getInt(COL_DEPARTURE);
        final long reminderMS = c.getInt(COL_REMINDER) * ONE_MINUTE;
        if (reminderMS == 0) {
            return;
        }
        final int days = c.getInt(COL_DAYS);
        if (days == 0) {
            Time tmp = new Time();
            tmp.set(0, departureMins, 0, tNow.monthDay, tNow.month, tNow.year);
            tmp.normalize(false);
            long remindTime = tmp.toMillis(false) - reminderMS;
            long triggerTime = remindTime - LOOKAHEAD_DURATION_MS;

            if (!scheduleAlert(tripUri, tripId, stopId, triggerTime)) {
                // If we failed to schedule a one-off alert, then it's
                // probably been cancelled or in the past and we should
                // just delete it.
                mCR.delete(tripUri, null, null);
            }
        } else {
            final int currentWeekDay = tNow.weekDay;
            for (int i = 0; i < 7; ++i) {
                final int day = (currentWeekDay + i) % 7;
                final int bit = ObaContract.Trips.getDayBit(day);
                if ((days & bit) == bit) {
                    Time tmp = new Time();
                    tmp.set(0, departureMins, 0, tNow.monthDay + i, tNow.month,
                            tNow.year);
                    tmp.normalize(false);
                    long remindTime = tmp.toMillis(false) - reminderMS;
                    long triggerTime = remindTime - LOOKAHEAD_DURATION_MS;

                    if (scheduleAlert(tripUri, tripId, stopId, triggerTime)) {
                        return;
                    }
                }
            }
        }
    }

    private boolean scheduleAlert(Uri uri,
                                  String tripId,
                                  String stopId,
                                  long triggerTime) {

        Time tmp = new Time();
        tmp.set(triggerTime);
        //Log.d(TAG, "Scheduling poll: " + uri.toString() + "  "
        //        + tmp.format2445());

        // Check to see if this alert has already been cancelled.
        Uri alertUri = null;

        Cursor cAlert = mCR.query(ObaContract.TripAlerts.CONTENT_URI,
                new String[]{ObaContract.TripAlerts._ID, ObaContract.TripAlerts.STATE},
                String.format("%s=? AND %s=? AND %s=?",
                        ObaContract.TripAlerts.TRIP_ID,
                        ObaContract.TripAlerts.STOP_ID,
                        ObaContract.TripAlerts.START_TIME),
                new String[]{tripId, stopId, String.valueOf(triggerTime)},
                null);

        if (cAlert != null) {
            try {
                if (cAlert.moveToNext()) {
                    if (cAlert.getInt(1) == ObaContract.TripAlerts.STATE_CANCELLED) {
                        return false;
                    }
                    alertUri = ObaContract.TripAlerts.buildUri(cAlert.getInt(0));

                }
            } finally {
                cAlert.close();
            }
        }
        if (alertUri == null) {
            // Insert a new trip alert.
            ContentValues values = new ContentValues();
            values.put(ObaContract.TripAlerts.TRIP_ID, tripId);
            values.put(ObaContract.TripAlerts.STOP_ID, stopId);
            values.put(ObaContract.TripAlerts.START_TIME, triggerTime);
            alertUri = mCR.insert(ObaContract.TripAlerts.CONTENT_URI, values);
        }

        // Should we schedule it in every case here??? What about when it's
        // already polling???
        TripService.pollTrip(mContext, alertUri, triggerTime);
        return true;
    }

    /**
     * Remove any alerts that are more than 24 hours in the past.
     */
    private void cleanupOldAlerts() {
        long then = System.currentTimeMillis() - ONE_MINUTE * 60 * 24;

        mCR.delete(ObaContract.TripAlerts.CONTENT_URI,
                ObaContract.TripAlerts.START_TIME + " < " + then,
                null);
    }
}
