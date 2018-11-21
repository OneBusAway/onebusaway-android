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

import org.onebusaway.android.util.BackupUtils;

import android.content.Context;
import android.os.Environment;
import android.preference.Preference;
import android.util.AttributeSet;

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
        BackupUtils.restore(getContext());
    }
}
