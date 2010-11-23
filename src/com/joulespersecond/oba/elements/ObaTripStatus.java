/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.elements;

import com.google.android.maps.GeoPoint;

public interface ObaTripStatus {
    public static final class Position {
        private final double lat = 0;
        private final double lon = 0;

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
        }
    }

    /**
     * @return The time, in milliseconds since the epoch, of midnight
     * for start of the service day for the trip.
     */
    public long getServiceDate();

    /**
     * @return 'true' if we have real-time arrival info available for this trip.
     */
    public boolean isPredicted();

    /**
     * @return If real-time arrival info is available, this lists the deviation
     * from the schedule in seconds, where positive number indicates the trip
     * is running late and negative indicates the trips is running early.
     * If not real-time arrival info is available, this will be zero.
     */
    public long getScheduleDeviation();

    /**
     * @return If real-time arrival info is available, this lists the id of the transit
     * vehicle currently running the trip.
     */
    public String getVehicleId();

    /**
     * @return The ID of the closest stop to the current location of the transit vehicle,
     * whether from schedule or real-time predicted location data
     */
    public String getClosestStop();

    /**
     * @return The time offset, in seconds, from the closest stop to the current
     *  position of the transit vehicle among the stop times of the current trip.
     *  If the number is positive, the stop is coming up. If negative, the stop
     *  has already been passed.
     */
    public long getClosestStopTimeOffset();

    /**
     * @return The current position of the transit vehicle. This element is optional,
     * and will only be present if the trip is actively running.
     * If real-time arrival data is available, the position will take that into account,
     * otherwise the position reflects the scheduled position of the vehicle.
     */
    public GeoPoint getPosition();
}
