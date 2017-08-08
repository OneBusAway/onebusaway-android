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

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

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

/**
 * A container Service for a thread pool that manages the scheduling, polling, and notifying the
 * user of an upcoming bus at their stop.. The thread pool can contain executing threads for
 * various
 * tasks related to the reminders feature - the SchedulerTask (executed when a new reminder is
 * saved, or after boot (via BootstrapService) to register all reminders saved in the database with
 * the Android platform), the PollerTask (triggered by the platform via AlarmReceiver to begin
 * polling the server 30 min ahead of the scheduled time of the arrival for which the reminder is
 * set), the NotifierTask (triggered by OBA Android when we should fire a notification for a
 * reminder), or the CancelNotifyTask (for when a notification is canceled).
 *
 * This Service is not constructed to continously run - instead, it can shut down in between the
 * execution of tasks.  For example, the PollerTask actually reschedules itself each time it polls
 * (in PollerTask.poll1()), so the TripService service could shut down in between polling events.
 *
 * Following #290, mNotifications is only used as a semaphore to synchronize the multiple tasks and
 * shutdown of the Service.  This is a complex implementation prone to multi-threading and
 * synchronization issues.  We should examine a re-implementation of the reminder service - see
 * #493
 * for details.
 */
public class TripService extends Service {

    public static final String TAG = "TripService";

    /**
     * Actions - should match intent-filter actions for AlarmReceiver in AndroidManifest.xml
     *
     * NOTE: The action names in this class should not be changed.  They need to stay under the
     * BuildConfig.APPLICATION_ID (for the original OBA brand, "com.joulespersecond.seattlebusbot")
     * namespace to support backwards compatibility with existing installed apps
     */
    public static final String ACTION_SCHEDULE =
            BuildConfig.APPLICATION_ID + ".action.SCHEDULE";

    public static final String ACTION_POLL =
            BuildConfig.APPLICATION_ID + ".action.POLL";

    public static final String ACTION_NOTIFY =
            BuildConfig.APPLICATION_ID + ".action.NOTIFY";

    public static final String ACTION_CANCEL =
            BuildConfig.APPLICATION_ID + ".action.CANCEL";

    private static final String NOTIFY_TEXT = ".notifyText";

    private static final String NOTIFY_TITLE = ".notifyTitle";

    private ExecutorService mThreadPool;

    private NotificationManager mNM;

    /**
     * TODO - Remove mNotifications - it's now only used as a semaphore to synchronize the multiple
     * tasks and shutdown of the Service.  However, this requires a new reminders impl (see #493).
     */
    private ConcurrentHashMap<Integer, Notification> mNotifications;

    @Override
    public void onCreate() {
        mThreadPool = Executors.newSingleThreadExecutor();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifications = new ConcurrentHashMap<Integer, Notification>();
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
            String notifyTitle = intent.getStringExtra(NOTIFY_TITLE);
            String notifyText = intent.getStringExtra(NOTIFY_TEXT);

            mThreadPool.submit(new NotifierTask(this, taskContext, uri, notifyTitle, notifyText));
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
        intent.setData(ObaContract.Trips.CONTENT_URI);
        context.startService(intent);
    }

    public static void pollTrip(Context context, Uri alertUri, long triggerTime) {
        Intent intent = new Intent(TripService.ACTION_POLL, alertUri,
                context, AlarmReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0,
                intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarm =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.set(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent);
    }

    public static void notifyTrip(Context context, Uri alertUri, String notifyTitle, String notifyText) {
        final Intent intent = new Intent(context, TripService.class);
        intent.setAction(ACTION_NOTIFY);
        intent.setData(alertUri);
        intent.putExtra(NOTIFY_TEXT, notifyText);
        intent.putExtra(NOTIFY_TITLE, notifyTitle);
        context.startService(intent);
    }

    public static String getRouteShortName(Context context, String id) {
        return UIUtils.stringForQuery(context, Uri.withAppendedPath(
                ObaContract.Routes.CONTENT_URI, id),
                ObaContract.Routes.SHORTNAME
        );
    }
}
