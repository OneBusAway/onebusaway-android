/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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
package org.onebusaway.android.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import org.onebusaway.android.R

/**
 * Applies the user's selected app theme to the AppCompat night-mode delegate.
 */
object ThemeUtils {

    @JvmStatic
    fun setAppTheme(context: Context, themeValue: String) {
        val mode = when {
            themeValue.equals(
                context.getString(R.string.preferences_app_theme_option_system_default),
                ignoreCase = true
            ) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

            themeValue.equals(
                context.getString(R.string.preferences_app_theme_option_dark),
                ignoreCase = true
            ) -> AppCompatDelegate.MODE_NIGHT_YES

            themeValue.equals(
                context.getString(R.string.preferences_app_theme_option_light),
                ignoreCase = true
            ) -> AppCompatDelegate.MODE_NIGHT_NO

            // Unrecognized value: leave the current mode unchanged (matches legacy behavior).
            else -> return
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /**
     * Returns true if the app is currently in dark mode: the AppCompat night-mode
     * override takes precedence, otherwise the system UI configuration decides.
     */
    @JvmStatic
    fun isInDarkMode(context: Context): Boolean {
        val mode = AppCompatDelegate.getDefaultNightMode()
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return true
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return false
        }
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
