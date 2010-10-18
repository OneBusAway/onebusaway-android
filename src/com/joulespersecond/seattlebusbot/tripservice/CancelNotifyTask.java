package com.joulespersecond.seattlebusbot.tripservice;

import com.joulespersecond.oba.provider.ObaContract.TripAlerts;
import com.joulespersecond.seattlebusbot.TripService;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

public final class CancelNotifyTask implements Runnable {

    private final Context mContext;
    private final TaskContext mTaskContext;
    private final Uri mUri;

    public CancelNotifyTask(Context context, TaskContext taskContext, Uri uri) {
        mContext = context;
        mTaskContext = taskContext;
        mUri = uri;
    }

    @Override
    public void run() {
        try {
            // Get the notification from the alert ID.
            long alertId = ContentUris.parseId(mUri);
            mTaskContext.cancelNotification((int)alertId);

            ContentResolver cr = mContext.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(TripAlerts.STATE, TripAlerts.STATE_CANCELLED);
            cr.update(mUri, values, null, null);

            TripService.scheduleAll(mContext);
        } finally {
            mTaskContext.taskComplete();
        }
    }
}
