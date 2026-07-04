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
package org.onebusaway.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * The app's notification channels: their stable IDs and one-time registration. Extracted from
 * `Application` so `onCreate` just calls [registerAll]. The IDs are referenced by the notification
 * builders across `nav`, `tripservice`, and `directions` (Java and Kotlin), so they live here as the
 * single source of truth rather than on the Application god-object.
 */
object NotificationChannels {

    private const val TAG = "NotificationChannels"

    const val TRIP_PLAN_UPDATES_ID = "trip_plan_updates"
    const val ARRIVAL_REMINDERS_ID = "arrival_reminders"
    const val DESTINATION_ALERT_ID = "destination_alerts"

    /**
     * Registers the app's notification channels. A no-op below API 26 (channels didn't exist);
     * idempotent above it, since registering an existing channel id just updates it.
     */
    @JvmStatic
    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        // NotificationManager is a platform type with a nullable getSystemService return; it's never
        // null on a real API 26+ device, but guard the restricted/instrumented-context edge rather
        // than NPE. Nothing to register (or recover) if the service is absent.
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager == null) {
            Log.w(TAG, "NotificationManager unavailable; skipping notification-channel registration")
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                TRIP_PLAN_UPDATES_ID,
                "Trip plan notifications (beta)",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "After planning a trip, send notifications if the trip is delayed or no longer recommended."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ARRIVAL_REMINDERS_ID,
                "Bus arrival notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications to remind the user of an arriving bus."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DESTINATION_ALERT_ID,
                "Destination alerts",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "All notifications relating to Destination alerts"
            }
        )
    }
}
