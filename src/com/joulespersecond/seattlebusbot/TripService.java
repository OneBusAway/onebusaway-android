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

import java.util.HashMap;
import java.util.List;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.text.format.Time;
import android.util.Log;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaArrivalInfo;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.provider.ObaContract;

// Required operations:
//
// 1. Given a TripID/StopID combination, poll OBA for the next arrival,
//        and notify at the scheduled reminder time.
//
// 2. Given a TripID/StopID combination, schedule (1) for the next trip
//        after a certain time or, if it's a one-time trip, delete it.
//
// 3. Given a trips DB, do (2) for *all* trips in the DB.
//
// 4. Remove past one-time reminders. (where days = 0 and departure < now_in_minutes)
//

public class TripService extends Service {
    public static final String TAG = "TripService";

    // Actions
    public static final String ACTION_SCHEDULE_ALL =
        "com.joulespersecond.seattlebusbot.action.SCHEDULE_ALL";
    public static final String ACTION_SCHEDULE_TRIP =
        "com.joulespersecond.seattlebusbot.action.SCHEDULE_TRIP";
    public static final String ACTION_POLL_TRIP =
        "com.joulespersecond.seattlebusbot.action.POLL_TRIP";
    public static final String ACTION_CANCEL_POLL =
        "com.joulespersecond.seattlebusbot.action.CANCEL_POLL";

    private static final long ONE_MINUTE = 60*1000;
    private static final long ONE_HOUR = 60*ONE_MINUTE;
    private static final long LOOKAHEAD_DURATION_MS = 5*ONE_MINUTE;
    // This is the amount in the past which will not consider reminders.
    private static final long SCHEDULE_BUFFER_TIME = ONE_HOUR;

    private static final String[] PROJECTION = {
        ObaContract.Trips._ID,
        ObaContract.Trips.ROUTE_ID,
        ObaContract.Trips.STOP_ID,
        ObaContract.Trips.REMINDER,
        ObaContract.Trips.DEPARTURE,
        ObaContract.Trips.DAYS
    };
    private static final int COL_ID = 0;
    private static final int COL_ROUTE_ID = 1;
    private static final int COL_STOP_ID = 2;
    private static final int COL_REMINDER = 3;
    private static final int COL_DEPARTURE = 4;
    private static final int COL_DAYS = 5;

    public static void scheduleAll(Context context) {
        final Intent tripService = new Intent(context, TripService.class);
        tripService.setAction(TripService.ACTION_SCHEDULE_ALL);
        context.startService(tripService);
    }


    // We don't want to stop the service when any particular call to onStart
    // completes: we want to stop the service when *all* tasks that have
    // been started by onStart complete.
    // So we store the startIds here as "task IDs" and each task calls
    // stopTask(), and when there are no more tasks, we call stopSelf().
    private HashMap<String,TaskBase> mActiveTasks = new HashMap<String,TaskBase>();

    @Override
    public void onStart(Intent intent, int startId) {
        UIHelp.bugHandler(this);

        final String action = intent.getAction();
        TaskBase newTask = null;
        if (ACTION_SCHEDULE_ALL.equals(action)) {
            newTask = new ScheduleAllTask();
        }
        else if (ACTION_SCHEDULE_TRIP.equals(action)) {
            // Decode the content URI.
            final Uri contentUri = intent.getData();
            if (contentUri != null) {
                newTask = new Schedule1Task(contentUri);
            }
        }
        else if (ACTION_POLL_TRIP.equals(action)) {
            // Decode the content URI.
            final Uri contentUri = intent.getData();
            if (contentUri != null) {
                newTask = new PollTask(contentUri);
            }
        }
        else if (ACTION_CANCEL_POLL.equals(action)) {
            // Decode the content URI.
            final Uri contentUri = intent.getData();
            if (contentUri != null) {
                final String taskId = PollTask.PREFIX+contentUri.toString();
                synchronized (this) {
                    TaskBase base = mActiveTasks.get(taskId);
                    if (base instanceof PollTask) {
                        ((PollTask)base).clearNotification();
                    }
                }
            }
            return;
        }
        if (newTask != null) {
            synchronized (this) {
                // If there is a task with the existing ID, then don't
                // start a new one.
                final String taskId = newTask.getTaskId();
                if (!mActiveTasks.containsKey(taskId)) {
                    Thread thr = new Thread(null, newTask, TAG);
                    thr.start();
                }
                else {
                    Log.w(TAG, "Task already started: " + taskId);
                }
            }
        }
        else {
            Log.e(TAG, "No new task -- something went wrong: " + action);
            synchronized (this) {
                if (mActiveTasks.isEmpty()) {
                    //Log.d(TAG, "Stopping service");
                    stopSelf();
                }
            }
        }
    }

