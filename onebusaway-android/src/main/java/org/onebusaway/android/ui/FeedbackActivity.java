package org.onebusaway.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.nav.NavigationService;
import org.onebusaway.android.nav.NavigationUploadWorker;
import org.onebusaway.android.util.PreferenceUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import static android.text.TextUtils.isEmpty;
import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;

public class FeedbackActivity extends AppCompatActivity {

    public static final String TAG = "FeedbackActivity";

    public static final String TRIP_ID = ".TRIP_ID";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";
    public static final String RESPONSE = ".RESPONSE";
    public static final String LOG_FILE = ".LOG_FILE";

    public static final int FEEDBACK_NO = 1;
    public static final int FEEDBACK_YES = 2;

    private String mUserResponse = null;
    private String mLogFile = null;
    private ImageButton dislikeButton;
    private ImageButton likeButton;

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        setTitle(getResources().getString(R.string.feedback_label));

        Intent intent = this.getIntent();
        CheckBox sendLogs = findViewById(R.id.feedback_send_logs);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        if (intent != null) {
            int response = intent.getIntExtra(RESPONSE, 0);
            mLogFile = intent.getExtras().getString(LOG_FILE);
            Log.d(TAG, "Intent LOG_FILE :" + mLogFile);
            if (response == FEEDBACK_YES) {
                mUserResponse = Application.get().getString(R.string.analytics_label_destination_reminder_yes);
            } else {
                mUserResponse = Application.get().getString(R.string.analytics_label_destination_reminder_no);
            }
            if (mUserResponse.equals(Application.get().getString(R.string.analytics_label_destination_reminder_no))) {
                Log.d(TAG, "Thumbs down tapped");
                dislikeButton = findViewById(R.id.ImageBtn_Dislike);
                dislikeButton.setSelected(true);
            } else if (mUserResponse.equals(Application.get().getString(R.string.analytics_label_destination_reminder_yes))) {
                Log.d(TAG, "Thumbs up tapped");
                likeButton = findViewById(R.id.ImageBtn_like);
                likeButton.setSelected(true);
            }
        }

        if (Application.getPrefs().getBoolean(getString(R.string.preferences_key_user_share_destination_logs), true)) {
            sendLogs.setChecked(true);
        } else {
            sendLogs.setChecked(false);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.report_issue_action, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.report_problem_send) {
            submitFeedback();
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void submitFeedback() {
        PreferenceUtils.saveBoolean(NavigationService.FIRST_FEEDBACK, false);
        Log.d(TAG, "First Feedback : " + String.valueOf(Application.getPrefs()
                .getBoolean(NavigationService.FIRST_FEEDBACK, true)));
        String feedback = ((EditText) this.findViewById(R.id.editFeedbackText)).getText().toString();
        if (Application.getPrefs()
                .getBoolean(getString(R.string.preferences_key_user_share_destination_logs), true)) {
            moveLog(feedback);
        } else {
            deleteLog();
            logFeedback(feedback);
        }
        Log.d(TAG, "Feedback send : " + feedback);
        Toast.makeText(FeedbackActivity.this,
                getString(R.string.feedback_notify_confirmation),
                Toast.LENGTH_SHORT).show();
    }

    public void likeBtnOnClick(View view) {
        mUserResponse = Application.get().getString(R.string.analytics_label_destination_reminder_yes);
        likeButton = findViewById(R.id.ImageBtn_like);
        dislikeButton = findViewById(R.id.ImageBtn_Dislike);
        likeButton.setSelected(true);
        dislikeButton.setSelected(false);
        Log.d(TAG, "Feedback changed to yes");
    }

    public void dislikeBtnOnClick(View view) {
        mUserResponse = Application.get().getString(R.string.analytics_label_destination_reminder_no);
        likeButton = findViewById(R.id.ImageBtn_like);
        dislikeButton = findViewById(R.id.ImageBtn_Dislike);
        dislikeButton.setSelected(true);
        likeButton.setSelected(false);
        Log.d(TAG, "Feedback changed to no");
    }

    private void moveLog(String feedback) {
        try {
            Log.d(TAG, "Log file: " + mLogFile);
            File lFile = new File(mLogFile);
            FileUtils.write(lFile, System.getProperty("line.separator") + feedback, true);
            Log.d(TAG, "Feedback appended");

            File destFolder = new File(Application.get().getApplicationContext().getFilesDir()
                    .getAbsolutePath() + File.separator + LOG_DIRECTORY + File.separator + mUserResponse);

            Log.d(TAG, "sourceLocation: " + lFile);
            Log.d(TAG, "targetLocation: " + destFolder);

            try {
                FileUtils.moveFileToDirectory(
                        FileUtils.getFile(lFile),
                        FileUtils.getFile(destFolder), true);
                Log.d(TAG, "Move file successful.");
            } catch (Exception e) {
                Log.e(TAG, "File move failed");
            }

            setupLogUploadTask();

        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    private void deleteLog() {
        File lFile = new File(mLogFile);
        boolean deleted = lFile.delete();
        Log.d(TAG, "Log deleted " + deleted);
    }

    public void setSendLogs(View view) {
        CheckBox checkBox = (CheckBox) view;
        if (checkBox.isChecked()) {
            if (!Application.getPrefs().getBoolean(getString(R.string.preferences_key_user_share_destination_logs), true)) {
                PreferenceUtils.saveBoolean(getString(R.string.preferences_key_user_share_destination_logs), true);
                Log.d(TAG, "User wants to share logs");
            }
        } else {
            if (Application.getPrefs().getBoolean(getString(R.string.preferences_key_user_share_destination_logs), true)) {
                PreferenceUtils.saveBoolean(getString(R.string.preferences_key_user_share_destination_logs), false);
                Log.d(TAG, "User doesn't want to share logs");
            }
        }
    }

    private void setupLogUploadTask() {
        PeriodicWorkRequest.Builder uploadLogsBuilder =
                new PeriodicWorkRequest.Builder(NavigationUploadWorker.class, 24,
                        TimeUnit.HOURS);

        // Create the actual work object
        PeriodicWorkRequest uploadCheckWork = uploadLogsBuilder.build();

        // Then enqueue the recurring task
        WorkManager.getInstance().enqueue(uploadCheckWork);
    }

    private void logFeedback(String feedbackText) {
        Boolean wasGoodReminder;
        if (mUserResponse.equals(Application.get().getString(R.string.analytics_label_destination_reminder_yes))) {
            wasGoodReminder = true;
        } else {
            wasGoodReminder = false;
        }
        ObaAnalytics.reportDestinationReminderFeedback(mFirebaseAnalytics, wasGoodReminder
                , ((!isEmpty(feedbackText)) ? feedbackText : null), null);
        Log.d(TAG, "User feedback logged to Firebase Analytics :: wasGoodReminder - "
                + wasGoodReminder + ", feedbackText - " + ((!isEmpty(feedbackText)) ? feedbackText : null));
    }
}
