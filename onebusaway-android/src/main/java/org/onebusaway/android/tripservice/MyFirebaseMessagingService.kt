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
import org.json.JSONException
import org.json.JSONObject
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.ui.ArrivalsListActivity
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ReminderUtils

/**
 * Service for handling Firebase Cloud Messaging (FCM) messages used for arrival reminders.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMsgService"
        private const val NOTIFICATION_COLOR = 0xFF4CAF50.toInt()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val payloadString = remoteMessage.data["payload"]
        if (payloadString == null) {
            Log.w(TAG, "FCM message received without 'payload' key. Data keys: ${remoteMessage.data.keys}")
            return
        }

        try {
            val jsonObject = JSONObject(payloadString)
            val dataObject = jsonObject.getJSONObject("data")

            val message = dataObject.optString("body", "No message content")
            val notificationID = dataObject.optInt("notification_id", 0)
            val arrivalAndDepartureData = dataObject.getJSONObject("arrival_and_departure")

            val tripId = arrivalAndDepartureData.optString("trip_id", "")
            val stopId = arrivalAndDepartureData.optString("stop_id", "")
            Log.d(TAG, "Received reminder for stopId: $stopId, tripId: $tripId")

            val context = Application.get().applicationContext
            ReminderUtils.deleteSavedReminder(context, tripId, stopId)
            showNotification(context, message, stopId, notificationID)
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing notification JSON: ${e.message}", e)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferenceUtils.saveString(getString(R.string.firebase_messaging_token), token)
        Log.d(TAG, "FCM token refreshed")
    }

    private fun showNotification(context: Context, message: String, stopId: String, notificationID: Int) {
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
        notificationManager.notify(notificationID, notificationBuilder.build())
    }
}