    //
    // Tasks are identified by their Task ID, which is their logical equality.
    //
    abstract class TaskBase implements Runnable {
        private final String mTaskId;
        private PowerManager.WakeLock mWakeLock;
        // Keep the CPU on while we work on these tasks.

        TaskBase(String taskId) {
            mTaskId = taskId;
        }
        protected void startTask() {
            synchronized(TripService.this) {
                PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                mWakeLock.acquire();
                mActiveTasks.put(mTaskId, this);
            }
        }
        protected void endTask() {
            synchronized(TripService.this) {
                mActiveTasks.remove(mTaskId);
                if (mActiveTasks.isEmpty()) {
                    //Log.d(TAG, "Stopping service");
                    stopSelf();
                }
                mWakeLock.release();
            }
        }
        public void run() {
            //Log.d(TAG, "Starting task: " + mTaskId);
            startTask();

            runTask();

            // Done with our work...  stop the service!
            //Log.d(TAG, "Exiting task: " + mTaskId);
            endTask();
        }
        protected void runTask() {
            // Meant to be overridden.
        }
        public String getTaskId() {
            return mTaskId;
        }

        @Override
        public String toString() {
            return String.format("{Task id=%s}", mTaskId);
        }
        // Everything that identifies the task *MUST* be specified in the TaskID.
        @Override
        public final boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof TaskBase)) {
                return false;
            }
            TaskBase task = (TaskBase)obj;
            return mTaskId.equals(task.mTaskId);
        }
        @Override
        public final int hashCode() {
            int result = 33;
            result = 31 * result + mTaskId.hashCode();
            return result;
        }
    }

    final class Schedule1Task extends TaskBase {
        public static final String PREFIX = "Schedule1:";
        private final Uri mUri;

        Schedule1Task(Uri uri) {
            super(PREFIX+uri.toString());
            mUri = uri;
        }
        @Override
        protected void runTask() {
            ContentResolver cr = getContentResolver();
            Cursor c = cr.query(mUri, PROJECTION, null, null, null);

            // Schedule will close the cursor
            Schedule(TripService.this, c);
        }
    }

    final class ScheduleAllTask extends TaskBase {
        public static final String PREFIX = "ScheduleAll:";
        ScheduleAllTask() {
            super(PREFIX);
        }
        @Override
        protected void runTask() {
            ContentResolver cr = getContentResolver();
            Cursor c = cr.query(
                    ObaContract.Trips.CONTENT_URI,
                    PROJECTION, null, null, null);

            // Schedule will close the cursor
            Schedule(TripService.this, c);
        }
    }

    final String getNotifyText(Cursor c, long timeDiff) {
        final String routeName = getRouteShortName(this, c.getString(COL_ROUTE_ID));

        if (timeDiff <= 0) {
            return getString(R.string.trip_stat_gone, routeName);
        }
        else if (timeDiff < ONE_MINUTE) {
            return getString(R.string.trip_stat_lessthanone, routeName);
        }
        else if (timeDiff < ONE_MINUTE*2) {
            return getString(R.string.trip_stat_one, routeName);
        }
        else {
            return getString(R.string.trip_stat, routeName, (int)(timeDiff/ONE_MINUTE));
        }
    }

    final class PollTask extends TaskBase {
        public static final String PREFIX = "Poll:";
        private final Uri mUri;
        private final String mTripId;
        private final String mStopId;
        // Notification should only be used during the task thread.
        private Notification mNotification = null;
        private PendingIntent mNotificationIntent = null;
        // This can be set in another thread.
        private int mState = UNINIT;

        PollTask(Uri uri) {
            super(PREFIX+uri.toString());
            mUri = uri;
            List<String> segments = uri.getPathSegments();
            mTripId = segments.get(1);
            mStopId = segments.get(2);
        }

        // These are states for the doPoll loop.
        private static final int UNINIT = 0;
        private static final int NOT_FOUND = 1;
        private static final int FOUND = 2;
        private static final int DONE = 3;

        synchronized void clearNotification() {
            //Log.d(TAG, "Clearing notification: " + getTaskId());
            mNotification = null;
            mState = DONE;
        }

        private static final int NOTIFY_ID = 1;

        // This is only every called within the synchronized block of
        // checkArrivals, so it doesn't have to be synchronized itself.
        final void doNotification(long timeDiff, Cursor c) {
            final Context ctx = TripService.this;

            if (mNotification == null) {
                //Log.d(TAG, "Creating notification for trip: " + getTaskId());
                mNotification = new Notification(R.drawable.stat_trip, null, System.currentTimeMillis());
                mNotification.defaults = Notification.DEFAULT_SOUND;
                mNotification.flags = Notification.FLAG_ONLY_ALERT_ONCE|
                                    Notification.FLAG_SHOW_LIGHTS;
                mNotification.ledOnMS = 1000;
                mNotification.ledOffMS = 1000;
                mNotification.ledARGB = 0xFF00FF00;
                mNotification.vibrate = new long[] {
                        0,    // on
                        1000, // off
                        1000, // on
                        1000, // off
                        1000, // on
                        1000, // off
                        };

                Intent deleteIntent = new Intent(ctx, TripService.class);
                deleteIntent.setAction(ACTION_CANCEL_POLL);
                deleteIntent.setData(mUri);
                mNotification.deleteIntent = PendingIntent.getService(ctx,
                        0, deleteIntent, PendingIntent.FLAG_ONE_SHOT);

                mNotificationIntent = PendingIntent.getActivity(ctx,
                        0, StopInfoActivity.makeIntent(ctx, mStopId),
                        PendingIntent.FLAG_ONE_SHOT);
            }

            final String title = getString(R.string.app_name);

            mNotification.setLatestEventInfo(ctx, title,
                    getNotifyText(c, timeDiff), mNotificationIntent);
            NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFY_ID, mNotification);
        }

        // This returns 'true' if we need to break out of the poll loop.
        final boolean checkArrivals(ObaResponse response,
                Cursor c, long reminderMS, long now) {
            final String tripId = mTripId;
            final ObaArray<ObaArrivalInfo> arrivals
                    = response.getData().getArrivalsAndDepartures();
            final int length = arrivals.length();
            for (int i=0; i < length; ++i) {
                ObaArrivalInfo info = arrivals.get(i);
                if (tripId.equals(info.getTripId())) {
                    if (mState == NOT_FOUND) {
                        //Log.d(TAG, "Found trip: " + getTaskId());
                        mState = FOUND;
                    }
                    // We found the trip. We notify when the reminder time
                    // when calculated with the *predicted* arrival time
                    // is past now.
                    long depart = info.getPredictedArrivalTime();
                    if (depart == 0) {
                        depart = info.getScheduledArrivalTime();
                    }

                    // This is the difference in time between now
                    // and when when the bus leaves.
                    final long timeDiff = depart-now;
                    if (timeDiff <= 0) {
                        // Bus has left. Do one last notification and bail.
                        // Note: only notify if we have already notified.
                        if (mNotification != null) {
                            doNotification(timeDiff, c);
                        }
                        mState = DONE;
                        return true;
                    }
                    else if (timeDiff <= reminderMS) {
                        // Within the reminder time. Do the first notification
                        // or continue to notify, but remain in the loop.
                        doNotification(timeDiff, c);
                    }
                    break;
                }
            }
            return false;
        }

        final void doPoll(Cursor c) {
            final String stopId = mStopId;

            // Add one so we notify at least that many minutes before the reminder time --
            // it's better to be half a minute early than half a minute late.
            final long reminderMS = (1+c.getInt(COL_REMINDER))*ONE_MINUTE;
            final long departMS = c.getInt(COL_DEPARTURE)*ONE_MINUTE;

            // This is OK, since we don't expect anything other than this thread
            // to be accessing this before we have notified someone.
            mState = NOT_FOUND;

            while (mState != DONE) {
                //Log.d(TAG, "Get arrivals/departures: " + getTaskId());
                ObaResponse response =
                        ObaApi.getArrivalsDeparturesForStop(TripService.this, stopId);
                synchronized (this) {
                    // First check to see if we were marked as DONE while
                    // getArrivalsDeparturesForStop was running
                    if (mState == DONE) {
                        break;
                    }
                    final long now = System.currentTimeMillis();

                    if (response.getCode() == ObaApi.OBA_OK) {
                        if (checkArrivals(response, c, reminderMS, now)) {
                            break;
                        }
                    }
                    // If we haven't found the trip, then give up after
                    // 10 minutes past the scheduled departure time.
                    if (mState == NOT_FOUND && ((departMS+LOOKAHEAD_DURATION_MS) > now)) {
                        // Give up.
                        //Log.d(TAG, "Giving up: " + getTaskId());
                        mState = DONE;
                        break;
                    }
                }
                try {
                    Thread.sleep(30*1000);
                } catch (InterruptedException e) {
                    // Okay...
                }
            }
        }

        // This schedules the trip service to schedule the next
        // trip in this series.
        final void scheduleNext(Cursor c) {
            final int days = c.getInt(COL_DAYS);
            if (days == 0) {
                // This was a one-shot timer. Delete it.
                ContentResolver cr = getContentResolver();
                cr.delete(mUri, null, null);
            }
            else {
                TripService ctx = TripService.this;
                Intent myIntent =
                    new Intent(ACTION_SCHEDULE_TRIP, mUri, ctx, AlarmReceiver.class);

                PendingIntent alarmIntent =
                    PendingIntent.getBroadcast(ctx, 0, myIntent, PendingIntent.FLAG_ONE_SHOT);

                AlarmManager alarm = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

                long triggerTime = System.currentTimeMillis() + SCHEDULE_BUFFER_TIME + 2*ONE_MINUTE;
                alarm.set(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent);
            }
        }

        @Override
        protected void runTask() {
            ContentResolver cr = getContentResolver();
            Cursor c = cr.query(mUri, PROJECTION, null, null, null);

            if (c != null && c.moveToFirst()) {
                doPoll(c);
                scheduleNext(c);
            }
            else {
                Log.e(TAG, "No such trip");
            }
            if (c != null) {
                c.close();
            }
        }
    }

    static void Schedule(Context context, Cursor c) {
        if (c != null) {
            try {
                Time tNow = new Time();
                tNow.setToNow();
                final long now = tNow.toMillis(false);

                while (c.moveToNext()) {
                    Schedule1(context, c, tNow, now);
                }
            }
            finally {
                c.close();
            }
        }
    }

    // When we schedule a poll, and we rely on a couple of things to
    // keep things from going awry:
    // 1. Scheduling an alarm with the same intent and trigger time will
    // replace the old event with the new;
    // 2. If a task is started with the same taskID (which is determined
    // by what's in the intent) then it won't be started again.
    // 3. Also, we never schedule a poll that's an hour in the past, since
    // we expect the bus has left by then.
    static void Schedule1(Context context, Cursor c, Time tNow, long now) {
        final int departureMins = c.getInt(COL_DEPARTURE);
        final long reminderMS = c.getInt(COL_REMINDER)*ONE_MINUTE;
        if (reminderMS == 0) {
            return;
        }
        final int days = c.getInt(COL_DAYS);
        long remindTime = 0;
        long triggerTime = 0;
        if (days == 0) {
            remindTime = ObaContract.Trips.convertDBToTime(departureMins) - reminderMS;
        }
        else {
            final int currentWeekDay = tNow.weekDay;
            for (int i=0; i < 7; ++i) {
                final int day = (currentWeekDay+i)%7;
                final int bit = ObaContract.Trips.getDayBit(day);
                if ((days & bit) == bit) {
                    Time tmp = new Time();
                    tmp.set(0, departureMins, 0, tNow.monthDay+i, tNow.month, tNow.year);
                    tmp.normalize(false);
                    remindTime = tmp.toMillis(false) - reminderMS;
                    triggerTime = remindTime - LOOKAHEAD_DURATION_MS;
                    // Ignore anything that has a trigger time more than
                    // one hour in the past.
                    if ((now-triggerTime) < ONE_HOUR) {
                        break;
                    }
                }
            }
            if (remindTime == 0) {
                // Not found?
                return;
            }
        }
        final String tripId = c.getString(COL_ID);
        final String stopId = c.getString(COL_STOP_ID);

        final Uri uri = ObaContract.Trips.buildUri(tripId, stopId);
        Time tmp = new Time();
        tmp.set(triggerTime);
        //Log.d(TAG, "Scheduling poll: " + uri.toString() + "  " + tmp.format2445());

        Intent myIntent = new Intent(ACTION_POLL_TRIP, uri, context, AlarmReceiver.class);
        PendingIntent alarmIntent =
            PendingIntent.getBroadcast(context, 0, myIntent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.set(AlarmManager.RTC_WAKEUP, triggerTime, alarmIntent);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private final IBinder mBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply,
                int flags) throws RemoteException {
            return super.onTransact(code, data, reply, flags);
        }
    };

    //
    // Trip helpers
    //
    public static String getRouteShortName(Context context, String id) {
        return UIHelp.stringForQuery(context,
                Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, id),
                ObaContract.Routes.SHORTNAME);
    }
}
