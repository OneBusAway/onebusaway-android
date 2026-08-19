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

import android.location.Location

/**
 * Interface defining a Stop element.
 */
interface ObaStop : ObaElement {

    /** The passenger-facing stop identifier. */
    val stopCode: String?

    /** The passenger-facing name for the stop. */
    val name: String?

    /**
     * The location of the stop, which every stop has: OBA declares a stop's `lat`/`lon` as primitive
     * doubles (`StopBean`), so they are on the wire for every stop entry and are never absent. There
     * is therefore no "unlocated stop" state here to read back — and reading a 0 coordinate as one
     * would be a guess about a stop sitting at null island, not a fact the source states.
     */
    val location: Location

    /** The latitude of the stop; see [location] — always the stop's real latitude. */
    val latitude: Double

    /** The longitude of the stop; see [location] — always the stop's real longitude. */
    val longitude: Double

    /** The direction of the stop (ex "NW", "E"). */
    val direction: String?

    /** The location type. */
    val locationType: Int

    /** The list of route IDs serving this stop. */
    val routeIds: Array<String>

    /** The wheelchair-boarding accessibility of the stop; [WheelchairBoarding.UNKNOWN] when unstated. */
    val wheelchairBoarding: WheelchairBoarding

    companion object {
        const val LOCATION_STOP = 0
        const val LOCATION_STATION = 1
    }
}
