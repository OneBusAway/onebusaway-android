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
package org.onebusaway.android.ui.dataview

/**
 * State for the trip trajectory debug screen: a header summary plus the distance-vs-time
 * [trajectory] to plot. Rebuilt each UI tick from the latest store snapshot.
 */
data class TripTrajectoryUiState(
    val tripId: String,
    val vehicleId: String?,
    val sampleCount: Int,
    /** The reporting vehicle has rolled onto its next run, so this trip is no longer being served. */
    val tripEnded: Boolean,
    val trajectory: TripTrajectory,
) {
    companion object {
        /** The pre-data state: header known, nothing plotted yet. */
        fun empty(tripId: String) =
            TripTrajectoryUiState(tripId, vehicleId = null, sampleCount = 0, tripEnded = false, trajectory = TripTrajectory())
    }
}
