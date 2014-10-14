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

        StopTime() {
            stopId = "";
            stopHeadsign = "";
            arrivalTime = 0;
            departureTime = 0;
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
}
