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
package org.onebusaway.android.travelbehavior.io.worker;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.model.DestinationReminderData;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorFirebaseIOUtils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DestinationReminderReaderWorker extends Worker {

    private static final String TAG = "DestRemindReadWorker";

    public DestinationReminderReaderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        readAndPostDestinationReminderData();
        return Result.success();
    }

    private void readAndPostDestinationReminderData() {
        try {
            File subFolder = new File(Application.get().getApplicationContext()
                    .getFilesDir().getAbsolutePath() + File.separator +
                    TravelBehaviorConstants.LOCAL_DESTINATION_REMINDER_FOLDER);

            // If the directory does not exist do not read
            if (subFolder == null || !subFolder.isDirectory()) return;

            Collection<File> files = FileUtils.listFiles(subFolder, TrueFileFilter.INSTANCE,
                    TrueFileFilter.INSTANCE);
            Gson gson = new Gson();
            if (files != null && !files.isEmpty()) {
                List<DestinationReminderData> l = new ArrayList<>();
                for (File f : files) {
                    try {
                        String jsonStr = FileUtils.readFileToString(f);
                        DestinationReminderData destinationReminderData =
                                gson.fromJson(jsonStr, DestinationReminderData.class);
                        l.add(destinationReminderData);
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                }

                FileUtils.cleanDirectory(subFolder);

                String uid = getInputData().getString(TravelBehaviorConstants.USER_ID);
                String recordId = getInputData().getString(TravelBehaviorConstants.RECORD_ID);

                TravelBehaviorFirebaseIOUtils.saveDestinationReminders(l, uid, recordId);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
