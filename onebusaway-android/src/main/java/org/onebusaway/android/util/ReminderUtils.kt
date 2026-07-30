/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
import android.util.Log
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import org.json.JSONException
import org.json.JSONObject
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

/** Pure payload and lead-time helpers for departure reminders. */
object ReminderUtils {
    const val ARRIVAL_PAYLOAD_KEY = "arrival_and_departure"

    @JvmStatic fun getStopIdFromPayload(arrivalJson: String?): String? = stringFromPayload(arrivalJson, "stop_id")

    @JvmStatic fun getTripIdFromPayload(arrivalJson: String?): String? = stringFromPayload(arrivalJson, "trip_id")

    private fun stringFromPayload(arrivalJson: String?, key: String): String? {
        if (arrivalJson == null) return null
        return try {
            JSONObject(arrivalJson).optString(key).ifEmpty { null }
        } catch (error: JSONException) {
            Log.e(TAG, "Error parsing arrival_and_departure JSON", error)
            null
        }
    }

    // No @JvmStatic on the two typed-instant overloads: a value-class parameter mangles the
    // generated JVM name, so the static would not be callable from Java under this name anyway.
    // Both callers are Kotlin.
    fun getReminderTimes(context: Context, departTime: ServerTime, now: ServerTime): Array<String> = getReminderTimes(context, departTime - now)

    fun getWallReminderTimes(context: Context, departTime: WallTime, now: WallTime): Array<String> = getReminderTimes(context, departTime - now)

    private fun getReminderTimes(context: Context, untilDeparture: kotlin.time.Duration): Array<String> {
        val thresholds = listOf(3, 5, 10, 15, 20, 25, 30)
        val minutes = ceil(untilDeparture / 1.minutes).toLong()
        val allTimes = context.resources.getStringArray(R.array.reminder_time)
        return buildList {
            add(allTimes[0])
            thresholds.takeWhile { it <= minutes }.forEachIndexed { index, _ -> add(allTimes[index + 1]) }
        }.toTypedArray()
    }

    private const val TAG = "ReminderUtils"
}
