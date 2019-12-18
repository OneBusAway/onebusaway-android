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
package org.onebusaway.android.travelbehavior.constants;

import android.Manifest;

import java.util.concurrent.TimeUnit;

public class TravelBehaviorConstants {

    public static final String RECORD_ID = "tbRecordId";

    public static final String USER_ID = "tbUserId";

    public static final String USER_EMAIL = "tbUserEmail";

    public static final String PARTICIPANT_SERVICE_RESULT = "STATUS OK";

    public static final String LOCATION_REQUEST_CODE = "tbRequestCode";

    public static final String RECOGNITION_REQUEST_CODE = "tbRecognitionRequestCode";

    public static final String TRIP_PLAN_COUNTER = "tripPlanCounter";

    public static final String ARRIVAL_LIST_COUNTER = "arrivalCounter";

    public static final String DEVICE_INFO_HASH = "deviceInfoHash";

    public static final String DESTINATION_REMINDER_COUNTER = "destinationReminderCounter";

    public static final String FIREBASE_ACTIVITY_TRANSITION_FOLDER = "activity-transitions";

    public static final String FIREBASE_ARRIVAL_AND_DEPARTURE_FOLDER = "arrival-and-departures";

    public static final String FIREBASE_TRIP_PLAN_FOLDER = "trip-plans";

    public static final String FIREBASE_DESTINATION_REMINDER_FOLDER = "destination-reminders";

    public static final String FIREBASE_DEVICE_INFO_FOLDER = "device-information";

    public static final String LOCAL_TRIP_PLAN_FOLDER = "trip-plans";

    public static final String LOCAL_ARRIVAL_AND_DEPARTURE_FOLDER = "arrival-and-departures";

    public static final String LOCAL_DESTINATION_REMINDER_FOLDER = "destination-reminders";

    private static final long MOST_RECENT_DATA_THRESHOLD_MINUTES = 30;

    public static final long MOST_RECENT_DATA_THRESHOLD_MILLIS = TimeUnit.MINUTES.toMillis(MOST_RECENT_DATA_THRESHOLD_MINUTES);

    public static final long MOST_RECENT_DATA_THRESHOLD_NANO = TimeUnit.MINUTES.toNanos(MOST_RECENT_DATA_THRESHOLD_MINUTES);

    public static final String USER_OPT_IN = "travelBehaviorUserOptIn";

    public static final String USER_OPT_OUT = "travelBehaviorUserOptOut";

    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
    };
}
