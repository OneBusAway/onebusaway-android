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

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.backup.Backup;
import org.onebusaway.android.region.ObaRegionsTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.onebusaway.android.util.PermissionUtils.STORAGE_PERMISSIONS;

public class BackupUtils {

    /**
     * Restores a backed up OBA database from storage.  If storage permission hasn't been granted
     * this method is a no-op.
     * @param activityContext Activity context (used for permission check)
     */
    public static void restore(Context activityContext) {
        if (!PermissionUtils.hasGrantedPermissions(activityContext, STORAGE_PERMISSIONS)) {
            // Let the PreferenceActivity request permissions from the user first
            return;
        }
        //
        // Because this is a destructive operation, we should warn the user.
        //
        AlertDialog dialog = new AlertDialog.Builder(activityContext)
                .setMessage(R.string.preferences_db_restore_warning)
                .setPositiveButton(android.R.string.ok, (dialog12, which) -> {
                    dialog12.dismiss();
                    doRestore(activityContext);
                })
                .setNegativeButton(android.R.string.cancel, (dialog1, which) -> dialog1.dismiss())
                .create();
        dialog.show();
    }

    static private void doRestore(Context activityContext) {
        final Context context = Application.get().getApplicationContext();
        ObaAnalytics.reportUiEvent(FirebaseAnalytics.getInstance(activityContext),
                context.getString(R.string.analytics_label_button_press_restore_preference),
                null);
        try {
            Backup.restore(context);

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
        }
    }

    /**
     * Creates a backup of the current OBA database on local storage. If storage permission hasn't
     * been granted this method is a no-op.
     * @param activityContext context of the calling activity (used to check permissions)
     */
    public static void save(Context activityContext) {
        if (!PermissionUtils.hasGrantedPermissions(activityContext, STORAGE_PERMISSIONS)) {
            // Let the PreferenceActivity request permissions from the user first
            return;
        }

        Context context = Application.get().getApplicationContext();
        ObaAnalytics.reportUiEvent(FirebaseAnalytics.getInstance(activityContext),
                context.getString(R.string.analytics_label_button_press_save_preference),
                null);
        try {
            Backup.backup(context);
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_saved),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_save_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
