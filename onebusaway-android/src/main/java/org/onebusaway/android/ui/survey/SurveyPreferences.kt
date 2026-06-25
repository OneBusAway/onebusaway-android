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

/**
 * Utility class for managing survey-related preferences using SharedPreferences.
 * This class handles storing and retrieving user UUIDs and survey reminder dates.
 */
object SurveyPreferences {

    private const val PREFS_NAME = "survey_pref"
    private const val UUID_KEY = "my_uuid"
    private const val SURVEY_REMINDER_DATE_KEY = "survey_reminder_day"

    /**
     * Saves the user's UUID to the shared preferences.
     *
     * @param uuid the UUID to be saved in shared preferences
     */
    private fun saveUserUUIDHelper(context: Context, uuid: UUID) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(UUID_KEY, uuid.toString()).apply()
    }

    /**
     * Retrieves the user's UUID from shared preferences.
     *
     * @return the saved UUID as a string, or null if not found
     */
    private fun getUserUUIDHelper(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uuidString = prefs.getString(UUID_KEY, null)
        return uuidString?.let { UUID.fromString(it).toString() }
    }

    /**
     * Saves the given survey reminder date in SharedPreferences.
     *
     * @param date The date to be saved as a reminder
     */
    @JvmStatic
    fun setSurveyReminderDate(context: Context, date: Date) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(SURVEY_REMINDER_DATE_KEY, date.time).apply()
    }

    /**
     * Retrieves the survey reminder date from SharedPreferences.
     *
     * @return The stored reminder date as a long (milliseconds since epoch), or -1 if not set
     */
    @JvmStatic
    fun getSurveyReminderDate(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(SURVEY_REMINDER_DATE_KEY, -1)
    }

    /**
     * Retrieves the user's UUID. If the UUID is not already stored, generates a new UUID and saves it.
     *
     * @return The user's UUID as a String.
     */
    @JvmStatic
    fun getUserUUID(context: Context): String? {
        if (getUserUUIDHelper(context) == null) {
            saveUserUUIDHelper(context, UUID.randomUUID())
        }
        return getUserUUIDHelper(context)
    }
}
