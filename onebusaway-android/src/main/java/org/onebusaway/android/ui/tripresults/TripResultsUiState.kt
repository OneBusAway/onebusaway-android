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

import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint

/**
 * One of the (up to three) itinerary option cards shown above the directions. Carries structured data
 * (not pre-formatted strings) so the card can render route badges / a walk glyph, the ETA-pill duration,
 * and a device-localized time range:
 *  - [mode] — what the card's first line shows for the trip (route badges, a walk glyph, or a label).
 *  - [durationMinutes] — whole-minute trip length, formatted like the arrivals ETA pill.
 *  - [startTime]/[endTime] — the server-clock trip endpoints, unwrapped only at the time formatter.
 *  - [walkDistanceMeters] — total walking across the trip's legs, in meters; the card formats it to the
 *    user's units (miles/km, or feet/meters for short walks). 0 when the trip has no walking.
 */
data class ItineraryOption(
    val mode: ModeSummary,
    val durationMinutes: Long,
    val startTime: ServerTime,
    val endTime: ServerTime,
    val walkDistanceMeters: Double = 0.0,
)

/** What an option card's first line shows for the trip's modes (mutually exclusive by construction). */
sealed interface ModeSummary {
    /** A transit trip: its legs' route roundels, in order. */
    data class Routes(val badges: List<RouteBadge>) : ModeSummary

    /** A walk-only trip — shown as a walk glyph. */
    data object Walk : ModeSummary

    /** Any other non-transit trip (bike/car), as the legacy mode-label title. */
    data class Label(val text: String) : ModeSummary
}

/** A transit leg's route roundel data: its short name and (nullable) GTFS color as an ARGB int. */
data class RouteBadge(val shortName: String, val routeColor: Int?)

/**
 * One step in the directions list — a Compose-ready projection of the legacy [Direction] +
 * `DirectionExpandableListAdapter`. [text] is the primary line (already prefixed with the step
 * number and, for transit, the time); transit steps add [placeAndHeadsign]/[agency]/[extra] detail
 * lines, and [subItems] holds the expandable sub-steps (intermediate stops / turn-by-turn).
 * [iconRes] is -1 when the step has no icon. [focusPoint] is the geographic point the step refers to
 * (a leg endpoint, an intermediate stop, or a walk step) — non-null when the step can focus the map,
 * null when the underlying place had no coordinates.
 */
data class DirectionItem(
    val iconRes: Int,
    val text: String,
    val placeAndHeadsign: String? = null,
    val agency: String? = null,
    val extra: String? = null,
    val isTransit: Boolean = false,
    val subItems: List<DirectionItem> = emptyList(),
    val focusPoint: GeoPoint? = null,
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
     */
    data class Success(
        val options: List<ItineraryOption>,
        val selectedIndex: Int,
        val directions: List<DirectionItem>
    ) : TripResultsUiState

    data class Error(val message: String) : TripResultsUiState
}
