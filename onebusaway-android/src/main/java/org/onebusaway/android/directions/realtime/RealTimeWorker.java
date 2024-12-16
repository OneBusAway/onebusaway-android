package org.onebusaway.android.directions.realtime;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.model.ItineraryDescription;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.TripPlan;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import androidx.core.app.NotificationCompat;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RealTimeWorker extends Worker {
    private static final String TAG = "RealTimeWorker";
    private static final String ITINERARY_DESC = ".ItineraryDesc";
    private static final String ITINERARY_END_DATE = ".ItineraryEndDate";
    private Result mResult = Result.success();

    public RealTimeWorker(@androidx.annotation.NonNull Context context, @androidx.annotation.NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @androidx.annotation.NonNull
    @Override
    public Result doWork() {
        Bundle bundle = RealtimeService.toBundle(getInputData());
        Log.d(TAG, bundle.toString()+" Worker");

        checkForItineraryChange(bundle);
        return mResult;
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

        String titleText = Resources.getSystem().getString(title);
        String messageText = Resources.getSystem().getString(message);

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
    private void disableListenForTripUpdates() {
        Log.d(TAG, "disabled");
        mResult = Result.failure();
        WorkManager.getInstance().getWorkInfosForUniqueWork(OTPConstants.REALTIME_UNIQUE_WORKER_NAME).cancel(true);

    }
    private void checkDisableDueToTimeout(ItineraryDescription itineraryDescription) {
        if (itineraryDescription.isExpired()) {
            Log.d(TAG, "End of trip has passed.");
            disableListenForTripUpdates();
        }
    }
    private ItineraryDescription getItineraryDescription(Bundle bundle) {
        String ids[] = bundle.getStringArray(ITINERARY_DESC);
        long date = bundle.getLong(ITINERARY_END_DATE);
        return new ItineraryDescription(Arrays.asList(ids), new Date(date));
    }

    private Class getNotificationTarget(Bundle bundle) {
        try {
            Class target = bundle.getSerializable(OTPConstants.NOTIFICATION_TARGET).getClass();
            return target;
        } catch(Exception e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }
}
