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

import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.DetectedActivity;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.model.TravelBehaviorInfo;
import org.onebusaway.android.util.PreferenceUtils;

import android.content.Context;
import android.os.Vibrator;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class TravelBehaviorUtils {

    public static String toActivityString(int activity) {
        switch (activity) {
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.WALKING:
                return "WALKING";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            default:
                return "UNKNOWN = " + activity;
        }
    }

    public static String toTransitionType(int transitionType) {
        switch (transitionType) {
            case ActivityTransition.ACTIVITY_TRANSITION_ENTER:
                return "ENTER";
            case ActivityTransition.ACTIVITY_TRANSITION_EXIT:
                return "EXIT";
            default:
                return "UNKNOWN";
        }
    }

    public static Map getLocationMapByLocationInfo(TravelBehaviorInfo.LocationInfo locationInfo) {
        Map<String, Object> m = new HashMap<>();
        if (locationInfo == null) return m;
        m.put("lat", locationInfo.lat);
        m.put("lon", locationInfo.lon);
        m.put("time", locationInfo.time);
        m.put("elapsedRealtimeNanos", locationInfo.elapsedRealtimeNanos);
        m.put("altitude", locationInfo.altitude);
        m.put("provider", locationInfo.provider);
        m.put("accuracy", locationInfo.accuracy);
        m.put("verticalAccuracyMeters", locationInfo.verticalAccuracyMeters);
        m.put("bearingAccuracyDegrees", locationInfo.bearingAccuracyDegrees);
        m.put("bearing", locationInfo.bearing);
        m.put("speed", locationInfo.speed);
        m.put("speedAccuracyMetersPerSecond", locationInfo.speedAccuracyMetersPerSecond);
        m.put("isFromMockProvider", locationInfo.isFromMockProvider);
        return m;
    }

    public static boolean isTravelBehaviorActiveInRegion() {
        ObaRegion currentRegion = Application.get().getCurrentRegion();
        return currentRegion != null && currentRegion.isTravelBehaviorDataCollectionEnabled();
    }

    public static boolean allowEnrollMoreParticipantsInStudy() {
        ObaRegion currentRegion = Application.get().getCurrentRegion();
        return currentRegion != null && currentRegion.isEnrollParticipantsInStudy();
    }

    public static boolean isUserParticipatingInStudy() {
        return isTravelBehaviorActiveInRegion() &&
                !PreferenceUtils.getBoolean(TravelBehaviorConstants.USER_OPT_OUT,
                false) && PreferenceUtils.getBoolean(TravelBehaviorConstants.USER_OPT_IN,
                false);
    }

    public static void showDebugToastMessageWithVibration(String message, Context context) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(200);
        }
    }
}
