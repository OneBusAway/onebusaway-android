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
package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.provider.ObaContract.Trips;
import com.joulespersecond.seattlebusbot.tripservice.CancelNotifyTask;
import com.joulespersecond.seattlebusbot.tripservice.NotifierTask;
import com.joulespersecond.seattlebusbot.tripservice.PollerTask;
import com.joulespersecond.seattlebusbot.tripservice.SchedulerTask;
import com.joulespersecond.seattlebusbot.tripservice.TaskContext;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripService extends Service {
    public static final String TAG = "TripService";

    // Actions
    public static final String ACTION_SCHEDULE =
        "com.joulespersecond.seattlebusbot.action.SCHEDULE";
    public static final String ACTION_POLL =
        "com.joulespersecond.seattlebusbot.action.POLL";
    public static final String ACTION_NOTIFY =
        "com.joulespersecond.seattlebusbot.action.NOTIFY";
    public static final String ACTION_CANCEL =
        "com.joulespersecond.seattlebusbot.action.CANCEL";

    private static final String EXTRA_TIMEDIFF = ".timeDiff";

    private ExecutorService mThreadPool;
    private NotificationManager mNM;
    private ConcurrentHashMap<Integer,Notification> mNotifications;

    @Override
    public void onCreate() {
        mThreadPool = Executors.newSingleThreadExecutor();
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mNotifications = new ConcurrentHashMap<Integer,Notification>();
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "service destroyed");
        if (mThreadPool != null) {
            mThreadPool.shutdownNow();
            // TODO: Await termination???
        }
    }

    //
    // This is the old onStart method that will be called on the pre-2.0
    // platform. On 2.0 or later we override onStartCommand so this
    // method will not be called.
    //
    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return handleCommand(intent, startId);
    }

    private final class TaskContextImpl implements TaskContext {
        private final int mStartId;

        public TaskContextImpl(int startId) {
            mStartId = startId;
        }

        @Override
        public void taskComplete() {
            //Log.d(TAG, "Task complete: " + mStartId);
            // If we have notifications, then we can't stop ourselves.
            if (mNotifications.isEmpty()) {
                stopSelfResult(mStartId);
            }
        }

        @Override
        public void setNotification(int id, Notification notification) {
            mNotifications.put(id, notification);
            mNM.notify(id, notification);
        }

        @Override
        public void cancelNotification(int id) {
            mNM.cancel(id);
            mNotifications.remove(id);
            // If there are no more notifications
            // (and nothing else to process in the thread queue?)
            // stop the service.
            if (mNotifications.isEmpty()) {
                //Log.d(TAG, "Stopping service");
                stopSelf();
            }
        }

        @Override
        public Notification getNotification(int id) {
            return mNotifications.get(id);
        }

    }

    private int handleCommand(Intent intent, int startId) {
        final String action = intent.getAction();
        final TaskContextImpl taskContext = new TaskContextImpl(startId);
        final Uri uri = intent.getData();
        //Log.d(TAG, "Handle command: startId=" + startId +
        //        " action=" + action +
        //        " uri=" + uri);

        if (ACTION_SCHEDULE.equals(action)) {
            mThreadPool.submit(new SchedulerTask(this, taskContext, uri));
            return START_REDELIVER_INTENT;

        } else if (ACTION_POLL.equals(action)) {
            mThreadPool.submit(new PollerTask(this, taskContext, uri));
            return START_NOT_STICKY;

        } else if (ACTION_NOTIFY.equals(action)) {
            // Create the notification
            long timeDiff = intent.getLongExtra(EXTRA_TIMEDIFF, 0);
            mThreadPool.submit(new NotifierTask(this,
                    taskContext, uri, timeDiff));
            return START_REDELIVER_INTENT;

        } else if (ACTION_CANCEL.equals(action)) {
            mThreadPool.submit(new CancelNotifyTask(this, taskContext, uri));
            return START_NOT_STICKY;

        } else {
            Log.e(TAG, "Unknown action: " + action);
            //stopSelfResult(startId);
            return START_NOT_STICKY;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private final IBinder mBinder = new Binder() {
        @Override
        protected boolean onTransact(int code,
                Parcel data,
                Parcel reply,
                int flags) throws RemoteException {
            return super.onTransact(code, data, reply, flags);
        }
    };

    //
    // Trip helpers
    //
    public static void scheduleAll(Context context) {
        final Intent intent = new Intent(context, TripService.class);
        intent.setAction(TripService.ACTION_SCHEDULE);
        intent.setData(Trips.CONTENT_URI);
        context.startService(intent);
    }

    public static void pollTrip(Context context, Uri alertUri, long triggerTime) {
        Intent intent = new Intent(TripService.ACTION_POLL, alertUri,
                context, AlarmReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0,
                intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarm =
            (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarm.set(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent);
    }

    public static void notifyTrip(Context context, Uri alertUri, long diffTime) {
        final Intent intent = new Intent(context, TripService.class);
        intent.setAction(ACTION_NOTIFY);
        intent.setData(alertUri);
        intent.putExtra(EXTRA_TIMEDIFF, diffTime);
        context.startService(intent);
    }

    public static String getRouteShortName(Context context, String id) {
        return UIHelp.stringForQuery(context, Uri.withAppendedPath(
                ObaContract.Routes.CONTENT_URI, id),
                ObaContract.Routes.SHORTNAME);
    }
}
