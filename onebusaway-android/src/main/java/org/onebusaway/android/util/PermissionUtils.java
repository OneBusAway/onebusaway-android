/*
 * Copyright (C) 2018 The Android Open Source Project, Sean J. Barbeau (sjbarbeau@gmail.com)
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;


public class PermissionUtils {

    public static final int LOCATION_PERMISSION_REQUEST = 1;
    public static final int SAVE_BACKUP_PERMISSION_REQUEST = 2;
    public static final int RESTORE_BACKUP_PERMISSION_REQUEST = 3;

    public static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    @SuppressLint("InlinedApi")
    public static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Returns true if all of the provided permissions in requiredPermissions have been granted, or false if they have not
     * @param context
     * @param requiredPermissions
     * @return true if all of the provided permissions in requiredPermissions have been granted, or false if they have not
     */
    public static boolean hasGrantedPermissions(Context context, String[] requiredPermissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Permissions granted at install time
            return true;
        }
        for (String p : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
