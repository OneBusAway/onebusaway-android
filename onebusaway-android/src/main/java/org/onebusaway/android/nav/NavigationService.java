/*
 * Copyright (C) 2005-2019 University of South Florida
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
package org.onebusaway.android.nav;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.nav.model.Path;
import org.onebusaway.android.nav.model.PathLink;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.FeedbackActivity;
import org.onebusaway.android.ui.TripDetailsListFragment;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The NavigationService is started when the user begins a trip, this service listens for location
 * updates and passes the locations to its instance of NavigationServiceProvider each time.
 * NavigationServiceProvider is responsible for computing the statuses of the trips and issuing
 * notifications/TTS messages. Once the NavigationServiceProvider is completed, the
 * NavigationService will stop itself.
 */
public class NavigationService extends Service implements LocationHelper.Listener {
    public static final String TAG = "NavigationService";

    public static final String DESTINATION_ID = ".DestinationId";
    public static final String BEFORE_STOP_ID = ".BeforeId";
    public static final String TRIP_ID = ".TripId";
    public static final String FIRST_FEEDBACK = "firstFeedback";
    public static final String KEY_TEXT_REPLY = "trip_feedback";

    public static final String LOG_DIRECTORY = "ObaNavLog";

    public static boolean mFirstFeedback = true;

    private static final int RECORDING_THRESHOLD = NavigationServiceProvider.DISTANCE_THRESHOLD + 100;

    private LocationHelper mLocationHelper = null;
    private Location mLastLocation = null;

    private String mDestinationStopId;              // Destination Stop ID
    private String mBeforeStopId;                   // Before Destination Stop ID
    private String mTripId;                         // Trip ID

    private int mCoordId = 0;

    private NavigationServiceProvider mNavProvider;
    private File mLogFile = null;

    private long mFinishedTime;

    private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting Service");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        long currentTime = System.currentTimeMillis();
        if (intent != null) {
            mDestinationStopId = intent.getStringExtra(DESTINATION_ID);
            mBeforeStopId = intent.getStringExtra(BEFORE_STOP_ID);
            mTripId = intent.getStringExtra(TRIP_ID);

            ObaContract.NavStops.insert(Application.get().getApplicationContext(), currentTime,
                    1, 1, mTripId, mDestinationStopId, mBeforeStopId);


            mNavProvider = new NavigationServiceProvider(mTripId, mDestinationStopId);
        } else {
            String[] args = ObaContract.NavStops.getDetails(Application.get().getApplicationContext(), "1");
            if (args != null && args.length == 3) {
                mTripId = args[0];
                mDestinationStopId = args[1];
                mBeforeStopId = args[2];
                mNavProvider = new NavigationServiceProvider(mTripId, mDestinationStopId, 1);

            }
        }

        // Log in anonymously via Firebase
        initAnonFirebaseLogin();

        // Setup file for logging.
        if (mLogFile == null) {
            setupLog();
        }

        if (mLocationHelper == null) {
            mLocationHelper = new LocationHelper(this, 1);
        }

        Log.d(TAG, "Requesting Location Updates");
        mLocationHelper.registerListener(this);

