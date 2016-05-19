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

import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalsListActivity;
import org.onebusaway.android.util.UIUtils;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

/**
 * A task (thread) that is responsible for generating a Notification to remind the user of an
 * arriving bus.
 *
 * @author paulw
 */
public final class NotifierTask implements Runnable {
    //private static final String TAG = "NotifierTask";

    private static final long ONE_MINUTE = 60 * 1000;

    private static final String[] ALERT_PROJECTION = {
            ObaContract.TripAlerts._ID,
            ObaContract.TripAlerts.TRIP_ID,
            ObaContract.TripAlerts.STOP_ID,
            ObaContract.TripAlerts.STATE,
    };

    private static final int COL_ID = 0;

    private static final int COL_TRIP_ID = 1;

    private static final int COL_STOP_ID = 2;

    private static final int COL_STATE = 3;

    private final Context mContext;

    private final TaskContext mTaskContext;

    private final ContentResolver mCR;

    private final Uri mUri;

    private long mTimeDiff;

    public NotifierTask(Context context,
            TaskContext taskContext,
            Uri uri,
            long timeDiff) {
        mContext = context;
        mTaskContext = taskContext;
        mCR = mContext.getContentResolver();
        mUri = uri;
        mTimeDiff = timeDiff;
    }

    @Override
    public void run() {
        Cursor c = mCR.query(mUri, ALERT_PROJECTION, null, null, null);

        try {
            if (c != null) {
                while (c.moveToNext()) {
                    notify(c);
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
            mTaskContext.taskComplete();
        }
    }

    private void notify(Cursor c) {
        final int id = c.getInt(COL_ID);
        final String tripId = c.getString(COL_TRIP_ID);
        final String stopId = c.getString(COL_STOP_ID);
        final int state = c.getInt(COL_STATE);
        if (state == ObaContract.TripAlerts.STATE_CANCELLED) {
            return;
        }
        final Uri tripUri = ObaContract.Trips.buildUri(tripId, stopId);
        final String routeId = UIUtils.stringForQuery(mContext,
                tripUri, ObaContract.Trips.ROUTE_ID);

        // Updating info on existing notifications is deprecated (see #290), so instead we
        // just create a new Notification each time. The notification manager handles preventing
        // duplicates of the same event.
        Intent deleteIntent = new Intent(mContext, TripService.class);
        deleteIntent.setAction(TripService.ACTION_CANCEL);
        deleteIntent.setData(mUri);
        PendingIntent pendingDeleteIntent = PendingIntent.getService(mContext, 0,
                deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final PendingIntent pendingContentIntent = PendingIntent.getActivity(mContext, 0,
                new ArrivalsListActivity.Builder(mContext, stopId).getIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = createNotification(routeId, mTimeDiff,
                pendingContentIntent, pendingDeleteIntent);

        mTaskContext.setNotification(id, notification);
    }

    /*
    private static final long[] VIBRATE_PATTERN = {
        0,    // on
        1000, // off
        1000, // on
        1000, // off
        1000, // on
        1000, // off
    };
    */

    /**
     * Create a notification and populate it with our latest data.  This method replaces
     * an implementation using Notification.setLatestEventInfo((), which was deprecated (see #290).
     *
     * @param routeId route identifer
     * @param timeDiff
     * @param contentIntent intent to fire on click
     * @param deleteIntent intent to remove/delete
     * @return
     */
    private Notification createNotification(String routeId,
            long timeDiff,
            PendingIntent contentIntent,
            PendingIntent deleteIntent) {
        final String title = mContext.getString(R.string.app_name);
        return new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setDefaults(Notification.DEFAULT_ALL)
                .setOnlyAlertOnce(true)
                        //.setLights(0xFF00FF00, 1000, 1000)
                        //.setVibrate(VIBRATE_PATTERN)
                .setContentIntent(contentIntent)
                .setDeleteIntent(deleteIntent)
                .setContentTitle(title)
                .setContentText(getNotifyText(routeId, timeDiff))
                .build();

    }

    private String getNotifyText(String routeId, long timeDiff) {
        final String routeName = TripService.getRouteShortName(mContext, routeId);

        if (timeDiff <= 0) {
            return mContext.getString(R.string.trip_stat_gone, routeName);
        } else if (timeDiff < ONE_MINUTE) {
            return mContext.getString(R.string.trip_stat_lessthanone, routeName);
        } else if (timeDiff < ONE_MINUTE * 2) {
            return mContext.getString(R.string.trip_stat_one, routeName);
        } else {
            return mContext.getString(R.string.trip_stat, routeName,
                    (int) (timeDiff / ONE_MINUTE));
        }
    }
}
