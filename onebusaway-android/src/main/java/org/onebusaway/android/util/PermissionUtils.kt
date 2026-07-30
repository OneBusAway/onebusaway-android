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
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {
    @JvmField
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @JvmStatic
    fun hasGrantedAllPermissions(context: Context, requiredPermissions: Array<String>): Boolean = requiredPermissions.all { hasGrantedPermission(context, it) }

    @JvmStatic
    fun hasGrantedAtLeastOnePermission(context: Context, permissions: Array<String>): Boolean = permissions.any { hasGrantedPermission(context, it) }

    @JvmStatic
    fun hasGrantedPermission(context: Context, requiredPermission: String): Boolean = ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
}
