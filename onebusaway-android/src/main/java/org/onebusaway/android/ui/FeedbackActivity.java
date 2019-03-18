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

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.nav.NavigationService;
import org.onebusaway.android.nav.NavigationServiceProvider;
import org.onebusaway.android.util.PreferenceUtils;

import java.io.File;
import java.io.IOException;

import androidx.appcompat.app.AppCompatActivity;

import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;

public class FeedbackActivity extends AppCompatActivity {

    public static final String TAG = "FeedbackActivity";

    private String mAction = null;
    private ImageButton dislikeButton;
    private ImageButton likeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        setTitle(getResources().getString(R.string.feedback_label));

        Intent intent = this.getIntent();
        CheckBox sendLogs = (CheckBox) findViewById(R.id.feedback_send_logs);
        if (intent != null) {
            mAction = intent.getExtras().getString("CallingAction");
            if (mAction.equals("no")) {
                Log.d(TAG, "Thumbs down tapped");
                dislikeButton = findViewById(R.id.ImageBtn_Dislike);
                dislikeButton.setSelected(true);
            } else if (mAction.equals("yes")) {
                Log.d(TAG, "Thumbs up tapped");
                likeButton = findViewById(R.id.ImageBtn_like);
                likeButton.setSelected(true);
            }
        }

        if (Application.getPrefs()
                .getBoolean(getString(R.string.preferences_key_user_share_logs), true)) {
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
        PreferenceUtils.saveBoolean(NavigationServiceProvider.FIRST_FEEDBACK, false);
        Log.d(TAG, "First Feedback : " + String.valueOf(Application.getPrefs()
                .getBoolean(NavigationServiceProvider.FIRST_FEEDBACK, true)));
        String feedback = ((EditText) this.findViewById(R.id.editFeedbackText)).getText().toString();
        if (Application.getPrefs()
                .getBoolean(getString(R.string.preferences_key_user_share_logs), true)) {
            moveLog(feedback);
        } else {
            deleteLog();
        }
        Log.d(TAG,"Feedback send : " + feedback);
        Toast.makeText(FeedbackActivity.this,
                getString(R.string.feedback_notify_confirmation),
                Toast.LENGTH_SHORT).show();
    }

    public void likeBtnOnClick(View view) {
        mAction = "yes";
        likeButton = findViewById(R.id.ImageBtn_like);
        dislikeButton = findViewById(R.id.ImageBtn_Dislike);
        likeButton.setSelected(true);
        dislikeButton.setSelected(false);
        Log.d(TAG,"Feedback changed to yes");
    }

    public void dislikeBtnOnClick(View view) {
        mAction = "no";
        likeButton = findViewById(R.id.ImageBtn_like);
        dislikeButton = findViewById(R.id.ImageBtn_Dislike);
        dislikeButton.setSelected(true);
        likeButton.setSelected(false);
        Log.d(TAG,"Feedback changed to no");
    }

    private void moveLog(String feedback) {
        try {
            File lFile = new File(NavigationService.LOG_FILE);
            FileUtils.write(lFile, System.getProperty("line.separator") + "User Feedback - " + feedback, true);
            Log.d(TAG, "Feedback appended");

            File destFolder = new File(Application.get().getApplicationContext().getFilesDir()
                    .getAbsolutePath() + File.separator + LOG_DIRECTORY + File.separator + mAction);

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

        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }

    }

    private void deleteLog() {
        File lFile = new File(NavigationService.LOG_FILE);
        boolean deleted = lFile.delete();
        Log.d(TAG,"Log deleted " + deleted);
    }

    public void setSendLogs(View view) {
        CheckBox checkBox = (CheckBox)view;
        if (checkBox.isChecked()) {
            if (!Application.getPrefs().getBoolean(getString(R.string.preferences_key_user_share_logs), true)) {
                PreferenceUtils.saveBoolean(getString(R.string.preferences_key_user_share_logs), true);
                Log.d(TAG,"User wants to share logs");
            }
        } else {
            if (Application.getPrefs().getBoolean(getString(R.string.preferences_key_user_share_logs), true)) {
                PreferenceUtils.saveBoolean(getString(R.string.preferences_key_user_share_logs), false);
                Log.d(TAG,"User doesn't want to share logs");
            }
        }
    }
}
