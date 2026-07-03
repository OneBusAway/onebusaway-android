/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.survey

import android.content.Context
import java.util.Date
import java.util.UUID
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Survey-related preferences (the user's survey UUID and the reminder date). Backed by the
 * DataStore [PreferencesRepository] (storage-modernization) rather than the old separate `survey_pref`
 * SharedPreferences file; values from that file are migrated once on first access so existing users
 * keep their survey identity.
 */
object SurveyPreferences {

    private const val LEGACY_PREFS_NAME = "survey_pref"
    private const val UUID_KEY = "my_uuid"
    private const val SURVEY_REMINDER_DATE_KEY = "survey_reminder_day"
    private const val LEGACY_MIGRATED_KEY = "survey_pref_migrated"

    private fun prefs(): PreferencesRepository = PreferencesEntryPoint.get(Application.get())

    /**
     * Saves the given survey reminder date.
     *
     * @param date The date to be saved as a reminder
     */
    @JvmStatic
    fun setSurveyReminderDate(context: Context, date: Date) {
        prefs().setLong(SURVEY_REMINDER_DATE_KEY, date.time)
    }

    /**
     * Retrieves the survey reminder date.
     *
     * @return The stored reminder date as a long (milliseconds since epoch), or -1 if not set
     */
    @JvmStatic
    fun getSurveyReminderDate(context: Context): Long {
        migrateLegacyIfNeeded(context)
        return prefs().getLong(SURVEY_REMINDER_DATE_KEY, -1)
    }

    /**
     * Retrieves the user's UUID, generating and storing one if absent.
     *
     * @return The user's UUID as a String.
     */
    @JvmStatic
    fun getUserUUID(context: Context): String {
        migrateLegacyIfNeeded(context)
        prefs().getString(UUID_KEY, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs().setString(UUID_KEY, it) }
    }

    /** One-time copy of the old separate `survey_pref` SharedPreferences values into DataStore. */
    private fun migrateLegacyIfNeeded(context: Context) {
        val prefs = prefs()
        if (prefs.getBoolean(LEGACY_MIGRATED_KEY, false)) return
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        legacy.getString(UUID_KEY, null)?.let { prefs.setString(UUID_KEY, it) }
        legacy.getLong(SURVEY_REMINDER_DATE_KEY, -1)
            .takeIf { it != -1L }
            ?.let { prefs.setLong(SURVEY_REMINDER_DATE_KEY, it) }
        prefs.setBoolean(LEGACY_MIGRATED_KEY, true)
    }
}
