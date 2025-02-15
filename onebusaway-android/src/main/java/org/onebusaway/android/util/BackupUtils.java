/*
 * Copyright (C) 2018 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.PlausibleAnalytics;
import org.onebusaway.android.io.backup.Backup;
import org.onebusaway.android.region.ObaRegionsTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.onebusaway.android.ui.PreferencesActivity.REQUEST_CODE_RESTORE_BACKUP;
import static org.onebusaway.android.ui.PreferencesActivity.REQUEST_CODE_SAVE_BACKUP;

public class BackupUtils {
    private static final String TAG = "BackupUtils";

    /**
     * Restores a backed up OBA database from storage.
     * @param activityContext Activity context (used for permission check)
     * @param uri URI to the backup file, as returned by the system UI picker. Following targeting
     *      Android 11 we can't access this directory and need to rely on the system UI picker.
     */
    public static void restore(Context activityContext, Uri uri) {
        //
        // Because this is a destructive operation, we should warn the user.
        //
        AlertDialog dialog = new AlertDialog.Builder(activityContext)
                .setMessage(R.string.preferences_db_restore_warning)
                .setPositiveButton(android.R.string.ok, (dialog12, which) -> {
                    dialog12.dismiss();
                    doRestore(activityContext, uri);
                })
                .setNegativeButton(android.R.string.cancel, (dialog1, which) -> dialog1.dismiss())
                .create();
        dialog.show();
    }

    /**
     *
     * @param activityContext
     * @param uri URI to the backup file, as returned by the system UI picker. Following targeting
     *      Android 11 we can't access this directory and need to rely on the system UI picker.
     */
    static private void doRestore(Context activityContext, Uri uri) {
        final Context context = Application.get().getApplicationContext();
        ObaAnalytics.reportUiEvent(FirebaseAnalytics.getInstance(activityContext),
                Application.get().getPlausibleInstance(),
                PlausibleAnalytics.REPORT_BACKUP_EVENT_URL,
                context.getString(R.string.analytics_label_button_press_restore_preference),
                null);
        try {
            Backup.restore(context, uri);

            if (activityContext != null) {
                List<ObaRegionsTask.Callback> callbacks = new ArrayList<>();
                callbacks.add(currentRegionChanged -> Toast.makeText(context,
                        R.string.preferences_db_restored,
                        Toast.LENGTH_LONG).show());
                ObaRegionsTask task = new ObaRegionsTask(activityContext, callbacks, true, true);
                task.setProgressDialogMessage(context.getString(R.string.preferences_restore_loading));
                task.execute();
            }
        } catch (IOException e) {
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_restore_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Creates a backup of the current OBA database on local storage.
     * @param uri The URI representing the location where the backup file should be saved.
     * @param activityContext context of the calling activity (used to check permissions)
     */
    public static void save(Context activityContext,Uri uri) {
        Context context = Application.get().getApplicationContext();
        ObaAnalytics.reportUiEvent(FirebaseAnalytics.getInstance(activityContext),
                Application.get().getPlausibleInstance(),
                PlausibleAnalytics.REPORT_BACKUP_EVENT_URL,
                context.getString(R.string.analytics_label_button_press_save_preference),
                null);
        try {
            Backup.backup(context,uri);
        } catch (IOException e) {
            Log.d(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    /**
     * Launches an intent to create a backup file with the specified file name.
     * The user will be prompted to choose a location to save the backup file.
     *
     * @param activity The activity that triggers the file creation process.
     */
    public static void createBackupFile(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Restricts file type to binary files
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, Backup.FILE_NAME);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        activity.startActivityForResult(intent, REQUEST_CODE_SAVE_BACKUP);
    }

    /**
     * Launches an intent to allow the user to select a backup file for restoration.
     * Only binary files (with the .bin extension) can be selected.
     *
     * @param activity The activity that triggers the file selection process.
     */
    public static void selectBackupFile(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Restricts file type to binary files
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "backup.bin");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        activity.startActivityForResult(intent, REQUEST_CODE_RESTORE_BACKUP);
    }


}
