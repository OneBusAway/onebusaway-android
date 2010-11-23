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
import com.joulespersecond.oba.ObaApi;

public final class ObaTripStatusElement implements ObaTripStatus {
    protected static final ObaTripStatusElement EMPTY_OBJECT = new ObaTripStatusElement();

    private final long serviceDate;
    private final boolean predicted;
    private final long scheduleDeviation;
    private final String vehicleId;
    private final String closestStop;
    private final long closestStopTimeOffset;
    private final Position position;

    ObaTripStatusElement() {
        serviceDate = 0;
        predicted = false;
        scheduleDeviation = 0;
        vehicleId = "";
        closestStop = "";
        closestStopTimeOffset = 0;
        position = null;
    }

    /**
     * @return The time, in milliseconds since the epoch, of midnight
     * for start of the service day for the trip.
     */
    public long getServiceDate() {
        return serviceDate;
    }

    /**
     * @return 'true' if we have real-time arrival info available for this trip.
     */
    public boolean isPredicted() {
        return predicted;
    }

    /**
     * @return If real-time arrival info is available, this lists the deviation
     * from the schedule in seconds, where positive number indicates the trip
     * is running late and negative indicates the trips is running early.
     * If not real-time arrival info is available, this will be zero.
     */
    public long getScheduleDeviation() {
        return scheduleDeviation;
    }

    /**
     * @return If real-time arrival info is available, this lists the id of the transit
     * vehicle currently running the trip.
     */
    public String getVehicleId() {
        return vehicleId;
    }

    /**
     * @return The ID of the closest stop to the current location of the transit vehicle,
     * whether from schedule or real-time predicted location data
     */
    public String getClosestStop() {
        return closestStop;
    }

    /**
     * @return The time offset, in seconds, from the closest stop to the current
     *  position of the transit vehicle among the stop times of the current trip.
     *  If the number is positive, the stop is coming up. If negative, the stop
     *  has already been passed.
     */
    public long getClosestStopTimeOffset() {
        return closestStopTimeOffset;
    }

    /**
     * @return The current position of the transit vehicle. This element is optional,
     * and will only be present if the trip is actively running.
     * If real-time arrival data is available, the position will take that into account,
     * otherwise the position reflects the scheduled position of the vehicle.
     */
    public GeoPoint getPosition() {
        return (position != null) ? ObaApi.makeGeoPoint(position.getLat(), position.getLon()) : null;
    }
}
