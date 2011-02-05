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

public final class ObaTripStatusElement implements ObaTripStatus {
    protected static final ObaTripStatusElement EMPTY_OBJECT = new ObaTripStatusElement();

    private final long serviceDate;
    private final boolean predicted;
    private final long scheduleDeviation;
    private final String vehicleId;
    private final String closestStop;
    private final long closestStopTimeOffset;
    private final Position position;
    private final String activeTripId;
    private final Double distanceAlongTrip;
    private final Double scheduledDistanceAlongTrip;
    private final Double totalDistanceAlongTrip;
    private final Double orientation;
    private final String nextStop;
    private final long nextStopTimeOffset;
    private final String phase;
    private final String status;
    private final Long lastUpdateTime;
    private final Position lastKnownLocation;
    private final Double lastKnownOrientation;

    ObaTripStatusElement() {
        serviceDate = 0;
        predicted = false;
        scheduleDeviation = 0;
        vehicleId = "";
        closestStop = "";
        closestStopTimeOffset = 0;
        position = null;
        activeTripId = null;
        distanceAlongTrip = null;
        scheduledDistanceAlongTrip = null;
        totalDistanceAlongTrip = null;
        orientation = null;
        nextStop = null;
        nextStopTimeOffset = 0;
        phase = null;
        status = null;
        lastUpdateTime = null;
        lastKnownLocation = null;
        lastKnownOrientation = null;
    }

    @Override
    public long getServiceDate() {
        return serviceDate;
    }

    @Override
    public boolean isPredicted() {
        return predicted;
    }

    @Override
    public long getScheduleDeviation() {
        return scheduleDeviation;
    }

    @Override
    public String getVehicleId() {
        return vehicleId;
    }

    @Override
    public String getClosestStop() {
        return closestStop;
    }

    @Override
    public long getClosestStopTimeOffset() {
        return closestStopTimeOffset;
    }

    @Override
    public GeoPoint getPosition() {
        return (position != null) ? position.getPoint() : null;
    }

    @Override
    public String getActiveTripId() {
        return activeTripId;
    }

    @Override
    public Double getDistanceAlongTrip() {
        return distanceAlongTrip;
    }

    @Override
    public Double getScheduledDistanceAlongTrip() {
        return scheduledDistanceAlongTrip;
    }

    @Override
    public Double getTotalDistanceAlongTrip() {
        return totalDistanceAlongTrip;
    }

    @Override
    public Double getOrientation() {
        return orientation;
    }

    @Override
    public String getNextStop() {
        return nextStop;
    }

    @Override
    public Long getNextStopTimeOffset() {
        return nextStopTimeOffset;
    }

    @Override
    public String getPhase() {
        return phase;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public GeoPoint getLastKnownLocation() {
        return (lastKnownLocation != null) ? lastKnownLocation.getPoint() : null;
    }

    @Override
    public Double getLastKnownOrientation() {
        return lastKnownOrientation;
    }
}
