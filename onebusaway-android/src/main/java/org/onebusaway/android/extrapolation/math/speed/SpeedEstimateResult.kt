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

import org.onebusaway.android.extrapolation.math.SpeedDistribution

/**
 * Result of a speed estimation attempt. Either a successful distribution or an error describing why
 * estimation failed.
 */
sealed class SpeedEstimateResult {

    /** Successful speed estimation. */
    data class Success(val distribution: SpeedDistribution) : SpeedEstimateResult()

    /** Speed estimation failed. */
    data class Failure(val error: SpeedEstimateError) : SpeedEstimateResult()
}

/** Reasons why speed estimation can fail. */
sealed class SpeedEstimateError {

    /** The requested timestamp is outside the valid time bounds for estimation. */
    data class TimestampOutOfBounds(val reason: String) : SpeedEstimateError()

    /**
     * Insufficient data to estimate speed (e.g., no active trip, no schedule, trip not started, not
     * enough AVL history).
     */
    data class InsufficientData(val reason: String) : SpeedEstimateError()
}
