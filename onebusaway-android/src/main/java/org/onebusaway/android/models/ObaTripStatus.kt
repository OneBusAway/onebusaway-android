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

import android.location.Location

interface ObaTripStatus {

    /**
     * The time, in milliseconds since the epoch, of midnight for start of the service day for the
     * trip.
     */
    val serviceDate: Long

    /** 'true' if we have real-time arrival info available for this trip. */
    val isPredicted: Boolean

    /**
     * If real-time arrival info is available, the deviation from the schedule in seconds (positive =
     * running late, negative = early); zero if no real-time info is available.
     */
    val scheduleDeviation: Long

    /** If real-time info is available, the id of the transit vehicle currently running the trip. */
    val vehicleId: String?

    /** The ID of the closest stop to the current vehicle location (schedule or real-time). */
    val closestStop: String?

    /**
     * The time offset, in seconds, from the closest stop to the current position of the vehicle.
     * Positive = the stop is coming up; negative = already passed.
     */
    val closestStopTimeOffset: Long

    /**
     * The current position of the vehicle. Optional — only present if the trip is actively running.
     */
    val position: Location?

    /** The trip ID of the trip the vehicle is actively serving. Can be null if not provided. */
    val activeTripId: String?

    /** The distance, in meters, the vehicle has progressed along the active trip; or null. */
    val distanceAlongTrip: Double?

    /** The distance, in meters, the vehicle is scheduled to have progressed; or null. */
    val scheduledDistanceAlongTrip: Double?

    /** The total length of the trip, in meters; or null. */
    val totalDistanceAlongTrip: Double?

    /** The orientation of the vehicle, as an angle in degrees. Can be null. */
    val orientation: Double?

    /** Similar to [closestStop], but always the next stop. Can be null. */
    val nextStop: String?

    /** Similar to [closestStopTimeOffset], but always the next stop. Can be null. */
    val nextStopTimeOffset: Long?

    /** The current journey phase. Can be null. */
    val phase: String?

    /** The status modifiers for the trip, defined by [Status]. Can be null. */
    val status: Status?

    /** The last known real-time update for the vehicle, or 0 if we haven't heard from it. */
    val lastUpdateTime: Long

    /**
     * The last known location of the vehicle. Can be null. Differs from [position], which is
     * potentially extrapolated forward from the last known position and other data.
     */
    val lastKnownLocation: Location?

    /**
     * The last known real-time location update from the vehicle, or zero if we haven't had one.
     */
    val lastLocationUpdateTime: Long

    /** The last known distance along trip received in real-time, in meters (not extrapolated). */
    val lastKnownDistanceAlongTrip: Double?

    /** The last known orientation received in real-time. Can be null. */
    val lastKnownOrientation: Double?

    /** The index of the active trip into the sequence of trips for the active block. */
    val blockTripSequence: Int

    /** The real-time occupancy of the vehicle. */
    val occupancyStatus: Occupancy?

    /**
     * True if the server provided a real-time location for this vehicle — i.e. the trip is predicted
     * *and* we have a real-time location for it.
     *
     * "A location" means either [lastKnownLocation] (a raw GPS fix) or [position] (which, per its own
     * contract, "is only present if the trip is actively running"). Requiring [lastKnownLocation]
     * specifically wrongly flagged an actively-running, predicted vehicle as *scheduled* when the feed
     * populated only [position] — that marker was still drawn (and extrapolated) from [position], and
     * its arrival listings showed predictions, so styling it scheduled contradicted both. See #1621.
     *
     * The arrival listings answer the same "is this real-time?" question with `ObaArrivalInfo.predicted`
     * (bare [isPredicted], with no location requirement since a list row needs no coordinates); keep the
     * two in step if the feed's real-time semantics ever change.
     */
    val isLocationRealtime: Boolean
        get() = isPredicted && (lastKnownLocation != null || position != null)
}
