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
package org.onebusaway.android.ui.tripresults

/** One of the (up to three) itinerary option cards shown above the directions. */
data class ItineraryOption(
    val title: String,
    val durationText: String,
    val intervalText: String
)

/**
 * One step in the directions list — a Compose-ready projection of the legacy [Direction] +
 * `DirectionExpandableListAdapter`. [text] is the primary line (already prefixed with the step
 * number and, for transit, the time); transit steps add [placeAndHeadsign]/[agency]/[extra] detail
 * lines, and [subItems] holds the expandable sub-steps (intermediate stops / turn-by-turn).
 * [iconRes] is -1 when the step has no icon.
 */
data class DirectionItem(
    val iconRes: Int,
    val text: String,
    val placeAndHeadsign: String? = null,
    val agency: String? = null,
    val extra: String? = null,
    val isTransit: Boolean = false,
    val subItems: List<DirectionItem> = emptyList()
) {
    companion object {
        /** Sentinel for "no icon", matching the legacy Direction.getIcon() contract. */
        const val NO_ICON = -1
    }
}

/** UI state for the trip-planning results screen. */
sealed interface TripResultsUiState {

    data object Loading : TripResultsUiState

    /**
     * @param options the itinerary option cards (1–3)
     * @param selectedIndex the currently-selected option
     * @param directions the directions for the selected option
     * @param showMap whether the map tab (rather than the list) is selected
     */
    data class Success(
        val options: List<ItineraryOption>,
        val selectedIndex: Int,
        val directions: List<DirectionItem>,
        val showMap: Boolean
    ) : TripResultsUiState

    data class Error(val message: String) : TripResultsUiState
}
