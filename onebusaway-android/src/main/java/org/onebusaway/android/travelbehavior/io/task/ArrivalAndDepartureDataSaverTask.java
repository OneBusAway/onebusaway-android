/*
 * Copyright (C) 2019 University of South Florida
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
package org.onebusaway.android.travelbehavior.io.task;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.io.TravelBehaviorFileSaverExecutorManager;
import org.onebusaway.android.travelbehavior.model.ArrivalAndDepartureData;
import org.onebusaway.android.util.PreferenceUtils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ArrivalAndDepartureDataSaverTask implements Runnable {

    private static final String TAG = "TravelBehaviorTripPlan";

    private ObaArrivalInfo[] mArrivalInfo;

    private long mServerTime;

    private String mStopId;

    private String mUrl;

    private Context mApplicationContext;

    public ArrivalAndDepartureDataSaverTask(ObaArrivalInfo[] arrivalInfo, long serverTime,
                                            String url, String stopId, Context applicationContext) {
        mArrivalInfo = arrivalInfo;
        mServerTime = serverTime;
        mStopId = stopId;
        mUrl = url;
        mApplicationContext = applicationContext;
    }

    @Override
    public void run() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                mApplicationContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                mApplicationContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            saveArrivalAndDepartureData(null);
        } else {
            requestFusedLocation();
        }

    }

    @SuppressLint("MissingPermission")
    private void requestFusedLocation() {
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(mApplicationContext);
        client.getLastLocation().addOnSuccessListener(TravelBehaviorFileSaverExecutorManager.
                getInstance().getThreadPoolExecutor(), location -> {
            saveArrivalAndDepartureData(location);
        });
    }

    private void saveArrivalAndDepartureData(Location location) {
        try {
            // Get the counter that's incremented for each test
            int counter = Application.getPrefs().getInt(TravelBehaviorConstants.ARRIVAL_LIST_COUNTER, 0);
            PreferenceUtils.saveInt(TravelBehaviorConstants.ARRIVAL_LIST_COUNTER, ++counter);

            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d yyyy, hh:mm aaa");
            Date time = Calendar.getInstance().getTime();
            String readableDate = sdf.format(time);

            File subFolder = new File(Application.get().getApplicationContext()
                    .getFilesDir().getAbsolutePath() + File.separator
                    + TravelBehaviorConstants.LOCAL_ARRIVAL_AND_DEPARTURE_FOLDER);

            if (!subFolder.exists()) {
                subFolder.mkdirs();
            }

            Long localElapsedRealtimeNanos = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                localElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
            }

            File file = new File(subFolder, counter + "-" + readableDate + ".json");
            ArrivalAndDepartureData add =
                    new ArrivalAndDepartureData(mArrivalInfo, mStopId,
                            Application.get().getCurrentRegion().getId(), mUrl, localElapsedRealtimeNanos,
                            time.getTime(), mServerTime);
            add.setLocation(location);

            // Used Gson instead of Jackson library - Jackson had problems while deserializing
            // nested objects.  When we deserialize the object and push it to Firebase, Firebase API
            // throws a null pointer exception.  Serializing and deserializing this arrival and
            // departure data with Gson fixed the problem.
            Gson gson = new Gson();
            String data = gson.toJson(add);

            FileUtils.write(file, data, false);
        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

}
