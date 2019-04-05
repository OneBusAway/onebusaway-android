package org.onebusaway.android.nav;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static android.text.TextUtils.isEmpty;
import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;

public class NavigationUploadWorker extends Worker {

    public static final String TAG = "NavigationUploadWorker";

    private FirebaseAnalytics mFirebaseAnalytics;

    public NavigationUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        uploadLog("yes");
        uploadLog("no");
        return Result.success();
    }

    private void uploadLog (String response) {

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        File dir = new File(Application.get().getApplicationContext().getFilesDir()
                .getAbsolutePath() + File.separator + LOG_DIRECTORY + File.separator + response);
        if (dir.exists()) {
            Log.d(TAG, "Directory exists");
            for (File lFile : dir.listFiles()) {
                Uri file = Uri.fromFile(lFile);
                String logFileName = lFile.getName();
                StorageReference logRef = storageRef.child("android/destination_reminders/" + response
                        + "/" + logFileName);
                Log.d(TAG, "Location : " + response + logFileName);

                String sCurrentLine, feedbackText = "";;
                try {
                    BufferedReader br = new BufferedReader(new FileReader(lFile.getAbsolutePath()));
                    while ((sCurrentLine = br.readLine()) != null) {
                        System.out.println(sCurrentLine);
                        feedbackText = sCurrentLine;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                StorageMetadata metadata = new StorageMetadata.Builder()
                        .setCustomMetadata("Response", response)
                        .setCustomMetadata("FeedbackText", feedbackText)
                        .build();

                UploadTask uploadTask = logRef.putFile(file, metadata);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.e(TAG, "Log upload failed");
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG, logFileName + " uploaded successful");
                        String userResponse = taskSnapshot.getMetadata().getCustomMetadata("Response");
                        String feedbackText = taskSnapshot.getMetadata().getCustomMetadata("FeedbackText");
                        String fileURL =  taskSnapshot.getStorage().getDownloadUrl().toString();
                        Log.d(TAG, "Response - " + userResponse);
                        Log.d(TAG, "FeedbackText - " + feedbackText);
                        Log.d(TAG, "Download URL - " + fileURL);
                        logFeedback(feedbackText, userResponse, fileURL);
                        boolean deleted = lFile.delete();
                        Log.v(TAG, logFileName + " deleted : " + deleted);
                    }
                });
            }
        }
    }

    private void logFeedback(String feedbackText, String userResponse, String fileName) {
        Boolean wasGoodReminder;
        if (userResponse.equals("yes")) {
            wasGoodReminder = true;
        } else {
            wasGoodReminder = false;
        }
        ObaAnalytics.reportDestinationReminderFeedback(mFirebaseAnalytics, wasGoodReminder
                , ((!isEmpty(feedbackText))? feedbackText : null), fileName);
        Log.d (TAG, "User feedback logged to Firebase Analytics :: wasGoodReminder - "
                + wasGoodReminder + ", feedbackText - " + ((!isEmpty(feedbackText))? feedbackText : null) + ", filename - " + fileName);
    }
}
