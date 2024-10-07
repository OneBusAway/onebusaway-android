package org.onebusaway.android.tripservice;

import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;

import org.json.JSONObject;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.ReminderUtils;

import android.app.Notification;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * ArrivalsRemindersServiceExtension is a service extension for handling notifications
 * related to transit arrivals. It implements the INotificationServiceExtension interface
 * from OneSignal and is responsible for customizing the appearance and behavior of
 * notifications received for upcoming transit arrivals.
 * </P>
 * The service extracts relevant data from the notification payload, such as trip and
 * stop information, and manages user reminders accordingly. It also sets notification
 * parameters like icon, color, channel ID.
 */

@Keep
public class ArrivalsRemindersServiceExtension implements INotificationServiceExtension {

    @Override
    public void onNotificationReceived(INotificationReceivedEvent event) {
        IDisplayableMutableNotification notification = event.getNotification();
        Application app = Application.get();

        JSONObject data = notification.getAdditionalData();
        if (data != null) {
            try {
                JSONObject arrivalAndDeparture = data.getJSONObject("arrival_and_departure");

                String regionId = arrivalAndDeparture.optString("region_id");
                String stopId = arrivalAndDeparture.optString("stop_id");
                String tripId = arrivalAndDeparture.optString("trip_id");
                String serviceDate = arrivalAndDeparture.optString("service_date");
                String vehicleId = arrivalAndDeparture.optString("vehicle_id");
                String stopSequence = arrivalAndDeparture.optString("stop_sequence");


                ReminderUtils.deleteSavedReminder(app.getApplicationContext(), tripId, stopId);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        notification.setExtender(new NotificationCompat.Extender() {
            @NonNull
            @Override
            public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {

                builder.setSmallIcon(R.drawable.ic_stat_notification);
                builder.setColor(0xFF4CAF50);
                builder.setChannelId(Application.CHANNEL_ARRIVAL_REMINDERS_ID);
                builder.setAutoCancel(true);

                SharedPreferences appPrefs = Application.getPrefs();
                boolean vibratePreference = appPrefs.getBoolean("preference_vibrate_allowed", true);
                if (vibratePreference) {
                    builder.setDefaults(Notification.DEFAULT_VIBRATE);
                }

                String soundPreference = appPrefs.getString("preference_notification_sound", "");
                if (soundPreference.isEmpty()) {
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                } else {
                    builder.setSound(Uri.parse(soundPreference));
                }
                return builder;
            }
        });
    }
}
