package org.onebusaway.android.nav;

import android.content.Context;
import android.util.Log;

import org.onebusaway.android.app.Application;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static org.onebusaway.android.nav.NavigationService.LOG_DIRECTORY;

public class NavigationCleanupWorker extends Worker {

    public static final String TAG = "NavigationCleanupWorker";

    public NavigationCleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        cleanDir();
        return Result.success();
    }

    private void cleanDir() {
        File dir = new File(Application.get().getApplicationContext().getFilesDir()
                .getAbsolutePath() + File.separator + LOG_DIRECTORY);
        Log.d(TAG, "Directory - " + Application.get().getApplicationContext()
                .getFilesDir().getAbsolutePath() + File.separator + LOG_DIRECTORY);
        Calendar time = Calendar.getInstance();
        time.add(Calendar.HOUR_OF_DAY,-24);
        Log.d(TAG, "Time threshold - " + time.getTime().toString());

        if (dir.exists()){
            Log.d(TAG, "Directory exists");
            for (File file : dir.listFiles()) {
                Date lastModified = new Date(file.lastModified());
                Log.d(TAG, "File Last modified at - " + lastModified.toString());

                if((!file.isDirectory()) && (lastModified.before(time.getTime()))) {
                    //file is older than a day
                    file.delete();
                    Log.d(TAG, "File deleted");
                }
            }
        }
    }

}
