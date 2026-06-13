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

public final class ObaTripSchedule {

    public static final ObaTripSchedule EMPTY_OBJECT = new ObaTripSchedule();

    public static final class StopTime {

        //private static final StopTime EMPTY_OBJECT = new StopTime();
        private static final StopTime[] EMPTY_ARRAY = new StopTime[]{};

        private final String stopId;

        private final String stopHeadsign;

        private final long arrivalTime;

        private final long departureTime;

        private final String historicalOccupancy;

        private final String predictedOccupancy;

        private final double distanceAlongTrip;

        StopTime() {
            stopId = "";
            stopHeadsign = "";
            arrivalTime = 0;
            departureTime = 0;
            historicalOccupancy = "";
            predictedOccupancy = "";
            distanceAlongTrip = 0;
        }

        /**
         * @return The stop ID of the stop visited during the trip.
         */
        public String getStopId() {
            return stopId;
        }

        /**
         * @return The headsign of the trip.
         */
        public String getHeadsign() {
            return stopHeadsign;
        }

        /**
         * @return The time, in seconds since the service start date,
         * when the trip arrivals at the specified stop.
         */
        public long getArrivalTime() {
            return arrivalTime;
        }

        /**
         * @return The time, in seconds since the service start date,
         * when the trip leaves the specified stop.
         */
        public long getDepartureTime() {
            return departureTime;
        }

        /**
         * @return the average historical occupancy of the vehicle when it arrives at this stop, or null if the occupancy is unknown
         */
        public Occupancy getHistoricalOccupancy() {
            return Occupancy.fromString(historicalOccupancy);
        }

        /**
         * @return the predicted occupancy of the vehicle when it arrives at this stop, or null if the occupancy is unknown
         */
        public Occupancy getPredictedOccupancy() {
            return Occupancy.fromString(predictedOccupancy);
        }

        /**
         * @return The distance along the trip in meters when the vehicle arrives at this stop.
         */
        public double getDistanceAlongTrip() {
            return distanceAlongTrip;
        }
    }

    private final StopTime[] stopTimes;

    private final String timeZone;

    private final String previousTripId;

    private final String nextTripId;

    private ObaTripSchedule() {
        stopTimes = StopTime.EMPTY_ARRAY;
        timeZone = "";
        previousTripId = "";
        nextTripId = "";
    }

    /**
     * @return A list of stops visited during the course of the trip,
     * in addition to schedule information for those stops.
     */
    public StopTime[] getStopTimes() {
        return stopTimes;
    }

    /**
     * @return The default time zone for this trip.
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * @return If this trip is part of a block and has an incoming trip
     * from another route, this element will give the ID of the incoming
     * trip.
     */
    public String getPreviousTripId() {
        return previousTripId;
    }

    /**
     * @return If this trip is a part of a block and has an outgoing
     * trip to another route, this element will give the ID of the outgoing trip.
     */
    public String getNextTripId() {
        return nextTripId;
    }

    /**
     * Finds the index of the next stop the vehicle has not yet reached, based on
     * distance along the trip.
     *
     * @param distanceAlongTrip current distance along the trip in meters
     * @return index of the next stop, or stopTimes.length if past the last stop,
     *         or null if stopTimes is null or empty
     */
    public Integer findNextStopIndex(double distanceAlongTrip) {
        if (stopTimes == null || stopTimes.length == 0) {
            return null;
        }
        for (int i = 0; i < stopTimes.length; i++) {
            if (stopTimes[i].distanceAlongTrip > distanceAlongTrip) {
                return i;
            }
        }
        return stopTimes.length;
    }

    /**
     * Returns the scheduled start time of the trip in seconds since the service start date.
     *
     * @return the trip start time in seconds, or null if no stop times exist
     */
    public Long getStartTime() {
        if (stopTimes == null || stopTimes.length == 0) {
            return null;
        }
        return stopTimes[0].getDepartureTime();
    }

    /**
     * Finds the index of the first stop in the segment that brackets the given distance.
     * The segment spans from stopTimes[result] to stopTimes[result + 1].
     *
     * @param distanceAlongTrip the distance along the trip in meters
     * @return the index of the first stop in the segment
     * @throws IndexOutOfBoundsException if distanceAlongTrip is before the first stop,
     *         after the last stop, or if there are fewer than 2 stops
     */
    public int findSegmentStartIndex(double distanceAlongTrip) {
        if (stopTimes == null || stopTimes.length < 2) {
            throw new IndexOutOfBoundsException("Fewer than 2 stop times");
        }

        if (distanceAlongTrip < stopTimes[0].distanceAlongTrip) {
            throw new IndexOutOfBoundsException("Distance is before first stop");
        }

        if (distanceAlongTrip > stopTimes[stopTimes.length - 1].distanceAlongTrip) {
            throw new IndexOutOfBoundsException("Distance is after last stop");
        }

        for (int i = 0; i < stopTimes.length - 1; i++) {
            if (stopTimes[i].distanceAlongTrip <= distanceAlongTrip &&
                    distanceAlongTrip < stopTimes[i + 1].distanceAlongTrip) {
                return i;
            }
        }

        // At exactly the last stop's distance
        return stopTimes.length - 2;
    }
}
