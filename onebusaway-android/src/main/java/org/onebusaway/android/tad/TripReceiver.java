package org.onebusaway.android.tad;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import org.onebusaway.android.app.Application;

/**
 * Created by azizmb on 3/16/16.
 */
public class TripReceiver extends BroadcastReceiver {
    public static final String TAG = "BroadcastReceiver";

    public static final String NAV_ID = ".NAV_ID";
    public static final String ACTION_NUM = ".ACTION_NUM";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";

    public static final int DISMISS_NOTIFICATION = 1;
    public static final int CANCEL_TRIP = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        int navId = intent.getIntExtra(NAV_ID, 0);
        int actionNum = intent.getIntExtra(ACTION_NUM, 0);
        int notifyId = intent.getIntExtra(NOTIFICATION_ID, TADNavigationServiceProvider.NOTIFICATION_ID);

        switch (actionNum) {
            case DISMISS_NOTIFICATION:
                TADNavigationServiceProvider.mTTS.stop();
                break;

            case CANCEL_TRIP:
                cancelTrip(navId);
                break;
        }

    }

    private void cancelTrip(int navId)
    {
        Context appCxt = Application.get().getApplicationContext();
        appCxt.stopService(new Intent(appCxt, TADService.class));
        NotificationManager manager = (NotificationManager)
                appCxt.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(TADNavigationServiceProvider.NOTIFICATION_ID);
    }

}
