/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com) and individual contributors.
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
package org.onebusaway.android.io.backup;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.region.ObaRegionsTask;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RestorePreference extends Preference {

    //
    // Needed constructors.
    //
    public RestorePreference(Context context) {
        super(context);
    }

    public RestorePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RestorePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        // This is only enabled if the SD card is attached.
        final String state = Environment.getExternalStorageState();
        // Also, this is only enabled if there's a backup file
        return (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state) ||
                Environment.MEDIA_MOUNTED.equals(state)) &&
                Backup.isRestoreAvailable(getContext());
    }

    @Override
    protected void onClick() {
        //
        // Because this is a destructive operation, we should warn the user.
        //
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setMessage(R.string.preferences_db_restore_warning)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        doRestore();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.show();

    }

    void doRestore() {
        final Context context = Application.get().getApplicationContext();
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                context.getString(R.string.analytics_action_button_press),
                context.getString(R.string.analytics_label_button_press_restore_preference));
        try {
            Backup.restore(context);

            Context activityContext = getContext();
            if (activityContext != null) {
                List<ObaRegionsTask.Callback> callbacks = new ArrayList<>();
                callbacks.add(new ObaRegionsTask.Callback() {
                    @Override
                    public void onRegionTaskFinished(boolean currentRegionChanged) {
                        Toast.makeText(context,
                                R.string.preferences_db_restored,
                                Toast.LENGTH_LONG).show();
                    }
                });
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
}
