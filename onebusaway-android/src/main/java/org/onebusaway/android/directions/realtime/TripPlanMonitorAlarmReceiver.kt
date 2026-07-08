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
package org.onebusaway.android.directions.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Fires when a deferred trip's pre-departure query window opens, and promotes monitoring to the
 * foreground [TripPlanMonitorService]. Replaces the WorkManager-enqueuing `RealtimeReceiver` — the
 * foreground service keeps the CPU awake for its (short, bounded) run, so no wake lock is needed.
 *
 * Starting a foreground service from the background is normally disallowed on Android 12+, but a
 * broadcast fired by an exact [android.app.AlarmManager] alarm is granted the temporary exemption
 * (see [TripPlanMonitor.scheduleStart]).
 */
class TripPlanMonitorAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras
        if (extras == null) {
            Log.w(TAG, "Alarm fired without monitoring state - ignoring")
            return
        }

        val serviceIntent = Intent(context, TripPlanMonitorService::class.java).putExtras(extras)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException (API 31+) if the exemption is unavailable
            // (e.g. the app was force-stopped). Log rather than crash — #791-class fragility.
            Log.w(TAG, "Unable to start trip-plan monitor from alarm", e)
        }
    }

    private companion object {
        const val TAG = "TripPlanMonitorAlarm"
    }
}
