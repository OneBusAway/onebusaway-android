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

import android.content.Context;
import android.os.Environment;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;

import java.io.IOException;

public class SavePreference extends Preference {

    //
    // Needed constructors.
    //
    public SavePreference(Context context) {
        super(context);
    }

    public SavePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SavePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        // This is only enabled if the SD card is attached.
        return Environment.MEDIA_MOUNTED
                .equals(Environment.getExternalStorageState());
    }

    @Override
    protected void onClick() {
        Context context = Application.get().getApplicationContext();
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                context.getString(R.string.analytics_action_button_press),
                context.getString(R.string.analytics_label_button_press_save_preference));
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
