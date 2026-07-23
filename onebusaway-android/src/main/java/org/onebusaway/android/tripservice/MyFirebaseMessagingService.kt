/*
 * Copyright The OneBusAway Authors.
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

package org.onebusaway.android.tripservice

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.reminders.ReminderRepository
import org.onebusaway.android.ui.arrivals.ArrivalsListLauncher
import org.onebusaway.android.util.ReminderUtils

/**
 * Service for handling Firebase Cloud Messaging (FCM) messages used for arrival reminders.
 *
 * When the app is in the foreground, onMessageReceived is called and we build the notification
 * ourselves with a deep-link PendingIntent. When the app is backgrounded, FCM shows the
 * notification automatically and delivers the data payload to the launcher activity (HomeActivity)
 * via intent extras on tap.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var prefsRepository: PreferencesRepository

    @Inject
    lateinit var reminderRepository: ReminderRepository

    companion object {
        private const val TAG = "FirebaseMsgService"
        private const val NOTIFICATION_COLOR = 0xFF4CAF50.toInt()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val arrivalJson = remoteMessage.data[ReminderUtils.ARRIVAL_PAYLOAD_KEY]
        val stopId = ReminderUtils.getStopIdFromPayload(arrivalJson)

        if (stopId == null) {
            Log.w(TAG, "FCM message received without stop_id. Data keys: ${remoteMessage.data.keys}")
            return
        }

        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "No message content"
        if (BuildConfig.DEBUG) Log.d(TAG, "Received reminder for stopId: $stopId")

        val context = applicationContext
        reminderRepository.deleteReminderFromPayload(arrivalJson)
        showNotification(context, message, stopId)
    }

    // onNewToken(String) is deprecated in favor of onRegistered(String), which delivers the Firebase
    // Installation ID rather than the FCM registration token stored here — a behavioral change tracked
    // separately. Keep the FCM-token callback until then.
    // TODO: migrate to onRegistered() — https://github.com/OneBusAway/onebusaway-android/issues/1866
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        prefsRepository.setString(R.string.firebase_messaging_token, token)
        Log.d(TAG, "FCM token refreshed")
    }

    private fun showNotification(context: Context, message: String, stopId: String) {
        val intent = ArrivalsListLauncher.Builder(context, stopId).intent
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder =
            NotificationCompat.Builder(this, NotificationChannels.ARRIVAL_REMINDERS_ID)
                .setSmallIcon(R.drawable.ic_bus)
                .setColor(NOTIFICATION_COLOR)
                .setContentTitle("OneBusAway")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

        if (prefsRepository.getBoolean(R.string.preference_key_preference_vibrate_allowed, true)) {
            notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE)
        }

        val soundPreference = prefsRepository.getString(R.string.preference_key_notification_sound, "")
        if (soundPreference.isNullOrEmpty()) {
            notificationBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
        } else {
            notificationBuilder.setSound(soundPreference.toUri())
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(stopId.hashCode(), notificationBuilder.build())
    }
}
