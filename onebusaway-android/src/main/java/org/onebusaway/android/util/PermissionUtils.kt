
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

package org.onebusaway.android.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    const val LOCATION_PERMISSION_REQUEST = 1
    const val SAVE_BACKUP_PERMISSION_REQUEST = 2
    const val RESTORE_BACKUP_PERMISSION_REQUEST = 3
    const val BACKGROUND_LOCATION_PERMISSION_REQUEST = 4
    const val NOTIFICATION_PERMISSION_REQUEST = 5

    @JvmField
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @JvmField
    @SuppressLint("InlinedApi")
    val STORAGE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /**
     * Returns true if all the provided permissions in requiredPermissions have been granted, or false if they have not
     * @param context
     * @param requiredPermissions
     * @return true if all the provided permissions in requiredPermissions have been granted, or false if they have not
     */
    @JvmStatic
    fun hasGrantedAllPermissions(context: Context, requiredPermissions: Array<String>): Boolean {
        requiredPermissions.forEach { permission ->
            if (!hasGrantedPermission(context, permission)) {
                return false
            }
        }
        return true
    }

    /**
     * Returns true if AT LEAST ONE of the provided permissions in permissions have been granted, or false if none of them have been granted
     * @param context
     * @param permissions
     * @return true if AT LEAST ONE of the provided permissions in permissions have been granted, or false if none of them have been granted
     */
    @JvmStatic
    fun hasGrantedAtLeastOnePermission(context: Context, permissions: Array<String>): Boolean {
        permissions.forEach { permission ->
            if (hasGrantedPermission(context, permission)) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if the provided permission in requiredPermission has been granted, or false if it has not
     * @param context
     * @param requiredPermission
     * @return true if the provided permission in requiredPermission has been granted, or false if it has not
     */
    @JvmStatic
    fun hasGrantedPermission(context: Context, requiredPermission: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // If permissions granted at install time
        }
        return ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
    }
}