package org.onebusaway.android.tripservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONException
import org.json.JSONObject
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.ui.ArrivalsListActivity
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ReminderUtils

/**
 * Service class for handling Firebase Cloud Messaging (FCM) messages used for arrival reminders.
 * This class extends `FirebaseMessagingService` to receive and handle FCM messages.
 */

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FirebaseMsgService", "Received notification")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferenceUtils.saveString(getString(R.string.firebase_messaging_token), token)
        Log.d("FirebaseMsgService", "New token: $token")
    }

    private fun showNotification(message: String, stopId: String, notificationID: Int = 0) {
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val context = Application.get().applicationContext

        val intent = ArrivalsListActivity.Builder(context, stopId).intent
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder =
            NotificationCompat.Builder(this, Application.CHANNEL_ARRIVAL_REMINDERS_ID)
                .setSmallIcon(R.drawable.ic_stat_notification).setColor(Color.parseColor("#4CAF50"))
                .setContentText(message).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).setChannelId(Application.CHANNEL_ARRIVAL_REMINDERS_ID)
                .setSound(defaultSoundUri).setContentIntent(pendingIntent)

        val appPrefs = Application.getPrefs()

        val vibratePreference = appPrefs.getBoolean("preference_vibrate_allowed", true)
        if (vibratePreference) {
            notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE)
        }

        val soundPreference = appPrefs.getString("preference_notification_sound", "")
        if (soundPreference!!.isEmpty()) {
            notificationBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
        } else {
            notificationBuilder.setSound(Uri.parse(soundPreference))
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                Application.CHANNEL_ARRIVAL_REMINDERS_ID, "Reminders", importance
            ).apply {
                description = "Notification for arrival reminders"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationID, notificationBuilder.build())
    }

    override fun handleIntent(intent: Intent?) {
        val data = intent?.extras as Bundle
        val remoteMessage = RemoteMessage(data)
        remoteMessage.data["payload"]?.let { jsonString ->
            try {
                val jsonObject = JSONObject(jsonString)
                val dataObject = jsonObject.getJSONObject("data")

                val message = dataObject.optString("body", "No message content")
                val notificationID = dataObject.optInt("notification_id", 0)
                val arrivalAndDepartureData = dataObject.getJSONObject("arrival_and_departure")

                val tripId = arrivalAndDepartureData.optString("trip_id", "")
                val stopId = arrivalAndDepartureData.optString("stop_id", "")
                Log.d("FirebaseMsgService", "Received notification: $message and stopId: $stopId and tripId: $tripId")

                ReminderUtils.deleteSavedReminder(
                    Application.get().applicationContext, tripId, stopId
                )

                showNotification(message, stopId, notificationID)
            } catch (e: JSONException) {
                Log.e("FirebaseMsgService", "Error parsing notification JSON: ${e.message}", e)
            }
        }
    }
}
