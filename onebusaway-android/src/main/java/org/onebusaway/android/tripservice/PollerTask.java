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

import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * A task (thread) that is responsible for polling the server to determine if a Notification to
 * remind the user of an arriving bus should be triggered.
 */
public final class PollerTask implements Runnable {
    //private static final String TAG = "PollerTask";

    private static final long ONE_MINUTE = 60 * 1000;

    private static final String[] ALERT_PROJECTION = {
            ObaContract.TripAlerts._ID,
            ObaContract.TripAlerts.TRIP_ID,
            ObaContract.TripAlerts.STOP_ID,
            ObaContract.TripAlerts.START_TIME,
            ObaContract.TripAlerts.STATE,
    };

    private static final int COL_ID = 0;

    private static final int COL_TRIP_ID = 1;

    private static final int COL_STOP_ID = 2;

    private static final int COL_START_TIME = 3;

    private static final int COL_STATE = 4;

    private final Context mContext;

    private final ContentResolver mCR;

    private final TaskContext mTaskContext;

    private final Uri mUri;

    public PollerTask(Context context, TaskContext taskContext, Uri uri) {
        mContext = context;
        mCR = mContext.getContentResolver();
        mTaskContext = taskContext;
        mUri = uri;
    }

    @Override
    public void run() {
        Cursor c = mCR.query(mUri, ALERT_PROJECTION, null, null, null);

        try {
            if (c != null) {
                while (c.moveToNext()) {
                    poll1(c);
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
            mTaskContext.taskComplete();
        }
    }

    private void poll1(Cursor c) {
        final Uri alertUri = ObaContract.TripAlerts.buildUri(c.getInt(COL_ID));
        final int state = c.getInt(COL_STATE);
        if (state == ObaContract.TripAlerts.STATE_CANCELLED) {
            return;
        }
        long now = System.currentTimeMillis();

        final long startTime = c.getLong(COL_START_TIME);
        // After a half-hour we can completely give up.
        if (startTime < (now - ONE_MINUTE * 30)) {
            ContentValues values = new ContentValues();
            values.put(ObaContract.TripAlerts.STATE, ObaContract.TripAlerts.STATE_CANCELLED);
            mCR.update(alertUri, values, null, null);

            TripService.scheduleAll(mContext);
            return;
        }

        // Before we do anything else, schedule another poll in a minute.
        // That way we know the polling will continue even if we're killed.
        TripService.pollTrip(mContext, alertUri, now + ONE_MINUTE);

        // If this is just scheduled, mark it as polling.
        if (state == ObaContract.TripAlerts.STATE_SCHEDULED) {
            ObaContract.TripAlerts
                    .setState(mContext, alertUri, ObaContract.TripAlerts.STATE_POLLING);
        }

        final String tripId = c.getString(COL_TRIP_ID);
        final String stopId = c.getString(COL_STOP_ID);
        final long reminderMS = getReminderMS(tripId, stopId);

        ObaArrivalInfoResponse response = ObaArrivalInfoRequest
                .newRequest(mContext, stopId).call();

        Long departMS = null;
        if (response.getCode() == ObaApi.OBA_OK) {
            departMS = checkArrivals(response, c);
        }

        if (departMS != null) {
            final long diffTime = departMS - System.currentTimeMillis();
            if (diffTime <= reminderMS) {
                // Bus is within the reminder interval (or it possibly has left!)
                // Send off a notification.
                //Log.d(TAG, "Notify for trip: " + alertUri);
                TripService.notifyTrip(mContext, mUri, diffTime);
            }
        }
    }

    private long getReminderMS(String tripId, String stopId) {
        final Uri uri = ObaContract.Trips.buildUri(tripId, stopId);
        return (long) UIUtils.intForQuery(mContext, uri, ObaContract.Trips.REMINDER) * ONE_MINUTE;
    }

    //
    // Return the difference between now and the predicted/scheduled
    // arrival time, or null if the arrival can't be found.
    //
    private Long checkArrivals(ObaArrivalInfoResponse response, Cursor c) {
        final String tripId = c.getString(COL_TRIP_ID);
        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        final int length = arrivals.length;
        for (int i = 0; i < length; ++i) {
            ObaArrivalInfo info = arrivals[i];
            if (tripId.equals(info.getTripId())) {
                // We found the trip. We notify when the reminder time
                // when calculated with the *predicted* arrival time
                // is past now.
                long depart = info.getPredictedArrivalTime();
                if (depart == 0) {
                    depart = info.getScheduledArrivalTime();
                }

                return depart;
            }
        }
        // Didn't find it.
        return null;
    }
}
