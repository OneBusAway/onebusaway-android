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

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalsListActivity;

/**
 * A task (thread) that is responsible for generating a Notification to remind the user of an
 * arriving bus.
 *
 * @author paulw
 */
public final class NotifierTask implements Runnable {
    //private static final String TAG = "NotifierTask";

    private static final String[] ALERT_PROJECTION = {
            ObaContract.TripAlerts._ID,
            ObaContract.TripAlerts.TRIP_ID,
            ObaContract.TripAlerts.STOP_ID,
            ObaContract.TripAlerts.STATE,
    };

    private static final int COL_ID = 0;

    private static final int COL_STOP_ID = 2;

    private static final int COL_STATE = 3;

    private final Context mContext;

    private final TaskContext mTaskContext;

    private final ContentResolver mCR;

    private final Uri mUri;

    private String mNotifyText;

    public NotifierTask(Context context,
                        TaskContext taskContext,
                        Uri uri,
                        String notifyText) {
        mContext = context;
        mTaskContext = taskContext;
        mCR = mContext.getContentResolver();
        mUri = uri;
        mNotifyText = notifyText;
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
        final String stopId = c.getString(COL_STOP_ID);
        final int state = c.getInt(COL_STATE);
        if (state == ObaContract.TripAlerts.STATE_CANCELLED) {
            return;
        }

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

        Notification notification = createNotification(mNotifyText,
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
     * @param contentIntent intent to fire on click
     * @param deleteIntent  intent to remove/delete
     */
    private Notification createNotification(String notifyText,
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
                .setContentText(notifyText)
                .build();

    }
}
