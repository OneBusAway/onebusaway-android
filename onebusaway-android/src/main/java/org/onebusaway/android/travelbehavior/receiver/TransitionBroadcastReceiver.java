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
package org.onebusaway.android.travelbehavior.receiver;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.DocumentReference;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.io.worker.ArrivalsAndDeparturesDataReaderWorker;
import org.onebusaway.android.travelbehavior.io.worker.DestinationReminderReaderWorker;
import org.onebusaway.android.travelbehavior.io.worker.TripPlanDataReaderWorker;
import org.onebusaway.android.travelbehavior.model.DeviceInformation;
import org.onebusaway.android.travelbehavior.model.TravelBehaviorInfo;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorFirebaseIOUtils;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorUtils;
import org.onebusaway.android.util.PermissionUtils;
import org.onebusaway.android.util.PreferenceUtils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import static android.content.Context.ACCESSIBILITY_SERVICE;

public class TransitionBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "ActivityTransition";

    private Context mContext;
    private List<TravelBehaviorInfo.TravelBehaviorActivity> mActivityList;
    private String mUid;
    private String mRecordId;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            if (ActivityTransitionResult.hasResult(intent)) {
                mContext = context;

                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                if (result == null) {
                    return;
                }

                StringBuilder sb = new StringBuilder();
                mActivityList = new ArrayList<>();

                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    sb.append(TravelBehaviorUtils.toActivityString(event.getActivityType())).append(" -- ");
                    sb.append(TravelBehaviorUtils.toTransitionType(event.getTransitionType())).append("\n");
                    mActivityList.add(new TravelBehaviorInfo.TravelBehaviorActivity(
                            TravelBehaviorUtils.toActivityString(event.getActivityType()),
                            TravelBehaviorUtils.toTransitionType(event.getTransitionType()),
                            event.getElapsedRealTimeNanos()));
                }

                Log.d(TAG, "Detected activity transition: " + sb.toString());
                TravelBehaviorUtils.showDebugToastMessageWithVibration(
                        "Detected activity transition: " + sb.toString(), mContext);

                mUid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
                saveTravelBehavior();
            }
        }
    }

    private void saveTravelBehavior() {
        saveTravelBehavior(new TravelBehaviorInfo(mActivityList,
                Application.isIgnoringBatteryOptimizations(mContext)));

        startSaveTripPlansWorker();

        startSaveArrivalAndDepartureWorker();

        startSaveDestinationRemindersWorker();

        requestActivityRecognition();

        requestLocationUpdates();
    }

    private void saveTravelBehavior(TravelBehaviorInfo tbi) {
        long riPrefix = PreferenceUtils.getLong(TravelBehaviorConstants.RECORD_ID, 0);
        mRecordId = riPrefix++ + "-" + UUID.randomUUID().toString();
        PreferenceUtils.saveLong(TravelBehaviorConstants.RECORD_ID, riPrefix);

        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(mUid, mRecordId,
                        TravelBehaviorConstants.FIREBASE_ACTIVITY_TRANSITION_FOLDER);

        document.set(tbi).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Activity transition document added with ID " + document.getId());
            } else {
                TravelBehaviorFirebaseIOUtils.logErrorMessage(task.getException(),
                        "Activity transition document failed to be added: ");
            }
        });
    }

    private void startSaveArrivalAndDepartureWorker() {
        Data myData = new Data.Builder()
                .putString(TravelBehaviorConstants.USER_ID, mUid)
                .putString(TravelBehaviorConstants.RECORD_ID, mRecordId)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.
                Builder(ArrivalsAndDeparturesDataReaderWorker.class)
                .setInputData(myData)
                .build();
        WorkManager.getInstance().enqueue(workRequest);
    }

    private void startSaveTripPlansWorker() {
        Data myData = new Data.Builder()
                .putString(TravelBehaviorConstants.USER_ID, mUid)
                .putString(TravelBehaviorConstants.RECORD_ID, mRecordId)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(TripPlanDataReaderWorker.class)
                .setInputData(myData)
                .build();
        WorkManager.getInstance().enqueue(workRequest);
    }

    private void startSaveDestinationRemindersWorker() {
        Data myData = new Data.Builder()
                .putString(TravelBehaviorConstants.USER_ID, mUid)
                .putString(TravelBehaviorConstants.RECORD_ID, mRecordId)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DestinationReminderReaderWorker.class)
                .setInputData(myData)
                .build();
        WorkManager.getInstance().enqueue(workRequest);
    }

    private void requestActivityRecognition() {
        ActivityRecognitionClient client = ActivityRecognition.getClient(mContext);
        Intent intent = new Intent(mContext, RecognitionBroadcastReceiver.class);
        intent.putExtra(TravelBehaviorConstants.RECORD_ID, mRecordId);

        int reqCode = PreferenceUtils.getInt(TravelBehaviorConstants.RECOGNITION_REQUEST_CODE, 0);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, reqCode++, intent, PendingIntent.FLAG_ONE_SHOT);
        PreferenceUtils.saveInt(TravelBehaviorConstants.RECOGNITION_REQUEST_CODE, reqCode);
        client.requestActivityUpdates(TimeUnit.SECONDS.toMillis(10), pi);
    }

    private void requestLocationUpdates() {
        String[] requiredPermissions = {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};
        if (PermissionUtils.hasGrantedPermissions(mContext, requiredPermissions)) {
            Log.d(TAG, "Location permissions are granted, requesting fused, GPS, and Network" +
                    "locations");
            requestFusedLocation();
            requestGPSNetworkLocation();
        } else {
            Log.d(TAG, "Location permissions not granted. Skipping location requests");
        }
    }

    @SuppressLint("MissingPermission")
    private void requestFusedLocation() {
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(mContext);
        client.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                TravelBehaviorFirebaseIOUtils.saveLocation(location, mUid, mRecordId);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void requestGPSNetworkLocation() {
        LocationManager lm = (LocationManager) Application.get().getBaseContext()
                .getSystemService(Context.LOCATION_SERVICE);

        if (lm == null) return;

        List<String> providers = lm.getProviders(true);
        for (String provider : providers) {
            if (LocationManager.PASSIVE_PROVIDER.equals(provider)) continue;

            int reqCode = PreferenceUtils.getInt(TravelBehaviorConstants.LOCATION_REQUEST_CODE, 0);
            Intent intent = new Intent(mContext, LocationBroadcastReceiver.class);
            intent.putExtra(TravelBehaviorConstants.RECORD_ID, mRecordId);
            PendingIntent pi = PendingIntent.getBroadcast(mContext, reqCode++, intent, PendingIntent.FLAG_ONE_SHOT);
            lm.requestLocationUpdates(provider, 0, 0, pi);
            PreferenceUtils.saveInt(TravelBehaviorConstants.LOCATION_REQUEST_CODE, reqCode);
        }
    }
}
