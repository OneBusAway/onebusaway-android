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
package org.onebusaway.android.io.elements;

import org.onebusaway.android.util.LocationUtils;

import android.location.Location;

import java.io.Serializable;

public interface ObaTripStatus {

    public static final class Position implements Serializable {

        private double lat = 0;

        private double lon = 0;

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
        }

        public Location getLocation() {
            return LocationUtils.makeLocation(lat, lon);
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
     * position of the transit vehicle among the stop times of the current trip.
     * If the number is positive, the stop is coming up. If negative, the stop
     * has already been passed.
     */
    public long getClosestStopTimeOffset();

    /**
     * @return The current position of the transit vehicle. This element is optional,
     * and will only be present if the trip is actively running.
     * If real-time arrival data is available, the position will take that into account,
     * otherwise the position reflects the scheduled position of the vehicle.
     */
    public Location getPosition();

    /**
     * @return The trip ID of the trip the vehicle is actively serving.
     * All trip-specific values will be in reference to this active trip.
     * This can be null if it not provided.
     */
    public String getActiveTripId();

    /**
     * @return The distance, in meters, the transit vehicle has progressed along
     * the active trip; or null if this isn't provided.
     */
    public Double getDistanceAlongTrip();

    /**
     * @return The distance, in meters, the transit vehicle is scheduled to have
     * progressed along the active trip; or null if it isn't provided.
     */
    public Double getScheduledDistanceAlongTrip();

    /**
     * @return The total length of the trip, in meters; or null if it isn't provided.
     */
    public Double getTotalDistanceAlongTrip();

    /**
     * @return The orientation of the transit vehicle, as an angle in degrees. Can be null.
     */
    public Double getOrientation();

    /**
     * @return Similar to getClosestStop(), but always retrieves the next stop. Can be null.
     */
    public String getNextStop();

    /**
     * @return Similar to getClosestStopTimeOffset(), but always retrieves the next stop.
     * Can be null.
     */
    public Long getNextStopTimeOffset();

    /**
     * @return The current journey phase. Can be null.
     */
    public String getPhase();

    /**
     * @return The status modifiers for the trip. Can be null.
     */
    public String getStatus();

    /**
     * @return The last known real-time update for the transit vehicle, or 0 if we
     * haven't heard from the vehicle.
     */
    public long getLastUpdateTime();

    /**
     * @return The last known location of the transit vehicle. Can be null.
     * This differs from the position, in that the position is potentially
     * extrapolated forward from the last known position and other data.
     */
    public Location getLastKnownLocation();

    /**
     * @return The last known real-time location update from the transit vehicle. This is different
     * from lastUpdateTime in that it reflects the last know location update. An update from a
     * vehicle might not contain location info, which means this field will not be updated. Will be
     * zero if we haven’t had a location update from the vehicle.
     */
    public long getLastLocationUpdateTime();

    /**
     * @return The last known orientation value received in real-time from the transit vehicle.
     * Can be null.
     */
    public Double getLastKnownOrientation();

    /**
     * @return The index of the active trip into the sequence of trips for the active block.
     * Compare to blockTripSequence in the ArrivalInfo element to determine where the active block
     * location is relative to an arrival-and-departure.
     */
    public int getBlockTripSequence();
}
