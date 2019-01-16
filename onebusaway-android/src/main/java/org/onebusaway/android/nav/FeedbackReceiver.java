package org.onebusaway.android.nav;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;


public class FeedbackReceiver extends BroadcastReceiver {
    public static final String TAG = "FeedbackReceiver";

    public static final String TRIP_ID = ".TRIP_ID";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";
    public static final String CALLING_ACTION = ".CALLING_ACTION";

    public static final int FEEDBACK_YES = 2;
    public static final int FEEDBACK_NO = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        int tripId = intent.getIntExtra(TRIP_ID, 0);
        int notifyId = intent.getIntExtra(NOTIFICATION_ID,
                NavigationServiceProvider.NOTIFICATION_ID + 1);
        int actionNum = intent.getIntExtra(CALLING_ACTION, 0);
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        String callingAction = null;

        if (remoteInput != null) {
            String feedback = remoteInput.getCharSequence(
                    NavigationServiceProvider.KEY_TEXT_REPLY).toString();

            NotificationCompat.Builder repliedNotification =
                    new NotificationCompat.Builder(context
                            , Application.CHANNEL_DESTINATION_ALERT_ID)
                            .setSmallIcon(R.drawable.ic_stat_notification)
                            .setContentTitle(Application.get().getResources().getString(R.string.feedback_notify_title))
                            .setContentText(Application.get().getResources().getString(R.string.feedback_notify_confirmation));


            repliedNotification.setOngoing(false);

            NotificationManager mNotificationManager = (NotificationManager)
                    Application.get().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notifyId, repliedNotification.build());

            mNotificationManager.cancel(notifyId);
        }

    }

}
