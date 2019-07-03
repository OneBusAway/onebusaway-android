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
package org.onebusaway.android.travelbehavior.model;

import android.location.Location;
import android.os.Build;

import java.util.List;

public class TravelBehaviorInfo {

    public static class TravelBehaviorActivity {
        public String detectedActivity;
        public String detectedActivityType;
        public Integer confidenceLevel;

        public TravelBehaviorActivity() {
        }

        public TravelBehaviorActivity(String detectedActivity, String detectedActivityType) {
            this.detectedActivity = detectedActivity;
            this.detectedActivityType = detectedActivityType;
        }
    }

    public static class LocationInfo {
        public Double lat = null;

        public Double lon = null;

        public Long time = null;

        public Long elapsedRealtimeNanos = null;

        public Double altitude = null;

        public String provider = null;

        public Float accuracy = null;

        public Float bearing = null;

        public Float verticalAccuracyMeters = null;

        public Float bearingAccuracyDegrees = null;

        public Float speed = null;

        public Float speedAccuracyMetersPerSecond = null;

        public Boolean isFromMockProvider = null;

        public LocationInfo() {
        }

        public LocationInfo(Location location) {
            if (location == null) return;

            this.lat = location.getLatitude();
            this.lon = location.getLongitude();
            this.time = location.getTime();
            this.altitude = location.hasAltitude()? location.getAltitude(): null;
            this.provider = location.getProvider();
            this.accuracy = location.hasAccuracy() ? location.getAccuracy(): null;
            this.bearing = location.hasBearing() ? location.getBearing(): null;
            this.speed = location.hasSpeed() ? location.getSpeed() : null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.verticalAccuracyMeters = location.hasVerticalAccuracy() ?
                        location.getVerticalAccuracyMeters() : null;
                this.bearingAccuracyDegrees = location.hasBearingAccuracy() ?
                        location.getBearingAccuracyDegrees() : null;
                this.speedAccuracyMetersPerSecond = location.hasSpeedAccuracy() ?
                        location.getSpeedAccuracyMetersPerSecond() : null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                this.elapsedRealtimeNanos = location.getElapsedRealtimeNanos();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                isFromMockProvider = location.isFromMockProvider();
            }
        }
    }

    public List<TravelBehaviorActivity> activities;

    public List<LocationInfo> locationInfoList;

    public Boolean isIgnoringBatteryOptimizations;

    public TravelBehaviorInfo() {
    }

    public TravelBehaviorInfo(List<TravelBehaviorActivity> activities,
                              Boolean isIgnoringBatteryOptimizations) {
        this.activities = activities;
        this.isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations;
    }
}
