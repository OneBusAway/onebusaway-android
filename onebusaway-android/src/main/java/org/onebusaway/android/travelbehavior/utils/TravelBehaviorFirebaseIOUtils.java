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
package org.onebusaway.android.travelbehavior.utils;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.model.ArrivalAndDepartureInfo;
import org.onebusaway.android.travelbehavior.model.DestinationReminderInfo;
import org.onebusaway.android.travelbehavior.model.DeviceInformation;
import org.onebusaway.android.travelbehavior.model.TravelBehaviorInfo;
import org.onebusaway.android.travelbehavior.model.TripPlanInfo;
import org.onebusaway.android.util.PreferenceUtils;

import android.location.Location;
import android.util.Log;

import java.util.List;
import java.util.Map;

public class TravelBehaviorFirebaseIOUtils {

    private static final String TAG = "TravelBehaviorFirebase";

    private static String buildDocumentPathByUid(String uid, String folder) {
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append("users/").append(uid).append("/").
                append(folder);
        return pathBuilder.toString();
    }

    private static DocumentReference getFirebaseDocReferenceByUserIdAndRecordId(String userId,
                                                                                String recordId,
                                                                                String folder) {
        String path = TravelBehaviorFirebaseIOUtils.buildDocumentPathByUid(userId, folder);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.collection(path).document(recordId);
    }

    public static void saveLocation(Location location, String userId, String recordId) {
        TravelBehaviorInfo.LocationInfo locationInfo = new TravelBehaviorInfo.LocationInfo(location);
        Map locationMap = TravelBehaviorUtils.getLocationMapByLocationInfo(locationInfo);

        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(userId, recordId,
                        TravelBehaviorConstants.FIREBASE_ACTIVITY_TRANSITION_FOLDER);
        document.update("locationInfoList", FieldValue.arrayUnion(locationMap)).
                addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.v(TAG, "Location update saved with provider: " +
                                location.getProvider());
                    } else {
                        Log.v(TAG, "Location update failed: " +
                                task.getException().getMessage());
                        task.getException().printStackTrace();
                    }
                });
    }

    public static void saveArrivalsAndDepartures(List<ArrivalAndDepartureInfo.ArrivalAndDepartureData> arrivalAndDepartureList,
                                                 String userId, String recordId) {
        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(userId, recordId,
                        TravelBehaviorConstants.FIREBASE_ARRIVAL_AND_DEPARTURE_FOLDER);

        document.set(new ArrivalAndDepartureInfo(arrivalAndDepartureList)).
                addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.v(TAG, "Arrivals and departure are saved with ID: " +
                                document.getId());
                    } else {
                        Log.v(TAG, "Arrivals and departure are failed to be saved " +
                                task.getException().getMessage());
                        task.getException().printStackTrace();
                    }
                });
    }

    public static void saveTripPlans(List<TripPlanInfo.TripPlanData> tripPlanDataList,
                                                 String userId, String recordId) {
        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(userId, recordId,
                        TravelBehaviorConstants.FIREBASE_TRIP_PLAN_FOLDER);

        document.set(new TripPlanInfo(tripPlanDataList)).
                addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.v(TAG, "Trip plans are saved with ID: " +
                                document.getId());
                    } else {
                        Log.v(TAG, "Trip plans are failed to be saved " +
                                task.getException().getMessage());
                        task.getException().printStackTrace();
                    }
                });
    }

    public static void saveDestinationReminders(List<DestinationReminderInfo.DestinationReminderData> reminderData,
                                     String userId, String recordId) {
        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(userId, recordId,
                        TravelBehaviorConstants.FIREBASE_DESTINATION_REMINDER_FOLDER);

        document.set(new DestinationReminderInfo(reminderData)).
                addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.v(TAG, "Destination reminders are saved with ID: " +
                                document.getId());
                    } else {
                        Log.v(TAG, "Destination reminders are failed to be saved " +
                                task.getException().getMessage());
                        task.getException().printStackTrace();
                    }
                });
    }

    public static void saveDeviceInfo(DeviceInformation deviceInformation, String userId,
                                      String recordId, int hashCode) {
        DocumentReference document = TravelBehaviorFirebaseIOUtils.
                getFirebaseDocReferenceByUserIdAndRecordId(userId, recordId,
                        TravelBehaviorConstants.FIREBASE_DEVICE_INFO_FOLDER);

        document.set(deviceInformation).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.v(TAG, "Device Info document added with ID " + document.getId());
                PreferenceUtils.saveInt(TravelBehaviorConstants.DEVICE_INFO_HASH, hashCode);
            } else {
                Log.v(TAG, "Device Info transition document failed to be added: " +
                        task.getException().getMessage());
                task.getException().printStackTrace();
            }
        });
    }
}
