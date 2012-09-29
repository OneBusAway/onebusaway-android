/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
