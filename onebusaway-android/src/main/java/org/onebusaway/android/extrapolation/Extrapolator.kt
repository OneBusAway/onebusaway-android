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
package org.onebusaway.android.extrapolation

import org.onebusaway.android.extrapolation.data.Trip
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution

/** Result of an extrapolation attempt. */
sealed class ExtrapolationResult {
    /** Extrapolation succeeded. */
    class Success(val distribution: ProbDistribution) : ExtrapolationResult()
    /** No valid vehicle data exists for the trip. */
    object NoData : ExtrapolationResult()
    /** Vehicle data is older than the extrapolation horizon. */
    object Stale : ExtrapolationResult()
    /** Vehicle is at the trip start before scheduled departure. */
    object TripNotStarted : ExtrapolationResult()
    /** Vehicle is at or near the end of the trip. */
    object TripEnded : ExtrapolationResult()
    /** Required schedule data is missing. */
    object MissingSchedule : ExtrapolationResult()
}

/**
 * Pure extrapolation strategy. Subclasses implement model-specific logic in [doExtrapolate]. The
 * [Trip] class handles strategy selection and common validation; this is just the model.
 */
abstract class Extrapolator(protected val trip: Trip) {
    /**
     * [lastTimeMs] and [queryTimeMs] must be in the same clock domain; [Trip.extrapolate] passes
     * device-local clock values so the interval is unaffected by server/device clock skew.
     */
    abstract fun doExtrapolate(
            lastDist: Double,
            lastTimeMs: Long,
            queryTimeMs: Long
    ): ExtrapolationResult
}
