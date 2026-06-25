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
package org.onebusaway.android.ui.mylists

import androidx.annotation.ColorRes

/**
 * A stop row for the My-tab starred/recent lists. [name] is the user-facing `UI_NAME` (reused for
 * the arrivals screen title and any launcher shortcut); [rawDirection] (N/S/…) feeds the arrivals
 * builder, while [directionText] is its resolved long form ("Northbound") for display, or null.
 *
 * [arrivals] is null for lists that don't show live arrivals (recents); the starred-stops list sets
 * it to [StopArrivals.Loading] until the first fetch, then [StopArrivals.Loaded].
 */
data class StopListItem(
    val id: String,
    val name: String,
    val rawDirection: String?,
    val directionText: String?,
    val lat: Double,
    val lon: Double,
    val isFavorite: Boolean,
    val arrivals: StopArrivals? = null
)

/** Per-stop live-arrivals state for the starred-stops list. */
sealed interface StopArrivals {
    /** Fetch in flight (no result yet) — the row shows a spinner. */
    data object Loading : StopArrivals

    /** Fetch complete; [badges] may be empty (no upcoming arrivals). */
    data class Loaded(val badges: List<ArrivalBadge>) : StopArrivals
}

/** One "route · ETA" arrival badge; [colorRes] is the lateness color. */
data class ArrivalBadge(
    val text: String,
    @ColorRes val colorRes: Int
)

/** A route row for the My-tab starred/recent lists. */
data class RouteListItem(
    val id: String,
    val shortName: String,
    val longName: String?,
    val url: String?
)

/**
 * A saved trip-reminder row (My Reminders). [tripId] + [stopId] identify the reminder; the display
 * strings ([name] with a "(no name)" fallback, formatted [headsign], "Route X" [routeText], and the
 * "Departs at …" [departureText]) are resolved in the repository.
 */
data class ReminderItem(
    val tripId: String,
    val stopId: String,
    val routeId: String,
    val name: String,
    val headsign: String?,
    val routeText: String?,
    val departureText: String
)
