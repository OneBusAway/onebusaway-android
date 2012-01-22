/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot.tripservice;

import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.provider.ObaContract.TripAlerts;
import com.joulespersecond.oba.provider.ObaContract.Trips;
import com.joulespersecond.seattlebusbot.ArrivalsListActivity;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.TripService;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

/**
 *
 * @author paulw
 *
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
        if (state == TripAlerts.STATE_CANCELLED) {
            return;
        }
        final Uri tripUri = ObaContract.Trips.buildUri(tripId, stopId);
        final String routeId = UIHelp.stringForQuery(mContext,
                tripUri, Trips.ROUTE_ID);

        // Set our state to notified
        Notification notification = mTaskContext.getNotification(id);
        if (notification == null) {
            notification = createNotification(mUri);
        }

        setLatestInfo(notification, stopId, routeId, mTimeDiff);
        mTaskContext.setNotification(id, notification);
    }

    private Notification createNotification(Uri alertUri) {
        //Log.d(TAG, "Creating notification for alert: " + alertUri);
        Notification notification = new Notification(R.drawable.stat_trip, null,
                System.currentTimeMillis());
        notification.defaults = Notification.DEFAULT_SOUND;
        notification.flags = Notification.FLAG_ONLY_ALERT_ONCE
                | Notification.FLAG_SHOW_LIGHTS;
        notification.ledOnMS = 1000;
        notification.ledOffMS = 1000;
        notification.ledARGB = 0xFF00FF00;
        notification.vibrate = new long[] {
                0,    // on
                1000, // off
                1000, // on
                1000, // off
                1000, // on
                1000, // off
        };

        Intent deleteIntent = new Intent(mContext, TripService.class);
        deleteIntent.setAction(TripService.ACTION_CANCEL);
        deleteIntent.setData(alertUri);
        notification.deleteIntent = PendingIntent.getService(mContext, 0,
                deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return notification;
    }

    private void setLatestInfo(Notification notification,
            String stopId,
            String routeId,
            long timeDiff) {
        final String title = mContext.getString(R.string.app_name);

        final PendingIntent intent = PendingIntent.getActivity(mContext, 0,
                ArrivalsListActivity.makeIntent(mContext, stopId),
                PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(mContext,
                title,
                getNotifyText(routeId, timeDiff),
                intent);
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
                    (int)(timeDiff / ONE_MINUTE));
        }
    }
}
