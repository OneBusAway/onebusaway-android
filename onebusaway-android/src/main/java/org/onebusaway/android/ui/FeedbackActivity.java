package org.onebusaway.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.nav.NavigationService;
import org.onebusaway.android.nav.NavigationServiceProvider;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class FeedbackActivity extends AppCompatActivity {

    public static final String TAG = "FeedbackActivity";

    private String mAction = null;
    private boolean mSendLogs = false;
    private ImageButton dislikeButton;
    private ImageButton likeButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        setTitle(getResources().getString(R.string.feedback_label));

        Intent intent = this.getIntent();
        if(intent != null){
            mAction = intent.getExtras().getString("CallingAction");
            if(mAction.equals("Dislike")){
                Log.d(TAG, "Thumbs down tapped");
                dislikeButton = findViewById(R.id.ImageBtn_Dislike);
                dislikeButton.setSelected(true);

            }
            else if(mAction.equals("Like")){
                Log.d(TAG, "Thumbs up tapped");
                likeButton = findViewById(R.id.ImageBtn_like);
                likeButton.setSelected(true);
            }
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
       }
       return super.onOptionsItemSelected(item);
    }

    private void submitFeedback() {
        NavigationServiceProvider.mFirstFeedback = false;
        String feedback = ((EditText) this.findViewById(R.id.editFeedbackText)).getText().toString();
        if (mSendLogs) {
            uploadLog(feedback);
        }
        else {
            deleteLog();
        }
        Log.d(TAG,"Feedback send :" + feedback);
    }

    public void likeBtnOnClick(View view) {
        mAction = "Like";
        likeButton = findViewById(R.id.ImageBtn_like);
        dislikeButton = findViewById(R.id.ImageBtn_Dislike);
        likeButton.setSelected(true);
        dislikeButton.setSelected(false);
        Log.d(TAG,"Like");
    }

    public void dislikeBtnOnClick(View view) {
        mAction = "Dislike";
        likeButton = findViewById(R.id.ImageBtn_like);
        dislikeButton = findViewById(R.id.ImageBtn_Dislike);
        dislikeButton.setSelected(true);
        likeButton.setSelected(false);
        Log.d(TAG,"Feedback changed to dislike");
    }

    private void uploadLog(String feedback) {
        try {
            File lFile = new File(NavigationService.LOG_FILE); //"/data/data/com.joulespersecond.seattlebusbot/files/ObaNavLog/1-Thu, Jan 24 2019, 12:29 PM.csv");
            FileUtils.write(lFile, feedback, true);
            Log.d(TAG, "Feedback appended");

            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference();

            Uri file = Uri.fromFile(lFile);
            String logFileName = lFile.getName();
            StorageReference logRef = storageRef.child(mAction + "/" + logFileName);
            Log.d(TAG, "Location : " + mAction + "/" + logFileName);

            UploadTask uploadTask = logRef.putFile(file);
            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Log.e(TAG, "Log upload failed");
                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Log.d(TAG, "Log upload successful");
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }

    }

    private void deleteLog() {
        File lFile = new File(NavigationService.LOG_FILE);
        boolean deleted = lFile.delete();
        Log.v(TAG,"Log deleted " + deleted);
    }

    public void setSendLogs(View view) {
        CheckBox checkBox = (CheckBox)view;
        if(checkBox.isChecked()){
            mSendLogs = true;
        }
        else {
            mSendLogs = false;
        }
    }
}
