package org.onebusaway.android.nav;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;


public class FeedbackReceiver extends BroadcastReceiver {
    public static final String TAG = "FeedbackReceiver";

    public static final String TRIP_ID = ".TRIP_ID";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";
    public static final String CALLING_ACTION = ".CALLING_ACTION";

    public static final int FEEDBACK_NO = 1;
    public static final int FEEDBACK_YES = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");

        int notifyId = intent.getIntExtra(NOTIFICATION_ID,
                NavigationServiceProvider.NOTIFICATION_ID + 1);
        int actionNum = intent.getIntExtra(CALLING_ACTION, 0);
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        String callingAction = null;

        if (remoteInput != null) {
            Log.d(TAG, "checked remoteInput ");
            String feedback = remoteInput.getCharSequence(
                    NavigationServiceProvider.KEY_TEXT_REPLY).toString();

            Log.d(TAG, "Feedback captured");

            //Cancel notification
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                NotificationManager manager = (NotificationManager) Application.get()
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                Log.d(TAG, "stop notification");
                manager.cancel(notifyId);
                Handler h = new Handler();
                long delayInMilliseconds = 5000;
                h.postDelayed(new Runnable() {
                    public void run() {
                        manager.cancel(notifyId);
                    }
                }, delayInMilliseconds);
            }

            NotificationCompat.Builder repliedNotification =
                    new NotificationCompat.Builder(context
                            , Application.CHANNEL_DESTINATION_ALERT_ID)
                            .setSmallIcon(R.drawable.ic_stat_notification)
                            .setContentTitle(Application.get().getResources().getString(R.string.feedback_notify_title))
                            .setContentText(Application.get().getResources().getString(R.string.feedback_notify_confirmation));

            Log.d(TAG, "Thank you!");
            repliedNotification.setOngoing(false);

            NotificationManager mNotificationManager = (NotificationManager)
                    Application.get().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notifyId, repliedNotification.build());

            if(actionNum == FEEDBACK_YES) {
                callingAction = "Like";
            }
            else {
                callingAction = "Dislike";
            }

            Log.d(TAG, "cancelling notification");
            mNotificationManager.cancel(notifyId);

            uploadLog(feedback, callingAction);

        }

    }

    private void uploadLog(String feedback, String action) {
        try {
            File lFile = new File(NavigationService.LOG_FILE); //"/data/data/com.joulespersecond.seattlebusbot/files/ObaNavLog/1-Tue, Jan 22 2019, 11:30 AM.csv");
            FileUtils.write(lFile, feedback, true);

            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference();

            Uri file = Uri.fromFile(lFile);
            String logFileName = lFile.getName();
            StorageReference logRef = storageRef.child(action + "/" + logFileName);

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

}