        Location dest = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mDestinationStopId);
        Location last = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mBeforeStopId);
        PathLink pathLink = new PathLink(currentTime, null, last, dest, mTripId);

        if (mNavProvider != null) {
            // TODO Support more than one path link
            ArrayList<PathLink> links = new ArrayList<>(1);
            links.add(pathLink);
            Path path = new Path(links);
            mNavProvider.navigate(path);
        }
        Notification notification = mNavProvider.getForegroundStartingNotification();
        startForeground(NavigationServiceProvider.NOTIFICATION_ID, notification);
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onRebind(Intent intent) {

    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying Service.");
        mLocationHelper.unregisterListener(this);
        super.onDestroy();

        // Send Broadcast
        sendBroadcast();
    }

    /**
     * Sends broadcast so that flag of destination alert is removed from trip detail screen
     */
    private void sendBroadcast() {
        Intent intent = new Intent(TripDetailsListFragment.ACTION_SERVICE_DESTROYED);
        sendBroadcast(intent);
    }

    @Override
    public synchronized void onLocationChanged(Location location) {
        Log.d(TAG, "Location Updated");
        if (mLastLocation == null) {
            mNavProvider.locationUpdated(location);
        } else if (!LocationUtils.isDuplicate(mLastLocation, location)) {
            mNavProvider.locationUpdated(location);
        }

        if (mNavProvider.mSectoCurDistance <= RECORDING_THRESHOLD) {
            writeToLog(location);
        }
        mLastLocation = location;

        // Is trip is finished? If so end service.
        if (mNavProvider.getFinished()) {
            if (mFinishedTime == 0) {
                mFinishedTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - mFinishedTime >= 30000) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics, getString(R.string.analytics_label_destination_reminder), getString(R.string.analytics_label_destination_reminder_variant_ended));
                getUserFeedback();
                stopSelf();
                setupLogCleanupTask();
            }
        }
    }

    private void initAnonFirebaseLogin() {
        mAuth = FirebaseAuth.getInstance();
        int numCores = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(numCores * 2, numCores * 2,
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        mAuth.signInAnonymously()
                .addOnCompleteListener(executor, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "signInAnonymously:success");
                    } else {
                        // Sign in failed
                        Log.w(TAG, "signInAnonymously:failure", task.getException());
                    }
                });
    }

    /**
     * Creates the log file that GPS data and navigation performance is written to - see DESTINATION_ALERTS.md
     */
    private void setupLog() {
        try {
            // Get the counter that's incremented for each test
            final String NAV_TEST_ID = getString(R.string.preference_key_nav_test_id);
            int counter = Application.getPrefs().getInt(NAV_TEST_ID, 0);
            counter++;
            PreferenceUtils.saveInt(NAV_TEST_ID, counter);

            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d yyyy, hh:mm aaa");
            String readableDate = sdf.format(Calendar.getInstance().getTime());

            File subFolder = new File(Application.get().getApplicationContext()
                    .getFilesDir().getAbsolutePath() + File.separator + LOG_DIRECTORY);

            if (!subFolder.exists()) {
                subFolder.mkdirs();
            }

            mLogFile = new File(subFolder, counter + "-" + readableDate + ".csv");

            Log.d(TAG, ":" + mLogFile.getAbsolutePath());

            Location dest = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mDestinationStopId);
            Location last = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mBeforeStopId);

            String header = String.format(Locale.US, "%s,%s,%f,%f,%s,%f,%f\n", mTripId, mDestinationStopId,
                    dest.getLatitude(), dest.getLongitude(), mBeforeStopId, last.getLatitude(), last.getLongitude());

            if (mLogFile != null) {
                FileUtils.write(mLogFile, header, false);
            } else {
                Log.e(TAG, "Failed to write to file - null file");
            }
        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    private void writeToLog(Location l) {
        try {
            String nanoTime = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                nanoTime = Long.toString(l.getElapsedRealtimeNanos());
            }

            int satellites = 0;
            if (l.getExtras() != null) {
                satellites = l.getExtras().getInt("satellites", 0);
            }

            // mGetReadyFlag =mNavProvider.getGetReady();
            //  mPullTheCordFlag = mNavProvider.getFinished();

            // TODO: Add isMockProvider
            String log = String.format(Locale.US, "%d,%s,%s,%s,%d,%f,%f,%f,%f,%f,%f,%d,%s\n",
                    mCoordId, mNavProvider.getGetReady(), mNavProvider.getFinished(), nanoTime, l.getTime(),
                    l.getLatitude(), l.getLongitude(), l.getAltitude(), l.getSpeed(),
                    l.getBearing(), l.getAccuracy(), satellites, l.getProvider());


            //Increments the id for each coordinate
            mCoordId++;

            if (mLogFile != null && mLogFile.canWrite()) {
                FileUtils.write(mLogFile, log, true);
            } else {
                Log.e(TAG, "Failed to write to file");
            }

        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    public void getUserFeedback() {
        Application app = Application.get();
        NotificationCompat.Builder mBuilder;

        String message = Application.get().getString(R.string.feedback_notify_dialog_msg);
        mFirstFeedback = Application.getPrefs().getBoolean(FIRST_FEEDBACK, true);

        // Create delete intent to set flag for snackbar creation next time the app is opened.
        Intent delIntent = new Intent(app.getApplicationContext(), FeedbackReceiver.class);
        delIntent.putExtra(FeedbackReceiver.NOTIFICATION_ID, mNavProvider.NOTIFICATION_ID + 1);

        if ((mFirstFeedback) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {

            Intent fdIntent = new Intent(app.getApplicationContext(), FeedbackActivity.class);
            fdIntent.setAction(FeedbackReceiver.ACTION_REPLY);
            fdIntent.putExtra(FeedbackActivity.RESPONSE, FeedbackActivity.FEEDBACK_NO);
            fdIntent.putExtra(FeedbackActivity.NOTIFICATION_ID, mNavProvider.NOTIFICATION_ID + 1);
            fdIntent.putExtra(FeedbackActivity.TRIP_ID, mTripId);
            fdIntent.putExtra(FeedbackActivity.LOG_FILE, mLogFile.getAbsolutePath());

            //Pending intent used to handle feedback when user taps on 'No'
            PendingIntent fdPendingIntentNo = PendingIntent.getActivity(app.getApplicationContext()
                    , 1, fdIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            fdIntent = new Intent(app.getApplicationContext(), FeedbackActivity.class);
            fdIntent.setAction(FeedbackReceiver.ACTION_REPLY);
            fdIntent.putExtra(FeedbackActivity.RESPONSE, FeedbackActivity.FEEDBACK_YES);
            fdIntent.putExtra(FeedbackActivity.NOTIFICATION_ID, mNavProvider.NOTIFICATION_ID + 1);
            fdIntent.putExtra(FeedbackActivity.TRIP_ID, mTripId);
            fdIntent.putExtra(FeedbackActivity.LOG_FILE, mLogFile.getAbsolutePath());

            //Pending intent used to handle feedback when user taps on 'Yes'
            PendingIntent fdPendingIntentYes = PendingIntent.getActivity(app.getApplicationContext()
                    , 2, fdIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            delIntent.setAction(FeedbackReceiver.ACTION_DISMISS_FEEDBACK);
            PendingIntent pDelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, delIntent, 0);

            mBuilder = new NotificationCompat.Builder(Application.get().getApplicationContext()
                    , Application.CHANNEL_DESTINATION_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(Application.get().getResources()
                            .getString(R.string.feedback_notify_title))
                    .setContentText(message)
                    .addAction(0, Application.get().getResources()
                            .getString(R.string.feedback_action_reply_no), fdPendingIntentNo)
                    .addAction(0, Application.get().getResources()
                            .getString(R.string.feedback_action_reply_yes), fdPendingIntentYes)
                    .setDeleteIntent(pDelIntent)
                    .setAutoCancel(true);
        } else {

            //Intent to handle user feedback when a user taps on 'No'
            Intent intentNo = new Intent(Application.get().getApplicationContext(), FeedbackReceiver.class);
            intentNo.setAction(FeedbackReceiver.ACTION_REPLY);
            intentNo.putExtra(FeedbackReceiver.NOTIFICATION_ID, mNavProvider.NOTIFICATION_ID + 1);
            intentNo.putExtra(FeedbackReceiver.TRIP_ID, mTripId);
            intentNo.putExtra(FeedbackReceiver.RESPONSE, FeedbackReceiver.FEEDBACK_NO);
            intentNo.putExtra(FeedbackReceiver.LOG_FILE, mLogFile.getAbsolutePath());

            //PendingIntent to handle user feedback when a user taps on 'No'
            PendingIntent fdPendingIntentNo = PendingIntent.getBroadcast(Application.get()
                    .getApplicationContext(), 100, intentNo, 0);

            String replyLabelNo = Application.get().getResources()
                    .getString(R.string.feedback_action_reply_no);

            RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel(replyLabelNo)
                    .build();

            NotificationCompat.Action replyActionNo = new NotificationCompat.Action.Builder(
                    0, replyLabelNo, fdPendingIntentNo)
                    .addRemoteInput(remoteInput)
                    .build();

            //Intent to handle user feedback when a user taps on 'Yes'
            Intent intentYes = new Intent(Application.get().getApplicationContext(), FeedbackReceiver.class);
            intentYes.setAction(FeedbackReceiver.ACTION_REPLY);
            intentYes.putExtra(FeedbackReceiver.NOTIFICATION_ID, mNavProvider.NOTIFICATION_ID + 1);
            intentYes.putExtra(FeedbackReceiver.TRIP_ID, mTripId);
            intentYes.putExtra(FeedbackReceiver.RESPONSE, FeedbackReceiver.FEEDBACK_YES);
            intentYes.putExtra(FeedbackReceiver.LOG_FILE, mLogFile.getAbsolutePath());

            //PendingIntent to handle user feedback when a user taps on 'No'
            PendingIntent fdPendingIntentYes = PendingIntent.getBroadcast(Application.get()
                    .getApplicationContext(), 101, intentYes, 0);

            String replyLabelYes = Application.get().getResources()
                    .getString(R.string.feedback_action_reply_yes);

            RemoteInput remoteInput1 = new RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel(replyLabelYes)
                    .build();

            NotificationCompat.Action replyActionYes = new NotificationCompat.Action.Builder(
                    0, replyLabelYes, fdPendingIntentYes)
                    .addRemoteInput(remoteInput1)
                    .build();

            delIntent.setAction(FeedbackReceiver.ACTION_DISMISS_FEEDBACK);
            PendingIntent pDelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, delIntent, 0);

            mBuilder = new NotificationCompat.Builder(Application.get().getApplicationContext()
                    , Application.CHANNEL_DESTINATION_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(Application.get().getResources().getString(R.string.feedback_notify_title))
                    .setContentText(message)
                    .addAction(replyActionNo)
                    .addAction(replyActionYes)
                    .setDeleteIntent(pDelIntent)
                    .setAutoCancel(true);
        }

        mBuilder.setOngoing(false);

        NotificationManager mNotificationManager = (NotificationManager)
                Application.get().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(mNavProvider.NOTIFICATION_ID + 1, mBuilder.build());

    }

    private void setupLogCleanupTask() {
        PeriodicWorkRequest.Builder cleanupLogsBuilder =
                new PeriodicWorkRequest.Builder(NavigationCleanupWorker.class, 24,
                        TimeUnit.HOURS);

        // Create the actual work object:
        PeriodicWorkRequest cleanUpCheckWork = cleanupLogsBuilder.build();

        // Then enqueue the recurring task:
        WorkManager.getInstance().enqueue(cleanUpCheckWork);
    }
}
