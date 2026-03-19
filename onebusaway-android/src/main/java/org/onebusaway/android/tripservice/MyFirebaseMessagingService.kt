package org.onebusaway.android.tripservice

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.ui.ArrivalsListActivity
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ReminderUtils

/**
 * Service for handling Firebase Cloud Messaging (FCM) messages used for arrival reminders.
 *
 * When the app is in the foreground, onMessageReceived is called and we build the notification
 * ourselves with a deep-link PendingIntent. When the app is backgrounded, FCM shows the
 * notification automatically and delivers the data payload to the launcher activity (HomeActivity)
 * via intent extras on tap.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMsgService"
        private const val NOTIFICATION_COLOR = 0xFF4CAF50.toInt()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val arrivalJson = remoteMessage.data["arrival_and_departure"]
        val stopId = ReminderUtils.getStopIdFromPayload(arrivalJson)

        if (stopId == null) {
            Log.w(TAG, "FCM message received without stop_id. Data keys: ${remoteMessage.data.keys}")
            return
        }

        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "No message content"
        Log.d(TAG, "Received reminder for stopId: $stopId")

        val context = Application.get().applicationContext
        ReminderUtils.handleArrivalPayload(context, arrivalJson)
        showNotification(context, message, stopId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferenceUtils.saveString(getString(R.string.firebase_messaging_token), token)
        Log.d(TAG, "FCM token refreshed")
    }

    private fun showNotification(context: Context, message: String, stopId: String) {
        val intent = ArrivalsListActivity.Builder(context, stopId).intent
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder =
            NotificationCompat.Builder(this, Application.CHANNEL_ARRIVAL_REMINDERS_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(NOTIFICATION_COLOR)
                .setContentTitle("OneBusAway")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

        val appPrefs = Application.getPrefs()

        if (appPrefs.getBoolean(getString(R.string.preference_key_preference_vibrate_allowed), true)) {
            notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE)
        }

        val soundPreference = appPrefs.getString(getString(R.string.preference_key_notification_sound), "")
        if (soundPreference.isNullOrEmpty()) {
            notificationBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
        } else {
            notificationBuilder.setSound(Uri.parse(soundPreference))
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(stopId.hashCode(), notificationBuilder.build())
    }
}
