/* Copyright (C) 2018 The Android Open Source Project, Sean J. Barbeau */
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
