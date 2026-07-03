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

import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.time.WallTime

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
 * Pure extrapolation strategy over a single immutable [TripState] snapshot. Subclasses implement
 * model-specific logic in [doExtrapolate]; [TripState.extrapolate] handles strategy selection and
 * common validation. Instances are created per snapshot, so any model fitting may be cached in
 * plain fields — snapshot identity is the invalidation.
 */
abstract class Extrapolator(protected val state: TripState) {
    /**
     * [lastTime] and [queryTime] are both device-wall-clock instants ([TripState.extrapolate] pairs
     * each server time with its local receive time), so their interval is unaffected by server/device
     * clock skew. The type makes that same-domain requirement a compile-time guarantee (#1620).
     */
    abstract fun doExtrapolate(
            lastDist: Double,
            lastTime: WallTime,
            queryTime: WallTime
    ): ExtrapolationResult
}
