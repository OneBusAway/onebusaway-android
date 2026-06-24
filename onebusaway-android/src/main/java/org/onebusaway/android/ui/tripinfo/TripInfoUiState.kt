/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.tripinfo

/** UI state for the trip-reminder editor (Trip Info) screen. */
sealed interface TripInfoUiState {

    /** The trip row (and any missing display names) are still being loaded. */
    data object Loading : TripInfoUiState

    /**
     * The editable reminder form. The header strings ([stopName], [routeName], [headsign],
     * [departureText]) are display-ready; [reminderOptions] are the valid lead times for this
     * departure (later choices drop off as the departure approaches) with [reminderSelection]
     * indexing into them. [isNewTrip] hides the delete action; [isSaving] shows the progress
     * overlay while the alarm is registered with the server.
     */
    data class Content(
        val stopName: String,
        val routeName: String,
        val headsign: String,
        val departureText: String,
        val reminderOptions: List<String>,
        val reminderSelection: Int,
        val tripName: String,
        val isNewTrip: Boolean,
        val isSaving: Boolean = false
    ) : TripInfoUiState
}

/** One-shot results of a save attempt, surfaced by the host as a toast (+ finish on success). */
sealed interface TripInfoEvent {
    data object Saved : TripInfoEvent
    data object SaveFailed : TripInfoEvent
}
