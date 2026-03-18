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

/** Interface for estimating the speed of a transit vehicle. */
interface SpeedEstimator {

    /**
     * Estimates the speed distribution for a vehicle.
     *
     * @param tripId the active trip ID
     * @param queryTime the timestamp (ms since epoch) at which to estimate speed
     * @param dataManager the manager holding trip data (history, schedule, etc.)
     * @return a [SpeedEstimateResult] containing either a speed distribution (m/s) or an error
     */
    fun estimateSpeed(
            tripId: String,
            queryTime: Long,
            dataManager: TripDataManager
    ): SpeedEstimateResult
}
