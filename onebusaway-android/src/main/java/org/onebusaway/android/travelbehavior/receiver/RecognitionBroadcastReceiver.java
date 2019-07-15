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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.firebase.firestore.DocumentReference;

import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.model.TravelBehaviorInfo;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorFirebaseIOUtils;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorUtils;
import org.onebusaway.android.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecognitionBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "ActivityRecognition";

    private Map<String, Integer> mDetectedActivityMap;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                mDetectedActivityMap = new HashMap<>();
                StringBuilder sb = new StringBuilder();
                for (DetectedActivity da: result.getProbableActivities()) {
                    mDetectedActivityMap.put(TravelBehaviorUtils.
                            toActivityString(da.getType()), da.getConfidence());
                    sb.append(TravelBehaviorUtils.toActivityString(da.getType())).append(" -- ");
                    sb.append("confidence level: ").append(da.getConfidence()).append("\n");
                }

                Log.d(TAG, "Detected activity recognition: " + sb.toString());
                String recordId = intent.getStringExtra(TravelBehaviorConstants.RECORD_ID);
                readActivitiesByRecordId(recordId);
            }
        }
    }

    private void readActivitiesByRecordId(String recordId) {
        String uid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(uid, recordId,
                        TravelBehaviorConstants.FIREBASE_ACTIVITY_TRANSITION_FOLDER);
        document.get().addOnSuccessListener(documentSnapshot -> {
            if(documentSnapshot.exists()) {
                Log.d(TAG, "Read document successful RecognitionBroadcastReceiver");
                TravelBehaviorInfo tbi = documentSnapshot.toObject(TravelBehaviorInfo.class);
                if (tbi != null) {
                    updateTravelBehavior(tbi, recordId);
                } else {
                    Log.d(TAG, "TravelBehaviorInfo is null");
                }
            } else {
                Log.d(TAG, "Read document FAILED RecognitionBroadcastReceiver");
            }
        });
    }

    private void updateTravelBehavior(TravelBehaviorInfo tbi, String recordId) {
        String uid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(uid, recordId,
                        TravelBehaviorConstants.FIREBASE_ACTIVITY_TRANSITION_FOLDER);

        List<Map<String,Object>> list=new ArrayList<>();
        for (TravelBehaviorInfo.TravelBehaviorActivity tba: tbi.activities) {
            tba.confidenceLevel = mDetectedActivityMap.get(tba.detectedActivity);;
            Map<String, Object> updateMap = new HashMap();
            updateMap.put("detectedActivity", tba.detectedActivity);
            updateMap.put("detectedActivityType", tba.detectedActivityType);
            updateMap.put("confidenceLevel", tba.confidenceLevel);
            updateMap.put("eventElapsedRealtimeNanos", tba.eventElapsedRealtimeNanos);
            updateMap.put("systemClockElapsedRealtimeNanos", tba.systemClockElapsedRealtimeNanos);
            updateMap.put("systemClockCurrentTimeMillis", tba.systemClockCurrentTimeMillis);
            updateMap.put("numberOfNanosInThePastWhenEventHappened", tba.numberOfNanosInThePastWhenEventHappened);
            updateMap.put("eventTimeMillis", tba.eventTimeMillis);
            list.add(updateMap);
        }

        document.update("activities", list).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Update travel behavior successful.");
            } else {
                TravelBehaviorFirebaseIOUtils.logErrorMessage(task.getException(),
                        "Update travel behavior failed: ");
            }
        });
    }
}
