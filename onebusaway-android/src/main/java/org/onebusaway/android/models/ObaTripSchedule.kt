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
package org.onebusaway.android.models

/**
 * The schedule for a trip: the ordered stop times plus the neighboring trips in the block. The
 * concrete holder lives in `api`; the schedule-geometry logic lives in `extrapolation`.
 */
interface ObaTripSchedule {

    /** The ordered stop times for the trip. */
    val stopTimes: Array<StopTime>

    /** The timezone the schedule times are expressed in. */
    val timeZone: String?

    /** The ID of the previous trip in the block, or null. */
    val previousTripId: String?

    /** The ID of the next trip in the block, or null. */
    val nextTripId: String?

    /** A single scheduled stop along the trip. */
    interface StopTime {

        /** The ID of the stop. */
        val stopId: String

        /** The passenger-facing headsign at this stop, or null. */
        val headsign: String?

        /** The scheduled arrival time, in seconds since the service start date. */
        val arrivalTime: Long

        /** The scheduled departure time, in seconds since the service start date. */
        val departureTime: Long

        /** The average historical occupancy when the vehicle arrives at this stop, or null. */
        val historicalOccupancy: Occupancy?

        /** The predicted occupancy when the vehicle arrives at this stop, or null. */
        val predictedOccupancy: Occupancy?

        /** The distance, in meters, of this stop along the trip. */
        val distanceAlongTrip: Double
    }
}
