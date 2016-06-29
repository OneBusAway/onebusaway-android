/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.util;

import org.onebusaway.android.tripservice.TripService;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * Utilities to assist in the registering of reminder alarms for arriving/departing buses
 */
public class ReminderUtils {

    /**
     * Starts the TripService service to schedule/poll/notify/cancel alarms for the "My Reminders"
     * feature.  This is called from a BroadcastReceiver triggered by an Intent registered with
     * Android.
     *
     * For now, just forward anything to the TripService. Eventually, we can distinguish by action
     * or Content URI. Also, handle CPU wake locking..
     *
     * TODO - We should be extending the support library version of WakefulBroadcastReceiver and
     * starting the service using startWakefulService().  See #493 for details.
     *
     * @param context Context from which to start the reminder service
     * @param intent  Intent received by the BroadcastReceiver
     * @param TAG     class name (or other tag) for debugging purposes.
     */
    public static void startReminderService(Context context, Intent intent, String TAG) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        lock.acquire(10 * 1000);
        Intent tripService = new Intent(context, TripService.class);
        tripService.setAction(intent.getAction());
        tripService.setData(intent.getData());
        context.startService(tripService);
    }
}
