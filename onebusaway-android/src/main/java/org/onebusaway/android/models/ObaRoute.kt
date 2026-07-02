/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.models

/**
 * Interface defining a Route element.
 */
interface ObaRoute : ObaElement {

    /** The short name of the route (ex. "10", "30"). */
    val shortName: String?

    /** The long name of the route (ex. "Sandpoint/QueenAnne"). */
    val longName: String?

    /** The description of the route. */
    val description: String?

    /** The type of route. */
    val type: Int

    /** The url to the route schedule. */
    val url: String?

    /**
     * The integer representation of the Android color for the route line, or null if this value is
     * not included in the API response.
     */
    val color: Int?

    /**
     * The integer representation of the Android color for the route text, or null if this value is
     * not included in the API response.
     */
    val textColor: Int?

    /** The ID of the agency operating this route. */
    val agencyId: String

    companion object {
        const val TYPE_TRAM = 0
        const val TYPE_SUBWAY = 1
        const val TYPE_RAIL = 2
        const val TYPE_BUS = 3
        const val TYPE_FERRY = 4
        const val TYPE_CABLECAR = 5
        const val TYPE_GONDOLA = 6
        const val TYPE_FUNICULAR = 7 // You can't spell "funicular" without "fun"!

        const val NUM_TYPES = 8 // 8 types of transit supported by GTFS

        /**
         * Returns true if the given route type operates on dedicated right-of-way with no traffic
         * interference (light rail, subway, commuter rail, ferry).
         */
        @JvmStatic
        fun isGradeSeparated(type: Int): Boolean =
            type == TYPE_TRAM || type == TYPE_SUBWAY || type == TYPE_RAIL || type == TYPE_FERRY
    }
}
