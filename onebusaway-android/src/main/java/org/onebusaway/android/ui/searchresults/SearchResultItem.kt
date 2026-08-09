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

import org.onebusaway.android.api.data.VehicleAssignment

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
     * [assignment] is the data layer's [VehicleAssignment] as-is: it already carries exactly what the
     * row renders (the ride, or which of the two rideless cases this is — the server's own "no trip"
     * answer captions differently from a lookup that never got one), so mirroring it into a parallel
     * UI enum would only add a hand-synced copy to keep in step.
     *
     * @param id the agency-prefixed OBA vehicle id (`1_4531`), used only as the row key
     * @param coachNumber the number as a rider reads it off the vehicle, shown as the row's title
     * @param agency the operating agency's display name
     * @param assignment what it is running; anything but [VehicleAssignment.OnTrip] makes the row
     *   unselectable rather than hiding the match
     */
    data class Vehicle(
        val id: String,
        val coachNumber: String,
        val agency: String,
        val assignment: VehicleAssignment
    ) : SearchResultItem
}
