/*
 * Copyright (C) 2018 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.nav.model

import android.location.Location

/**
 * A model class that holds path link information from one stop to another stop.  Multiple path links
 * form a [Path].
 */
data class PathLink

/**
 * Construct a path link between locations (stops)
 *
 * @param originLocation   User's origin location (may be null if origin isn't known)
 * @param secondToLast Second to last location (stop prior to destination stop)
 * @param destinationLocation     Destination location where the user wishes to exit the transit vehicle
 */
(val originLocation: Location?, val beforeLocation: Location, val destinationLocation: Location) {

    val pathLinkId: Int = 0

    val routeIdGtfs: String? = null

    val tripHeadsignGtfs: String? = null

    val directionIdGtfs: Int = 0

    val originStopIdGtfs: String? = null

    val destinationStopIdGtfs: String? = null

    val alertDistance: Float = 0.toFloat()

    var tripId: String? = null
}
