/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.realtime;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import androidx.work.OneTimeWorkRequest;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.model.ItineraryDescription;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.TripPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This service is started after a trip is planned by the user so they can be notified if the
 * trip results for their request change in the near future. For example, if a user plans a trip,
 * and then the top result for that trip gets delayed by 20 minutes, the user will be notified
 * that new trip results are available.
 */
public class RealtimeService extends Worker {

    private static final String TAG = "RealtimeService";

    private static final String ITINERARY_DESC = ".ItineraryDesc";
    private static final String ITINERARY_END_DATE = ".ItineraryEndDate";

    public RealtimeService(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
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
        Data inputData = new Data.Builder()
                .putAll(toMap(bundle))
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RealtimeService.class)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(source).enqueue(workRequest);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        Bundle bundle = toBundle(inputData);

        String action = inputData.getString("action");
        if (OTPConstants.INTENT_START_CHECKS.equals(action)) {
            disableListenForTripUpdates();
            if (!rescheduleRealtimeUpdates(bundle)) {
                Itinerary itinerary = getItinerary(bundle);
                startRealtimeUpdates(bundle, itinerary);
            }
        } else if (OTPConstants.INTENT_CHECK_TRIP_TIME.equals(action)) {
            checkForItineraryChange(bundle);
        }

        return Result.success();
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

    /**
     * Check to see if the start of real-time trip updates should be rescheduled, and if necessary
     * reschedule it
     *
     * @param bundle trip details to be passed to TripRequestBuilder constructor
     * @return true if the start of trip real-time updates has been rescheduled, false if updates
     * should begin immediately
     */
    private boolean rescheduleRealtimeUpdates(Bundle bundle) {
        // Delay if this trip doesn't start for at least an hour
        Date start = new TripRequestBuilder(bundle).getDateTime();
        if (start == null) {
            // To avoid NPE, return true to say that it's been rescheduled, but don't actually reschedule it
            // FIXME - Figure out why sometimes the bundle is empty - see #790 and #791
            return true;
        }
        Date queryStart = new Date(start.getTime() - OTPConstants.REALTIME_SERVICE_QUERY_WINDOW);
        boolean reschedule = new Date().before(queryStart);

        if (reschedule) {
            Log.d(TAG, "Start service at " + queryStart);
            Data inputData = new Data.Builder()
                    .putAll(toMap(bundle))
                    .putString("action", OTPConstants.INTENT_START_CHECKS)
                    .build();
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RealtimeService.class)
                    .setInitialDelay(queryStart.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .build();
            WorkManager.getInstance(getApplicationContext()).enqueue(workRequest);
        }

        return reschedule;
    }

    private void checkForItineraryChange(final Bundle bundle) {
        TripRequestBuilder builder = TripRequestBuilder.initFromBundleSimple(bundle);
        ItineraryDescription desc = getItineraryDescription(bundle);
        Class target = getNotificationTarget(bundle);
        if (target == null) {
            disableListenForTripUpdates();
            return;
        }
        checkForItineraryChange(target, builder, desc);
    }

    private void checkForItineraryChange(final Class<? extends Activity> source, final TripRequestBuilder builder, final ItineraryDescription itineraryDescription) {

        Log.d(TAG, "Check for change");

        TripRequest.Callback callback = new TripRequest.Callback() {
            @Override
            public void onTripRequestComplete(TripPlan tripPlan, String url) {
                if (tripPlan == null || tripPlan.itineraries == null || tripPlan.itineraries.isEmpty()) {
                    onTripRequestFailure(-1, null);
                    return;
                }

                // Check each itinerary. Notify user if our *current* itinerary doesn't exist
                // or has a lower rank.
                for (int i = 0; i < tripPlan.itineraries.size(); i++) {
                    ItineraryDescription other = new ItineraryDescription(tripPlan.itineraries.get(i));

                    if (itineraryDescription.itineraryMatches(other)) {

                        long delay = itineraryDescription.getDelay(other);
                        Log.d(TAG, "Schedule deviation on itinerary: " + delay);

                        if (Math.abs(delay) > OTPConstants.REALTIME_SERVICE_DELAY_THRESHOLD) {
                            Log.d(TAG, "Notify due to large early/late schedule deviation.");
                            showNotification(itineraryDescription,
                                    (delay > 0) ? R.string.trip_plan_delay
                                            : R.string.trip_plan_early,
                                    R.string.trip_plan_notification_new_plan_text,
                                    source, builder.getBundle(), tripPlan.itineraries);
                            disableListenForTripUpdates();
                            return;
                        }

                        // Otherwise, we are still good.
                        Log.d(TAG, "Itinerary exists and no large schedule deviation.");
                        checkDisableDueToTimeout(itineraryDescription);

                        return;
                    }
                }
                Log.d(TAG, "Did not find a matching itinerary in new call - notify user that something has changed.");
                showNotification(itineraryDescription,
                        R.string.trip_plan_notification_new_plan_title,
                        R.string.trip_plan_notification_new_plan_text, source,
                        builder.getBundle(), tripPlan.itineraries);
                disableListenForTripUpdates();
            }

            @Override
            public void onTripRequestFailure(int result, String url) {
                Log.e(TAG, "Failure checking itineraries. Result=" + result + ", url=" + url);
                disableListenForTripUpdates();
            }
        };

        builder.setListener(callback);

        try {
            builder.execute();
        } catch (Exception e) {
            e.printStackTrace();
            disableListenForTripUpdates();
        }
    }

