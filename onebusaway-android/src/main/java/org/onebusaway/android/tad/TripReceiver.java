package org.onebusaway.android.tad;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.onebusaway.android.app.Application;

/**
 * Created by azizmb on 3/16/16.
 */
public class TripReceiver extends BroadcastReceiver {
    public static final String NAV_ID = ".NAV_ID";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("BR", "Received.");
        int navId = intent.getIntExtra(NAV_ID, 0);
        Context appCxt = Application.get().getApplicationContext();
        appCxt.stopService(new Intent(appCxt, TADService.class));
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(TADNavigationServiceProvider.NOTIFICATION_ID);
    }

}
