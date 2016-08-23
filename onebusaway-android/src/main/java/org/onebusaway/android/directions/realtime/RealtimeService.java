/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.realtime;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.model.ItineraryDescription;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class RealtimeService extends IntentService {

    private static final String TAG = "RealtimeService";

    public RealtimeService() {
        super("RealtimeService");
    }

    /**
     * Start realtime updates.
     *
     * @param source Activity from which updates are started
     * @param bundle Bundle with selected itinerary/parameters
     */
    public static void start(Activity source, Bundle bundle) {

        SharedPreferences prefs = Application.getPrefs();
        if (!prefs.getBoolean(OTPConstants.PREFERENCE_KEY_LIVE_UPDATES, true)) {
            return;
        }

        bundle.putSerializable(OTPConstants.NOTIFICATION_TARGET, source.getClass());
        Intent intent = new Intent(OTPConstants.INTENT_START_CHECKS);
        intent.putExtras(bundle);
        source.sendBroadcast(intent);
    }

    @Override
    public void onHandleIntent(Intent intent) {
        Bundle bundle = intent.getExtras();

        Itinerary itinerary = getItinerary(bundle);
        Class<? extends Activity> activity = (Class<? extends  Activity>)
                bundle.getSerializable(OTPConstants.NOTIFICATION_TARGET);

        if (intent.getAction().equals(OTPConstants.INTENT_START_CHECKS)) {
            disableListenForTripUpdates();
            if (!possibleReschedule(bundle)) {
                startRealtimeUpdates(bundle, itinerary);
            }
        } else if (intent.getAction().equals(OTPConstants.INTENT_CHECK_TRIP_TIME)) {
            checkForItineraryChange(bundle, itinerary, activity);
        }

        RealtimeWakefulReceiver.completeWakefulIntent(intent);
    }

    // Depending on preferences / whether there is realtime info, start updates.
    private void startRealtimeUpdates(Bundle params, Itinerary itinerary) {

        Log.d(TAG, "Checking whether to start realtime updates.");

        boolean realtimeLegsOnItineraries = false;

        for (Leg leg : itinerary.legs) {
            if (leg.realTime) {
                realtimeLegsOnItineraries = true;
            }
        }

        if (realtimeLegsOnItineraries) {
            Log.d(TAG, "Starting realtime updates for itinerary");

            // init alarm mgr
            getAlarmManager().setInexactRepeating(AlarmManager.RTC, new Date().getTime(),
                    OTPConstants.DEFAULT_UPDATE_INTERVAL_TRIP_TIME, getAlarmIntent(params));
        } else {
            Log.d(TAG, "No realtime legs on itinerary");
        }

    }

    // Reschedule the start of checks, if necessary
    // Return true if rescheduled
    private boolean possibleReschedule(Bundle bundle) {

        // Delay if this trip doesn't start for at least an hour
        Date start = new TripRequestBuilder(bundle).getDateTime();
        Date queryStart = new Date(start.getTime() - OTPConstants.REALTIME_SERVICE_QUERY_WINDOW);
        boolean reschedule = new Date().before(queryStart);

        if (reschedule) {
            Log.d(TAG, "Start service at " + queryStart);
            Intent future = new Intent(OTPConstants.INTENT_START_CHECKS);
            future.putExtras(bundle);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
                    0, future, PendingIntent.FLAG_CANCEL_CURRENT);
            getAlarmManager().set(AlarmManager.RTC_WAKEUP, queryStart.getTime(), pendingIntent);
        }

        return reschedule;
    }

    private void checkForItineraryChange(final Bundle params, Itinerary itinerary, final Class<? extends Activity> source) {

        Log.d(TAG, "Check for change");

        final ItineraryDescription itineraryDescription = new ItineraryDescription(itinerary);

        TripRequest.Callback callback = new TripRequest.Callback() {
            @Override
            public void onTripRequestComplete(List<Itinerary> itineraries, String url) {
                if (itineraries == null || itineraries.isEmpty()) {
                    onTripRequestFailure(-1, null);
                    return;
                }

                // Check each itinerary. Notify user if our *current* itinerary doesn't exist
                // or has a lower rank.
                for (int i = 0; i < itineraries.size(); i++) {
                    ItineraryDescription other = new ItineraryDescription(itineraries.get(i));

                    if (itineraryDescription.itineraryMatches(other)) {

                        long delay = itineraryDescription.getDelay(other);
                        Log.d(TAG, "Delay on itinerary: " + delay);

                        if (Math.abs(delay) > OTPConstants.REALTIME_SERVICE_DELAY_THRESHOLD) {
                            Log.d(TAG, "Notify due to large delay.");
                            showNotification(itineraryDescription,
                                    (delay > 0) ? R.string.trip_plan_delay : R.string.trip_plan_early,
                                    source, params, itineraries);
                            disableListenForTripUpdates();
                            return;
                        }

                        // Otherwise, we are still good.
                        Log.d(TAG, "Itinerary exists and is not delayed.");
                        checkDisableDueToTimeout(itineraryDescription);

                        return;
                    }
                }
                Log.d(TAG, "Did not find a matching itinerary in new call.");
                showNotification(itineraryDescription, R.string.trip_plan_not_recommended, source, params, itineraries);
                disableListenForTripUpdates();
            }

            @Override
            public void onTripRequestFailure(int result, String url) {
                Log.e(TAG, "Failure checking itineraries. Result=" + result + ", url=" + url);
                disableListenForTripUpdates();
                return;
            }
        };

        // Create trip request from the original. Do not update the departure time.
        TripRequestBuilder builder = new TripRequestBuilder(params)
                .setListener(callback);

        try {
            builder.execute();
        } catch (Exception e) {
            e.printStackTrace();
            disableListenForTripUpdates();
        }
    }

    private void showNotification(ItineraryDescription description, int message, Class<? extends Activity> notificationTarget,
                                  Bundle params, List<Itinerary> itineraries) {

        String itineraryChange = getResources().getString(message);

        Intent openIntent = new Intent(getApplicationContext(), notificationTarget);
        openIntent.putExtras(params);
        openIntent.putExtra(OTPConstants.INTENT_SOURCE, OTPConstants.Source.NOTIFICATION);
        openIntent.putExtra(OTPConstants.ITINERARIES, (ArrayList<Itinerary>) itineraries);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent
                .getActivity(getApplicationContext(),
                        0,
                        openIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_stat_notification)
                        .setContentTitle(getResources().getString(R.string.title_activity_trip_plan))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(itineraryChange))
                        .setContentText(itineraryChange)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(openPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = mBuilder.build();
        notification.defaults = Notification.DEFAULT_ALL;
        notification.flags |= Notification.FLAG_AUTO_CANCEL | Notification.FLAG_SHOW_LIGHTS;

        Integer notificationId = description.getId();
        notificationManager.notify(notificationId, notification);
    }

    // If the end time for this itinerary has passed, disable trip updates.
    private void checkDisableDueToTimeout(ItineraryDescription itineraryDescription) {
        if (itineraryDescription.isExpired()) {
            Log.d(TAG, "End of trip has passed.");
            disableListenForTripUpdates();
        }
    }

    public void disableListenForTripUpdates() {
        Log.d(TAG, "Disable trip updates.");
        getAlarmManager().cancel(getAlarmIntent(null));
    }


    private AlarmManager getAlarmManager() {
        return  (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
    }

    private PendingIntent getAlarmIntent(Bundle bundle) {
        Intent intent = new Intent(OTPConstants.INTENT_CHECK_TRIP_TIME);
        if (bundle != null) {
            intent.putExtras(bundle);
        }
        PendingIntent alarmIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return alarmIntent;
    }

    private Itinerary getItinerary(Bundle bundle) {
        ArrayList<Itinerary> itineraries = (ArrayList<Itinerary>) bundle.getSerializable(OTPConstants.ITINERARIES);
        int i = bundle.getInt(OTPConstants.SELECTED_ITINERARY);
        return itineraries.get(i);
    }

}
