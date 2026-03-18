/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.math.speed

import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.extrapolation.math.DiracDistribution

/**
 * Estimates speed using the trip schedule: finds the two stops bracketing the vehicle's current
 * scheduled position and computes segment speed from the timetable.
 */
class ScheduleSpeedEstimator : SpeedEstimator {

        override fun estimateSpeed(
                tripId: String,
                queryTime: Long,
                dataManager: TripDataManager
        ): SpeedEstimateResult {
                val status =
                        dataManager.getLastState(tripId)
                                ?: return SpeedEstimateResult.Failure(
                                        SpeedEstimateError.InsufficientData("No state for trip")
                                )

                // Validate timestamp is not before the status was recorded
                if (queryTime < status.lastLocationUpdateTime) {
                        return SpeedEstimateResult.Failure(
                                SpeedEstimateError.TimestampOutOfBounds(
                                        "Timestamp is before vehicle state"
                                )
                        )
                }

                val currentDist =
                        status.scheduledDistanceAlongTrip
                                ?: return SpeedEstimateResult.Failure(
                                        SpeedEstimateError.InsufficientData(
                                                "No scheduled distance along trip"
                                        )
                                )

                val schedule =
                        dataManager.getSchedule(tripId)
                                ?: return SpeedEstimateResult.Failure(
                                        SpeedEstimateError.InsufficientData(
                                                "No schedule available for trip"
                                        )
                                )

                val stopTimes = schedule.stopTimes
                if (stopTimes == null || stopTimes.size < 2) {
                        return SpeedEstimateResult.Failure(
                                SpeedEstimateError.InsufficientData(
                                        "Insufficient stop times in schedule"
                                )
                        )
                }

                val segmentStart =
                        try {
                                schedule.findSegmentStartIndex(currentDist)
                        } catch (e: IndexOutOfBoundsException) {
                                return SpeedEstimateResult.Failure(
                                        SpeedEstimateError.InsufficientData(
                                                "Distance out of schedule bounds: ${e.message}"
                                        )
                                )
                        }

                val distDelta =
                        stopTimes[segmentStart + 1].distanceAlongTrip -
                                stopTimes[segmentStart].distanceAlongTrip
                val timeDelta =
                        stopTimes[segmentStart + 1].arrivalTime -
                                stopTimes[segmentStart].departureTime

                if (distDelta <= 0 || timeDelta <= 0) {
                        return SpeedEstimateResult.Failure(
                                SpeedEstimateError.InsufficientData(
                                        "Invalid distance or time delta between stops"
                                )
                        )
                }

                return SpeedEstimateResult.Success(DiracDistribution(distDelta / timeDelta))
        }
}
