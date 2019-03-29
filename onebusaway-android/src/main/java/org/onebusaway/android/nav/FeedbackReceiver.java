package org.onebusaway.android.nav;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;


public class FeedbackReceiver extends BroadcastReceiver {
    public static final String TAG = "FeedbackReceiver";
    public static final String ACTION_REPLY = BuildConfig.APPLICATION_ID + ".action.REPLY";
    public static final String ACTION_DISMISS_FEEDBACK = BuildConfig.APPLICATION_ID + ".action.DISMISS_FEEDBACK";
    public static final String TRIP_ID = ".TRIP_ID";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";
    public static final String RESPONSE = ".RESPONSE";
    public static final String LOG_FILE = ".LOG_FILE";

    public static final int FEEDBACK_NO = 1;
    public static final int FEEDBACK_YES = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");

        int notifyId = intent.getIntExtra(NOTIFICATION_ID,
                NavigationServiceProvider.NOTIFICATION_ID + 1);
        //int actionNum = intent.getIntExtra(ACTION_NUM, 0);
        String action = intent.getAction();

        if (action == ACTION_DISMISS_FEEDBACK) {
            Log.d(TAG, "Dismiss intent");
            //TODO : Create Snack bar if user dismissed feedback notification without providing feedback
        } else if (action == ACTION_REPLY) {
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

            if(response == FEEDBACK_YES) {
                userResponse = "yes";
            } else {
                userResponse = "no";
            }

            Log.d(TAG, "cancelling notification");
            mNotificationManager.cancel(notifyId);

            Boolean pref = Application.getPrefs().getBoolean(Application.get().getResources()
                    .getString(R.string.preferences_key_user_share_logs), true);

            if (pref) {
                Log.d(TAG, "True");
                moveLog(feedback, userResponse, logFile);
            } else {
                Log.d(TAG, "False");
                deleteLog(logFile);
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
            }
            catch (Exception e) {
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
        Log.v(TAG,"Log deleted " + deleted);
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

}