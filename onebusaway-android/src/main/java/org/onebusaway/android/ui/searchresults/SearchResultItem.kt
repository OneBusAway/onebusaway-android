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
package org.onebusaway.android.ui.searchresults

/**
 * One row of the combined search results list — a matching route, stop, or vehicle. The screen
 * renders these into a single heterogeneous list (routes first, then stops, then vehicles; the first
 * two orderings match the legacy screen, and coach-number hits come last so a numeric query still
 * leads with the route/stop it names).
 */
sealed interface SearchResultItem {

    /**
     * @param longName secondary name (long name or description), or null when there is none
     * @param url the route's schedule page, used when registering the route in recents
     * @param routeColor the route's GTFS color as an Android ARGB int, or null when unset; drives the
     *   route-colored [LineBadge] chip (a null falls back to the neutral chip)
     * @param agency the operating agency's display name, or null/blank when unknown (the row omits it)
     */
    data class Route(
        val id: String,
        val shortName: String,
        val longName: String?,
        val url: String?,
        val routeColor: Int? = null,
        val agency: String? = null
    ) : SearchResultItem

    /** @param direction raw compass direction code ("N", "SW", ...); empty when unknown. */
    data class Stop(
        val id: String,
        val name: String,
        val direction: String,
        val isFavorite: Boolean,
        val latitude: Double,
        val longitude: Double
    ) : SearchResultItem

    /**
     * A vehicle matched by its coach number — the number painted on the bus, which riders use to
     * find a specific vehicle (e.g. to meet someone riding it).
     *
     * @param id the agency-prefixed OBA vehicle id (`1_4531`), used only as the row key
     * @param coachNumber the number as a rider reads it off the vehicle, shown as the row's title
     * @param agency the operating agency's display name
     * @param status what it is running, or why there's nothing to open — anything but [Status.OnRide]
     *   makes the row unselectable rather than hiding the match
     */
    data class Vehicle(
        val id: String,
        val coachNumber: String,
        val agency: String,
        val status: Status
    ) : SearchResultItem {

        /**
         * What the row can say about the vehicle. The two rideless cases stay distinct because they
         * caption differently: the server telling us a coach is off duty is not the same as our
         * failing to ask it.
         */
        sealed interface Status {

            /** On [ride] — the row opens the map on it. */
            data class OnRide(val ride: Ride) : Status

            /** The server says it isn't running a trip right now. */
            data object NotInService : Status

            /** Its status couldn't be looked up, so the row reports the match and nothing more. */
            data object Unknown : Status
        }

        /**
         * The ride a matched vehicle is on: the ids the map needs to drill into it, plus the labels
         * that let a rider recognize it in the list before tapping.
         *
         * @param routeShortName the route's badge text, or null when unknown
         * @param routeColor the route's GTFS color as an Android ARGB int, or null when unset
         * @param headsign where the vehicle is headed, or null when unknown
         */
        data class Ride(
            val routeId: String,
            val tripId: String,
            val routeShortName: String?,
            val routeColor: Int?,
            val headsign: String?
        )
    }
}
