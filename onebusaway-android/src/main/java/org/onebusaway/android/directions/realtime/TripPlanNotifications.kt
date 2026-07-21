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

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.notifications.NotificationChannels

/**
 * The single gate for whether the trip-plan-change monitor should arm. Replaces the two overlapping,
 * SDK-inconsistent checks that preceded it: a hidden `live_updates` preference with no settings UI, and
 * an arm-time check that consulted the in-app toggle only pre-O (on O+ it read channel importance and
 * ignored the toggle, so the Settings switch did nothing on modern devices).
 *
 * Both sides must allow notifications: the **in-app** toggle
 * ([R.string.preference_key_trip_plan_notifications], the Settings switch, default on) gates arming on
 * every SDK level, and the **OS** stays the source of truth for delivery — app-level notifications
 * enabled (which also covers the `POST_NOTIFICATIONS` runtime grant on Android 13+) and the
 * [NotificationChannels.TRIP_PLAN_UPDATES_ID] channel not set to "None" (O+).
 */
object TripPlanNotifications {

    fun isEnabled(context: Context): Boolean {
        val app = context.applicationContext

        val appPrefEnabled = PreferencesEntryPoint.get(app)
            .getBoolean(R.string.preference_key_trip_plan_notifications, true)
        if (!appPrefEnabled) return false

        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(NotificationChannels.TRIP_PLAN_UPDATES_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }
}
