/*
 * Copyright (C) 2019 University of South Florida
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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.ui.FeedbackActivity;
import org.onebusaway.android.ui.HomeActivity;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import static android.text.TextUtils.isEmpty;
import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;

public class FeedbackReceiver extends BroadcastReceiver {
    public static final String TAG = "FeedbackReceiver";
    public static final String ACTION_REPLY = BuildConfig.APPLICATION_ID + ".action.REPLY";
    public static final String ACTION_DISMISS_FEEDBACK = BuildConfig.APPLICATION_ID + ".action.DISMISS_FEEDBACK";
    public static final String TRIP_ID = ".TRIP_ID";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";
    public static final String RESPONSE = ".RESPONSE";
    public static final String LOG_FILE = ".LOG_FILE";
    public static final String SHOW_SNACKBAR = "show_snackbar";

    public static final int FEEDBACK_NO = 1;
    public static final int FEEDBACK_YES = 2;

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);

        int notifyId = intent.getIntExtra(NOTIFICATION_ID,
                NavigationServiceProvider.NOTIFICATION_ID + 1);
        //int actionNum = intent.getIntExtra(ACTION_NUM, 0);
        String action = intent.getAction();

        if (ACTION_DISMISS_FEEDBACK.equals(action)) {
            Log.d(TAG, "Dismiss intent - showing Snackbar");
            showFeedbackDismissedSnackbar(context, intent);
        } else if (ACTION_REPLY.equals(action)) {
            Log.d(TAG, "Capturing user feedback from notification");
            captureFeedback(context, intent, notifyId);
        }
    }

    private void captureFeedback(Context context, Intent intent, int notifyId) {
        int response = intent.getIntExtra(RESPONSE, 0);
        String logFile = intent.getExtras().getString(LOG_FILE);
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        String userResponse = null;

        if (remoteInput != null) {
            Log.d(TAG, "checked remoteInput ");
            String feedback = remoteInput.getCharSequence(
                    NavigationService.KEY_TEXT_REPLY).toString();

            Log.d(TAG, "Feedback captured");

            NotificationCompat.Builder repliedNotification =
                    new NotificationCompat.Builder(context
                            , Application.CHANNEL_DESTINATION_ALERT_ID)
                            .setSmallIcon(R.drawable.ic_stat_notification)
                            .setContentTitle(Application.get().getResources()
                                    .getString(R.string.feedback_notify_title))
                            .setContentText(Application.get().getResources()
                                    .getString(R.string.feedback_notify_confirmation));

            Log.d(TAG, "Thank you!");
            repliedNotification.setOngoing(false);

            NotificationManager mNotificationManager = (NotificationManager)
                    Application.get().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notifyId, repliedNotification.build());

            if (response == FEEDBACK_YES) {
                userResponse = Application.get().getString(R.string.analytics_label_destination_reminder_yes);
            } else {
                userResponse = Application.get().getString(R.string.analytics_label_destination_reminder_no);
            }

            Log.d(TAG, "cancelling notification");
            mNotificationManager.cancel(notifyId);

            Boolean pref = Application.getPrefs().getBoolean(Application.get().getResources()
                    .getString(R.string.preferences_key_user_share_destination_logs), true);

            if (pref) {
                Log.d(TAG, "True");
                moveLog(feedback, userResponse, logFile);
            } else {
                Log.d(TAG, "False");
                deleteLog(logFile);
                logFeedback(feedback, userResponse);
            }
        }
    }

    private void moveLog(String feedback, String userResponse, String logFile) {
        try {
            File lFile = new File(logFile);
            FileUtils.write(lFile, System.getProperty("line.separator") + "User Feedback - " + feedback, true);
            Log.d(TAG, "Feedback appended");

            File destFolder = new File(Application.get().getApplicationContext().getFilesDir()
                    .getAbsolutePath() + File.separator + LOG_DIRECTORY + File.separator + userResponse);

            Log.d(TAG, "sourceLocation: " + lFile);
            Log.d(TAG, "targetLocation: " + destFolder);

            try {
                FileUtils.moveFileToDirectory(
                        FileUtils.getFile(lFile),
                        FileUtils.getFile(destFolder), true);
                Log.d(TAG, "Move file successful.");
            } catch (Exception e) {
                Log.d(TAG, "File move failed");
            }

            setupLogUploadTask();

        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }

    }

    private void deleteLog(String logFile) {
        File lFile = new File(logFile);
        boolean deleted = lFile.delete();
        Log.v(TAG, "Log deleted " + deleted);
    }

    private void setupLogUploadTask() {
        PeriodicWorkRequest.Builder uploadLogsBuilder =
                new PeriodicWorkRequest.Builder(NavigationUploadWorker.class, 24,
                        TimeUnit.HOURS);

        // Create the actual work object:
        PeriodicWorkRequest uploadCheckWork = uploadLogsBuilder.build();

        // Then enqueue the recurring task:
        WorkManager.getInstance().enqueue(uploadCheckWork);
    }

    private void logFeedback(String feedbackText, String userResponse) {
        Boolean wasGoodReminder;
        if (userResponse.equals(Application.get().getString(R.string.analytics_label_destination_reminder_yes))) {
            wasGoodReminder = true;
        } else {
            wasGoodReminder = false;
        }
        ObaAnalytics.reportDestinationReminderFeedback(mFirebaseAnalytics, wasGoodReminder
                , ((!isEmpty(feedbackText)) ? feedbackText : null), null);
        Log.d(TAG, "User feedback logged to Firebase Analytics :: wasGoodReminder - "
                + wasGoodReminder + ", feedbackText - " + ((!isEmpty(feedbackText)) ? feedbackText : null));
    }

    /**
     * Shows a Snackbar when feedback notification is dismissed
     */
    private void showFeedbackDismissedSnackbar(Context context, Intent originalIntent) {
        // Get notification ID and optional extras
        int notifyId = originalIntent.getIntExtra(NOTIFICATION_ID, 
                NavigationServiceProvider.NOTIFICATION_ID + 1);
        String tripId = originalIntent.getStringExtra(TRIP_ID);
        String logFilePath = originalIntent.getStringExtra(LOG_FILE);
        
        // Launch HomeActivity to show the Snackbar
        Intent intent = new Intent(context, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(SHOW_SNACKBAR, true);
        intent.putExtra(NOTIFICATION_ID, notifyId);
        
        // Pass trip ID and log file if available
        if (tripId != null) {
            intent.putExtra(TRIP_ID, tripId);
        }
        if (logFilePath != null) {
            intent.putExtra(LOG_FILE, logFilePath);
        }
        
        context.startActivity(intent);
    }
}