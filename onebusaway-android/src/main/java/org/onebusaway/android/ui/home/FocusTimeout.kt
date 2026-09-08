/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.home

import androidx.annotation.StringRes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.time.WallTime

/**
 * How long map focus survives in the background (#2294). [value] is the stable preference token;
 * [labelRes] is its settings label. A null duration ([ALWAYS]) disables expiration.
 */
enum class FocusTimeout(val value: String, val duration: Duration?, @param:StringRes val labelRes: Int) {
    THIRTY_MINUTES("30m", 30.minutes, R.string.preferences_focus_timeout_30_minutes),
    ONE_HOUR("1h", 1.hours, R.string.preferences_focus_timeout_1_hour),
    TWO_HOURS("2h", 2.hours, R.string.preferences_focus_timeout_2_hours),
    FOUR_HOURS("4h", 4.hours, R.string.preferences_focus_timeout_4_hours),
    EIGHT_HOURS("8h", 8.hours, R.string.preferences_focus_timeout_8_hours),
    ALWAYS("always", null, R.string.preferences_focus_timeout_always);

    /** An absent timestamp (including older saved state) leaves the focus intact. */
    fun hasExpired(lastActive: WallTime?, now: WallTime): Boolean {
        val limit = duration ?: return false
        if (lastActive == null) return false
        return now - lastActive >= limit
    }

    companion object {
        const val PREFERENCE_KEY = "focus_timeout"
        val DEFAULT = FOUR_HOURS

        fun fromPreference(value: String?): FocusTimeout = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

fun PreferencesRepository.focusTimeout(): FocusTimeout = FocusTimeout.fromPreference(getString(FocusTimeout.PREFERENCE_KEY, null))