    private void showNotification(ItineraryDescription description, int title, int message,
                                  Class<? extends Activity> notificationTarget,
                                  Bundle params, List<Itinerary> itineraries) {

        String titleText = getApplicationContext().getResources().getString(title);
        String messageText = getApplicationContext().getResources().getString(message);

        Intent openIntent = new Intent(getApplicationContext(), notificationTarget);
        openIntent.putExtras(params);
        openIntent.putExtra(OTPConstants.INTENT_SOURCE, OTPConstants.Source.NOTIFICATION);
        openIntent.putExtra(OTPConstants.ITINERARIES, (ArrayList<Itinerary>) itineraries);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE;
        } else {
            flags = PendingIntent.FLAG_CANCEL_CURRENT;
        }
        PendingIntent openPendingIntent = PendingIntent
                .getActivity(getApplicationContext(),
                        0,
                        openIntent,
                        flags);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext(), Application.CHANNEL_TRIP_PLAN_UPDATES_ID)
                        .setSmallIcon(R.drawable.ic_stat_notification)
                        .setContentTitle(titleText)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText))
                        .setContentText(messageText)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(openPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);
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
        return (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
    }

    private PendingIntent getAlarmIntent(Bundle bundle) {
        Intent intent = new Intent(OTPConstants.INTENT_CHECK_TRIP_TIME);
        if (bundle != null) {
            Bundle extras = getSimplifiedBundle(bundle);
            intent.putExtras(extras);
        }
        int flags;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        } else {
            flags = PendingIntent.FLAG_UPDATE_CURRENT;
        }
        PendingIntent alarmIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent,
                flags);
        return alarmIntent;
    }

    private Itinerary getItinerary(Bundle bundle) {
        ArrayList<Itinerary> itineraries = (ArrayList<Itinerary>) bundle
                .getSerializable(OTPConstants.ITINERARIES);
        int i = bundle.getInt(OTPConstants.SELECTED_ITINERARY);
        return itineraries.get(i);
    }

    private ItineraryDescription getItineraryDescription(Bundle bundle) {
        String ids[] = bundle.getStringArray(ITINERARY_DESC);
        long date = bundle.getLong(ITINERARY_END_DATE);
        return new ItineraryDescription(Arrays.asList(ids), new Date(date));
    }

    private Class getNotificationTarget(Bundle bundle) {
        String name = bundle.getString(OTPConstants.NOTIFICATION_TARGET);
        try {
            return Class.forName(name);
        } catch(ClassNotFoundException e) {
            Log.e(TAG, "unable to find class for name " + name);
        }
        return null;
    }

    private Bundle getSimplifiedBundle(Bundle params) {
        Itinerary itinerary = getItinerary(params);
        ItineraryDescription desc = new ItineraryDescription(itinerary);

        Bundle extras = new Bundle();
        new TripRequestBuilder(params).copyIntoBundleSimple(extras);

        List<String> idList = desc.getTripIds();
        String[] ids = idList.toArray(new String[idList.size()]);
        extras.putStringArray(ITINERARY_DESC, ids);
        extras.putLong(ITINERARY_END_DATE, desc.getEndDate().getTime());

        Class<? extends Activity> source = (Class<? extends Activity>)
                params.getSerializable(OTPConstants.NOTIFICATION_TARGET);

        extras.putString(OTPConstants.NOTIFICATION_TARGET, source.getName());

        return extras;
    }

    private static Data toMap(Bundle bundle) {
        Data.Builder builder = new Data.Builder();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof String) {
                builder.putString(key, (String) value);
            } else if (value instanceof Integer) {
                builder.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                builder.putLong(key, (Long) value);
            } else if (value instanceof Boolean) {
                builder.putBoolean(key, (Boolean) value);
            } else if (value instanceof Float) {
                builder.putFloat(key, (Float) value);
            } else if (value instanceof Double) {
                builder.putDouble(key, (Double) value);
            } else if (value instanceof String[]) {
                builder.putStringArray(key, (String[]) value);
            } else if (value instanceof int[]) {
                builder.putIntArray(key, (int[]) value);
            } else if (value instanceof long[]) {
                builder.putLongArray(key, (long[]) value);
            } else if (value instanceof boolean[]) {
                builder.putBooleanArray(key, (boolean[]) value);
            } else if (value instanceof float[]) {
                builder.putFloatArray(key, (float[]) value);
            } else if (value instanceof double[]) {
                builder.putDoubleArray(key, (double[]) value);
            }
        }
        return builder.build();
    }

    private static Bundle toBundle(Data data) {
        Bundle bundle = new Bundle();
        for (String key : data.getKeyValueMap().keySet()) {
            Object value = data.getKeyValueMap().get(key);
            if (value instanceof String) {
                bundle.putString(key, (String) value);
            } else if (value instanceof Integer) {
                bundle.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                bundle.putLong(key, (Long) value);
            } else if (value instanceof Boolean) {
                bundle.putBoolean(key, (Boolean) value);
            } else if (value instanceof Float) {
                bundle.putFloat(key, (Float) value);
            } else if (value instanceof Double) {
                bundle.putDouble(key, (Double) value);
            } else if (value instanceof String[]) {
                bundle.putStringArray(key, (String[]) value);
            } else if (value instanceof int[]) {
                bundle.putIntArray(key, (int[]) value);
            } else if (value instanceof long[]) {
                bundle.putLongArray(key, (long[]) value);
            } else if (value instanceof boolean[]) {
                bundle.putBooleanArray(key, (boolean[]) value);
            } else if (value instanceof float[]) {
                bundle.putFloatArray(key, (float[]) value);
            } else if (value instanceof double[]) {
                bundle.putDoubleArray(key, (double[]) value);
            }
        }
        return bundle;
    }
}