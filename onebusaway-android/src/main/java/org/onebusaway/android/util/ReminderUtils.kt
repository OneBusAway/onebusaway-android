/* Copyright (C) 2016 University of South Florida */
package org.onebusaway.android.util

import android.content.Context
import android.util.Log
import kotlin.math.ceil
import org.json.JSONException
import org.json.JSONObject
import org.onebusaway.android.R

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

    @JvmStatic
    fun getReminderTimes(context: Context, departTimeMs: Long, nowMs: Long): Array<String> {
        val thresholds = listOf(3, 5, 10, 15, 20, 25, 30)
        val minutes = ceil((departTimeMs - nowMs) / 60_000.0).toLong()
        val allTimes = context.resources.getStringArray(R.array.reminder_time)
        return buildList {
            add(allTimes[0])
            thresholds.takeWhile { it <= minutes }.forEachIndexed { index, _ -> add(allTimes[index + 1]) }
        }.toTypedArray()
    }

    private const val TAG = "ReminderUtils"
}
