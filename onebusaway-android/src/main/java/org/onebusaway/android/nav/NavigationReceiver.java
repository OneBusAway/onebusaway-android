/*
 * Copyright (C) 2005-2019 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.nav;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.onebusaway.android.app.Application;

/**
 * Receives broadcasts when the user interacts with the navigation notification and passes them
 * to the NavigationServiceProvider
 */
public class NavigationReceiver extends BroadcastReceiver {
    public static final String TAG = "NavigationReceiver";

    public static final String NAV_ID = ".NAV_ID";
    public static final String ACTION_NUM = ".ACTION_NUM";
    public static final String NOTIFICATION_ID = ".NOTIFICATION_ID";

    public static final int DISMISS_NOTIFICATION = 1;
    public static final int CANCEL_TRIP = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        int navId = intent.getIntExtra(NAV_ID, 0);
        int actionNum = intent.getIntExtra(ACTION_NUM, 0);
        int notifyId = intent.getIntExtra(NOTIFICATION_ID, NavigationServiceProvider.NOTIFICATION_ID);

        switch (actionNum) {
            case DISMISS_NOTIFICATION:
                NavigationServiceProvider.mTTS.stop();
                break;

            case CANCEL_TRIP:
                cancelTrip(navId);
                break;
        }

    }

    private void cancelTrip(int navId) {
        Context appCxt = Application.get().getApplicationContext();
        appCxt.stopService(new Intent(appCxt, NavigationService.class));
        NotificationManager manager = (NotificationManager)
                appCxt.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NavigationServiceProvider.NOTIFICATION_ID);
    }

}
