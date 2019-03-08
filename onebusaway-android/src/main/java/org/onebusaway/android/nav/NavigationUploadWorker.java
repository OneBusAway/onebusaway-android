package org.onebusaway.android.nav;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.onebusaway.android.app.Application;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;

public class NavigationUploadWorker extends Worker {

    public static final String TAG = "NavigationUploadWorker";

    public NavigationUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        uploadLog("yes");
        uploadLog("no");
        return Result.success();
    }

    private void uploadLog (String action) {

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        File dir = new File(Application.get().getApplicationContext().getFilesDir()
                .getAbsolutePath() + File.separator + LOG_DIRECTORY + File.separator + action);
        if (dir.exists()) {
            Log.d(TAG, "Directory exists");
            for (File lFile : dir.listFiles()) {

                Uri file = Uri.fromFile(lFile);
                String logFileName = lFile.getName();
                StorageReference logRef = storageRef.child("android/destination_reminders/" + action
                        + "/" + logFileName);
                Log.d(TAG, "Location : " + action + logFileName);

                UploadTask uploadTask = logRef.putFile(file);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.e(TAG, "Log upload failed");
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(TAG, logFileName + " uploaded successful");
                        boolean deleted = lFile.delete();
                        Log.v(TAG, logFileName + " deleted : " + deleted);
                    }
                });
            }
        }
    }
}
