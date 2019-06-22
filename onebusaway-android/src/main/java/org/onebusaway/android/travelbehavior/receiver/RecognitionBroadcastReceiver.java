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

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.model.TravelBehaviorInfo;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorUtils;
import org.onebusaway.android.util.PreferenceUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

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

                Log.v(TAG, "Detected activity recognition: " + sb.toString());
                String recordId = intent.getStringExtra(TravelBehaviorConstants.RECORD_ID);
                readActivitiesByRecordId(recordId);
            }
        }
    }

    private void readActivitiesByRecordId(String recordId) {
        String uid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append("users/").append(uid).append("/").
                append(TravelBehaviorConstants.FIREBASE_ACTIVITY_TRANSITION_FOLDER);

        DocumentReference document = db.collection(pathBuilder.toString()).document(recordId);
        document.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                if(documentSnapshot.exists()) {
                    Log.v(TAG, "Read document successful RecognitionBroadcastReceiver");
                    TravelBehaviorInfo tbi = documentSnapshot.toObject(TravelBehaviorInfo.class);
                    updateTravelBehavior(tbi, recordId);
                } else {
                    Log.v(TAG, "Read document FAILED RecognitionBroadcastReceiver");
                }
            }
        });
    }

    private void updateTravelBehavior(TravelBehaviorInfo tbi, String recordId) {
        String uid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append("users/").append(uid).append("/").append("activity-transitions");

        DocumentReference document = db.collection(pathBuilder.toString()).document(recordId);
        List<Map<String,Object>> list=new ArrayList<>();
        for (TravelBehaviorInfo.TravelBehaviorActivity tba: tbi.activities) {
            Integer confidence = mDetectedActivityMap.get(tba.detectedActivity);
            tba.confidenceLevel = confidence;
            Map<String, Object> updateMap = new HashMap();
            updateMap.put("detectedActivity", tba.detectedActivity);
            updateMap.put("detectedActivityType", tba.detectedActivityType);
            updateMap.put("confidenceLevel", tba.confidenceLevel);
            list.add(updateMap);
        }

        document.update("activities", list).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.v(TAG, "Update travel behavior successful.");
                } else {
                    Log.v(TAG, "Update travel behavior failed: " + task.getException().getMessage());
                    task.getException().printStackTrace();
                }
            }
        });
    }
}
