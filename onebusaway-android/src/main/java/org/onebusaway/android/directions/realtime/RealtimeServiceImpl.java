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
import org.onebusaway.android.directions.model.ItineraryDescription;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.ui.TripPlanActivity;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.Date;
import java.util.List;


public class RealtimeServiceImpl implements RealtimeService {

    private static final String TAG = "RealtimeServiceImpl";

    private Context mApplicationContext;

    private AlarmManager mAlarmMgr;

    private Activity mActivity;

    private IntentFilter mIntentFilter;

    PendingIntent mAlarmIntentTripUpdate;

    Intent mTripUpdateIntent;

    Bundle mBundle;

    ItineraryDescription mItineraryDescription;

    boolean mRegistered;

    private static final long DELAY_THRESHOLD_SEC = 120;

    public RealtimeServiceImpl(Context context, Activity mActivity, Bundle bundle) {
        mActivity = mActivity;
        mApplicationContext = context;
        mAlarmMgr = (AlarmManager) mApplicationContext.getSystemService(Context.ALARM_SERVICE);

        mIntentFilter = new IntentFilter(OTPConstants.INTENT_UPDATE_TRIP_TIME_ACTION);
        mIntentFilter.addAction(OTPConstants.INTENT_NOTIFICATION_ACTION_OPEN_APP);

        mTripUpdateIntent = new Intent(OTPConstants.INTENT_UPDATE_TRIP_TIME_ACTION);
        mAlarmIntentTripUpdate = PendingIntent.getBroadcast(mApplicationContext, 0, mTripUpdateIntent, 0);

        mBundle = bundle;

        mRegistered = false;
    }

    @Override
    public void onItinerarySelected(Itinerary itinerary) {
        disableListenForTripUpdates();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApplicationContext);

        if (prefs.getBoolean(OTPConstants.PREFERENCE_KEY_LIVE_UPDATES, true)) {

            boolean realtimeLegsOnItineraries = false;

            for (Leg leg : itinerary.legs) {
                if (leg.realTime) {
                    realtimeLegsOnItineraries = true;
                }
            }

            if (realtimeLegsOnItineraries) {
                Log.d(TAG, "Starting realtime updates for itinerary");

                mItineraryDescription = new ItineraryDescription(itinerary);

                mApplicationContext.registerReceiver(broadcastReceiver, mIntentFilter);
                mAlarmMgr.setInexactRepeating(AlarmManager.RTC, new Date().getTime(),
                        OTPConstants.DEFAULT_UPDATE_INTERVAL_TRIP_TIME, mAlarmIntentTripUpdate);
                mRegistered = true;
            } else {
                Log.d(TAG, "No realtime legs on itinerary");
            }
        }
    }

    private void checkForItineraryChange() {
        TripRequest.Callback callback = new TripRequest.Callback() {
            @Override
            public void onTripRequestComplete(List<Itinerary> itineraries) {
                if (itineraries == null || itineraries.isEmpty()) {
                    onTripRequestFailure(-1, null);
                    return;
                }

                // Check each itinerary. Notify user if our *current* itinerary doesn't exist
                // or has a lower rank.
                for (int i = 0; i < itineraries.size(); i++) {
                    ItineraryDescription other = new ItineraryDescription(itineraries.get(i));

                    if (mItineraryDescription.itineraryMatches(other)) {

                        long delay = mItineraryDescription.getDelay(other);
                        Log.d(TAG, "Delay on itinerary: " + delay);

                        if (Math.abs(delay) > DELAY_THRESHOLD_SEC) {
                            Log.d(TAG, "Notify due to large delay.");
                            showNotification(mItineraryDescription, 
                                    (delay > 0) ? R.string.trip_plan_delay : R.string.trip_plan_early);
                            disableListenForTripUpdates();
                            return;
                        }

                        // Otherwise, we are still good.
                        Log.d(TAG, "Itinerary exists and is not delayed.");
                        checkDisableDueToTimeout();

                        return;
                    }
                }
                Log.d(TAG, "Did not find a matching itinerary in new call.");
                showNotification(mItineraryDescription, R.string.trip_plan_not_recommended);
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
        TripRequestBuilder builder = new TripRequestBuilder(mBundle)
                .setListener(callback);

        try {
            builder.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // If the end time for this itinerary has passed, disable trip updates.
    private void checkDisableDueToTimeout() {
        if (mItineraryDescription.isExpired()) {
            Log.d(TAG, "End of trip has passed.");
            disableListenForTripUpdates();
        }
    }

    public void disableListenForTripUpdates() {
        if (mRegistered) {
            mAlarmMgr.cancel(mAlarmIntentTripUpdate);
            mApplicationContext.unregisterReceiver(broadcastReceiver);
        }
        mRegistered = false;
    }

    private void showNotification(ItineraryDescription description, int message) {
        String itineraryChange = getResources().getString(message);

        Intent notificationIntentOpenApp = new Intent(mApplicationContext, mActivity.getClass());
        notificationIntentOpenApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent notificationOpenAppPendingIntent = PendingIntent
                .getActivity(mApplicationContext,
                        0,
                        notificationIntentOpenApp,
                        0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(mApplicationContext)
                        .setSmallIcon(R.drawable.ic_stat_notification)
                        .setContentTitle(getResources().getString(R.string.title_activity_trip_plan))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(itineraryChange))
                        .setContentText(itineraryChange)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(notificationOpenAppPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = mBuilder.build();
        notification.defaults = Notification.DEFAULT_ALL;
        notification.flags |= Notification.FLAG_AUTO_CANCEL | Notification.FLAG_SHOW_LIGHTS;

        Integer notificationId = description.getId();
        notificationManager.notify(notificationId, notification);
    }

    private Resources getResources() {
        return mApplicationContext.getResources();
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(OTPConstants.INTENT_UPDATE_TRIP_TIME_ACTION)) {
                checkForItineraryChange();
            } else if (intent.getAction().equals(OTPConstants.INTENT_NOTIFICATION_ACTION_OPEN_APP)) {
                // OTP-for-Android opens a new activity with trip ID data. We just open the old activity.
                Intent activityIntent = new Intent(mApplicationContext, TripPlanActivity.class);
                activityIntent.setAction(Intent.ACTION_MAIN);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                mApplicationContext.startActivity(activityIntent);
            }
        }
    };
}
