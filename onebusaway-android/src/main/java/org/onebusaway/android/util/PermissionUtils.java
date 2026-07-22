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
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class PermissionUtils {

  public static final String[] LOCATION_PERMISSIONS = {
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
  };

  /**
   * Returns true if all of the provided permissions in requiredPermissions have been granted, or
   * false if they have not
   *
   * @param context
   * @param requiredPermissions
   * @return true if all of the provided permissions in requiredPermissions have been granted, or
   *     false if they have not
   */
  public static boolean hasGrantedAllPermissions(
      @NonNull Context context, @NonNull String[] requiredPermissions) {
    for (String p : requiredPermissions) {
      if (!hasGrantedPermission(context, p)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if AT LEAST ONE of the provided permissions in permissions have been granted, or
   * false if none of them have been granted
   *
   * @param context
   * @param permissions
   * @return true if AT LEAST ONE of the provided permissions in permissions have been granted, or
   *     false if none of them have been granted
   */
  public static boolean hasGrantedAtLeastOnePermission(
      @NonNull Context context, @NonNull String[] permissions) {
    for (String p : permissions) {
      if (hasGrantedPermission(context, p)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the provided permission in requiredPermission has been granted, or false if it
   * has not
   *
   * @param context
   * @param requiredPermission
   * @return true if the provided permission in requiredPermission has been granted, or false if it
   *     has not
   */
  public static boolean hasGrantedPermission(
      @NonNull Context context, @NonNull String requiredPermission) {
    return ContextCompat.checkSelfPermission(context, requiredPermission)
        == PackageManager.PERMISSION_GRANTED;
  }
}
