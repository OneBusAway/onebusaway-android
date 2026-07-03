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

package org.onebusaway.android.backup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.onebusaway.android.R;
import org.onebusaway.android.app.di.AnalyticsEntryPoint;
import org.onebusaway.android.analytics.PlausibleAnalytics;

import java.io.IOException;
import java.util.Objects;

import androidx.appcompat.app.AlertDialog;

public class BackupUtils {
    private static final String TAG = "BackupUtils";

    /**
     * Restores a backed up OBA database from storage.
     * @param activityContext Activity context (used for permission check)
     * @param uri URI to the backup file, as returned by the system UI picker. Following targeting
     *      Android 11 we can't access this directory and need to rely on the system UI picker.
     */
    public static void restore(Context activityContext, Uri uri, Runnable onRestored) {
        //
        // Because this is a destructive operation, we should warn the user.
        //
        AlertDialog dialog = new MaterialAlertDialogBuilder(activityContext)
                .setMessage(R.string.preferences_db_restore_warning)
                .setPositiveButton(android.R.string.ok, (dialog12, which) -> {
                    dialog12.dismiss();
                    doRestore(activityContext, uri, onRestored);
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
    static private void doRestore(Context activityContext, Uri uri, Runnable onRestored) {
        final Context context = activityContext.getApplicationContext();
        AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_BACKUP_EVENT_URL,
                context.getString(R.string.analytics_label_button_press_restore_preference),
                null);
        try {
            boolean restartRequired = Backup.restore(context, uri);
            if (restartRequired) {
                // A Room-format backup swapped the database file, leaving Hilt holding the old, closed
                // AppDatabase singleton (and every repository that captured a DAO from it). Relaunch the
                // app so the whole object graph rebuilds against the restored file; the fresh start
                // re-resolves the region, so onRestored isn't needed on this path.
                restartApp(context);
                return;
            }
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_restored,
                            context.getString(R.string.app_name)),
                    Toast.LENGTH_LONG).show();

            // Re-resolve the region on the Compose home surface (raises the picker if it's ambiguous),
            // in case the restored data implies a different region.
            if (onRestored != null) {
                onRestored.run();
            }
        } catch (IOException e) {
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_restore_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Relaunches the app in a fresh process. Used after a Room-format backup restore swaps the database
     * file: the Hilt-scoped {@link org.onebusaway.android.database.AppDatabase} singleton (and every
     * repository that captured a DAO from it) still points at the old, now-closed instance, and Hilt
     * can't evict a singleton — a process restart is the reliable way to rebuild the graph. The activity
     * start is handed to the system before the process dies, so it relaunches into a clean process.
     */
    private static void restartApp(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch != null && launch.getComponent() != null) {
            context.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
        }
        Runtime.getRuntime().exit(0);
    }

    /**
     * Creates a backup of the current OBA database on local storage.
     * @param uri The URI representing the location where the backup file should be saved.
     * @param activityContext context of the calling activity (used to check permissions)
     */
    public static void save(Context activityContext,Uri uri) {
        Context context = activityContext.getApplicationContext();
        AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_BACKUP_EVENT_URL,
                context.getString(R.string.analytics_label_button_press_save_preference),
                null);
        Backup.backup(context, uri);
    }

    /**
     * Builds the intent that prompts the user to choose a location to save a backup file (with the
     * default file name). Launched via an {@code ActivityResultLauncher}; the picked save URI is
     * passed back to {@link #save(Context, Uri)}.
     */
    public static Intent buildCreateBackupFileIntent() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Restricts file type to binary files
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, Backup.FILE_NAME);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        return intent;
    }

    /**
     * Builds the intent that lets the user select a backup file to restore. Launched via an
     * {@code ActivityResultLauncher}; the picked URI is passed back to {@link #restore(Context, Uri)}.
     */
    public static Intent buildSelectBackupFileIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Restricts file type to binary files
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "backup.bin");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        return intent;
    }


}
