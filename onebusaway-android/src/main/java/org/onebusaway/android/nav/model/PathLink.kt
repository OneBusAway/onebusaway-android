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
 * Construct a path link between the [originLocation] stop and [destinationLocation] stop, including
 * a [secondToLastLocation] stop prior to the [destinationLocation].  The [destinationLocation] is where
 * the user wishes to exit the transit vehicle.  The [startTime] is the system time in milliseconds
 * at which the navigation of this PathLink instance started.
 */
(val startTime: Long?, val originLocation: Location?, val secondToLastLocation: Location?, val destinationLocation: Location?, val tripId: String?) {

    val pathId: Int = 0

    val routeId: String? = null

    val tripHeadsign: String? = null

    val directionId: Int = 0

    val originStopId: String? = null

    val secondToLastStopId: String? = null

    val destinationStopId: String? = null

    val alertDistance: Float = 0.toFloat()
}
