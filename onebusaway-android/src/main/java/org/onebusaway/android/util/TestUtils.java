/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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

import org.onebusaway.android.BuildConfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A class containing utility methods related to unit tests
 */
public class TestUtils {

    static String CATEGORY_TEST = BuildConfig.APPLICATION_ID + ".category.TEST";

    static String ACTION_LOAD_FINISHED = BuildConfig.APPLICATION_ID + ".LOAD_FINISHED";

    static void notifyLoadFinished(Context context) {
        sendBroadcast(context, CATEGORY_TEST, ACTION_LOAD_FINISHED);
    }

    static void sendBroadcast(Context context, String action, String category) {
        if (BuildConfig.DEBUG) {
            Intent intent = new Intent();
            intent.setAction(action);
            intent.addCategory(category);

            context.sendBroadcast(intent);
        }
    }

    public static void waitForLoadFinished(Context context) throws InterruptedException {
        waitForBroadcast(context, ACTION_LOAD_FINISHED, CATEGORY_TEST);
    }

    public static void waitForBroadcast(Context context, String action, String category)
            throws InterruptedException {
        final CountDownLatch signal = new CountDownLatch(1);

        IntentFilter intentFilter = new IntentFilter(action);
        intentFilter.addCategory(category);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                signal.countDown();
            }
        };

        context.registerReceiver(broadcastReceiver, intentFilter);
        signal.await(10000, TimeUnit.MILLISECONDS);
        context.unregisterReceiver(broadcastReceiver);
        Thread.sleep(1000);
    }

    /**
     * Returns true if tests are running on an emulator, false if tests are running
     * on an actual device
     *
     * @return true if tests are running on an emulator, false if tests are running
     * on an actual device
     */
    public static boolean isRunningOnEmulator() {
        return Build.FINGERPRINT.contains("generic");
    }
}
